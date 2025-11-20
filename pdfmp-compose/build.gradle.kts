import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.kotlin.dsl.desktop
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.kt)
    alias(libs.plugins.android.lib)
    alias(libs.plugins.publish)
}


kotlin {
    jvmToolchain(21)
    androidTarget()
    jvm()
//    iosArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":pdfmp"))
            implementation(compose.runtime)
            implementation(compose.material3)
            implementation(compose.foundation)
        }
    }
}

android {
    namespace = "com.dshatz.pdfmp.compose"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
}
