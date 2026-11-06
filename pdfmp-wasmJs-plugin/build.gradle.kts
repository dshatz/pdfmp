import org.jetbrains.kotlin.gradle.internal.backend.common.serialization.metadata.DynamicTypeDeserializer.id
import com.vanniktech.maven.publish.DeploymentValidation

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.publish)
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
            id = "com.dshatz.pdfmp.wasm-plugin"
            implementationClass = "com.dshatz.pdfmp.WasmAssetPlugin"
        }
    }
}

mavenPublishing {
    signAllPublications()
    publishToMavenCentral(true, DeploymentValidation.NONE)
    coordinates("com.dshatz.pdfmp", "wasm-plugin", project.version.toString())

    pom {
        name.set("PDF Multiplatform")
        description.set("A multiplatform PDF display library for Kotlin. Gradle plugin for wasm.")
        inceptionYear.set("2025")
        url.set("https://github.com/dshatz/pdfmp/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("dshatz")
                name.set("Daniels Šatcs")
                url.set("https://github.com/dshatz/")
            }
        }
        scm {
            url.set("https://github.com/dshatz/pdfmp/")
            connection.set("scm:git:git://github.com/dshatz/pdfmp.git")
            developerConnection.set("scm:git:ssh://git@github.com/dshatz/pdfmp.git")
        }
    }
}