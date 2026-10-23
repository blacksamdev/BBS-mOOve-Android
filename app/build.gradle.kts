plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

// Clé de signature PRIVÉE de BBS mOOve.
// Elle n'est jamais dans le dépôt : en CI, le workflow la reconstitue depuis
// les secrets GitHub (BBS_KEYSTORE_BASE64 -> fichier temporaire) et passe son
// chemin + mots de passe par variables d'environnement.
// Sans ces variables (build local sur un autre poste), l'APK release sort
// simplement non signé : aucune clé ni mot de passe n'est jamais committé.
val releaseKeystorePath: String? = System.getenv("BBS_KEYSTORE_PATH")

android {
    namespace = "com.blacksamdev.bbsmoove"
    compileSdk = 35

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("BBS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BBS_KEY_ALIAS")
                keyPassword = System.getenv("BBS_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.blacksamdev.bbsmoove"
        minSdk = 26
        targetSdk = 35

        // versionCode auto-incrémenté en CI : on prend le numéro de run
        // GitHub Actions (GITHUB_RUN_NUMBER), qui augmente à chaque build.
        // En local (variable absente) on retombe sur 1 pour les builds manuels.
        // Sans ça, le versionCode resterait figé et Android pourrait refuser
        // d'installer une nouvelle nightly par-dessus l'ancienne.
        val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = runNumber
        versionName = if (System.getenv("GITHUB_RUN_NUMBER") != null) {
            "0.1.0-build.$runNumber"
        } else {
            "0.1.0-dev"
        }

        // Chaquopy : on cible arm64 + armeabi, les profils courants des téléphones Android
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        // L'app doit pouvoir tourner en portrait ET paysage (split zone1/zone2)
        // -> pas de android:screenOrientation fixé dans le manifest
    }

    buildTypes {
        debug {
            // Builds locaux uniquement : signés avec la clé de debug générée
            // par Android Studio sur le poste (~/.android/debug.keystore).
            // L'ancienne clé debug.keystore committée (mot de passe "android"
            // public) a été retirée du dépôt.
        }
        release {
            // Pas de minification : R8 risquerait de casser Chaquopy et les
            // appels par réflexion, pour un gain de taille négligeable ici.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
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

    // play-services-location tire en dépendance une très vieille version de
    // androidx.fragment (< 1.3), qui ne relaie pas correctement les résultats
    // de demande de permission (registerForActivityResult). Le contrôle lint
    // des builds release le signale comme erreur fatale : on force une version
    // récente.
    implementation("androidx.fragment:fragment-ktx:1.8.3")

    // SQLite local (extrait OSM + radars)
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Préférences utilisateur (panneau d'options)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
