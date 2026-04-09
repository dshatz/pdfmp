plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.kt)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":sample-shared"))
    implementation(compose.desktop.currentOs)
}

compose.desktop.application {
    mainClass = "com.dshatz.pdfmp.MainKt"
}