package org.javacs.kt

import org.javacs.kt.compiler.Compiler
import org.javacs.kt.util.LoggingMessageCollector
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StructuralCompilerObservationTest {
    companion object {
        lateinit var outputDirectory: File

        @JvmStatic @BeforeClass fun setup() {
            LOG.connectStdioBackend()
            outputDirectory = Files.createTempDirectory("klsStructuralObservationBuildOutput").toFile()
        }

        @JvmStatic @AfterClass fun tearDown() {
            outputDirectory.delete()
        }
    }

    private data class CompileObservation(
        val diagnosticsCount: Int,
        val collectorCount: Int,
    )

    private fun compileObservation(relativePath: String): CompileObservation = Compiler(
        javaSourcePath = setOf(),
        classPath = setOf(),
        scriptsConfig = ScriptsConfiguration(),
        codegenConfig = CodegenConfiguration(),
        outputDirectory = outputDirectory
    ).use { compiler ->
        val file = testResourcesRoot().resolve("diagnosticStructural/$relativePath")
        val content = Files.readAllLines(file).joinToString("\n")
        val parse = compiler.createKtFile(content, file)
        LoggingMessageCollector.clear()
        val (context, _) = compiler.compileKtFile(parse, listOf(parse))
        val messages = LoggingMessageCollector.drain()
        CompileObservation(
            diagnosticsCount = context.diagnostics.count(),
            collectorCount = messages.size,
        )
    }

    @Test fun `extra closing brace compile exposes diagnostics even if collector is empty`() {
        val observation = compileObservation("ExtraClosingBrace.kt")

        assertTrue(
            "Expected binding-context diagnostics for ExtraClosingBrace.kt, but diagnosticsCount=${observation.diagnosticsCount} and collectorCount=${observation.collectorCount}",
            observation.diagnosticsCount > 0
        )
    }
}
