plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.axolync.android"
    compileSdk = 34

    signingConfigs {
        create("axolyncTrackedDebug") {
            storeFile = rootProject.file("signing/axolync-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.axolync.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "1.7.0-beta.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        aaptOptions {
            ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"
        }
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
        debug {
            signingConfig = signingConfigs.getByName("axolyncTrackedDebug")
        }
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
            assets.setSrcDirs(listOf("src/main/assets"))
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.11.0")
    implementation(project(":capacitor-android"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

tasks.register<Exec>("stageCapacitorBrowserAssets") {
    description = "Stage browser bundle into Capacitor public assets"
    group = "build"
    workingDir = rootProject.projectDir
    commandLine("node", "scripts/stage-browser-assets.mjs")

    inputs.file(rootProject.file("scripts/stage-browser-assets.mjs"))
    outputs.dir(file("$projectDir/src/main/assets/public"))
    outputs.upToDateWhen { false }
}

tasks.named("preBuild") {
    dependsOn("stageCapacitorBrowserAssets")
}
