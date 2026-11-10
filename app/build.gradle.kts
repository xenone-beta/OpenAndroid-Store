plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Values injected by CI (see .github/workflows/release.yml). Locally they fall back to defaults.
val appVersionName: String = System.getenv("APP_VERSION_NAME") ?: "1.2.0"
val appVersionCode: Int = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 1
val releaseKeystorePath: String? = System.getenv("STORE_KEYSTORE_FILE")

android {
    namespace = "dev.openandroid.store"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.openandroid.store"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        // Where the catalog lives. Users can change it in Settings.
        buildConfigField("String", "DEFAULT_INDEX_URL", "\"https://xenone-beta.github.io/OpenAndroid-Index/index.json\"")
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("STORE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("STORE_KEY_ALIAS")
                keyPassword = System.getenv("STORE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Shrinking is off on purpose: the app is small and this avoids reflection surprises.
            isMinifyEnabled = false
            // Without a release keystore the APK is signed with the debug key, so it still installs.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

    lint {
        abortOnError = false
        checkDependencies = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // android.jar only has stubs for org.json; unit tests need the real thing.
    testImplementation(libs.org.json)
}
