plugins {
    alias(libs.plugins.jvm)
    if (freshCompose) {
        alias(libs.plugins.compose.fresh)
    } else {
        alias(libs.plugins.compose)
    }
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
// Prototype: pdfium rendering into a GL texture. Needs Skiko 0.151+ (Canvas.recordingContext), i.e. fresh Compose.
if (freshCompose) {
    kotlin.sourceSets.getByName("main").kotlin.srcDir("src/glDemo/kotlin")
    val lwjgl = "3.3.6"
    dependencies {
        implementation("org.lwjgl:lwjgl:$lwjgl")
        implementation("org.lwjgl:lwjgl-opengl:$lwjgl")
        runtimeOnly("org.lwjgl:lwjgl:$lwjgl:natives-linux")
        runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjgl:natives-linux")
    }
    tasks.register<JavaExec>("runGlDemo") {
        group = "application"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.dshatz.pdfmp.gldemo.GlDemoKt")
        workingDir = rootProject.projectDir
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        args(providers.gradleProperty("glDemoArgs").orElse("").get().split(" ").filter { it.isNotBlank() })
    }
}
