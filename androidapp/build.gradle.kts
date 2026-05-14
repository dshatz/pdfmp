plugins {
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.kt)
    alias(libs.plugins.android.app)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "com.dshatz.pdfmp.sample"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = buildTypes.getByName("debug").signingConfig
            proguardFiles("rules.pro")
        }
    }
}

dependencies {
    implementation(project(":sample-shared"))
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
}