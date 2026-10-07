import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Voice-sign ingest key for the UniFusion machine. The committed value below is a PLACEHOLDER
// (empty string) — never commit a real key here. For local/debug builds you may inject it via
// the gitignored `local.properties` (VOICE_SIGN_API_KEY=...) or an environment variable;
// production should deliver it at runtime via SecureStorage / remote config instead of baking
// it into the APK.
val voiceSignApiKey: String = Properties().apply {
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) localProps.inputStream().use { load(it) }
}.getProperty("VOICE_SIGN_API_KEY", "")

android {
    namespace = "ai.voxsign.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.voxsign.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Backend endpoint is configuration-driven. In a real deployment this points at the
        // VoxSign cloud; when the endpoint is unreachable the app falls back to local mock data so
        // the UI is fully exercisable with no server.
        buildConfigField("String", "BACKEND_BASE_URL", "\"https://cloud.voxsign.ai\"")

        // UniFusion voice-channel ingest key (placeholder in VCS; injected locally / delivered
        // at runtime in production — see note above).
        buildConfigField("String", "VOICE_SIGN_API_KEY", "\"${voiceSignApiKey.replace("\"", "\\\"")}\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
