plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.axolync.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.axolync.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
tasks.register<Copy>("copyAxolyncBrowserAssets") {
    description = "Copy built axolync-browser assets to Android assets directory"
    group = "build"
    
    // Copy dist directory contents
    from("${rootProject.projectDir}/axolync-browser/dist") {
        include("**/*")
    }
    // Copy index.html from root
    from("${rootProject.projectDir}/axolync-browser") {
        include("index.html")
    }
    into("${projectDir}/src/main/assets/axolync-browser")
    
    doFirst {
        val sourceDir = file("${rootProject.projectDir}/axolync-browser/dist")
        if (!sourceDir.exists()) {
            throw GradleException(
                "axolync-browser dist directory not found at ${sourceDir.absolutePath}. " +
                "Please build axolync-browser first or add it as a Git submodule."
            )
        }
        val indexHtml = file("${rootProject.projectDir}/axolync-browser/index.html")
        if (!indexHtml.exists()) {
            throw GradleException(
                "axolync-browser index.html not found at ${indexHtml.absolutePath}."
            )
        }
    }
}

// Make preBuild depend on copying assets
tasks.named("preBuild") {
    dependsOn("copyAxolyncBrowserAssets")
}
