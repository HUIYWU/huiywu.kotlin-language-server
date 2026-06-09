package org.javacs.kt.diagnostic

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.net.URI
import java.util.ArrayDeque

private fun Char.isLineTerminator(): Boolean = this == '\n' || this == '\r'

private data class StructuralDelimiterState(
    val char: Char,
    val line: Int,
    val column: Int,
)

private data class StructuralTokenStart(
    val line: Int,
    val column: Int,
)

private data class PendingIncompleteExpression(
    val start: StructuralTokenStart,
    val trigger: String,
    var seenExpressionToken: Boolean = false,
)

private const val MAX_STRUCTURAL_DIAGNOSTICS = 4
private const val TOKEN_MARKER_LENGTH = 1
private const val COMMENT_DELIMITER_LENGTH = 2
private const val RAW_STRING_DELIMITER_LENGTH = 3

fun structuralDiagnostics(uri: URI, content: String): List<Pair<URI, Diagnostic>> =
    StructuralDiagnosticScanner(uri, content).scan()

private class StructuralScannerCursor(
    private val content: String,
) {
    var index: Int = 0
        private set
    var line: Int = 0
        private set
    var column: Int = 0
        private set

    fun hasMore(): Boolean = index < content.length

    fun current(): Char = content[index]

    fun next(): Char = charAtOffset(1)

    fun previous(): Char = if (index > 0) content[index - 1] else '\u0000'

    fun charAtOffset(offset: Int): Char =
        if (index + offset < content.length) content[index + offset] else '\u0000'

    fun start(): StructuralTokenStart = StructuralTokenStart(line, column)

    fun advanceOne() {
        column++
        index++
    }

    fun advanceColumns(count: Int) {
        column += count
        index += count
    }

    fun advanceLineWithIndex() {
        line++
        column = 0
        index++
    }

    fun advanceWithinMultilineToken(ch: Char) {
        if (ch == '\n') {
            advanceLineWithIndex()
        } else {
            advanceOne()
        }
    }
}

private class StructuralDiagnosticReporter(
    private val uri: URI,
) {
    private val diagnostics = mutableListOf<Pair<URI, Diagnostic>>()

    val size: Int
        get() = diagnostics.size

    fun result(): List<Pair<URI, Diagnostic>> = diagnostics

    fun add(line: Int, startCol: Int, endCol: Int, message: String, code: String) {
        if (diagnostics.size >= MAX_STRUCTURAL_DIAGNOSTICS) return
        diagnostics.add(
            Pair(
                uri,
                Diagnostic(
                    Range(Position(line, startCol), Position(line, maxOf(endCol, startCol + 1))),
                    message,
                    DiagnosticSeverity.Error,
                    STRUCTURAL_DIAGNOSTIC_SOURCE,
                    code
                )
            )
        )
    }
}

private class StructuralIncompleteExpressionState(
    private val reporter: StructuralDiagnosticReporter,
) {
    private var pendingAssignment: PendingIncompleteExpression? = null
    private var pendingOperator: PendingIncompleteExpression? = null

    fun startAssignment(start: StructuralTokenStart) {
        pendingAssignment = PendingIncompleteExpression(
            start = start,
            trigger = "=",
        )
    }

    fun startOperator(start: StructuralTokenStart, trigger: String) {
        pendingOperator = PendingIncompleteExpression(
            start = start,
            trigger = trigger,
        )
    }

    fun markExpressionTokenIfNeeded(ch: Char) {
        if (isExpressionStartToken(ch)) {
            pendingAssignment?.seenExpressionToken = true
            pendingOperator?.seenExpressionToken = true
        }
    }

    fun markNonWhitespaceAfterAssignment(ch: Char) {
        if (!ch.isWhitespace() && pendingAssignment != null) {
            pendingAssignment?.seenExpressionToken = true
        }
    }

    fun finishAtBoundary() {
        pendingOperator = finishIfNeeded(pendingOperator)
        pendingAssignment = finishIfNeeded(pendingAssignment)
    }

    private fun finishIfNeeded(pending: PendingIncompleteExpression?): PendingIncompleteExpression? {
        if (pending != null && !pending.seenExpressionToken) {
            reporter.add(
                line = pending.start.line,
                startCol = pending.start.column,
                endCol = pending.start.column + pending.trigger.length,
                message = "Incomplete expression after '${pending.trigger}'",
                code = STRUCTURAL_INCOMPLETE_EXPRESSION
            )
        }
        return null
    }

    private fun isExpressionStartToken(ch: Char): Boolean =
        ch.isLetterOrDigit() || ch == '_' || ch == '"' || ch == '\'' || ch == '`' || ch == '(' || ch == '[' || ch == '{'
}

private class StructuralDelimiterStateMachine(
    private val reporter: StructuralDiagnosticReporter,
    private val lines: List<String>,
) {
    private val stack = ArrayDeque<StructuralDelimiterState>()
    private val openToClose = mapOf('(' to ')', '[' to ']', '{' to '}')

    fun process(ch: Char, line: Int, column: Int) {
        when (ch) {
            '(', '[', '{' -> stack.addLast(StructuralDelimiterState(ch, line, column))
            ')', ']', '}' -> processClosing(ch, line, column)
        }
    }

    fun finishRemaining() {
        val remainingDelimiters = mutableListOf<StructuralDelimiterState>()
        while (stack.isNotEmpty()) {
            remainingDelimiters.add(stack.removeLast())
        }

        val incompleteFunctionBodies = remainingDelimiters.filter { looksLikeFunctionBodyStart(it) }
        for (openState in incompleteFunctionBodies) {
            if (reporter.size >= MAX_STRUCTURAL_DIAGNOSTICS) break
            reporter.add(
                line = openState.line,
                startCol = openState.column,
                endCol = openState.column + TOKEN_MARKER_LENGTH,
                message = "Incomplete function body",
                code = STRUCTURAL_INCOMPLETE_FUNCTION_BODY
            )
        }

        for (openState in remainingDelimiters.filterNot { incompleteFunctionBodies.contains(it) }) {
            if (reporter.size >= MAX_STRUCTURAL_DIAGNOSTICS) break
            val expectedClose = openToClose[openState.char] ?: openState.char
            addUnclosed(openState, expectedClose)
        }
    }

    private fun processClosing(ch: Char, line: Int, column: Int) {
        val expectedOpen = when (ch) {
            ')' -> '('
            ']' -> '['
            '}' -> '{'
            else -> null
        }
        val open = if (stack.isEmpty()) null else stack.removeLast()
        when {
            expectedOpen == null -> Unit
            open == null -> reporter.add(
                line = line,
                startCol = column,
                endCol = column + TOKEN_MARKER_LENGTH,
                message = "Unexpected closing '$ch'",
                code = STRUCTURAL_UNMATCHED_CLOSING
            )
            open.char != expectedOpen -> handleMismatchedClosing(ch, expectedOpen, open, line, column)
        }
    }

    private fun handleMismatchedClosing(
        ch: Char,
        expectedOpen: Char,
        open: StructuralDelimiterState,
        line: Int,
        column: Int,
    ) {
        val expectedClose = openToClose[open.char] ?: ch
        val nextOpen = if (stack.isEmpty()) null else stack.last()
        if (nextOpen?.char == expectedOpen) {
            addUnclosed(open, expectedClose)
            stack.removeLast()
        } else {
            reporter.add(
                line = line,
                startCol = column,
                endCol = column + TOKEN_MARKER_LENGTH,
                message = "Mismatched closing '$ch'; expected '$expectedClose' for '${open.char}'",
                code = STRUCTURAL_MISMATCHED_DELIMITER
            )
        }
    }

    private fun addUnclosed(openState: StructuralDelimiterState, expectedClose: Char) {
        reporter.add(
            line = openState.line,
            startCol = openState.column,
            endCol = openState.column + TOKEN_MARKER_LENGTH,
            message = "Unclosed '${openState.char}'; expected '$expectedClose'",
            code = STRUCTURAL_UNCLOSED_DELIMITER
        )
    }

    private fun looksLikeFunctionBodyStart(openState: StructuralDelimiterState): Boolean =
        openState.char == '{' &&
            (lines.getOrNull(openState.line)
                ?.take(openState.column)
                ?.let { Regex("\\bfun\\b").containsMatchIn(it) }
                ?: false)
}

private enum class StructuralUnterminatedTokenKind {
    BLOCK_COMMENT,
    STRING,
    RAW_STRING,
    CHAR,
    BACKTICK_IDENTIFIER,
}

private class StructuralUnterminatedTokenState(
    private val reporter: StructuralDiagnosticReporter,
) {
    private var blockCommentStart: StructuralTokenStart? = null
    private var stringStart: StructuralTokenStart? = null
    private var rawStringStart: StructuralTokenStart? = null
    private var charStart: StructuralTokenStart? = null
    private var backtickIdentifierStart: StructuralTokenStart? = null

    fun start(kind: StructuralUnterminatedTokenKind, start: StructuralTokenStart) {
        when (kind) {
            StructuralUnterminatedTokenKind.BLOCK_COMMENT -> blockCommentStart = start
            StructuralUnterminatedTokenKind.STRING -> stringStart = start
            StructuralUnterminatedTokenKind.RAW_STRING -> rawStringStart = start
            StructuralUnterminatedTokenKind.CHAR -> charStart = start
            StructuralUnterminatedTokenKind.BACKTICK_IDENTIFIER -> backtickIdentifierStart = start
        }
    }

    fun finish(kind: StructuralUnterminatedTokenKind) {
        when (kind) {
            StructuralUnterminatedTokenKind.BLOCK_COMMENT -> blockCommentStart = null
            StructuralUnterminatedTokenKind.STRING -> stringStart = null
            StructuralUnterminatedTokenKind.RAW_STRING -> rawStringStart = null
            StructuralUnterminatedTokenKind.CHAR -> charStart = null
            StructuralUnterminatedTokenKind.BACKTICK_IDENTIFIER -> backtickIdentifierStart = null
        }
    }

    fun addDiagnostics(): Boolean {
        val hasUnterminatedToken = blockCommentStart != null ||
            stringStart != null ||
            rawStringStart != null ||
            charStart != null ||
            backtickIdentifierStart != null
        blockCommentStart?.let {
            reporter.add(
                line = it.line,
                startCol = it.column,
                endCol = it.column + COMMENT_DELIMITER_LENGTH,
                message = "Unterminated block comment",
                code = STRUCTURAL_UNTERMINATED_BLOCK_COMMENT
            )
        }
        stringStart?.let {
            reporter.add(
                line = it.line,
                startCol = it.column,
                endCol = it.column + TOKEN_MARKER_LENGTH,
                message = "Unterminated string literal",
                code = STRUCTURAL_UNTERMINATED_STRING_LITERAL
            )
        }
        rawStringStart?.let {
            reporter.add(
                line = it.line,
                startCol = it.column,
                endCol = it.column + RAW_STRING_DELIMITER_LENGTH,
                message = "Unterminated raw string literal",
                code = STRUCTURAL_UNTERMINATED_RAW_STRING_LITERAL
            )
        }
        charStart?.let {
            reporter.add(
                line = it.line,
                startCol = it.column,
                endCol = it.column + TOKEN_MARKER_LENGTH,
                message = "Unterminated char literal",
                code = STRUCTURAL_UNTERMINATED_CHAR_LITERAL
            )
        }
        backtickIdentifierStart?.let {
            reporter.add(
                line = it.line,
                startCol = it.column,
                endCol = it.column + TOKEN_MARKER_LENGTH,
                message = "Unterminated backtick identifier",
                code = STRUCTURAL_UNTERMINATED_BACKTICK_IDENTIFIER
            )
        }
        return hasUnterminatedToken
    }
}

private enum class StructuralLexicalMode {
    DEFAULT,
    LINE_COMMENT,
    BLOCK_COMMENT,
    STRING,
    RAW_STRING,
    CHAR,
    BACKTICK_IDENTIFIER,
}

private class StructuralLexicalModeState {
    private var mode: StructuralLexicalMode = StructuralLexicalMode.DEFAULT
    var escaped: Boolean = false

    val inLineComment: Boolean
        get() = mode == StructuralLexicalMode.LINE_COMMENT
    val inBlockComment: Boolean
        get() = mode == StructuralLexicalMode.BLOCK_COMMENT
    val inString: Boolean
        get() = mode == StructuralLexicalMode.STRING
    val inRawString: Boolean
        get() = mode == StructuralLexicalMode.RAW_STRING
    val inChar: Boolean
        get() = mode == StructuralLexicalMode.CHAR
    val inBacktickIdentifier: Boolean
        get() = mode == StructuralLexicalMode.BACKTICK_IDENTIFIER

    fun enterLineComment() {
        mode = StructuralLexicalMode.LINE_COMMENT
    }

    fun enterBlockComment() {
        mode = StructuralLexicalMode.BLOCK_COMMENT
    }

    fun enterString() {
        mode = StructuralLexicalMode.STRING
        escaped = false
    }

    fun enterRawString() {
        mode = StructuralLexicalMode.RAW_STRING
        escaped = false
    }

    fun enterChar() {
        mode = StructuralLexicalMode.CHAR
        escaped = false
    }

    fun enterBacktickIdentifier() {
        mode = StructuralLexicalMode.BACKTICK_IDENTIFIER
    }

    fun leaveMode() {
        mode = StructuralLexicalMode.DEFAULT
        escaped = false
    }
}

private data class StructuralActiveModeResult(
    val handled: Boolean,
    val shouldStopScanning: Boolean = false,
)

private class StructuralLexicalActiveModeHandler(
    private val cursor: StructuralScannerCursor,
    private val lexicalMode: StructuralLexicalModeState,
    private val unterminatedTokens: StructuralUnterminatedTokenState,
    private val incompleteExpressions: StructuralIncompleteExpressionState,
) {
    fun handle(): StructuralActiveModeResult = when {
        lexicalMode.inLineComment -> handleLineComment()
        lexicalMode.inBlockComment -> handleBlockComment()
        lexicalMode.inString -> handleStringLiteral()
        lexicalMode.inRawString -> handleRawStringLiteral()
        lexicalMode.inChar -> handleCharLiteral()
        lexicalMode.inBacktickIdentifier -> handleBacktickIdentifier()
        else -> StructuralActiveModeResult(handled = false)
    }

    private fun handleLineComment(): StructuralActiveModeResult {
        if (cursor.current() == '\n') {
            lexicalMode.leaveMode()
            incompleteExpressions.finishAtBoundary()
            cursor.advanceLineWithIndex()
        } else {
            cursor.advanceOne()
        }
        return StructuralActiveModeResult(handled = true)
    }

    private fun handleBlockComment(): StructuralActiveModeResult {
        val ch = cursor.current()
        if (ch == '*' && cursor.next() == '/') {
            lexicalMode.leaveMode()
            unterminatedTokens.finish(StructuralUnterminatedTokenKind.BLOCK_COMMENT)
            cursor.advanceColumns(COMMENT_DELIMITER_LENGTH)
            return StructuralActiveModeResult(handled = true)
        }
        cursor.advanceWithinMultilineToken(ch)
        return StructuralActiveModeResult(handled = true)
    }

    private fun handleStringLiteral(): StructuralActiveModeResult {
        when {
            lexicalMode.escaped -> lexicalMode.escaped = false
            cursor.current() == '\\' -> lexicalMode.escaped = true
            cursor.current() == '"' -> {
                lexicalMode.leaveMode()
                unterminatedTokens.finish(StructuralUnterminatedTokenKind.STRING)
            }
            cursor.current().isLineTerminator() -> return StructuralActiveModeResult(handled = true, shouldStopScanning = true)
        }
        cursor.advanceOne()
        return StructuralActiveModeResult(handled = true)
    }

    private fun handleRawStringLiteral(): StructuralActiveModeResult {
        val ch = cursor.current()
        if (ch == '"' && cursor.next() == '"' && cursor.charAtOffset(COMMENT_DELIMITER_LENGTH) == '"') {
            lexicalMode.leaveMode()
            unterminatedTokens.finish(StructuralUnterminatedTokenKind.RAW_STRING)
            cursor.advanceColumns(RAW_STRING_DELIMITER_LENGTH)
            return StructuralActiveModeResult(handled = true)
        }
        cursor.advanceWithinMultilineToken(ch)
        return StructuralActiveModeResult(handled = true)
    }

    private fun handleCharLiteral(): StructuralActiveModeResult {
        when {
            lexicalMode.escaped -> lexicalMode.escaped = false
            cursor.current() == '\\' -> lexicalMode.escaped = true
            cursor.current() == '\'' -> {
                lexicalMode.leaveMode()
                unterminatedTokens.finish(StructuralUnterminatedTokenKind.CHAR)
            }
            cursor.current().isLineTerminator() -> return StructuralActiveModeResult(handled = true, shouldStopScanning = true)
        }
        cursor.advanceOne()
        return StructuralActiveModeResult(handled = true)
    }

    private fun handleBacktickIdentifier(): StructuralActiveModeResult {
        when (cursor.current()) {
            '`' -> {
                lexicalMode.leaveMode()
                unterminatedTokens.finish(StructuralUnterminatedTokenKind.BACKTICK_IDENTIFIER)
            }
            '\n' -> return StructuralActiveModeResult(handled = true, shouldStopScanning = true)
        }
        cursor.advanceOne()
        return StructuralActiveModeResult(handled = true)
    }
}

private class StructuralLexicalStartModeHandler(
    private val cursor: StructuralScannerCursor,
    private val lexicalMode: StructuralLexicalModeState,
    private val unterminatedTokens: StructuralUnterminatedTokenState,
    private val incompleteExpressions: StructuralIncompleteExpressionState,
) {
    fun tryStart(ch: Char, next: Char): Boolean =
        tryStartComment(ch, next) || tryStartLiteral(ch, next)

    private fun tryStartComment(ch: Char, next: Char): Boolean {
        val modeStarted = when {
            ch == '/' && next == '/' -> {
                lexicalMode.enterLineComment()
                true
            }
            ch == '/' && next == '*' -> {
                lexicalMode.enterBlockComment()
                unterminatedTokens.start(StructuralUnterminatedTokenKind.BLOCK_COMMENT, cursor.start())
                true
            }
            else -> false
        }
        if (modeStarted) cursor.advanceColumns(COMMENT_DELIMITER_LENGTH)
        return modeStarted
    }

    private fun tryStartLiteral(ch: Char, next: Char): Boolean {
        val literalLength = when {
            ch == '"' && next == '"' && cursor.charAtOffset(COMMENT_DELIMITER_LENGTH) == '"' -> {
                incompleteExpressions.markExpressionTokenIfNeeded(ch)
                lexicalMode.enterRawString()
                unterminatedTokens.start(StructuralUnterminatedTokenKind.RAW_STRING, cursor.start())
                RAW_STRING_DELIMITER_LENGTH
            }
            ch == '"' -> {
                incompleteExpressions.markExpressionTokenIfNeeded(ch)
                lexicalMode.enterString()
                unterminatedTokens.start(StructuralUnterminatedTokenKind.STRING, cursor.start())
                TOKEN_MARKER_LENGTH
            }
            ch == '\'' -> {
                incompleteExpressions.markExpressionTokenIfNeeded(ch)
                lexicalMode.enterChar()
                unterminatedTokens.start(StructuralUnterminatedTokenKind.CHAR, cursor.start())
                TOKEN_MARKER_LENGTH
            }
            ch == '`' -> {
                incompleteExpressions.markExpressionTokenIfNeeded(ch)
                lexicalMode.enterBacktickIdentifier()
                unterminatedTokens.start(StructuralUnterminatedTokenKind.BACKTICK_IDENTIFIER, cursor.start())
                TOKEN_MARKER_LENGTH
            }
            else -> 0
        }
        if (literalLength > 0) cursor.advanceColumns(literalLength)
        return literalLength > 0
    }
}

private class StructuralIncompleteExpressionTriggerScanner(
    private val content: String,
    private val cursor: StructuralScannerCursor,
    private val incompleteExpressions: StructuralIncompleteExpressionState,
) {
    fun tryStartOperator(ch: Char, next: Char): Boolean =
        tryStartKeywordOperator() || tryStartSymbolOperator(ch, next)

    fun processAssignment(ch: Char, next: Char) {
        if (ch != '=') return
        val previous = cursor.previous()
        val nextIsEquals = next == '='
        val previousIsOperatorPrefix = previous == '=' || previous == '!' || previous == '<' || previous == '>'
        if (!nextIsEquals && !previousIsOperatorPrefix) {
            incompleteExpressions.startAssignment(cursor.start())
        }
    }

    private fun tryStartKeywordOperator(): Boolean {
        val keywordOperatorText = keywordOperatorAt(cursor.index) ?: return false
        incompleteExpressions.startOperator(
            start = cursor.start(),
            trigger = keywordOperatorText,
        )
        cursor.advanceColumns(keywordOperatorText.length)
        return true
    }

    private fun tryStartSymbolOperator(ch: Char, next: Char): Boolean {
        val operatorText = incompleteExpressionOperatorAt(ch, cursor.previous(), next) ?: return false
        incompleteExpressions.startOperator(
            start = cursor.start(),
            trigger = operatorText,
        )
        incompleteExpressions.markNonWhitespaceAfterAssignment(ch)
        cursor.advanceColumns(operatorText.length)
        return true
    }

    private fun incompleteExpressionOperatorAt(ch: Char, previous: Char, next: Char): String? =
        when {
            ch.isSingleCharIncompleteOperator(previous, next) -> ch.toString()
            ch == '&' && next == '&' -> "&&"
            ch == '|' && next == '|' -> "||"
            ch == '?' && (next == ':' || next == '.') -> "?$next"
            ch == ':' && next == ':' -> "::"
            else -> null
        }

    private fun Char.isSingleCharIncompleteOperator(previous: Char, next: Char): Boolean =
        when (this) {
            '+' -> previous != '+' && next != '+'
            '-' -> previous != '-' && next != '-' && next != '>'
            '*', '/', '%' -> true
            else -> false
        }

    private fun keywordOperatorAt(index: Int): String? {
        fun hasKeywordAt(keyword: String): Boolean {
            val end = index + keyword.length
            val previous = if (index > 0) content[index - 1] else '\u0000'
            val next = if (end < content.length) content[end] else '\u0000'
            return content.startsWith(keyword, index) && !isIdentifierPart(previous) && !isIdentifierPart(next)
        }

        return when {
            content.startsWith("as?", index) &&
                !isIdentifierPart(if (index > 0) content[index - 1] else '\u0000') &&
                !isIdentifierPart(if (index + RAW_STRING_DELIMITER_LENGTH < content.length) content[index + RAW_STRING_DELIMITER_LENGTH] else '\u0000') -> "as?"
            hasKeywordAt("as") -> "as"
            hasKeywordAt("is") -> "is"
            else -> null
        }
    }

    private fun isIdentifierPart(ch: Char): Boolean =
        ch.isLetterOrDigit() || ch == '_'
}

private class StructuralDiagnosticScanner(
    uri: URI,
    private val content: String,
) {
    private val cursor = StructuralScannerCursor(content)
    private val reporter = StructuralDiagnosticReporter(uri)
    private val incompleteExpressions = StructuralIncompleteExpressionState(reporter)
    private val delimiters = StructuralDelimiterStateMachine(reporter, content.split('\n'))
    private val unterminatedTokens = StructuralUnterminatedTokenState(reporter)
    private val lexicalMode = StructuralLexicalModeState()
    private val activeModeHandler = StructuralLexicalActiveModeHandler(
        cursor = cursor,
        lexicalMode = lexicalMode,
        unterminatedTokens = unterminatedTokens,
        incompleteExpressions = incompleteExpressions,
    )
    private val startModeHandler = StructuralLexicalStartModeHandler(
        cursor = cursor,
        lexicalMode = lexicalMode,
        unterminatedTokens = unterminatedTokens,
        incompleteExpressions = incompleteExpressions,
    )
    private val incompleteExpressionTriggers = StructuralIncompleteExpressionTriggerScanner(
        content = content,
        cursor = cursor,
        incompleteExpressions = incompleteExpressions,
    )

    private var stopScanning = false

    fun scan(): List<Pair<URI, Diagnostic>> {
        while (cursor.hasMore() && !stopScanning) {
            processCurrentToken()
        }

        incompleteExpressions.finishAtBoundary()
        val hasUnterminatedToken = unterminatedTokens.addDiagnostics()
        if (hasUnterminatedToken) return reporter.result()

        delimiters.finishRemaining()
        return reporter.result()
    }

    private fun processCurrentToken() {
        when {
            handleActiveMode() -> Unit
            handleDefaultToken() -> Unit
            else -> {
                delimiters.process(cursor.current(), cursor.line, cursor.column)
                cursor.advanceOne()
            }
        }
    }

    private fun handleActiveMode(): Boolean {
        val result = activeModeHandler.handle()
        if (result.shouldStopScanning) stopScanning = true
        return result.handled
    }

    private fun handleDefaultToken(): Boolean {
        val ch = cursor.current()
        val next = cursor.next()

        val handled = when {
            ch == '\n' -> {
                incompleteExpressions.finishAtBoundary()
                cursor.advanceLineWithIndex()
                true
            }
            startModeHandler.tryStart(ch, next) -> true
            incompleteExpressionTriggers.tryStartOperator(ch, next) -> true
            else -> {
                incompleteExpressions.markExpressionTokenIfNeeded(ch)
                incompleteExpressionTriggers.processAssignment(ch, next)
                false
            }
        }
        return handled
    }
}
