plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Firebase is wired in only when the project has actually been configured.
 *
 * The Google Services plugin fails the whole build if google-services.json is
 * missing, which would mean nobody can compile CareLoop until they have created
 * a Firebase project. That is a bad first experience for anyone cloning this, and
 * it also blocks work on pure-UI changes.
 *
 * So: drop the file in and Firebase turns on; leave it out and the app builds and
 * runs on local demo data. `BuildConfig.FIREBASE_ENABLED` lets the Kotlin side
 * make the same distinction at runtime.
 */
val googleServicesFile = project.file("google-services.json")
val firebaseEnabled = googleServicesFile.exists()

if (firebaseEnabled) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle(
        "CareLoop: google-services.json not found, building without Firebase. " +
            "See docs/MORNING_CHECKLIST.md step 5.",
    )
}

android {
    namespace = "com.careloop.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.careloop.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"

        buildConfigField("boolean", "FIREBASE_ENABLED", firebaseEnabled.toString())
    }

    buildTypes {
        debug {
            // No applicationIdSuffix.
            //
            // The suffix made the debug package com.careloop.app.debug, which
            // the Google Services plugin rejects outright: google-services.json
            // registers com.careloop.app and nothing else. The alternative is
            // registering a second Android app in the Firebase console purely so
            // debug builds can exist, which also means a second FCM registration
            // and a second place for push to silently go to the wrong one.
            //
            // The debug build IS the build that gets installed and demonstrated
            // here, so it should carry the real application id. The cost is that
            // debug and release cannot sit side by side on one device, which
            // this project never needs.
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Firebase is compiled in regardless of whether google-services.json exists.
    // The classes must resolve for the code to build; what changes is whether
    // they are initialised at runtime. See CareLoopApplication.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)

    implementation(libs.okhttp)

    debugImplementation(libs.androidx.ui.tooling)
}
