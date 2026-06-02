package org.javacs.kt

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.name.FqName
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class PredefinedJarClasspathInitializationTest : LanguageServerTestFixture(
    "mainWorkspaceJar",
    initializeParamsConfigurator = { init ->
        val predefinedClasspath = listOf(classesJar().toString())
        init.initializationOptions = createInitializationOptions(predefinedClasspath)
    }
) {
    private val file = "MainWorkspaceFile.kt"

    @Test fun `module sees androidx package from predefined jar injected at initialize time`() {
        val classesJar = classesJar()
        assertTrue(Files.exists(classesJar))

        val runtimeClasspath = languageServer.classPath.classPath.map { it.compiledJar.toString() }
        assertThat(runtimeClasspath, not(empty()))
        assertThat(runtimeClasspath, hasItem(containsString("classes.jar")))

        val compilerClasspath = languageServer.classPath.compiler.defaultJvmClasspathRootsForTests()
            .map { it.toString() }
        assertThat(compilerClasspath, not(empty()))
        assertThat(compilerClasspath, hasItem(containsString("classes.jar")))

        open(file)
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        assertThat(diagnostics, notNullValue())

        val compiled = languageServer.sourcePath.currentVersion(uri(file))
        val material3Package = compiled.module.getPackage(FqName("androidx.compose.material3"))
        val material3Descriptors = material3Package.memberScope.getContributedDescriptors()
            .map { it.name.asString() }

        assertThat(material3Descriptors, not(empty()))
        assertThat(material3Descriptors, hasItem("AlertDialogDefaults"))

        val compileDiagnostics = compiled.compile.diagnostics.map { diagnostic: Diagnostic ->
            val severity = diagnostic.severity.name
            val factory = diagnostic.factory.name
            "$severity:$factory"
        }

        assertThat(
            compileDiagnostics,
            not(hasItem(containsString("UNRESOLVED_REFERENCE")))
        )
        assertThat(errors, empty())
    }

    private companion object {
        fun classesJar() = testResourcesRoot().resolve("classpath/classes.jar")

        fun createInitializationOptions(predefinedClasspath: List<String>): JsonObject = JsonObject().apply {
            addProperty("usePredefinedClasspath", true)
            addProperty("disableDependencyResolution", true)
            add("classpath", JsonArray().apply {
                predefinedClasspath.forEach { add(it) }
            })
        }
    }
}
