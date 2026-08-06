plugins {
    alias(libs.plugins.jvm)
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
}

gradlePlugin {
    plugins {
        create("wasmAssetPlugin") {
            id = "com.dshatz.wasm-plugin"
            implementationClass = "com.dshatz.pdfmp.WasmAssetPlugin"
        }
    }
}