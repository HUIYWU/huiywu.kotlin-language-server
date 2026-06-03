package org.javacs.kt

import org.javacs.kt.compiler.Compiler
import org.javacs.kt.diagnostic.structuralCompilerMessagesFor
import org.javacs.kt.diagnostic.structuralCompilerMessagesFromDiagnostics
import org.javacs.kt.util.LoggingMessageCollector
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StructuralCompilerBridgeObservationTest {
    companion object {
        lateinit var outputDirectory: File

        @JvmStatic @BeforeClass fun setup() {
            LOG.connectStdioBackend()
            outputDirectory = Files.createTempDirectory("klsStructuralBridgeObservationBuildOutput").toFile()
        }

        @JvmStatic @AfterClass fun tearDown() {
            outputDirectory.delete()
        }
    }

    @Test fun `extra closing brace bridge emits compiler-like messages from binding diagnostics`() {
        Compiler(
            javaSourcePath = setOf(),
            classPath = setOf(),
            scriptsConfig = ScriptsConfiguration(),
            codegenConfig = CodegenConfiguration(),
            outputDirectory = outputDirectory
        ).use { compiler ->
            val file = testResourcesRoot().resolve("diagnosticStructural/ExtraClosingBrace.kt")
            val content = Files.readAllLines(file).joinToString("\n")
            val parse = compiler.createKtFile(content, file)
            LoggingMessageCollector.clear()
            val (context, _) = compiler.compileKtFile(parse, listOf(parse))
            val bridgeMessages = structuralCompilerMessagesFromDiagnostics(context)
            val bindingFactoryNames = context.diagnostics.map { it.factory.name }.toList()

            assertTrue(
                "Expected non-empty bridge messages for ExtraClosingBrace.kt, factories=$bindingFactoryNames",
                bridgeMessages.isNotEmpty()
            )
        }
    }

    @Test fun `incomplete function body compiler-like messages can fall back to structural synthesis`() {
        Compiler(
            javaSourcePath = setOf(),
            classPath = setOf(),
            scriptsConfig = ScriptsConfiguration(),
            codegenConfig = CodegenConfiguration(),
            outputDirectory = outputDirectory
        ).use { compiler ->
            val file = testResourcesRoot().resolve("diagnosticStructural/IncompleteFunctionBody.kt")
            val content = Files.readAllLines(file).joinToString("\n")
            val parse = compiler.createKtFile(content, file)
            LoggingMessageCollector.clear()
            val (context, _) = compiler.compileKtFile(parse, listOf(parse))
            val compilerLikeMessages = structuralCompilerMessagesFor(listOf(parse), context)
            val bindingFactoryNames = context.diagnostics.map { it.factory.name }.toList()

            assertTrue(
                "Expected non-empty compiler-like messages for IncompleteFunctionBody.kt, factories=$bindingFactoryNames",
                compilerLikeMessages.isNotEmpty()
            )
        }
    }
}
