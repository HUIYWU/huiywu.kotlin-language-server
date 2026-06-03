package org.javacs.kt.diagnostic

import com.intellij.openapi.util.TextRange
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.javacs.kt.position.position
import org.javacs.kt.util.CompilerMessageEntry
import org.javacs.kt.util.toPath
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.Diagnostic as KotlinDiagnostic
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext

fun structuralCompilerMessagesFor(files: Collection<KtFile>, bindingContext: BindingContext): List<CompilerMessageEntry> {
    val bridgedDiagnostics = structuralCompilerMessagesFromDiagnostics(bindingContext)
    if (bridgedDiagnostics.isNotEmpty()) return bridgedDiagnostics

    return files.flatMap(::structuralCompilerMessagesFromFallback)
}

fun structuralCompilerMessagesFromDiagnostics(bindingContext: BindingContext): List<CompilerMessageEntry> {
    val errorDiagnostics = bindingContext.diagnostics
        .asSequence()
        .filter { diagnostic -> diagnostic.severity == Severity.ERROR }
        .toList()
    val selectedDiagnostics = errorDiagnostics.filter(::isStructuralDiagnostic).ifEmpty { errorDiagnostics }

    return selectedDiagnostics
        .asSequence()
        .flatMap { diagnostic: KotlinDiagnostic ->
            diagnosticRanges(diagnostic).asSequence().map { range: TextRange ->
                CompilerMessageEntry(
                    severity = CompilerMessageSeverity.ERROR,
                    message = renderCompilerStyleMessage(diagnostic),
                    location = compilerMessageLocation(diagnostic, range)
                )
            }
        }
        .toList()
}

private fun structuralCompilerMessagesFromFallback(file: KtFile): List<CompilerMessageEntry> {
    val uri = file.toPath().toUri()
    val content = file.text
    return structuralFallbackDiagnostics(uri, content)
        .map { (_, diagnostic) ->
            CompilerMessageEntry(
                severity = CompilerMessageSeverity.ERROR,
                message = diagnostic.message,
                location = compilerMessageLocation(file, diagnostic)
            )
        }
}

private fun diagnosticRanges(diagnostic: KotlinDiagnostic): List<TextRange> =
    diagnostic.textRanges.takeIf { it.isNotEmpty() }
        ?: diagnostic.psiElement.textRange
            ?.takeIf { it.length >= 0 }
            ?.let(::listOf)
        ?: listOf(TextRange(0, diagnostic.psiFile.textLength.coerceAtLeast(1)))

private fun isStructuralDiagnostic(diagnostic: KotlinDiagnostic): Boolean {
    val factoryName = diagnostic.factory.name
    return factoryName in STRUCTURAL_DIAGNOSTIC_FACTORIES ||
        STRUCTURAL_DIAGNOSTIC_KEYWORDS.any { keyword -> keyword in factoryName }
}

private fun renderCompilerStyleMessage(diagnostic: KotlinDiagnostic): String =
    org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages.render(diagnostic)

private fun compilerMessageLocation(diagnostic: KotlinDiagnostic, range: TextRange): CompilerMessageLocation? {
    val content = diagnostic.psiFile.text
    val safeStartOffset = range.startOffset.coerceIn(0, content.length.coerceAtLeast(0))
    val start = position(content, safeStartOffset)
    val path = diagnostic.psiFile.toPath().toString()
    val lineContent = content.lineSequence().elementAtOrNull(start.line)
    return CompilerMessageLocation.create(
        path,
        start.line + 1,
        start.character + 1,
        lineContent
    )
}

private fun compilerMessageLocation(file: KtFile, diagnostic: Diagnostic): CompilerMessageLocation? {
    val content = file.text
    val start: Position = diagnostic.range.start
    val path = file.toPath().toString()
    val lineContent = content.lineSequence().elementAtOrNull(start.line)
    return CompilerMessageLocation.create(
        path,
        start.line + 1,
        start.character + 1,
        lineContent
    )
}

private val STRUCTURAL_DIAGNOSTIC_FACTORIES = setOf(
    "EXTRA_RIGHT_BRACE",
    "SYNTAX",
    "PARSER_ERROR"
)

private val STRUCTURAL_DIAGNOSTIC_KEYWORDS = listOf(
    "EXPECTED",
    "SYNTAX",
    "PARSE",
    "BRACE",
    "PAREN"
)
