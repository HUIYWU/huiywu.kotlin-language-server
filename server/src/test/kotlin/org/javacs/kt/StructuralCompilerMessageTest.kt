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
        assertTrue("Expected compiler messages for $relativePath, but collector was empty", messages.isNotEmpty())
        assertTrue("Expected at least one ERROR/EXCEPTION for $relativePath, actual severities=${messages.map { it.severity }}", messages.any {
            it.severity == CompilerMessageSeverity.ERROR || it.severity == CompilerMessageSeverity.EXCEPTION
        })
        assertTrue("Expected at least one message with location path ending in $relativePath, actual locations=${messages.map { it.location?.path }}", messages.any {
            val path = it.location?.path
            path != null && path.endsWith(relativePath)
        })
    }


    @Test fun `extra closing brace emits compiler error messages with file location`() {
        assertStructuralCompilerMessages("ExtraClosingBrace.kt")
    }

    @Test fun `missing closing brace emits compiler error messages with file location`() {
        assertStructuralCompilerMessages("MissingClosingBrace.kt")
    }

    @Test fun `missing closing paren emits compiler error messages with file location`() {
        assertStructuralCompilerMessages("MissingClosingParen.kt")
    }

    @Test fun `incomplete function body emits compiler error messages with file location`() {
        assertStructuralCompilerMessages("IncompleteFunctionBody.kt")
    }

    @Test fun `incomplete expression emits compiler error messages with file location`() {
        assertStructuralCompilerMessages("IncompleteExpression.kt")
    }
}