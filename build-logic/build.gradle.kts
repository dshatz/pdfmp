plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(gradleKotlinDsl())
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin")
    implementation("com.google.gradle:osdetector-gradle-plugin:1.7.3")
}

group = "com.dshatz.pdfmp.buildlogic"