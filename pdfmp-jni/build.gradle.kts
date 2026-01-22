import com.android.build.api.dsl.androidLibrary

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.android.lib.mp)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(21)
    jvm()
    androidLibrary {
        namespace = "com.dshatz.jni"
        minSdk = 24
        compileSdk = 36
    }

    linuxX64()
    linuxArm64()
    macosArm64()
    mingwX64()
    androidNativeX64()
    androidNativeX86()
    androidNativeArm64()
    androidNativeArm32()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.jni)
            implementation(libs.jni.annotations)
        }
    }
}

dependencies {
    add("kspLinuxX64", libs.jni.ksp)
    add("kspLinuxArm64", libs.jni.ksp)
    add("kspMingwX64", libs.jni.ksp)
    add("kspMacosArm64", libs.jni.ksp)
    add("kspAndroidNativeX64", libs.jni.ksp)
    add("kspAndroidNativeX86", libs.jni.ksp)
    add("kspAndroidNativeArm64", libs.jni.ksp)
    add("kspAndroidNativeArm32", libs.jni.ksp)
}