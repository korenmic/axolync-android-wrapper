# Android Wrapper Backlog

- [x] Restore native debug archive save in the active Capacitor host so browser-generated debug ZIP exports land in a truthful Android-visible location and return structured `{ success, uri, error }` results instead of falling back to fragile WebView blob-download behavior.

- [x] Replace Android debug archive save's dependency on the native service companion bridge with a dedicated Capacitor-native save capability.
  - The current failure shape still reaches `Native debug archive save requested` and then dies with `Capacitor native service companion bridge is unavailable.`, which proves debug archive export is still coupled to the broader native companion bootstrap instead of standing on its own.
  - Give debug archive save its own dedicated Capacitor plugin/capability path rather than routing it through `AxolyncNativeServiceCompanionHost`.
  - Prefer the standard Capacitor plugin publication path for this dedicated save capability so Android file export is not blocked by native proxy/native companion bootstrap failures.
  - Preserve truthful Android-visible save semantics and the structured `{ success, uri, error }` result contract for browser.
  - Keep the operator-facing target explicit: a successful Android save should return a truthful URI/location for the written archive rather than silently falling back to a best-effort WebView blob download.

- [ ] Block Android WebView text selection and contextual copy/translate menus outside explicitly editable or debug-copy surfaces.
  - The shipped APK currently allows long-press text selection and the Android copy/translate toolbar on app UI text, which violates the wrapper interaction policy.
  - Enforce the Android-specific suppression in the Capacitor host/WebView layer where possible, and use browser-side wrapper-scoped CSS/event guards only when needed.
  - Do not break normal typing/selecting inside real text inputs or any explicit debug-log copy/export affordance that Axolync intentionally provides.
  - Add Android wrapper proof that long-press selection/contextual action mode is suppressed on ordinary app text while input fields still behave correctly.

- [ ] Classify Capacitor loopback route mismatches that return HTML to native runtime callers.
  - Diagnose whether Android Capacitor native operator requests can be routed to the wrong WebView asset/server path and return `<!doctype` HTML instead of native JSON.
  - Add Android-owned diagnostics that distinguish cleartext/mixed-content blocks, unreachable loopback, wrapper asset-server HTML, route miss, and operator-side request handling.
  - Fix Android routing only if the HTML response originates from Capacitor/WebView transport or wrapper route publication, without weakening the working Tauri loopback shape.
  - Add proof that native runtime loopback calls either reach the intended operator route or produce a classified Android transport failure.
