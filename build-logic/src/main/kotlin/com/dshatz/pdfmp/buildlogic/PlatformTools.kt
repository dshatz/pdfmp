package com.dshatz.pdfmp.buildlogic

import com.google.gradle.osdetector.OsDetector
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

fun Project.getHost(): Host {
    val osdetector = extensions.getByType<OsDetector>()
    return when (osdetector.os) {
        "linux" -> Host.Linux
        "osx" -> Host.MAC
        "windows" -> Host.Windows
        else -> {
            val hostOs = System.getProperty("os.name")
            val isMingwX64 = hostOs.startsWith("Windows")

            when {
                hostOs == "Linux" -> Host.Linux
                hostOs == "Mac OS X" -> Host.MAC
                isMingwX64 -> Host.Windows
                else -> throw IllegalStateException("Unknown OS: ${osdetector.classifier}")
            }
        }
    }
}


fun Project.isMacos(): Boolean {
    return getHost() == Host.MAC
}
