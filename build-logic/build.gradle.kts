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
}

group = "com.dshatz.pdfmp.buildlogic"