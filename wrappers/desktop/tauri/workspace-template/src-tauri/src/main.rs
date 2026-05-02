#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod native_service_companion;

fn main() {
    native_service_companion::build_tauri_app()
        .run(tauri::generate_context!())
        .expect("error while running tauri desktop host");
}
