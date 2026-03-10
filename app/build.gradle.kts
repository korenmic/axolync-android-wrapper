import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}


val embeddedPythonRoot = layout.buildDirectory.dir("generated/axolync-python")
val embeddedPythonSourceDir = embeddedPythonRoot.map { it.dir("src/main/python") }
val embeddedPythonRequirementsFile = embeddedPythonRoot.map { it.file("requirements-android.txt") }

android {
    namespace = "com.axolync.android"
    compileSdk = 34

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        applicationId = "com.axolync.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "mode"
    productFlavors {
        create("normal") {
            dimension = "mode"
            buildConfigField("boolean", "DEMO_MODE", "false")
        }
        create("demo") {
            dimension = "mode"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            resValue("string", "app_name", "Axolync Demo")
            buildConfigField("boolean", "DEMO_MODE", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("diagNoNotif") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".diag.nonotif"
            versionNameSuffix = "-diag-nonotif"
            isDebuggable = true
            matchingFallbacks += listOf("debug")
        }
        create("diagNoNotifRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".diag.nonotif"
            versionNameSuffix = "-diag-nonotif-release"
            isDebuggable = false
            matchingFallbacks += listOf("diagNoNotif", "release")
        }
        create("diagNoMic") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".diag.nomic"
            versionNameSuffix = "-diag-nomic"
            isDebuggable = true
            matchingFallbacks += listOf("debug")
        }
        create("diagNoNet") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".diag.nonet"
            versionNameSuffix = "-diag-nonet"
            isDebuggable = true
            matchingFallbacks += listOf("debug")
        }
        create("diagMinimal") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".diag.minimal"
            versionNameSuffix = "-diag-minimal"
            isDebuggable = true
            matchingFallbacks += listOf("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}


chaquopy {
    defaultConfig {
        version = "3.12"
        buildPython("/usr/bin/python3.12")
        pip {
            install("-r", embeddedPythonRequirementsFile.get().asFile.absolutePath)
        }
    }

    sourceSets {
        getByName("main") {
            srcDir(embeddedPythonSourceDir)
        }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Splash Screen API
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Embedded HTTP server
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Testing - JUnit
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Testing - Kotest for property-based testing
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")
    
    // Mockito for unit tests
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    
    // Robolectric for Android unit tests
    testImplementation("org.robolectric:robolectric:4.11.1")
}

// Task to copy built axolync-browser assets to app/src/main/assets/axolync-browser/
tasks.register<Exec>("buildAxolyncBrowserDist") {
    description = "Build axolync-browser dist before packaging Android assets"
    group = "build"
    workingDir = file("${rootProject.projectDir}/axolync-browser")
    commandLine("bash", "-lc", "npm run build && npx vite build")

    inputs.file(file("${rootProject.projectDir}/axolync-browser/package.json"))
    inputs.file(file("${rootProject.projectDir}/axolync-browser/package-lock.json"))
    inputs.file(file("${rootProject.projectDir}/axolync-browser/index.html"))
    inputs.dir(file("${rootProject.projectDir}/axolync-browser/src"))
    inputs.dir(file("${rootProject.projectDir}/axolync-browser/demo"))
    outputs.dir(file("${rootProject.projectDir}/axolync-browser/dist"))

    // Builder-provided artifacts already include compiled browser bundle + preinstalled plugins.
    onlyIf {
        val skipAssetSync = providers.environmentVariable("AXOLYNC_SKIP_BROWSER_ASSET_SYNC")
            .orNull
            ?.equals("true", ignoreCase = true) == true
        if (skipAssetSync) {
            return@onlyIf false
        }
        val builderBundle = providers.environmentVariable("AXOLYNC_BUILDER_BROWSER_NORMAL").orNull
        builderBundle.isNullOrBlank()
    }
}

tasks.register("copyAxolyncBrowserAssets") {
    description = "Copy built axolync-browser assets to Android assets directory"
    group = "build"

    doFirst {
        val skipAssetSync = providers.environmentVariable("AXOLYNC_SKIP_BROWSER_ASSET_SYNC")
            .orNull
            ?.equals("true", ignoreCase = true) == true
        if (skipAssetSync) {
            logger.lifecycle("Skipping browser asset sync (AXOLYNC_SKIP_BROWSER_ASSET_SYNC=true)")
            return@doFirst
        }

        val targetDir = file("${projectDir}/src/main/assets/axolync-browser")
        delete(targetDir)
        targetDir.mkdirs()

        val builderBundlePath = providers.environmentVariable("AXOLYNC_BUILDER_BROWSER_NORMAL").orNull
        val sourceRoot = if (!builderBundlePath.isNullOrBlank()) {
            file(builderBundlePath)
        } else {
            file("${rootProject.projectDir}/axolync-browser")
        }

        if (!sourceRoot.exists()) {
            throw GradleException("Browser source root not found at ${sourceRoot.absolutePath}")
        }

        val builderHasCompiledRoot = file("${sourceRoot.absolutePath}/index.html").exists()
            && file("${sourceRoot.absolutePath}/assets").exists()

        if (builderHasCompiledRoot) {
            copy {
                from(sourceRoot)
                into(targetDir)
            }
            // Keep demo audio/lrc assets bundled for parity with legacy installable debug builds.
            val legacyDemoDir = file("${rootProject.projectDir}/axolync-browser/demo/assets")
            if (legacyDemoDir.exists()) {
                copy {
                    from(legacyDemoDir) {
                        include("*.ogg")
                        include("*.lrc")
                    }
                    into(file("${targetDir.absolutePath}/demo/assets"))
                }
            }
        } else {
            val distDir = file("${rootProject.projectDir}/axolync-browser/dist")
            val indexHtml = file("${rootProject.projectDir}/axolync-browser/index.html")
            if (!distDir.exists()) {
                throw GradleException(
                    "axolync-browser dist directory not found at ${distDir.absolutePath}. " +
                    "Please build axolync-browser first or provide AXOLYNC_BUILDER_BROWSER_NORMAL."
                )
            }
            if (!indexHtml.exists()) {
                throw GradleException("axolync-browser index.html not found at ${indexHtml.absolutePath}.")
            }

            copy {
                from(distDir) { include("**/*") }
                into(targetDir)
            }
            copy {
                from(indexHtml)
                into(targetDir)
                filter { line: String -> line.replace("src=\"/src/main.ts\"", "src=\"/main.js\"") }
            }
            copy {
                from("${rootProject.projectDir}/axolync-browser/demo") {
                    include("plugins/*.js")
                    include("assets/*.ogg")
                    include("assets/*.lrc")
                    into("demo")
                }
                into(targetDir)
            }
        }

        val preinstalledManifest = file("${targetDir.absolutePath}/plugins/preinstalled/manifest.json")
        if (!preinstalledManifest.exists()) {
            logger.lifecycle("No preinstalled plugin manifest found in copied browser assets (${preinstalledManifest.absolutePath})")
        }

        val copiedAssetsDir = file("${targetDir.absolutePath}/assets")
        val wrappedWorkersDir = file("${targetDir.absolutePath}/workers")
        wrappedWorkersDir.mkdirs()
        val requiredWorkers = listOf("lyricflowBridgeWorker.js", "syncengineBridgeWorker.js")
        requiredWorkers.forEach { workerName ->
            val workerFile = file("${wrappedWorkersDir.absolutePath}/${workerName}")
            if (!workerFile.exists()) {
                throw GradleException(
                    "Expected generated packaged bridge worker ${workerName} in ${wrappedWorkersDir.absolutePath}"
                )
            }
        }
        fileTree(copiedAssetsDir).matching {
            include("lyricflowBridgeWorker-*.ts")
            include("syncengineBridgeWorker-*.ts")
        }.files.forEach { staleWorker ->
            if (staleWorker.delete()) {
                logger.lifecycle("Removed stale raw bridge worker asset ${staleWorker.relativeTo(projectDir)}")
            }
        }
    }
}

// Make preBuild depend on copying assets
tasks.named("preBuild") {
    dependsOn("copyAxolyncBrowserAssets")
}

// Ensure copied assets always come from a freshly built browser dist.
tasks.named("copyAxolyncBrowserAssets") {
    dependsOn("buildAxolyncBrowserDist")
}

val lyricflowBackendRoot = providers.environmentVariable("AXOLYNC_LYRICFLOW_BACKEND_ROOT")
    .orElse("${rootProject.projectDir}/../axolync-lyricflow-plugin/backend-python")
val lyricflowBackendSourceDir = lyricflowBackendRoot.map { File(it).resolve("src/axolync_lyricflow_backend") }
val lyricflowAndroidBridgeSourceDir = lyricflowBackendRoot.map { File(it).resolve("src/axolync_android_bridge") }

val prepareEmbeddedPythonScaffold by tasks.registering {
    description = "Stage Android-usable Python sources from the LyricFlow backend repo"
    group = "build"

    inputs.dir(lyricflowBackendSourceDir)
    inputs.dir(lyricflowAndroidBridgeSourceDir)
    outputs.dir(embeddedPythonRoot)

    doLast {
        val sourceDir = embeddedPythonSourceDir.get().asFile
        val requirementsFile = embeddedPythonRequirementsFile.get().asFile
        val backendSourceDir = lyricflowBackendSourceDir.get()
        val bridgeSourceDir = lyricflowAndroidBridgeSourceDir.get()

        if (!backendSourceDir.exists()) {
            throw GradleException("Expected LyricFlow backend source directory at ${backendSourceDir.absolutePath}")
        }
        if (!bridgeSourceDir.exists()) {
            throw GradleException("Expected Android bridge source directory at ${bridgeSourceDir.absolutePath}")
        }

        delete(sourceDir)
        sourceDir.mkdirs()

        copy {
            from(backendSourceDir)
            into(File(sourceDir, "axolync_lyricflow_backend"))
        }
        copy {
            from(bridgeSourceDir)
            into(File(sourceDir, "axolync_android_bridge"))
        }

        if (!requirementsFile.exists()) {
            requirementsFile.parentFile.mkdirs()
            requirementsFile.writeText("# Populated by later Android embedded-Python packaging tasks.\n")
        }
    }
}

tasks.named("preBuild") {
    dependsOn(prepareEmbeddedPythonScaffold)
}

tasks.matching { it.name.endsWith("PythonRequirements") || it.name.endsWith("PythonBuildPackages") }.configureEach {
    dependsOn(prepareEmbeddedPythonScaffold)
}
