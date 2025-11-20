plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.kt)
    alias(libs.plugins.android.app)
}

kotlin {
    jvmToolchain(21)
    jvm {
        mainRun {
            mainClass = "com.dshatz.pdfmp.MainKt"
        }
    }
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":pdfmp-compose"))
//            implementation("com.dshatz.pdfmp:core:1.0.0")
            implementation(compose.desktop.common)
            implementation(compose.runtime)
            implementation(compose.material3)
            implementation(compose.components.resources)

            implementation(libs.filepicker)
            implementation(libs.filepicker.compose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.11.0")
        }
    }
}

android {
    namespace = "com.dshatz.pdfmp.sample"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
}

compose.desktop.application {
    mainClass = "com.dshatz.pdfmp.MainKt"
}