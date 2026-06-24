plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.blacksamdev.bbsmoove"
    compileSdk = 35

    signingConfigs {
        getByName("debug") {
            // Keystore fixe (committé dans app/keystore/debug.keystore), pas le
            // ~/.android/debug.keystore aléatoire généré par poste -- sinon
            // chaque run CI produirait une signature différente et casserait
            // l'installation incrémentale ("mise à jour" au lieu de
            // "désinstaller puis réinstaller") d'un build nightly à l'autre.
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.blacksamdev.bbsmoove"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Chaquopy : on cible arm64 + armeabi, les profils courants des téléphones Android
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        // L'app doit pouvoir tourner en portrait ET paysage (split zone1/zone2)
        // -> pas de android:screenOrientation fixé dans le manifest
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        // Pas de pip install : road_lookup.py / danger_lookup.py sont en
        // pure stdlib (math, json) -- on évite les libs à extensions C
        // (type shapely) qui sont une source classique de galères de
        // compilation/ABI sous Chaquopy.
    }
    sourceSets {
        getByName("main") {
            srcDir("src/main/python")
        }
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Localisation
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // SQLite local (extrait OSM + radars)
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
