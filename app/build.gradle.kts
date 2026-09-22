plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dk.fynogjylland.paafarten"
    compileSdk = 36

    defaultConfig {
        applicationId = "dk.fynogjylland.paafarten"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.2.5"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            storeFile = System.getenv("PAAFARTEN_KEYSTORE_FILE")?.let { file(it) }
            storePassword = System.getenv("PAAFARTEN_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("PAAFARTEN_KEY_ALIAS")
            keyPassword = System.getenv("PAAFARTEN_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
