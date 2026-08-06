plugins {
    alias(libs.plugins.jvm)
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
}

gradlePlugin {
    plugins {
        create("wasmAssetPlugin") {
            id = "com.dshatz.wasm-plugin"
            implementationClass = "com.dshatz.pdfmp.WasmAssetPlugin"
        }
    }
}