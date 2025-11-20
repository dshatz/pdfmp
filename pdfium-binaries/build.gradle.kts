plugins {
    // 'base' gives us 'group' and 'version' helpers without all the java stuff
    id("base")
    id("maven-publish")
}

// Set your group and version.
// Make this match your other KMP libraries.
group = "com.dshatz"
version = "1.0.0"


val linuxJar by tasks.registering(Jar::class) {
    // This is the classifier that consumers will use
    archiveClassifier.set("linux-x64")

    // Copy from your binaries folder...
    from("binaries/linux-x64") {
        into("natives/linux-x64")
    }
}

val macosJar by tasks.registering(Jar::class) {
    archiveClassifier.set("macos-arm64")
    from("binaries/macos-arm64") {
        into("natives/macos-arm64")
    }
}

val windowsJar by tasks.registering(Jar::class) {
    archiveClassifier.set("windows-x64")
    from("binaries/windows-x64") {
        into("natives/windows-x64")
    }
}

publishing {
    publications {
        create<MavenPublication>("binaries") {
            // This is the Maven artifactId
            artifactId = "pdfium-binaries"

            // Add all your JARs as artifacts
            // Gradle will automatically use the classifiers
            // you defined in the Jar tasks above.
            artifact(linuxJar)
            artifact(macosJar)
            artifact(windowsJar)
        }
    }

    repositories {
        // For local testing:
        mavenLocal()

        // For Maven Central:
        // maven {
        //     url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
        //     // ... credentials ...
        // }
    }
}