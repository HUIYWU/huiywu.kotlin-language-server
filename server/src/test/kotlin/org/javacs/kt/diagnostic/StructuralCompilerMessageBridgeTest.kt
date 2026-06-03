package org.javacs.kt.diagnostic

import org.javacs.kt.CodegenConfiguration
import org.javacs.kt.LOG
import org.javacs.kt.ScriptsConfiguration
import org.javacs.kt.compiler.Compiler
import org.javacs.kt.testResourcesRoot
import org.javacs.kt.util.CompilerMessageEntry
import org.javacs.kt.util.LoggingMessageCollector
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StructuralCompilerMessageBridgeTest {
    companion object {
        lateinit var outputDirectory: File

        @JvmStatic @BeforeClass fun setup() {
            LOG.connectStdioBackend()
            outputDirectory = Files.createTempDirectory("klsStructuralBridgeBuildOutput").toFile()
        }

        @JvmStatic @AfterClass fun tearDown() {
            outputDirectory.delete()
        }
    }

    private data class BridgeObservation(
        val bindingFactoryNames: List<String>,
        val bridgedMessages: List<CompilerMessageEntry>,
        val compilerLikeMessages: List<CompilerMessageEntry>,
    )

    private fun compileObservation(relativePath: String): BridgeObservation = Compiler(
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
        LoggingMessageCollector.drain()
        BridgeObservation(
            bindingFactoryNames = context.diagnostics.map { it.factory.name }.toList(),
            bridgedMessages = structuralCompilerMessagesFromDiagnostics(context),
            compilerLikeMessages = structuralCompilerMessagesFor(listOf(parse), context),
        )
    }

    private fun messageSignatures(entries: List<CompilerMessageEntry>) = entries.map {
        listOf(
            it.severity.name,
            it.message,
            it.location?.path ?: "",
            (it.location?.line ?: -1).toString(),
            (it.location?.column ?: -1).toString(),
        )
    }

    @Test fun `extra closing brace prefers binding bridge when diagnostics are available`() {
        val observation = compileObservation("ExtraClosingBrace.kt")

        assertTrue(
            "Expected non-empty bridge messages for ExtraClosingBrace.kt, factories=${observation.bindingFactoryNames}",
            observation.bridgedMessages.isNotEmpty()
        )
        assertEquals(messageSignatures(observation.bridgedMessages), messageSignatures(observation.compilerLikeMessages))
        assertTrue(
            "Expected at least one bridged location path ending in ExtraClosingBrace.kt, actual=${observation.bridgedMessages.map { it.location?.path }}",
            observation.bridgedMessages.any { it.location?.path?.endsWith("ExtraClosingBrace.kt") == true }
        )
    }

    @Test fun `incomplete function body falls back to structural synthesis when binding bridge is empty`() {
        val observation = compileObservation("IncompleteFunctionBody.kt")

        assertTrue(
            "Expected empty bridge messages for IncompleteFunctionBody.kt, factories=${observation.bindingFactoryNames}",
            observation.bridgedMessages.isEmpty()
        )
        assertTrue(
            "Expected non-empty compiler-like messages for IncompleteFunctionBody.kt",
            observation.compilerLikeMessages.isNotEmpty()
        )
        assertTrue(
            "Expected structural synthesis message for IncompleteFunctionBody.kt, actual=${observation.compilerLikeMessages.map { it.message }}",
            observation.compilerLikeMessages.any { it.message.contains("Incomplete function body") }
        )
        assertTrue(
            "Expected at least one synthesized location path ending in IncompleteFunctionBody.kt, actual=${observation.compilerLikeMessages.map { it.location?.path }}",
            observation.compilerLikeMessages.any { it.location?.path?.endsWith("IncompleteFunctionBody.kt") == true }
        )
    }

    @Test fun `incomplete expression currently prefers binding bridge over structural synthesis`() {
        val observation = compileObservation("IncompleteExpression.kt")

        assertTrue(
            "Expected non-empty bridge messages for IncompleteExpression.kt, factories=${observation.bindingFactoryNames}",
            observation.bridgedMessages.isNotEmpty()
        )
        assertEquals(messageSignatures(observation.bridgedMessages), messageSignatures(observation.compilerLikeMessages))
        assertTrue(
            "Expected at least one bridged location path ending in IncompleteExpression.kt, actual=${observation.compilerLikeMessages.map { it.location?.path }}",
            observation.compilerLikeMessages.any { it.location?.path?.endsWith("IncompleteExpression.kt") == true }
        )
        assertTrue(
            "Expected binding-bridge message for IncompleteExpression.kt to remain compiler-originated rather than empty, actual=${observation.compilerLikeMessages.map { it.message }}",
            observation.compilerLikeMessages.any { it.message.isNotBlank() }
        )
    }
}
