import java.util.Properties
import groovy.lang.MissingPropertyException

plugins {
    // Important: use the built-in kotlin-dsl plugin without an externally resolved version.
    // CI on Java 11/macOS failed when Gradle tried to resolve
    // `org.gradle.kotlin.kotlin-dsl:...:4.5.0` from the plugin portal instead of using the bundled plugin.
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// Use the same Java version for compiling the convention plugins as for the main project
// For this we need to manually load the gradle.properties, since variables from the main
// project are generally not visible from buildSrc.

val javaVersionProperty = "javaVersion"
val javaVersion = try {
    project.property(javaVersionProperty) as String
} catch (e: MissingPropertyException) {
    Properties().also { properties ->
        File("$rootDir/../gradle.properties").inputStream().use { stream ->
            properties.load(stream)
        }
    }[javaVersionProperty] as String
}

kotlin {
    jvmToolchain(javaVersion.trim().toInt())
}

dependencies {
    implementation(libs.org.jetbrains.kotlin.gradle.plugin)
}
