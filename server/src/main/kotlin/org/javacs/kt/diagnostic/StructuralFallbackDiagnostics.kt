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

private data class StructuralTokenStart(
    val line: Int,
    val column: Int,
)

private const val MAX_STRUCTURAL_DIAGNOSTICS = 4

@Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
fun structuralFallbackDiagnostics(uri: URI, content: String): List<Pair<URI, Diagnostic>> {
    val diagnostics = mutableListOf<Pair<URI, Diagnostic>>()
    val stack = ArrayDeque<StructuralDelimiterState>()
    val openToClose = mapOf('(' to ')', '[' to ']', '{' to '}')
    val lines = content.split('\n')
    var index = 0
    var line = 0
    var column = 0
    var inLineComment = false
    var inBlockComment = false
    var inString = false
    var inChar = false
    var inBacktickIdentifier = false
    var escaped = false
    var blockCommentStart: StructuralTokenStart? = null
    var stringStart: StructuralTokenStart? = null
    var charStart: StructuralTokenStart? = null
    var backtickIdentifierStart: StructuralTokenStart? = null
    var pendingAssignmentStart: StructuralTokenStart? = null
    var seenExpressionTokenAfterAssignment = false

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

    fun finishPendingAssignmentIfNeeded() {
        val assignment = pendingAssignmentStart ?: return
        if (!seenExpressionTokenAfterAssignment) {
            addStructuralDiagnostic(
                line = assignment.line,
                startCol = assignment.column,
                endCol = assignment.column + 1,
                message = "Incomplete expression after '='",
                code = "STRUCTURAL_INCOMPLETE_EXPRESSION"
            )
        }
        pendingAssignmentStart = null
        seenExpressionTokenAfterAssignment = false
    }

    fun looksLikeFunctionBodyStart(openState: StructuralDelimiterState): Boolean =
        openState.char == '{' &&
            (lines.getOrNull(openState.line)
                ?.take(openState.column)
                ?.let { Regex("\\bfun\\b").containsMatchIn(it) }
                ?: false)

    while (index < content.length) {
        val ch = content[index]
        val next = if (index + 1 < content.length) content[index + 1] else '\u0000'

        if (inLineComment) {
            if (ch == '\n') {
                inLineComment = false
                finishPendingAssignmentIfNeeded()
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
                blockCommentStart = null
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
                stringStart = null
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
                charStart = null
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

        if (inBacktickIdentifier) {
            if (ch == '`') {
                inBacktickIdentifier = false
                backtickIdentifierStart = null
                if (pendingAssignmentStart != null) {
                    seenExpressionTokenAfterAssignment = true
                }
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

        if (ch == '\n') {
            finishPendingAssignmentIfNeeded()
            line++
            column = 0
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
            blockCommentStart = StructuralTokenStart(line, column)
            column += 2
            index += 2
            continue
        }

        if (ch == '"') {
            inString = true
            stringStart = StructuralTokenStart(line, column)
            if (pendingAssignmentStart != null) {
                seenExpressionTokenAfterAssignment = true
            }
            column++
            index++
            continue
        }

        if (ch == '\'') {
            inChar = true
            charStart = StructuralTokenStart(line, column)
            if (pendingAssignmentStart != null) {
                seenExpressionTokenAfterAssignment = true
            }
            column++
            index++
            continue
        }

        if (ch == '`') {
            inBacktickIdentifier = true
            backtickIdentifierStart = StructuralTokenStart(line, column)
            column++
            index++
            continue
        }

        if (!ch.isWhitespace() && pendingAssignmentStart != null) {
            seenExpressionTokenAfterAssignment = true
        }

        if (ch == '=') {
            val previous = if (index > 0) content[index - 1] else '\u0000'
            val nextIsEquals = next == '='
            val previousIsOperatorPrefix = previous == '=' || previous == '!' || previous == '<' || previous == '>'
            if (!nextIsEquals && !previousIsOperatorPrefix) {
                pendingAssignmentStart = StructuralTokenStart(line, column)
                seenExpressionTokenAfterAssignment = false
            }
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

        column++
        index++
    }

    finishPendingAssignmentIfNeeded()

    blockCommentStart?.let {
        addStructuralDiagnostic(
            line = it.line,
            startCol = it.column,
            endCol = it.column + 2,
            message = "Unterminated block comment",
            code = "STRUCTURAL_UNTERMINATED_BLOCK_COMMENT"
        )
    }

    stringStart?.let {
        addStructuralDiagnostic(
            line = it.line,
            startCol = it.column,
            endCol = it.column + 1,
            message = "Unterminated string literal",
            code = "STRUCTURAL_UNTERMINATED_STRING_LITERAL"
        )
    }

    charStart?.let {
        addStructuralDiagnostic(
            line = it.line,
            startCol = it.column,
            endCol = it.column + 1,
            message = "Unterminated char literal",
            code = "STRUCTURAL_UNTERMINATED_CHAR_LITERAL"
        )
    }

    backtickIdentifierStart?.let {
        addStructuralDiagnostic(
            line = it.line,
            startCol = it.column,
            endCol = it.column + 1,
            message = "Unterminated backtick identifier",
            code = "STRUCTURAL_UNTERMINATED_BACKTICK_IDENTIFIER"
        )
    }

    val remainingDelimiters = mutableListOf<StructuralDelimiterState>()
    while (stack.isNotEmpty()) {
        remainingDelimiters.add(stack.removeLast())
    }

    val incompleteFunctionBodies = remainingDelimiters.filter { looksLikeFunctionBodyStart(it) }
    for (openState in incompleteFunctionBodies) {
        if (diagnostics.size >= MAX_STRUCTURAL_DIAGNOSTICS) break
        addStructuralDiagnostic(
            line = openState.line,
            startCol = openState.column,
            endCol = openState.column + 1,
            message = "Incomplete function body",
            code = "STRUCTURAL_INCOMPLETE_FUNCTION_BODY"
        )
    }

    for (openState in remainingDelimiters) {
        if (diagnostics.size >= MAX_STRUCTURAL_DIAGNOSTICS) break
        if (incompleteFunctionBodies.contains(openState)) continue
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
