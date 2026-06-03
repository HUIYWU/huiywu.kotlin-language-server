package org.javacs.kt.diagnostic

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.net.URI
import java.util.ArrayDeque

private data class StructuralDelimiterState(
    val char: Char,
    val line: Int,
    val column: Int,
)

private const val MAX_STRUCTURAL_DIAGNOSTICS = 4

fun structuralFallbackDiagnostics(uri: URI, content: String): List<Pair<URI, Diagnostic>> {
    val diagnostics = mutableListOf<Pair<URI, Diagnostic>>()
    val stack = ArrayDeque<StructuralDelimiterState>()
    val openToClose = mapOf('(' to ')', '[' to ']', '{' to '}')
    var index = 0
    var line = 0
    var column = 0
    var inLineComment = false
    var inBlockComment = false
    var inString = false
    var inChar = false
    var escaped = false

    fun addStructuralDiagnostic(line: Int, startCol: Int, endCol: Int, message: String, code: String) {
        if (diagnostics.size >= MAX_STRUCTURAL_DIAGNOSTICS) return
        diagnostics.add(
            Pair(
                uri,
                Diagnostic(
                    Range(Position(line, startCol), Position(line, maxOf(endCol, startCol + 1))),
                    message,
                    DiagnosticSeverity.Error,
                    "kotlin-structural-fallback",
                    code
                )
            )
        )
    }

    while (index < content.length) {
        val ch = content[index]
        val next = if (index + 1 < content.length) content[index + 1] else '\u0000'

        if (inLineComment) {
            if (ch == '\n') {
                inLineComment = false
                line++
                column = 0
            } else {
                column++
            }
            index++
            continue
        }

        if (inBlockComment) {
            if (ch == '*' && next == '/') {
                inBlockComment = false
                column += 2
                index += 2
                continue
            }
            if (ch == '\n') {
                line++
                column = 0
            } else {
                column++
            }
            index++
            continue
        }

        if (inString) {
            if (escaped) {
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                inString = false
            }
            if (ch == '\n') {
                line++
                column = 0
            } else {
                column++
            }
            index++
            continue
        }

        if (inChar) {
            if (escaped) {
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '\'') {
                inChar = false
            }
            if (ch == '\n') {
                line++
                column = 0
            } else {
                column++
            }
            index++
            continue
        }

        if (ch == '/' && next == '/') {
            inLineComment = true
            column += 2
            index += 2
            continue
        }

        if (ch == '/' && next == '*') {
            inBlockComment = true
            column += 2
            index += 2
            continue
        }

        if (ch == '"') {
            inString = true
            column++
            index++
            continue
        }

        if (ch == '\'') {
            inChar = true
            column++
            index++
            continue
        }

        when (ch) {
            '(', '[', '{' -> stack.addLast(StructuralDelimiterState(ch, line, column))
            ')', ']', '}' -> {
                val expectedOpen = when (ch) {
                    ')' -> '('
                    ']' -> '['
                    '}' -> '{'
                    else -> null
                }
                val open = if (stack.isEmpty()) null else stack.removeLast()
                if (expectedOpen == null) {
                    // no-op
                } else if (open == null) {
                    addStructuralDiagnostic(
                        line = line,
                        startCol = column,
                        endCol = column + 1,
                        message = "Unmatched closing '$ch'",
                        code = "STRUCTURAL_UNMATCHED_CLOSING"
                    )
                } else if (open.char != expectedOpen) {
                    val expectedClose = openToClose[open.char] ?: ch
                    addStructuralDiagnostic(
                        line = line,
                        startCol = column,
                        endCol = column + 1,
                        message = "Mismatched closing '$ch'; expected '$expectedClose' for '${open.char}'",
                        code = "STRUCTURAL_MISMATCHED_DELIMITER"
                    )
                }
            }
        }

        if (ch == '\n') {
            line++
            column = 0
        } else {
            column++
        }
        index++
    }

    while (stack.isNotEmpty() && diagnostics.size < MAX_STRUCTURAL_DIAGNOSTICS) {
        val openState = stack.removeLast()
        val expectedClose = openToClose[openState.char] ?: openState.char
        addStructuralDiagnostic(
            line = openState.line,
            startCol = openState.column,
            endCol = openState.column + 1,
            message = "Unclosed '${openState.char}'; expected '$expectedClose'",
            code = "STRUCTURAL_UNCLOSED_DELIMITER"
        )
    }

    return diagnostics
}