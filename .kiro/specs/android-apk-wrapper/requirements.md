# Requirements Document

## Introduction

This document specifies requirements for the Android APK Wrapper that hosts the Axolync karaoke companion experience. The wrapper will package the existing web-based Axolync platform (axolync-browser v1.0.0) into a native Android application while preserving all existing functionality including the state machine, plugin pipeline, and user experience semantics.

## Glossary

- **Axolync_Browser**: The web-based karaoke companion platform that serves as the baseline for this wrapper
- **Android_Wrapper**: The native Android application that hosts the Axolync experience
- **WebView**: The Android component that renders web content within the native app
- **Plugin_Pipeline**: The 3-plugin system (SongSense, SyncEngine, LyricFlow) that processes audio and synchronizes lyrics
- **State_Machine**: The exact state model and transitions defined in axolync-browser v1.0.0 (source of truth), including state names and transition semantics
- **Local_Runtime**: The execution environment for the web application within the Android wrapper
- **Mic_Capture**: Audio input from the device microphone for song identification
- **Splash_Screen**: Initial loading screen displayed during app startup
- **Plugin_Package**: Installable/updatable plugin components stored on Android storage
- **Offline_Mode**: Operation mode where the app functions without network connectivity
- **Online_Mode**: Operation mode where the app requires network connectivity

## Requirements

### Requirement 1: Baseline Integration

**User Story:** As a developer, I want to integrate the stable axolync-browser v1.0.0 as the baseline, so that the Android wrapper builds on proven functionality.

#### Acceptance Criteria

1. THE Android_Wrapper SHALL integrate axolync-browser at version v1.0.0 as a submodule
2. THE Android_Wrapper SHALL preserve all State_Machine semantics from axolync-browser
3. THE Android_Wrapper SHALL preserve all UX semantics from axolync-browser
4. THE Android_Wrapper SHALL remain updateable independently from provider backend projects

### Requirement 2: Native App Experience

**User Story:** As a user, I want the app to feel like a native Android application, so that I have a seamless mobile experience.

#### Acceptance Criteria

1. WHEN the app starts, THE Android_Wrapper SHALL display a Splash_Screen during initialization
2. WHILE the app is loading on slow devices, THE Android_Wrapper SHALL maintain the Splash_Screen until the Local_Runtime is ready
3. THE Android_Wrapper SHALL integrate with Android system UI patterns for navigation and status
4. THE Android_Wrapper SHALL handle Android lifecycle events (pause, resume, background, foreground)

### Requirement 3: Microphone Permissions and Capture

**User Story:** As a user, I want to grant microphone access to the app, so that it can listen to and identify songs.

#### Acceptance Criteria

1. WHEN the app requires Mic_Capture, THE Android_Wrapper SHALL request Android microphone permissions
2. IF microphone permission is denied, THEN THE Android_Wrapper SHALL display an explanation and provide a path to grant permission
3. WHEN microphone permission is granted, THE Android_Wrapper SHALL enable Mic_Capture for the Plugin_Pipeline
4. THE Android_Wrapper SHALL route audio from the device microphone to the web application
5. THE Android_Wrapper SHALL be tested on physical Android devices for audio latency behavior

### Requirement 4: State Machine Preservation

**User Story:** As a developer, I want the state machine to function identically to the web version, so that behavior is consistent across platforms.

#### Acceptance Criteria

1. THE Android_Wrapper SHALL preserve the exact state model and transitions defined by axolync-browser v1.0.0
2. A state-transition parity test suite SHALL verify Android behavior against v1.0.0 baseline traces
3. THE Android_Wrapper SHALL maintain all state transition semantics identical to axolync-browser
4. THE Android_Wrapper SHALL use state names exactly as defined in the axolync-browser v1.0.0 state machine specification

### Requirement 5: Plugin Pipeline Integration

**User Story:** As a user, I want the song identification and lyric synchronization to work on Android, so that I can use all karaoke companion features.

#### Acceptance Criteria

1. THE Android_Wrapper SHALL execute the SongSense plugin for audio analysis
2. THE Android_Wrapper SHALL execute the SyncEngine plugin for timeline synchronization
3. THE Android_Wrapper SHALL execute the LyricFlow plugin for lyric rendering
4. THE Android_Wrapper SHALL support Web Workers for plugin execution
5. THE Android_Wrapper SHALL store Plugin_Package files on Android storage
6. THE Android_Wrapper SHALL provide a mechanism for Plugin_Package installation
7. THE Android_Wrapper SHALL provide a mechanism for Plugin_Package updates
8. Plugin_Package installation SHALL verify package integrity using signature or checksum validation
9. IF a Plugin_Package update fails, THEN THE Android_Wrapper SHALL rollback to the previous working version
10. THE Android_Wrapper SHALL document the storage location and permission model for Plugin_Package files
11. IF a Plugin_Package is corrupt or invalid, THEN THE Android_Wrapper SHALL provide deterministic error UX and prevent partial activation

### Requirement 6: Content Delivery Architecture

**User Story:** As a developer, I want to choose an appropriate architecture for serving web content, so that the app performs optimally on Android devices.

#### Acceptance Criteria

1. THE Android_Wrapper SHALL serve the web application content to the WebView
2. THE Android_Wrapper v1 SHALL use an embedded HTTP server (NanoHTTPD) to serve bundled static assets from Android app storage to WebView via http://localhost:<port>/ (canonical URL uses hostname 'localhost', not IP literal)
3. THE Android_Wrapper SHALL load the web application within a WebView component
4. THE Android_Wrapper SHALL serve all core web app assets from internal app storage; external network requests MAY be used for online-dependent plugin/provider operations

### Requirement 7: Network Connectivity Modes

**User Story:** As a user, I want the app to work in different network conditions, so that I can use it regardless of connectivity.

#### Acceptance Criteria

1. THE Android_Wrapper v1 SHALL require online connectivity for song identification and lyric synchronization
2. IF network connectivity is unavailable, THEN THE Android_Wrapper SHALL display a connectivity status message with clear guidance
3. THE Android_Wrapper SHALL detect network availability before attempting song identification
4. THE Android_Wrapper SHALL handle transitions between online and offline states gracefully
5. WHEN transitioning from online to offline, THE Android_Wrapper SHALL preserve current state and display appropriate UX feedback

### Requirement 8: Audio Routing and Quality

**User Story:** As a user, I want high-quality audio capture with minimal latency, so that song identification is accurate and responsive.

#### Acceptance Criteria

1. THE Android_Wrapper SHALL route audio from the device microphone to Mic_Capture
2. Audio path initialization latency (start action to first captured chunk available to plugin pipeline) SHALL be <= 150 ms on target device profile
3. Cold start to first render SHALL be 3 seconds or less on target device class (mid-range Android devices from 2019+ with 4GB RAM)
4. Mic_Capture start latency SHALL be 150ms or less after permission grant and start action
5. THE Android_Wrapper SHALL tolerate audio processing loop jitter with maximum 2 consecutive missed chunks before error signaling
6. THE Android_Wrapper SHALL be tested on physical Android devices for audio routing behavior
7. THE Android_Wrapper SHALL be tested on physical Android devices for audio latency measurements
8. WHEN audio capture is active, THE Android_Wrapper SHALL maintain consistent audio quality
9. THE Android_Wrapper SHALL define a target device profile: mid-range Android devices from 2019 or newer with minimum 4GB RAM for benchmark validation

### Requirement 9: Demo and Automation Support

**User Story:** As a developer, I want to support demo and automation capabilities, so that I can test the app without live audio input.

#### Acceptance Criteria

1. THE Android_Wrapper v1 SHALL support fake microphone capture for demo/testing flows
2. THE Android_Wrapper v1 SHALL support scripted automation test scenarios
3. THE Android_Wrapper SHALL preserve demo and automation capabilities from axolync-browser

### Requirement 10: App Lifecycle and Performance

**User Story:** As a user, I want the app to handle Android system events gracefully, so that it behaves reliably during normal device usage.

#### Acceptance Criteria

1. WHEN the app is paused, THE Android_Wrapper SHALL suspend Mic_Capture
2. WHEN the app is resumed, THE Android_Wrapper SHALL restore Mic_Capture if previously active
3. WHEN the app is sent to background, THE Android_Wrapper SHALL preserve application state
4. WHEN the app is brought to foreground, THE Android_Wrapper SHALL restore application state
5. WHEN the device is low on memory, THE Android_Wrapper SHALL handle Android system memory pressure events
6. Cold start to first render SHALL meet the SLO defined in Requirement 8

### Requirement 11: WebView and Runtime Security

**User Story:** As a developer, I want the WebView and local runtime to be secure, so that the app protects user data and prevents unauthorized access.

#### Acceptance Criteria

1. WebView JavaScript interface exposure SHALL be minimal and documented
2. Remote WebView debugging SHALL be disabled in production builds
3. THE Android_Wrapper SHALL expose a localhost-only HTTP endpoint in v1 for serving web app content, binding to 127.0.0.1 and serving via canonical URL http://localhost:<port>/ to ensure cleartext compatibility across Android API levels
4. THE local runtime HTTP endpoint SHALL bind to 127.0.0.1 (localhost) only and SHALL NOT be accessible from external networks. The canonical base URL SHALL use hostname 'localhost' for WebView loading.
5. THE Android_Wrapper SHALL configure cleartext traffic policy to allow localhost only if needed, otherwise HTTPS only
6. THE Android_Wrapper SHALL block untrusted navigation by allowlisting app origins only
7. THE Android_Wrapper SHALL document all exposed JavaScript interfaces and their security implications
8. THE Android_Wrapper SHALL prevent WebView from loading content from untrusted origins
9. THE Android_Wrapper SHALL configure network security policy to allow cleartext HTTP traffic ONLY for hostname 'localhost' and SHALL block cleartext traffic to all other destinations. The policy SHALL be verified to work on target API levels 24-34.
10. THE Android_Wrapper SHALL start the embedded HTTP server asynchronously on a background thread to avoid blocking Application.onCreate() and SHALL NOT perform I/O operations on the main thread during server initialization.
