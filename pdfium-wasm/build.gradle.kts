import com.dshatz.jsworker.asWebWorker
import com.vanniktech.maven.publish.DeploymentValidation
import org.gradle.kotlin.dsl.jacoco

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.ksp)
    alias(libs.plugins.publish)
    alias(libs.plugins.kni)
    alias(libs.plugins.serial)
    alias(libs.plugins.testballoon)
    alias(libs.plugins.jsworker)
    alias(libs.plugins.wasmplugin)
}

kotlin {
    jvmToolchain(21)
    applyDefaultHierarchyTemplate()
    wasmJs {
        binaries.executable()
        browser()
        asWebWorker()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":pdfium"))
            api(libs.jsworker.workers)
        }
    }
}

dependencies {
    "kspWasmJs"(libs.jsworker.ksp)
}

mavenPublishing {
    signAllPublications()
    publishToMavenCentral(true, DeploymentValidation.NONE)
    coordinates("com.dshatz.pdfmp", "pdfium-wasm", project.version.toString())

    pom {
        name.set("PDF Multiplatform Pdfium Wasm")
        description.set("A multiplatform PDF display library for Kotlin.")
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