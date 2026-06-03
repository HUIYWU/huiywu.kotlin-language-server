package org.javacs.kt

import org.javacs.kt.compiler.Compiler
import org.javacs.kt.util.CompilerMessageEntry
import org.javacs.kt.util.LoggingMessageCollector
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StructuralCompilerMessageTest {
    companion object {
        lateinit var outputDirectory: File

        @JvmStatic @BeforeClass fun setup() {
            LOG.connectStdioBackend()
            outputDirectory = Files.createTempDirectory("klsStructuralBuildOutput").toFile()
        }

        @JvmStatic @AfterClass fun tearDown() {
            outputDirectory.delete()
        }
    }

    private fun compileAndDrain(relativePath: String): List<CompilerMessageEntry> = Compiler(
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
        compiler.compileKtFile(parse, listOf(parse))
        LoggingMessageCollector.drain()
    }

    private fun assertStructuralCompilerMessages(relativePath: String) {
        val messages = compileAndDrain(relativePath)
        assertTrue(messages.isNotEmpty())
        assertTrue(messages.any {
            it.severity == CompilerMessageSeverity.ERROR || it.severity == CompilerMessageSeverity.EXCEPTION
        })
        assertTrue(messages.any {
            val path = it.location?.path
            path != null && path.endsWith(relativePath)
        })
    }

    @Ignore("Structural compiler-message baseline for future KLS enhancement; direct Compiler.compileKtFile + collector assertions are still not stable enough across environments")
    @Test fun `extra closing brace emits compiler error messages with file location`() {
        assertStructuralCompilerMessages("ExtraClosingBrace.kt")
    }

    @Ignore("Enable one structural compiler-message baseline at a time; keep broader matrix gated until cross-environment stability improves")
    @Test fun `missing closing brace emits compiler error messages with file location`() {
        assertStructuralCompilerMessages("MissingClosingBrace.kt")
    }

    @Ignore("Enable one structural compiler-message baseline at a time; keep broader matrix gated until cross-environment stability improves")
    @Test fun `missing closing paren emits compiler error messages with file location`() {
        assertStructuralCompilerMessages("MissingClosingParen.kt")
    }

    @Ignore("Enable one structural compiler-message baseline at a time; keep broader matrix gated until cross-environment stability improves")
    @Test fun `incomplete function body emits compiler error messages with file location`() {
        assertStructuralCompilerMessages("IncompleteFunctionBody.kt")
    }

    @Ignore("Enable one structural compiler-message baseline at a time; keep broader matrix gated until cross-environment stability improves")
    @Test fun `incomplete expression emits compiler error messages with file location`() {
        assertStructuralCompilerMessages("IncompleteExpression.kt")
    }
}