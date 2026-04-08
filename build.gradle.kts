plugins {
    alias(libs.plugins.mp) apply false
    alias(libs.plugins.knee) apply false
    alias(libs.plugins.android.lib) apply false
    alias(libs.plugins.android.app) apply false
    alias(libs.plugins.publish) apply false
    alias(libs.plugins.testballoon) apply false
}

buildscript {
    dependencies {
        classpath("com.dshatz.pdfmp.buildlogic:build-logic")
    }
}