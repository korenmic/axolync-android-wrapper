use rand::seq::SliceRandom;
use reqwest::Method;
use rusqlite::{params, Connection, OpenFlags};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::collections::HashMap;
use std::fs;
use std::fs::File;
use std::io::{Cursor, Read, Write};
use std::net::{TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::Duration;
use url::Url;
use uuid::Uuid;
use zip::ZipArchive;

const HOST_FAMILY: &str = "tauri";
const HOST_PLATFORM: &str = "desktop";
const UNSUPPORTED_BUNDLE_MESSAGE: &str = "Native bridge is unavailable in this bundle for the current host.";
const MAX_NATIVE_BRIDGE_DIAGNOSTICS: usize = 200;
const EMBEDDED_NATIVE_SERVICE_COMPANION_MANIFEST_JSON: &str =
    include_str!("generated_native_service_companions.json");
const EMBEDDED_NATIVE_SERVICE_COMPANION_RUNTIME_CONFIG_JSON: &str =
    include_str!("generated_native_service_companion_runtime_config.json");

#[derive(Clone, Serialize)]
struct NativeCompanionStatus {
    state: String,
    available: bool,
    enabled: bool,
    #[serde(rename = "lastError")]
    last_error: Option<String>,
}

#[derive(Clone, Serialize)]
struct NativeCompanionStatusEnvelope {
    #[serde(rename = "addonId")]
    addon_id: String,
    #[serde(rename = "companionId")]
    companion_id: String,
    status: NativeCompanionStatus,
}

#[derive(Clone, Serialize)]
struct NativeConnectionHandle {
    kind: String,
    #[serde(rename = "baseUrl")]
    base_url: String,
}

#[derive(Clone, Serialize)]
struct NativeCompanionConnectionEnvelope {
    #[serde(rename = "addonId")]
    addon_id: String,
    #[serde(rename = "companionId")]
    companion_id: String,
    connection: Option<NativeConnectionHandle>,
}

#[derive(Clone, Serialize)]
struct NativeCompanionResponse {
    ok: bool,
    payload: Value,
    error: Option<String>,
}

#[derive(Clone, Serialize)]
struct NativeCompanionResponseEnvelope {
    #[serde(rename = "addonId")]
    addon_id: String,
    #[serde(rename = "companionId")]
    companion_id: String,
    response: NativeCompanionResponse,
}

#[derive(Clone, Serialize)]
struct NativeDiagnosticEnvelope {
    #[serde(rename = "hostFamily")]
    host_family: String,
    #[serde(rename = "hostPlatform")]
    host_platform: String,
    #[serde(rename = "hostAbi")]
    host_abi: String,
    #[serde(rename = "generatedAtMs")]
    generated_at_ms: i64,
    #[serde(rename = "collectionMethod")]
    collection_method: String,
    logs: Vec<Value>,
}

#[derive(Clone, Deserialize)]
pub struct NativeCompanionRequest {
    operation: String,
    payload: Value,
}

#[derive(Clone, Deserialize)]
struct EmbeddedNativeCompanionManifest {
    companions: Vec<EmbeddedNativeCompanionRegistration>,
}

#[derive(Clone, Deserialize)]
struct NativeCompanionRuntimeConfig {
    #[serde(rename = "nativeRuntimeDiagnosticsFile", default)]
    native_runtime_diagnostics_file: Option<String>,
}

#[derive(Clone, Deserialize)]
struct EmbeddedNativeCompanionRegistration {
    #[serde(rename = "addonId")]
    addon_id: String,
    #[serde(rename = "companionId")]
    companion_id: String,
    #[serde(rename = "displayName")]
    display_name: String,
    wrapper: String,
    entrypoint: String,
    operator: RuntimeOperatorDescriptor,
}

#[derive(Clone, Deserialize)]
struct RuntimeOperatorDescriptor {
    #[serde(rename = "runtime_operator_kind")]
    runtime_operator_kind: String,
    #[serde(rename = "listen_path", default)]
    listen_path: Option<String>,
    #[serde(rename = "upstream_method", default)]
    upstream_method: Option<String>,
    #[serde(rename = "upstream_url_template", default)]
    upstream_url_template: Option<String>,
    #[serde(rename = "content_language", default)]
    content_language: Option<String>,
    #[serde(default)]
    geo: Option<RuntimeOperatorGeo>,
    #[serde(rename = "user_agents", default)]
    user_agents: Vec<String>,
    #[serde(default)]
    timezones: Vec<String>,
    #[serde(default)]
    db: Option<RuntimeOperatorDbConfig>,
    #[serde(rename = "database_env", default)]
    database_env: Option<String>,
    #[serde(rename = "local_result_header", default)]
    local_result_header: Option<String>,
}

#[derive(Clone, Deserialize)]
struct RuntimeOperatorGeo {
    altitude: f64,
    latitude: f64,
    longitude: f64,
}

#[derive(Clone, Deserialize)]
struct RuntimeOperatorDbConfig {
    #[serde(rename = "compressedAssetPath", default)]
    compressed_asset_path: Option<String>,
    #[serde(rename = "packagedAssetPath", default)]
    packaged_asset_path: Option<String>,
    #[serde(rename = "packagedProvenancePath", default)]
    packaged_provenance_path: Option<String>,
    #[serde(rename = "deployedFileName", default)]
    deployed_file_name: Option<String>,
    #[serde(rename = "deployPolicy", default)]
    deploy_policy: Option<String>,
    #[serde(rename = "sqliteHeaderRequired", default)]
    sqlite_header_required: bool,
}

#[derive(Clone, Serialize)]
struct LrclibDbDeployment {
    #[serde(rename = "compressedAssetPath")]
    compressed_asset_path: String,
    #[serde(rename = "compressedAssetSha256")]
    compressed_asset_sha256: String,
    #[serde(rename = "deployedDbPath")]
    deployed_db_path: String,
    #[serde(rename = "deploymentStatePath")]
    deployment_state_path: String,
    deployed: bool,
    #[serde(rename = "reusedExisting")]
    reused_existing: bool,
}

struct ResolvedPackagedDbAsset {
    descriptor_path: String,
    source_label: String,
    archive_entry: Option<String>,
    bytes: Vec<u8>,
}

struct RunningRuntimeOperator {
    stop_flag: Arc<AtomicBool>,
    join_handle: Option<JoinHandle<()>>,
    connection: NativeConnectionHandle,
}

impl RunningRuntimeOperator {
    fn stop(mut self) {
        self.stop_flag.store(true, Ordering::SeqCst);
        if let Some(join_handle) = self.join_handle.take() {
            let _ = join_handle.join();
        }
    }
}

struct NativeCompanionRuntimeState {
    enabled: bool,
    state: String,
    last_error: Option<String>,
    connection: Option<NativeConnectionHandle>,
    runtime_operator: Option<RunningRuntimeOperator>,
}

impl Default for NativeCompanionRuntimeState {
    fn default() -> Self {
        Self {
            enabled: false,
            state: String::from("idle"),
            last_error: None,
            connection: None,
            runtime_operator: None,
        }
    }
}

struct NativeCompanionHostState {
    registration_by_key: HashMap<String, EmbeddedNativeCompanionRegistration>,
    state_by_key: Mutex<HashMap<String, NativeCompanionRuntimeState>>,
    diagnostics: Arc<Mutex<Vec<Value>>>,
}

impl NativeCompanionHostState {
    fn load() -> Self {
        let parsed: EmbeddedNativeCompanionManifest = serde_json::from_str(
            EMBEDDED_NATIVE_SERVICE_COMPANION_MANIFEST_JSON,
        )
        .unwrap_or(EmbeddedNativeCompanionManifest {
            companions: Vec::new(),
        });
        let registration_by_key: HashMap<String, EmbeddedNativeCompanionRegistration> = parsed
            .companions
            .into_iter()
            .filter(|registration| registration.wrapper == HOST_FAMILY)
            .map(|registration| {
                (
                    companion_key(&registration.addon_id, &registration.companion_id),
                    registration,
                )
            })
            .collect();
        let diagnostics = Arc::new(Mutex::new(Vec::new()));
        push_diagnostic(
            &diagnostics,
            "wrapper-host",
            "info",
            None,
            None,
            "host.registrations.loaded",
            json!({
                "count": registration_by_key.len(),
                "diagnosticsFile": configured_native_runtime_diagnostics_file_path()
                    .map(|path| path.to_string_lossy().to_string()),
            }),
        );
        Self {
            registration_by_key,
            state_by_key: Mutex::new(HashMap::new()),
            diagnostics,
        }
    }

    fn get_registration(
        &self,
        addon_id: &str,
        companion_id: &str,
    ) -> Option<&EmbeddedNativeCompanionRegistration> {
        self.registration_by_key
            .get(&companion_key(addon_id, companion_id))
    }

    fn with_runtime_state_mut<F, T>(&self, addon_id: &str, companion_id: &str, callback: F) -> T
    where
        F: FnOnce(&mut NativeCompanionRuntimeState) -> T,
    {
        let key = companion_key(addon_id, companion_id);
        let mut guard = self
            .state_by_key
            .lock()
            .expect("native companion state mutex poisoned");
        let state = guard.entry(key).or_default();
        callback(state)
    }

    fn unsupported_status(
        &self,
        addon_id: &str,
        companion_id: &str,
        last_error: Option<String>,
    ) -> NativeCompanionStatusEnvelope {
        NativeCompanionStatusEnvelope {
            addon_id: addon_id.to_string(),
            companion_id: companion_id.to_string(),
            status: NativeCompanionStatus {
                state: String::from("unsupported"),
                available: false,
                enabled: false,
                last_error,
            },
        }
    }

    fn get_status(
        &self,
        addon_id: &str,
        companion_id: &str,
    ) -> NativeCompanionStatusEnvelope {
        push_diagnostic(
            &self.diagnostics,
            "wrapper-host",
            "info",
            Some(addon_id),
            Some(companion_id),
            "companion.status.requested",
            Value::Null,
        );
        if self.get_registration(addon_id, companion_id).is_none() {
            return self.unsupported_status(addon_id, companion_id, Some(String::from(UNSUPPORTED_BUNDLE_MESSAGE)));
        }
        self.with_runtime_state_mut(addon_id, companion_id, |state| NativeCompanionStatusEnvelope {
            addon_id: addon_id.to_string(),
            companion_id: companion_id.to_string(),
            status: NativeCompanionStatus {
                state: state.state.clone(),
                available: true,
                enabled: state.enabled,
                last_error: state.last_error.clone(),
            },
        })
    }

    fn set_enabled(
        &self,
        addon_id: &str,
        companion_id: &str,
        enabled: bool,
    ) -> NativeCompanionStatusEnvelope {
        push_diagnostic(
            &self.diagnostics,
            "wrapper-host",
            "info",
            Some(addon_id),
            Some(companion_id),
            "companion.enabled.updated",
            json!({ "enabled": enabled }),
        );
        if self.get_registration(addon_id, companion_id).is_none() {
            return self.unsupported_status(addon_id, companion_id, Some(String::from(UNSUPPORTED_BUNDLE_MESSAGE)));
        }
        self.with_runtime_state_mut(addon_id, companion_id, |state| {
            state.enabled = enabled;
            if !enabled {
                if let Some(runtime_operator) = state.runtime_operator.take() {
                    runtime_operator.stop();
                }
                state.connection = None;
                state.last_error = None;
                state.state = String::from("idle");
            }
        });
        self.get_status(addon_id, companion_id)
    }

    fn start(
        &self,
        addon_id: &str,
        companion_id: &str,
    ) -> NativeCompanionStatusEnvelope {
        push_diagnostic(
            &self.diagnostics,
            "wrapper-host",
            "info",
            Some(addon_id),
            Some(companion_id),
            "companion.start.requested",
            Value::Null,
        );
        let registration = match self.get_registration(addon_id, companion_id) {
            Some(registration) => registration.clone(),
            None => {
                return self.unsupported_status(
                    addon_id,
                    companion_id,
                    Some(String::from(UNSUPPORTED_BUNDLE_MESSAGE)),
                )
            }
        };
        self.with_runtime_state_mut(addon_id, companion_id, |state| {
            state.enabled = true;
            if state.runtime_operator.is_some() {
                state.state = String::from("running");
                state.last_error = None;
                return;
            }
            state.state = String::from("starting");
            match start_runtime_operator(
                addon_id,
                companion_id,
                &registration,
                Arc::clone(&self.diagnostics),
            ) {
                Ok(runtime_operator) => {
                    state.connection = Some(runtime_operator.connection.clone());
                    state.runtime_operator = Some(runtime_operator);
                    state.last_error = None;
                    state.state = String::from("running");
                }
                Err(error) => {
                    state.connection = None;
                    state.runtime_operator = None;
                    state.last_error = Some(error);
                    state.state = String::from("error");
                }
            }
        });
        self.get_status(addon_id, companion_id)
    }

    fn stop(
        &self,
        addon_id: &str,
        companion_id: &str,
    ) -> NativeCompanionStatusEnvelope {
        push_diagnostic(
            &self.diagnostics,
            "wrapper-host",
            "info",
            Some(addon_id),
            Some(companion_id),
            "companion.stop.requested",
            Value::Null,
        );
        if self.get_registration(addon_id, companion_id).is_none() {
            return self.unsupported_status(addon_id, companion_id, Some(String::from(UNSUPPORTED_BUNDLE_MESSAGE)));
        }
        self.with_runtime_state_mut(addon_id, companion_id, |state| {
            if let Some(runtime_operator) = state.runtime_operator.take() {
                runtime_operator.stop();
            }
            state.connection = None;
            state.last_error = None;
            state.state = String::from("idle");
        });
        self.get_status(addon_id, companion_id)
    }

    fn request(
        &self,
        addon_id: &str,
        companion_id: &str,
        request: NativeCompanionRequest,
    ) -> NativeCompanionResponseEnvelope {
        push_diagnostic(
            &self.diagnostics,
            "wrapper-host",
            "info",
            Some(addon_id),
            Some(companion_id),
            "companion.request.received",
            json!({ "operation": request.operation }),
        );
        let registration = match self.get_registration(addon_id, companion_id) {
            Some(registration) => registration,
            None => {
                return build_response_envelope(
                    addon_id,
                    companion_id,
                    NativeCompanionResponse {
                        ok: false,
                        payload: Value::Null,
                        error: Some(String::from(UNSUPPORTED_BUNDLE_MESSAGE)),
                    },
                )
            }
        };
        let _payload = request.payload;
        self.with_runtime_state_mut(addon_id, companion_id, |state| {
            if state.runtime_operator.is_none() {
                return build_response_envelope(
                    addon_id,
                    companion_id,
                    NativeCompanionResponse {
                        ok: false,
                        payload: Value::Null,
                        error: Some(String::from("Native bridge runtime operator is not running.")),
                    },
                );
            }
            if request.operation == "proxy.health" {
                return build_response_envelope(
                    addon_id,
                    companion_id,
                    NativeCompanionResponse {
                        ok: true,
                        payload: json!({
                            "ok": true,
                            "addonId": addon_id,
                            "companionId": companion_id,
                            "displayName": registration.display_name,
                            "entrypoint": registration.entrypoint,
                            "hostFamily": HOST_FAMILY,
                            "baseUrl": state.connection.as_ref().map(|connection| connection.base_url.clone()),
                        }),
                        error: None,
                    },
                );
            }
            state.last_error = Some(format!(
                "Unsupported native bridge operation \"{}\".",
                request.operation
            ));
            state.state = String::from("error");
            build_response_envelope(
                addon_id,
                companion_id,
                NativeCompanionResponse {
                    ok: false,
                    payload: Value::Null,
                    error: state.last_error.clone(),
                },
            )
        })
    }

    fn get_connection(
        &self,
        addon_id: &str,
        companion_id: &str,
    ) -> NativeCompanionConnectionEnvelope {
        push_diagnostic(
            &self.diagnostics,
            "wrapper-host",
            "info",
            Some(addon_id),
            Some(companion_id),
            "companion.connection.requested",
            Value::Null,
        );
        if self.get_registration(addon_id, companion_id).is_none() {
            return NativeCompanionConnectionEnvelope {
                addon_id: addon_id.to_string(),
                companion_id: companion_id.to_string(),
                connection: None,
            };
        }
        self.with_runtime_state_mut(addon_id, companion_id, |state| NativeCompanionConnectionEnvelope {
            addon_id: addon_id.to_string(),
            companion_id: companion_id.to_string(),
            connection: state.connection.clone(),
        })
    }

    fn get_diagnostics(&self) -> NativeDiagnosticEnvelope {
        NativeDiagnosticEnvelope {
            host_family: String::from(HOST_FAMILY),
            host_platform: String::from(HOST_PLATFORM),
            host_abi: String::from(std::env::consts::ARCH),
            generated_at_ms: chrono_like_now_ms(),
            collection_method: String::from("native-bridge-host"),
            logs: self
                .diagnostics
                .lock()
                .expect("native companion diagnostics mutex poisoned")
                .clone(),
        }
    }
}

fn push_diagnostic(
    diagnostics: &Arc<Mutex<Vec<Value>>>,
    source: &str,
    level: &str,
    addon_id: Option<&str>,
    companion_id: Option<&str>,
    event: &str,
    details: Value,
) {
    let entry = json!({
        "atMs": chrono_like_now_ms(),
        "source": source,
        "level": level,
        "addonId": addon_id,
        "companionId": companion_id,
        "event": event,
        "details": details,
    });
    let mut guard = diagnostics
        .lock()
        .expect("native companion diagnostics mutex poisoned");
    guard.push(entry.clone());
    if guard.len() > MAX_NATIVE_BRIDGE_DIAGNOSTICS {
        let drain_count = guard.len() - MAX_NATIVE_BRIDGE_DIAGNOSTICS;
        guard.drain(0..drain_count);
    }
    drop(guard);
    append_diagnostic_to_configured_file(&entry);
}

fn configured_native_runtime_diagnostics_file_path() -> Option<PathBuf> {
    if let Ok(explicit) = std::env::var("AXOLYNC_NATIVE_RUNTIME_DIAGNOSTICS_FILE") {
        let trimmed = explicit.trim();
        if !trimmed.is_empty() {
            return Some(PathBuf::from(trimmed));
        }
    }
    let parsed: NativeCompanionRuntimeConfig = serde_json::from_str(
        EMBEDDED_NATIVE_SERVICE_COMPANION_RUNTIME_CONFIG_JSON,
    )
    .unwrap_or(NativeCompanionRuntimeConfig {
        native_runtime_diagnostics_file: None,
    });
    parsed
        .native_runtime_diagnostics_file
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(PathBuf::from)
}

fn append_diagnostic_to_configured_file(entry: &Value) {
    let Some(path) = configured_native_runtime_diagnostics_file_path() else {
        return;
    };
    if let Some(parent) = path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    if let Ok(mut file) = fs::OpenOptions::new().create(true).append(true).open(&path) {
        let _ = writeln!(file, "{}", entry);
    }
}

fn companion_key(addon_id: &str, companion_id: &str) -> String {
    format!("{}::{}", addon_id.trim(), companion_id.trim())
}

fn build_response_envelope(
    addon_id: &str,
    companion_id: &str,
    response: NativeCompanionResponse,
) -> NativeCompanionResponseEnvelope {
    NativeCompanionResponseEnvelope {
        addon_id: addon_id.to_string(),
        companion_id: companion_id.to_string(),
        response,
    }
}

fn required_descriptor_string(value: &Option<String>, field_name: &str) -> Result<String, String> {
    match value {
        Some(value) if !value.trim().is_empty() => Ok(value.trim().to_string()),
        _ => Err(format!("Runtime operator descriptor is missing {}.", field_name)),
    }
}

fn normalize_relative_descriptor_path(value: &Option<String>) -> Option<PathBuf> {
    value
        .as_ref()
        .map(|value| value.trim())
        .filter(|value| !value.is_empty())
        .map(|value| PathBuf::from(value.replace('\\', "/")))
        .filter(|path| path.is_relative())
}

fn axolync_native_data_root() -> PathBuf {
    if let Ok(explicit) = std::env::var("AXOLYNC_NATIVE_DATA_DIR") {
        let trimmed = explicit.trim();
        if !trimmed.is_empty() {
            return PathBuf::from(trimmed);
        }
    }
    if cfg!(target_os = "windows") {
        if let Ok(local_app_data) = std::env::var("LOCALAPPDATA") {
            return PathBuf::from(local_app_data).join("Axolync").join("native-service-companions");
        }
    }
    if let Ok(home) = std::env::var("HOME") {
        return PathBuf::from(home)
            .join(".local")
            .join("share")
            .join("axolync")
            .join("native-service-companions");
    }
    std::env::temp_dir().join("axolync").join("native-service-companions")
}

fn resolve_packaged_db_asset_path(
    addon_id: &str,
    registration_root: &Path,
    db: &RuntimeOperatorDbConfig,
) -> Result<ResolvedPackagedDbAsset, String> {
    let packaged_path = normalize_relative_descriptor_path(&db.packaged_asset_path)
        .or_else(|| normalize_relative_descriptor_path(&db.compressed_asset_path))
        .ok_or_else(|| String::from("LRCLIB DB descriptor is missing packagedAssetPath or compressedAssetPath."))?;
    let packaged_path_label = packaged_path.to_string_lossy().to_string();
    let mut candidates: Vec<PathBuf> = Vec::new();

    // Packaged addon zips describe shared native assets relative to the payload root,
    // but desktop wrapper staging may place those assets beside the wrapper-specific
    // entrypoint root or beside one of its parents. Search both shapes before failing.
    for root in registration_root.ancestors().take(8) {
        let candidate = root.join(&packaged_path);
        if !candidates.iter().any(|existing| existing == &candidate) {
            candidates.push(candidate);
        }
    }

    if let Some(candidate) = candidates.iter().find(|candidate| candidate.exists()) {
        let bytes = fs::read(candidate).map_err(|error| error.to_string())?;
        return Ok(ResolvedPackagedDbAsset {
            descriptor_path: packaged_path_label,
            source_label: candidate.to_string_lossy().to_string(),
            archive_entry: None,
            bytes,
        });
    }

    let addon_zip_name = format!("{}.zip", addon_id);
    let mut zip_candidates: Vec<PathBuf> = Vec::new();
    for root in registration_root.ancestors().take(8) {
        let candidate = root.join("plugins").join("preinstalled").join(&addon_zip_name);
        if !zip_candidates.iter().any(|existing| existing == &candidate) {
            zip_candidates.push(candidate);
        }
    }
    for zip_candidate in &zip_candidates {
        if !zip_candidate.exists() {
            continue;
        }
        let zip_file = File::open(zip_candidate).map_err(|error| error.to_string())?;
        let mut archive = ZipArchive::new(zip_file).map_err(|error| error.to_string())?;
        {
            if let Ok(mut entry) = archive.by_name(&packaged_path_label) {
                let mut bytes = Vec::new();
                entry.read_to_end(&mut bytes).map_err(|error| error.to_string())?;
                return Ok(ResolvedPackagedDbAsset {
                    descriptor_path: packaged_path_label.clone(),
                    source_label: zip_candidate.to_string_lossy().to_string(),
                    archive_entry: Some(packaged_path_label.clone()),
                    bytes,
                });
            }
        };
    }

    let searched = candidates
        .iter()
        .chain(zip_candidates.iter())
        .map(|candidate| candidate.to_string_lossy().to_string())
        .collect::<Vec<_>>()
        .join(" | ");
    Err(format!(
        "Packaged LRCLIB compressed DB asset is missing for descriptor path {}. Searched: {}",
        packaged_path_label,
        searched
    ))
}

fn resolve_registration_root(entrypoint: &str) -> Result<PathBuf, String> {
    let normalized_entrypoint = PathBuf::from(entrypoint.trim().replace('\\', "/"));
    if normalized_entrypoint.as_os_str().is_empty() || !normalized_entrypoint.is_relative() {
        return Err(format!("Native service companion entrypoint must be relative: {}", entrypoint));
    }

    let mut candidates: Vec<PathBuf> = Vec::new();
    if let Ok(current_dir) = std::env::current_dir() {
        candidates.push(current_dir.join("app").join(&normalized_entrypoint));
        candidates.push(current_dir.join(&normalized_entrypoint));
    }
    if let Ok(exe_path) = std::env::current_exe() {
        if let Some(exe_dir) = exe_path.parent() {
            candidates.push(exe_dir.join("app").join(&normalized_entrypoint));
            candidates.push(exe_dir.join(&normalized_entrypoint));
        }
    }

    for candidate in &candidates {
        if candidate.exists() {
            return candidate
                .parent()
                .map(Path::to_path_buf)
                .ok_or_else(|| format!("Native service companion entrypoint has no parent: {}", candidate.display()));
        }
    }

    let searched = candidates
        .iter()
        .map(|candidate| candidate.to_string_lossy().to_string())
        .collect::<Vec<_>>()
        .join(" | ");
    Err(format!(
        "Native service companion entrypoint was not found for {}. Searched: {}",
        entrypoint,
        searched
    ))
}

fn verify_sqlite_header_if_required(path: &Path, required: bool) -> Result<(), String> {
    if !required {
        return Ok(());
    }
    let mut file = File::open(path).map_err(|error| error.to_string())?;
    let mut header = [0_u8; 16];
    file.read_exact(&mut header).map_err(|error| error.to_string())?;
    if &header != b"SQLite format 3\0" {
        return Err(String::from("Deployed LRCLIB DB does not have a SQLite header."));
    }
    Ok(())
}

fn deploy_brotli_db_once(
    addon_id: &str,
    companion_id: &str,
    registration_root: &Path,
    descriptor: &RuntimeOperatorDescriptor,
    diagnostics: &Arc<Mutex<Vec<Value>>>,
) -> Result<LrclibDbDeployment, String> {
    let db = descriptor
        .db
        .as_ref()
        .ok_or_else(|| String::from("LRCLIB runtime operator descriptor is missing db."))?;
    if db.deploy_policy.as_deref().unwrap_or("once-per-compressed-sha256") != "once-per-compressed-sha256" {
        return Err(String::from("Unsupported LRCLIB DB deploy policy."));
    }
    let packaged_asset = resolve_packaged_db_asset_path(addon_id, registration_root, db)?;
    push_diagnostic(
        diagnostics,
        "runtime-operator",
        "info",
        Some(addon_id),
        Some(companion_id),
        "lrclib.db.asset.found",
        json!({
            "packagedAssetPath": &packaged_asset.descriptor_path,
            "resolvedAssetPath": &packaged_asset.source_label,
            "archiveEntry": &packaged_asset.archive_entry,
            "registrationRoot": registration_root.to_string_lossy(),
        }),
    );
    let compressed_asset_sha256 = format!("{:x}", Sha256::digest(&packaged_asset.bytes));
    let deployed_file_name = db
        .deployed_file_name
        .as_deref()
        .filter(|value| !value.trim().is_empty())
        .unwrap_or("db.sqlite3");
    let deployment_root = axolync_native_data_root()
        .join(addon_id)
        .join(companion_id)
        .join(&compressed_asset_sha256);
    fs::create_dir_all(&deployment_root).map_err(|error| error.to_string())?;
    let deployed_db_path = deployment_root.join(deployed_file_name);
    let deployment_state_path = deployment_root.join("deployment-state.json");

    if deployed_db_path.exists() {
        verify_sqlite_header_if_required(&deployed_db_path, db.sqlite_header_required)?;
        push_diagnostic(
            diagnostics,
            "runtime-operator",
            "info",
            Some(addon_id),
            Some(companion_id),
            "lrclib.db.deploy.reused",
            json!({
                "compressedAssetSha256": &compressed_asset_sha256,
                "deployedDbPath": deployed_db_path.to_string_lossy(),
            }),
        );
        return Ok(LrclibDbDeployment {
            compressed_asset_path: packaged_asset.source_label,
            compressed_asset_sha256,
            deployed_db_path: deployed_db_path.to_string_lossy().to_string(),
            deployment_state_path: deployment_state_path.to_string_lossy().to_string(),
            deployed: false,
            reused_existing: true,
        });
    }

    let input = Cursor::new(packaged_asset.bytes);
    let mut decompressor = brotli::Decompressor::new(input, 4096);
    let temp_deployed_db_path = deployed_db_path.with_extension("sqlite3.tmp");
    {
        let mut output = File::create(&temp_deployed_db_path).map_err(|error| error.to_string())?;
        std::io::copy(&mut decompressor, &mut output).map_err(|error| error.to_string())?;
        output.flush().map_err(|error| error.to_string())?;
    }
    verify_sqlite_header_if_required(&temp_deployed_db_path, db.sqlite_header_required)?;
    fs::rename(&temp_deployed_db_path, &deployed_db_path).map_err(|error| error.to_string())?;
    let deployment_state = json!({
        "schema": "axolync-lrclib-db-deployment-state/v1",
        "compressedAssetPath": &packaged_asset.source_label,
        "compressedAssetSha256": &compressed_asset_sha256,
        "deployedDbPath": deployed_db_path.to_string_lossy(),
        "deployedAtMs": chrono_like_now_ms(),
    });
    fs::write(&deployment_state_path, deployment_state.to_string()).map_err(|error| error.to_string())?;
    push_diagnostic(
        diagnostics,
        "runtime-operator",
        "info",
        Some(addon_id),
        Some(companion_id),
        "lrclib.db.deploy.completed",
        json!({
            "compressedAssetSha256": &compressed_asset_sha256,
            "deployedDbPath": deployed_db_path.to_string_lossy(),
        }),
    );
    Ok(LrclibDbDeployment {
        compressed_asset_path: packaged_asset.source_label,
        compressed_asset_sha256,
        deployed_db_path: deployed_db_path.to_string_lossy().to_string(),
        deployment_state_path: deployment_state_path.to_string_lossy().to_string(),
        deployed: true,
        reused_existing: false,
    })
}

fn start_runtime_operator(
    addon_id: &str,
    companion_id: &str,
    registration: &EmbeddedNativeCompanionRegistration,
    diagnostics: Arc<Mutex<Vec<Value>>>,
) -> Result<RunningRuntimeOperator, String> {
    let descriptor = &registration.operator;
    push_diagnostic(
        &diagnostics,
        "runtime-operator",
        "info",
        Some(addon_id),
        Some(companion_id),
        "runtime-operator.payload.selected",
        json!({
            "runtimeOperatorKind": descriptor.runtime_operator_kind.as_str(),
            "entrypoint": registration.entrypoint.as_str(),
        }),
    );
    match descriptor.runtime_operator_kind.as_str() {
        "shazam-discovery-loopback-v1" => start_shazam_runtime_operator(
            addon_id,
            companion_id,
            descriptor,
            diagnostics,
        ),
        "lrclib-local-loopback-v1" => start_lrclib_runtime_operator(
            addon_id,
            companion_id,
            registration,
            diagnostics,
        ),
        _ => Err(format!(
            "Unsupported runtime operator kind: {}",
            descriptor.runtime_operator_kind
        )),
    }
}

fn start_shazam_runtime_operator(
    addon_id: &str,
    companion_id: &str,
    descriptor: &RuntimeOperatorDescriptor,
    diagnostics: Arc<Mutex<Vec<Value>>>,
) -> Result<RunningRuntimeOperator, String> {
    if descriptor.runtime_operator_kind != "shazam-discovery-loopback-v1" {
        return Err(format!(
            "Unsupported runtime operator kind: {}",
            descriptor.runtime_operator_kind
        ));
    }
    let listen_path = required_descriptor_string(&descriptor.listen_path, "listen_path")?;
    let _ = required_descriptor_string(&descriptor.upstream_method, "upstream_method")?;
    let _ = required_descriptor_string(&descriptor.upstream_url_template, "upstream_url_template")?;
    let _ = required_descriptor_string(&descriptor.content_language, "content_language")?;
    if descriptor.geo.is_none() {
        return Err(String::from("Runtime operator descriptor is missing geo."));
    }
    let listener = TcpListener::bind("127.0.0.1:0").map_err(|error| error.to_string())?;
    listener
        .set_nonblocking(true)
        .map_err(|error| error.to_string())?;
    let listen_port = listener.local_addr().map_err(|error| error.to_string())?.port();
    let base_url = format!("http://127.0.0.1:{}{}", listen_port, listen_path);
    push_diagnostic(
        &diagnostics,
        "runtime-operator",
        "info",
        Some(addon_id),
        Some(companion_id),
        "runtime-operator.loopback.bound",
        json!({
            "runtimeOperatorKind": "shazam-discovery-loopback-v1",
            "baseUrl": base_url,
            "listenPath": listen_path,
        }),
    );
    let stop_flag = Arc::new(AtomicBool::new(false));
    let thread_stop_flag = Arc::clone(&stop_flag);
    let descriptor_clone = descriptor.clone();
    let diagnostics_clone = Arc::clone(&diagnostics);
    let addon_id_string = addon_id.to_string();
    let companion_id_string = companion_id.to_string();
    let join_handle = thread::spawn(move || {
        push_diagnostic(
            &diagnostics_clone,
            "runtime-operator",
            "info",
            Some(&addon_id_string),
            Some(&companion_id_string),
            "runtime-operator.started",
            json!({ "baseUrl": format!("http://127.0.0.1:{}{}", listen_port, descriptor_clone.listen_path.as_deref().unwrap_or("/")) }),
        );
        while !thread_stop_flag.load(Ordering::SeqCst) {
            match listener.accept() {
                Ok((stream, _addr)) => {
                    let _ = handle_shazam_loopback_connection(
                        stream,
                        &descriptor_clone,
                        &addon_id_string,
                        &companion_id_string,
                        &diagnostics_clone,
                    );
                }
                Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
                    thread::sleep(Duration::from_millis(25));
                }
                Err(_) => {
                    thread::sleep(Duration::from_millis(50));
                }
            }
        }
    });
    Ok(RunningRuntimeOperator {
        stop_flag,
        join_handle: Some(join_handle),
        connection: NativeConnectionHandle {
            kind: String::from("loopback-http-base-url"),
            base_url,
        },
    })
}

fn start_lrclib_runtime_operator(
    addon_id: &str,
    companion_id: &str,
    registration: &EmbeddedNativeCompanionRegistration,
    diagnostics: Arc<Mutex<Vec<Value>>>,
) -> Result<RunningRuntimeOperator, String> {
    let descriptor = &registration.operator;
    let listen_path = descriptor
        .listen_path
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .unwrap_or("/api")
        .to_string();
    let local_result_header = descriptor
        .local_result_header
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .unwrap_or("x-axolync-lrclib-local-result")
        .to_string();
    let registration_root = resolve_registration_root(&registration.entrypoint)?;
    let db_deployment = deploy_brotli_db_once(
        addon_id,
        companion_id,
        &registration_root,
        descriptor,
        &diagnostics,
    )?;
    let db_path = PathBuf::from(&db_deployment.deployed_db_path);

    let listener = TcpListener::bind("127.0.0.1:0").map_err(|error| error.to_string())?;
    listener
        .set_nonblocking(true)
        .map_err(|error| error.to_string())?;
    let listen_port = listener.local_addr().map_err(|error| error.to_string())?.port();
    let base_url = format!("http://127.0.0.1:{}", listen_port);
    push_diagnostic(
        &diagnostics,
        "runtime-operator",
        "info",
        Some(addon_id),
        Some(companion_id),
        "runtime-operator.loopback.bound",
        json!({
            "runtimeOperatorKind": "lrclib-local-loopback-v1",
            "baseUrl": base_url,
            "listenPath": listen_path,
        }),
    );
    push_diagnostic(
        &diagnostics,
        "runtime-operator",
        "info",
        Some(addon_id),
        Some(companion_id),
        "runtime-operator.probe.ready",
        json!({
            "baseUrl": base_url,
            "routes": ["/api/get", "/api/search"],
        }),
    );
    let stop_flag = Arc::new(AtomicBool::new(false));
    let thread_stop_flag = Arc::clone(&stop_flag);
    let diagnostics_clone = Arc::clone(&diagnostics);
    let addon_id_string = addon_id.to_string();
    let companion_id_string = companion_id.to_string();
    let db_path_for_thread = db_path.clone();
    let listen_path_for_thread = listen_path.clone();
    let local_result_header_for_thread = local_result_header.clone();
    let db_deployment_value = serde_json::to_value(&db_deployment).unwrap_or(Value::Null);
    let join_handle = thread::spawn(move || {
        push_diagnostic(
            &diagnostics_clone,
            "runtime-operator",
            "info",
            Some(&addon_id_string),
            Some(&companion_id_string),
            "runtime-operator.started",
            json!({
                "runtimeOperatorKind": "lrclib-local-loopback-v1",
                "baseUrl": format!("http://127.0.0.1:{}", listen_port),
                "listenPath": listen_path_for_thread,
                "db": db_deployment_value,
            }),
        );
        while !thread_stop_flag.load(Ordering::SeqCst) {
            match listener.accept() {
                Ok((stream, _addr)) => {
                    let _ = handle_lrclib_loopback_connection(
                        stream,
                        &db_path_for_thread,
                        &listen_path_for_thread,
                        &local_result_header_for_thread,
                        &addon_id_string,
                        &companion_id_string,
                        &diagnostics_clone,
                    );
                }
                Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
                    thread::sleep(Duration::from_millis(25));
                }
                Err(_) => {
                    thread::sleep(Duration::from_millis(50));
                }
            }
        }
    });

    Ok(RunningRuntimeOperator {
        stop_flag,
        join_handle: Some(join_handle),
        connection: NativeConnectionHandle {
            kind: String::from("loopback-http-base-url"),
            base_url,
        },
    })
}

fn handle_shazam_loopback_connection(
    mut stream: TcpStream,
    descriptor: &RuntimeOperatorDescriptor,
    addon_id: &str,
    companion_id: &str,
    diagnostics: &Arc<Mutex<Vec<Value>>>,
) -> Result<(), String> {
    stream
        .set_read_timeout(Some(Duration::from_secs(2)))
        .map_err(|error| error.to_string())?;
    let mut buffer = [0u8; 8192];
    let bytes_read = stream.read(&mut buffer).map_err(|error| error.to_string())?;
    if bytes_read == 0 {
        return Ok(());
    }
    let request_text = String::from_utf8_lossy(&buffer[..bytes_read]).to_string();
    let request_line = request_text
        .lines()
        .next()
        .ok_or_else(|| String::from("Loopback request is missing a request line."))?;
    let request_method = request_line
        .split_whitespace()
        .next()
        .unwrap_or("GET")
        .to_uppercase();
    push_diagnostic(
        diagnostics,
        "runtime-operator",
        "info",
        Some(addon_id),
        Some(companion_id),
        "runtime-operator.loopback.request.received",
        json!({ "method": request_method, "requestLine": request_line }),
    );
    let target = request_line
        .split_whitespace()
        .nth(1)
        .ok_or_else(|| String::from("Loopback request line is missing a path."))?;
    if request_method == "OPTIONS" {
        write_http_response(&mut stream, "204 No Content", "").map_err(|error| error.to_string())?;
        return Ok(());
    }
    let parsed_url = Url::parse(&format!("http://127.0.0.1{}", target)).map_err(|error| error.to_string())?;
    let listen_path = required_descriptor_string(&descriptor.listen_path, "listen_path")?;
    if parsed_url.path() != listen_path {
        push_diagnostic(
            diagnostics,
            "runtime-operator",
            "warn",
            Some(addon_id),
            Some(companion_id),
            "runtime-operator.loopback.request.unknown-path",
            json!({ "path": parsed_url.path() }),
        );
        write_http_response(
            &mut stream,
            "404 Not Found",
            &json!({ "error": "Unknown runtime operator path." }).to_string(),
        )
        .map_err(|error| error.to_string())?;
        return Ok(());
    }
    let query_pairs: HashMap<String, String> = parsed_url
        .query_pairs()
        .map(|(key, value)| (key.to_string(), value.to_string()))
        .collect();
    let uri = match query_pairs.get("uri") {
        Some(value) if !value.trim().is_empty() => value.clone(),
        _ => {
            push_diagnostic(
                diagnostics,
                "runtime-operator",
                "warn",
                Some(addon_id),
                Some(companion_id),
                "runtime-operator.loopback.request.missing-uri",
                Value::Null,
            );
            write_http_response(
                &mut stream,
                "400 Bad Request",
                &json!({ "error": "Missing required query parameters: uri and samplems" }).to_string(),
            )
            .map_err(|error| error.to_string())?;
            return Ok(());
        }
    };
    let sample_ms = match query_pairs.get("samplems") {
        Some(value) if !value.trim().is_empty() => value.clone(),
        _ => {
            push_diagnostic(
                diagnostics,
                "runtime-operator",
                "warn",
                Some(addon_id),
                Some(companion_id),
                "runtime-operator.loopback.request.missing-samplems",
                Value::Null,
            );
            write_http_response(
                &mut stream,
                "400 Bad Request",
                &json!({ "error": "Missing required query parameters: uri and samplems" }).to_string(),
            )
            .map_err(|error| error.to_string())?;
            return Ok(());
        }
    };
    let body = match proxy_shazam_discovery_request(descriptor, &uri, &sample_ms) {
        Ok(body) => {
            push_diagnostic(
                diagnostics,
                "runtime-operator",
                "info",
                Some(addon_id),
                Some(companion_id),
                "runtime-operator.loopback.request.completed",
                json!({ "sampleMs": sample_ms }),
            );
            body
        }
        Err(error) => {
            push_diagnostic(
                diagnostics,
                "runtime-operator",
                "error",
                Some(addon_id),
                Some(companion_id),
                "runtime-operator.loopback.request.failed",
                json!({ "sampleMs": sample_ms, "error": error }),
            );
            json!({
                "error": "Failed to make request to Shazam API",
                "details": error,
            })
            .to_string()
        }
    };
    write_http_response(&mut stream, "200 OK", &body).map_err(|error| error.to_string())?;
    Ok(())
}

fn query_param(query_pairs: &HashMap<String, String>, key: &str) -> Option<String> {
    query_pairs
        .get(key)
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
}

fn open_lrclib_database(db_path: &Path) -> Result<Connection, String> {
    Connection::open_with_flags(
        db_path,
        OpenFlags::SQLITE_OPEN_READ_ONLY | OpenFlags::SQLITE_OPEN_URI,
    )
    .map_err(|error| format!("Failed to open LRCLIB native DB: {}", error))
}

fn lrclib_record_from_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<Value> {
    let duration = row.get::<_, Option<f64>>(4)?;
    let instrumental = row.get::<_, Option<i64>>(5)?.unwrap_or(0) != 0;
    Ok(json!({
        "id": row.get::<_, i64>(0)?,
        "trackName": row.get::<_, Option<String>>(1)?.unwrap_or_default(),
        "artistName": row.get::<_, Option<String>>(2)?.unwrap_or_default(),
        "albumName": row.get::<_, Option<String>>(3)?.unwrap_or_default(),
        "duration": duration,
        "instrumental": instrumental,
        "plainLyrics": row.get::<_, Option<String>>(6)?,
        "syncedLyrics": row.get::<_, Option<String>>(7)?,
    }))
}

fn query_lrclib_get(db_path: &Path, query_pairs: &HashMap<String, String>) -> Result<Option<Value>, String> {
    let track_name = query_param(query_pairs, "track_name")
        .ok_or_else(|| String::from("Missing required query parameter: track_name"))?;
    let artist_name = query_param(query_pairs, "artist_name").unwrap_or_default();
    let album_name = query_param(query_pairs, "album_name").unwrap_or_default();
    let duration_seconds = query_param(query_pairs, "duration")
        .and_then(|value| value.parse::<f64>().ok())
        .unwrap_or(-1.0);
    let db = open_lrclib_database(db_path)?;
    let mut statement = db
        .prepare(
            r#"
            SELECT
              tracks.id,
              tracks.name,
              tracks.artist_name,
              tracks.album_name,
              tracks.duration,
              lyrics.instrumental,
              lyrics.plain_lyrics,
              lyrics.synced_lyrics
            FROM tracks
            LEFT JOIN lyrics ON lyrics.id = tracks.last_lyrics_id
              OR (tracks.last_lyrics_id IS NULL AND lyrics.track_id = tracks.id)
            WHERE tracks.name_lower = lower(?1)
              AND (?2 = '' OR tracks.artist_name_lower = lower(?2))
              AND (?3 = '' OR tracks.album_name_lower = lower(?3))
              AND (?4 < 0 OR tracks.duration IS NULL OR abs(tracks.duration - ?4) <= 2.5)
            ORDER BY
              CASE WHEN COALESCE(lyrics.has_synced_lyrics, 0) != 0 THEN 0 ELSE 1 END,
              tracks.id ASC,
              lyrics.id ASC
            LIMIT 1
            "#,
        )
        .map_err(|error| error.to_string())?;
    let mut rows = statement
        .query_map(params![track_name, artist_name, album_name, duration_seconds], lrclib_record_from_row)
        .map_err(|error| error.to_string())?;
    match rows.next() {
        Some(record) => Ok(Some(record.map_err(|error| error.to_string())?)),
        None => Ok(None),
    }
}

fn query_lrclib_search(db_path: &Path, query_pairs: &HashMap<String, String>) -> Result<Vec<Value>, String> {
    let search_text = query_param(query_pairs, "track_name")
        .or_else(|| query_param(query_pairs, "q"))
        .ok_or_else(|| String::from("Missing required query parameter: track_name or q"))?;
    let artist_name = query_param(query_pairs, "artist_name").unwrap_or_default();
    let db = open_lrclib_database(db_path)?;
    let mut statement = db
        .prepare(
            r#"
            SELECT
              tracks.id,
              tracks.name,
              tracks.artist_name,
              tracks.album_name,
              tracks.duration,
              lyrics.instrumental,
              lyrics.plain_lyrics,
              lyrics.synced_lyrics
            FROM tracks
            LEFT JOIN lyrics ON lyrics.id = tracks.last_lyrics_id
              OR (tracks.last_lyrics_id IS NULL AND lyrics.track_id = tracks.id)
            WHERE (
                tracks.name_lower LIKE '%' || lower(?1) || '%'
                OR tracks.artist_name_lower LIKE '%' || lower(?1) || '%'
                OR tracks.album_name_lower LIKE '%' || lower(?1) || '%'
              )
              AND (?2 = '' OR tracks.artist_name_lower LIKE '%' || lower(?2) || '%')
            ORDER BY
              CASE
                WHEN tracks.name_lower = lower(?1) THEN 0
                WHEN tracks.name_lower LIKE lower(?1) || '%' THEN 1
                ELSE 2
              END,
              CASE WHEN COALESCE(lyrics.has_synced_lyrics, 0) != 0 THEN 0 ELSE 1 END,
              tracks.id ASC,
              lyrics.id ASC
            LIMIT 25
            "#,
        )
        .map_err(|error| error.to_string())?;
    let rows = statement
        .query_map(params![search_text, artist_name], lrclib_record_from_row)
        .map_err(|error| error.to_string())?;
    let mut records = Vec::new();
    for row in rows {
        records.push(row.map_err(|error| error.to_string())?);
    }
    Ok(records)
}

fn handle_lrclib_loopback_connection(
    mut stream: TcpStream,
    db_path: &Path,
    listen_path: &str,
    local_result_header: &str,
    addon_id: &str,
    companion_id: &str,
    diagnostics: &Arc<Mutex<Vec<Value>>>,
) -> Result<(), String> {
    stream
        .set_read_timeout(Some(Duration::from_secs(2)))
        .map_err(|error| error.to_string())?;
    let mut buffer = [0u8; 8192];
    let bytes_read = stream.read(&mut buffer).map_err(|error| error.to_string())?;
    if bytes_read == 0 {
        return Ok(());
    }
    let request_text = String::from_utf8_lossy(&buffer[..bytes_read]).to_string();
    let request_line = request_text
        .lines()
        .next()
        .ok_or_else(|| String::from("Loopback request is missing a request line."))?;
    let request_method = request_line
        .split_whitespace()
        .next()
        .unwrap_or("GET")
        .to_uppercase();
    let target = request_line
        .split_whitespace()
        .nth(1)
        .ok_or_else(|| String::from("Loopback request line is missing a path."))?;
    push_diagnostic(
        diagnostics,
        "runtime-operator",
        "info",
        Some(addon_id),
        Some(companion_id),
        "runtime-operator.loopback.request.received",
        json!({ "method": request_method, "requestLine": request_line }),
    );
    if request_method == "OPTIONS" {
        write_http_response(&mut stream, "204 No Content", "").map_err(|error| error.to_string())?;
        return Ok(());
    }
    if request_method != "GET" {
        write_http_response(
            &mut stream,
            "405 Method Not Allowed",
            &json!({ "error": "LRCLIB native route only supports GET." }).to_string(),
        )
        .map_err(|error| error.to_string())?;
        return Ok(());
    }

    let parsed_url = Url::parse(&format!("http://127.0.0.1{}", target)).map_err(|error| error.to_string())?;
    let listen_prefix = listen_path.trim_end_matches('/');
    if !parsed_url.path().starts_with(listen_prefix) {
        push_diagnostic(
            diagnostics,
            "runtime-operator",
            "warn",
            Some(addon_id),
            Some(companion_id),
            "runtime-operator.loopback.request.unknown-path",
            json!({ "path": parsed_url.path() }),
        );
        write_http_response(
            &mut stream,
            "404 Not Found",
            &json!({ "error": "Unknown LRCLIB native runtime path." }).to_string(),
        )
        .map_err(|error| error.to_string())?;
        return Ok(());
    }
    let query_pairs: HashMap<String, String> = parsed_url
        .query_pairs()
        .map(|(key, value)| (key.to_string(), value.to_string()))
        .collect();

    let route_result = match parsed_url.path() {
        "/api/get" => query_lrclib_get(db_path, &query_pairs).map(|record| {
            match record {
                Some(record) => ("200 OK", record.to_string(), "local-hit"),
                None => (
                    "404 Not Found",
                    json!({ "error": "LRCLIB native subset miss." }).to_string(),
                    "subset-miss",
                ),
            }
        }),
        "/api/search" => query_lrclib_search(db_path, &query_pairs).map(|records| {
            let local_result = if records.is_empty() { "subset-miss" } else { "local-hit" };
            ("200 OK", Value::Array(records).to_string(), local_result)
        }),
        _ => Ok((
            "404 Not Found",
            json!({ "error": "Unknown LRCLIB native runtime path." }).to_string(),
            "subset-miss",
        )),
    };

    match route_result {
        Ok((status, body, local_result)) => {
            push_diagnostic(
                diagnostics,
                "runtime-operator",
                "info",
                Some(addon_id),
                Some(companion_id),
                "runtime-operator.loopback.request.completed",
                json!({
                    "path": parsed_url.path(),
                    "status": status,
                    "localResult": local_result,
                }),
            );
            write_http_response_with_headers(
                &mut stream,
                status,
                &body,
                &[(local_result_header, local_result)],
            )
            .map_err(|error| error.to_string())?;
        }
        Err(error) => {
            push_diagnostic(
                diagnostics,
                "runtime-operator",
                "error",
                Some(addon_id),
                Some(companion_id),
                "runtime-operator.loopback.request.failed",
                json!({ "path": parsed_url.path(), "error": error }),
            );
            write_http_response_with_headers(
                &mut stream,
                "500 Internal Server Error",
                &json!({ "error": "LRCLIB native runtime query failed." }).to_string(),
                &[(local_result_header, "error")],
            )
            .map_err(|error| error.to_string())?;
        }
    }
    Ok(())
}

fn write_http_response(
    stream: &mut TcpStream,
    status_line: &str,
    body: &str,
) -> Result<(), std::io::Error> {
    write_http_response_with_headers(stream, status_line, body, &[])
}

fn write_http_response_with_headers(
    stream: &mut TcpStream,
    status_line: &str,
    body: &str,
    extra_headers: &[(&str, &str)],
) -> Result<(), std::io::Error> {
    let body_bytes = body.as_bytes();
    let mut rendered_extra_headers = String::new();
    for (name, value) in extra_headers {
        if !name.trim().is_empty() {
            rendered_extra_headers.push_str(&format!("{}: {}\r\n", name.trim(), value.trim()));
        }
    }
    let response = format!(
        "HTTP/1.1 {}\r\ncontent-type: application/json; charset=utf-8\r\ncontent-length: {}\r\naccess-control-allow-origin: *\r\naccess-control-allow-methods: GET, OPTIONS\r\naccess-control-allow-headers: Accept, Content-Type\r\n{}connection: close\r\n\r\n",
        status_line,
        body_bytes.len(),
        rendered_extra_headers
    );
    stream.write_all(response.as_bytes())?;
    stream.write_all(body_bytes)?;
    stream.flush()
}

fn proxy_shazam_discovery_request(
    descriptor: &RuntimeOperatorDescriptor,
    uri: &str,
    sample_ms: &str,
) -> Result<String, String> {
    let now_ms = chrono_like_now_ms();
    let sample_ms_value = sample_ms
        .parse::<i64>()
        .map_err(|error| error.to_string())?;
    let geo = descriptor
        .geo
        .as_ref()
        .ok_or_else(|| String::from("Runtime operator descriptor is missing geo."))?;
    let upstream_url_template = required_descriptor_string(
        &descriptor.upstream_url_template,
        "upstream_url_template",
    )?;
    let upstream_method = required_descriptor_string(&descriptor.upstream_method, "upstream_method")?;
    let content_language = required_descriptor_string(&descriptor.content_language, "content_language")?;
    let mut rng = rand::thread_rng();
    let request_body = json!({
        "geolocation": {
            "altitude": geo.altitude,
            "latitude": geo.latitude,
            "longitude": geo.longitude,
        },
        "signature": {
            "uri": uri,
            "samplems": sample_ms_value,
            "timestamp": now_ms,
        },
        "timestamp": now_ms,
        "timezone": descriptor
            .timezones
            .choose(&mut rng)
            .cloned()
            .unwrap_or_else(|| String::from("UTC")),
    });
    let request_url = replace_uuid_placeholders(&upstream_url_template);
    let method = Method::from_bytes(upstream_method.as_bytes())
        .unwrap_or(Method::POST);
    let user_agent = descriptor
        .user_agents
        .choose(&mut rng)
        .cloned()
        .unwrap_or_else(|| String::from("Axolync Native Bridge"));
    let client = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(10))
        .build()
        .map_err(|error| error.to_string())?;
    let response = client
        .request(method, request_url)
        .header("Content-Type", "application/json")
        .header("Content-Language", content_language)
        .header("User-Agent", user_agent)
        .body(request_body.to_string())
        .send()
        .map_err(|error| error.to_string())?;
    let body = response.text().map_err(|error| error.to_string())?;
    if body.trim().is_empty() {
        Ok(String::from("{}"))
    } else {
        Ok(body)
    }
}

fn replace_uuid_placeholders(template: &str) -> String {
    let mut resolved = template.to_string();
    while resolved.contains("{uuid}") {
        resolved = resolved.replacen("{uuid}", &Uuid::new_v4().to_string(), 1);
    }
    resolved
}

fn chrono_like_now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_millis() as i64)
        .unwrap_or(0)
}

#[allow(non_snake_case)]
#[tauri::command]
pub fn axolync_native_service_companion_get_status(
    addonId: String,
    companionId: String,
    state: tauri::State<'_, NativeCompanionHostState>,
) -> NativeCompanionStatusEnvelope {
    state.get_status(&addonId, &companionId)
}

#[allow(non_snake_case)]
#[tauri::command]
pub fn axolync_native_service_companion_set_enabled(
    addonId: String,
    companionId: String,
    enabled: bool,
    state: tauri::State<'_, NativeCompanionHostState>,
) -> NativeCompanionStatusEnvelope {
    state.set_enabled(&addonId, &companionId, enabled)
}

#[allow(non_snake_case)]
#[tauri::command]
pub fn axolync_native_service_companion_start(
    addonId: String,
    companionId: String,
    state: tauri::State<'_, NativeCompanionHostState>,
) -> NativeCompanionStatusEnvelope {
    state.start(&addonId, &companionId)
}

#[allow(non_snake_case)]
#[tauri::command]
pub fn axolync_native_service_companion_stop(
    addonId: String,
    companionId: String,
    state: tauri::State<'_, NativeCompanionHostState>,
) -> NativeCompanionStatusEnvelope {
    state.stop(&addonId, &companionId)
}

#[allow(non_snake_case)]
#[tauri::command]
pub fn axolync_native_service_companion_request(
    addonId: String,
    companionId: String,
    request: NativeCompanionRequest,
    state: tauri::State<'_, NativeCompanionHostState>,
) -> NativeCompanionResponseEnvelope {
    state.request(&addonId, &companionId, request)
}

#[allow(non_snake_case)]
#[tauri::command]
pub fn axolync_native_service_companion_get_connection(
    addonId: String,
    companionId: String,
    state: tauri::State<'_, NativeCompanionHostState>,
) -> NativeCompanionConnectionEnvelope {
    state.get_connection(&addonId, &companionId)
}

#[allow(non_snake_case)]
#[tauri::command]
pub fn axolync_native_service_companion_get_diagnostics(
    state: tauri::State<'_, NativeCompanionHostState>,
) -> NativeDiagnosticEnvelope {
    state.get_diagnostics()
}

pub fn build_tauri_app() -> tauri::Builder<tauri::Wry> {
    tauri::Builder::default()
        .manage(NativeCompanionHostState::load())
        .invoke_handler(tauri::generate_handler![
            axolync_native_service_companion_get_status,
            axolync_native_service_companion_set_enabled,
            axolync_native_service_companion_start,
            axolync_native_service_companion_stop,
            axolync_native_service_companion_request,
            axolync_native_service_companion_get_connection,
            axolync_native_service_companion_get_diagnostics,
        ])
}
