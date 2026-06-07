package org.javacs.kt.completion

import com.google.common.cache.CacheBuilder
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionItemTag
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Position
import org.javacs.kt.CompiledFile
import org.javacs.kt.LOG
import org.javacs.kt.CompletionConfiguration
import org.javacs.kt.index.Symbol
import org.javacs.kt.index.SymbolIndex
import org.javacs.kt.util.containsCharactersInOrder
import org.javacs.kt.util.findParent
import org.javacs.kt.util.noResult
import org.javacs.kt.util.stringDistance
import org.javacs.kt.util.toPath
import org.javacs.kt.util.onEachIndexed
import org.javacs.kt.util.logPerf
import org.javacs.kt.position.location
import org.javacs.kt.imports.getImportTextEditEntry
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.load.java.descriptors.JavaMethodDescriptor
import org.jetbrains.kotlin.load.java.sources.JavaSourceElement
import org.jetbrains.kotlin.load.java.structure.JavaMethod
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.*
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion
import org.jetbrains.kotlin.resolve.scopes.HierarchicalScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.resolve.scopes.utils.parentsWithSelf
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.utils.addToStdlib.applyIf
import java.util.concurrent.TimeUnit

// The maximum number of completion items
private const val MAX_COMPLETION_ITEMS = 75
private const val ELEMENT_MATERIALIZE_LIMIT = MAX_COMPLETION_ITEMS * 2
private const val SHORT_PREFIX_ELEMENT_MATERIALIZE_LIMIT = 50
private const val MEDIUM_PREFIX_ELEMENT_MATERIALIZE_LIMIT = 100
private const val DETAIL_RENDER_SKIP_PREFIX_LENGTH = 2
private const val RAW_DESCRIPTOR_LIMIT_SHORT_PREFIX = 300
private const val RAW_DESCRIPTOR_LIMIT_MEDIUM_PREFIX = 500
private const val RAW_DESCRIPTOR_LIMIT_LONG_PREFIX = 800
private const val LOG_PARTIAL_ELEMENT_PREVIEW_LENGTH = 40
private const val INFIX_COMPLETION_MIN_PARTIAL_LENGTH = 2

// The minimum length after which completion lists are sorted
private const val MIN_SORT_LENGTH = 3

/** Finds completions at the specified position. */
fun completions(file: CompiledFile, cursor: Int, index: SymbolIndex, config: CompletionConfiguration): CompletionList {
    val partial = logPerf("completion.partial", "cursor=$cursor") {
        findPartialIdentifier(file, cursor)
    }
    LOG.debug("Looking for completions that match '{}'", partial)

    val elementCompletion = logPerf("completion.elementItems", "partial=$partial") {
        elementCompletionItems(file, cursor, config, partial)
    }
    val elementItems = elementCompletion.items
    val element = elementCompletion.element
    val elementMaterializeLimit = when {
        partial.length <= 1 -> SHORT_PREFIX_ELEMENT_MATERIALIZE_LIMIT
        partial.length == 2 -> MEDIUM_PREFIX_ELEMENT_MATERIALIZE_LIMIT
        else -> ELEMENT_MATERIALIZE_LIMIT
    }
    val elementItemList = logPerf("completion.elementItems.materialize", "partial=$partial limit=$elementMaterializeLimit") {
        elementItems.take(elementMaterializeLimit).toList()
    }
    // Index items are materialized before de-duplication so their import metadata can be merged into
    // same-label element completion items when the element route skipped detail rendering.

    val isExhaustive = element !is KtNameReferenceExpression
                    && element !is KtTypeElement
                    && element !is KtQualifiedExpression

    val indexItems = if (!isExhaustive) {
        logPerf("completion.indexItems", "partial=$partial") {
            indexCompletionItems(file, cursor, element, index, partial).toList()
        }
    } else {
        emptyList()
    }
    val importEnhancedElementItems = mergeImportEditsFromIndexItems(elementItemList, indexItems)
    val importEnhancedElementLabels = importEnhancedElementItems.mapNotNull { it.label }.toSet()
    val keywordItems = if (elementItemList.isEmpty()) {
        logPerf("completion.keywordItems", "partial=$partial") {
            keywordCompletionItems(partial).toList()
        }
    } else {
        emptyList()
    }

    val itemList = logPerf("completion.finalize", "partial=$partial") {
        (importEnhancedElementItems.asSequence() +
            indexItems.asSequence().filter { it.label !in importEnhancedElementLabels } +
            keywordItems.asSequence())
            .take(MAX_COMPLETION_ITEMS)
            .toList()
            .onEachIndexed { i, item -> item.sortText = i.toString().padStart(2, '0') }
    }
    val isIncomplete = itemList.size >= MAX_COMPLETION_ITEMS || elementItemList.isEmpty()

    return CompletionList(isIncomplete, itemList)
}

private fun mergeImportEditsFromIndexItems(
    elementItems: List<CompletionItem>,
    indexItems: List<CompletionItem>
): List<CompletionItem> {
    val importItemsByLabel = indexItems
        .filter { it.hasImportEdit() }
        .groupBy { it.label }

    return elementItems.map { elementItem ->
        val label = elementItem.label
        val importItem = label?.let { importItemsByLabel[it]?.singleOrNull() }

        when {
            label == null -> elementItem
            importItem == null -> elementItem
            !elementItem.canAcceptImportEditFrom(importItem) -> elementItem
            else -> elementItem.apply {
                detail = detail?.takeIf { it.isNotBlank() } ?: importItem.detail
                additionalTextEdits = additionalTextEdits?.takeIf { it.isNotEmpty() } ?: importItem.additionalTextEdits
            }
        }
    }
}

private fun CompletionItem.hasImportEdit(): Boolean =
    !additionalTextEdits.isNullOrEmpty() && !detail.isNullOrBlank()

private fun CompletionItem.canAcceptImportEditFrom(importItem: CompletionItem): Boolean =
    kind == importItem.kind &&
        additionalTextEdits.isNullOrEmpty() &&
        !importItem.additionalTextEdits.isNullOrEmpty()

private fun getQueryNameFromExpression(receiver: KtExpression?, cursor: Int, file: CompiledFile): FqName? {
    val receiverType = receiver?.let { expr -> file.scopeAtPoint(cursor)?.let { file.typeOfExpression(expr, it) } }
    return receiverType?.constructor?.declarationDescriptor?.fqNameSafe
}

private fun indexCompletionItems(file: CompiledFile, cursor: Int, element: KtElement?, index: SymbolIndex, partial: String): Sequence<CompletionItem> {
    val parsedFile = file.parse
    val imports = parsedFile.importDirectives
    // TODO: Deal with alias imports
    val wildcardPackages = imports
        .mapNotNull { it.importPath }
        .filter { it.isAllUnder }
        .map { it.fqName }
        .toSet()
    val importedNames = imports
        .mapNotNull { it.importedFqName?.shortName() }
        .toSet()

    val queryName = when (element) {
        is KtQualifiedExpression -> getQueryNameFromExpression(element.receiverExpression, element.receiverExpression.startOffset, file)
        is KtSimpleNameExpression -> {
            val receiver = element.getReceiverExpression()
            when {
                receiver != null -> getQueryNameFromExpression(receiver, receiver.startOffset, file)
                else -> null
            }
        }
        is KtUserType -> file.referenceAtPoint(element.qualifier?.startOffset ?: cursor)?.second?.fqNameSafe
        is KtTypeElement -> file.referenceAtPoint(element.startOffsetInParent)?.second?.fqNameOrNull()
        else -> null
    }

    val queriedSymbols = index.query(partial, queryName, limit = MAX_COMPLETION_ITEMS)

    return queriedSymbols
        .asSequence()
        .filter { it.kind != Symbol.Kind.MODULE } // Ignore global module/package name completions for now, since they cannot be 'imported'
        .filter { it.fqName.shortName() !in importedNames && it.fqName.parent() !in wildcardPackages }
        .filter {
            // TODO: Visibility checker should be less liberal
               it.visibility == Symbol.Visibility.PUBLIC
            || it.visibility == Symbol.Visibility.PROTECTED
            || it.visibility == Symbol.Visibility.INTERNAL
        }
        .map { CompletionItem().apply {
            label = it.fqName.shortName().toString()
            kind = when (it.kind) {
                Symbol.Kind.CLASS -> CompletionItemKind.Class
                Symbol.Kind.INTERFACE -> CompletionItemKind.Interface
                Symbol.Kind.FUNCTION -> CompletionItemKind.Function
                Symbol.Kind.VARIABLE -> CompletionItemKind.Variable
                Symbol.Kind.MODULE -> CompletionItemKind.Module
                Symbol.Kind.ENUM -> CompletionItemKind.Enum
                Symbol.Kind.ENUM_MEMBER -> CompletionItemKind.EnumMember
                Symbol.Kind.CONSTRUCTOR -> CompletionItemKind.Constructor
                Symbol.Kind.FIELD -> CompletionItemKind.Field
                Symbol.Kind.UNKNOWN -> CompletionItemKind.Text
            }
            detail = "(import from ${it.fqName.parent()})"
            additionalTextEdits = listOf(getImportTextEditEntry(parsedFile, it.fqName)) // TODO: CRLF?
        } }
}

/** Finds keyword completions starting with the given partial identifier. */
private fun keywordCompletionItems(partial: String): Sequence<CompletionItem> =
    (KtTokens.SOFT_KEYWORDS.getTypes() + KtTokens.KEYWORDS.getTypes()).asSequence()
        .mapNotNull { (it as? KtKeywordToken)?.value }
        .filter { it.startsWith(partial) }
        .map { CompletionItem().apply {
            label = it
            kind = CompletionItemKind.Keyword
        } }

data class ElementCompletionItems(val items: Sequence<CompletionItem>, val element: KtElement? = null)

data class PreparedElementCompletions(
    val descriptors: List<DeclarationDescriptor>,
    val surroundingElement: KtElement,
    val renderDetails: Boolean
)

/** Finds completions based on the element around the user's cursor. */
private fun elementCompletionItems(file: CompiledFile, cursor: Int, config: CompletionConfiguration, partial: String): ElementCompletionItems {
    val prepared = prepareElementCompletions(file, cursor, partial) ?: return ElementCompletionItems(emptySequence())

    return ElementCompletionItems(
        prepared.descriptors.asSequence().map {
            logPerf("completion.elementItems.render", "partial=$partial renderDetails=${prepared.renderDetails}") {
                completionItem(it, prepared.surroundingElement, file, config, prepared.renderDetails)
            }
        },
        prepared.surroundingElement
    )
}
private fun prepareElementCompletions(file: CompiledFile, cursor: Int, partial: String): PreparedElementCompletions? {
    val (surroundingElement, isGlobal) = completableElement(file, cursor) ?: return null
    val rawDescriptorLimit = when {
        partial.length <= 1 -> RAW_DESCRIPTOR_LIMIT_SHORT_PREFIX
        partial.length == 2 -> RAW_DESCRIPTOR_LIMIT_MEDIUM_PREFIX
        else -> RAW_DESCRIPTOR_LIMIT_LONG_PREFIX
    }

    val rawSequence = logPerf("completion.elementItems.raw", "partial=$partial") {
        elementCompletions(file, cursor, surroundingElement, isGlobal, partial)
    }
    val filteredDescriptors = logPerf("completion.elementItems.filter.materialize", "partial=$partial rawLimit=$rawDescriptorLimit") {
        rawSequence
            .applyIf(isGlobal) { filter { declarationIsInfix(it) } }
            .applyIf(surroundingElement.endOffset == cursor) {
                filter {
                    val candidateName = name(it)
                    candidateName.startsWith(partial, ignoreCase = true)
                        || containsCharactersInOrder(candidateName, partial, caseSensitive = false)
                }
            }
            .take(rawDescriptorLimit)
            .toList()
    }

    val sortedDescriptors = logPerf("completion.elementItems.sort.materialize", "partial=$partial") {
        if (partial.length >= MIN_SORT_LENGTH) {
            filteredDescriptors.sortedBy { stringDistance(name(it), partial) }
        } else {
            filteredDescriptors.sortedBy { if (name(it).startsWith(partial)) 0 else 1 }
        }
    }

    val visibleDescriptors = logPerf("completion.elementItems.visible.materialize", "partial=$partial") {
        val visibilityCheck = isVisible(file, cursor)
        sortedDescriptors.filter(visibilityCheck)
    }

    val renderDetails = partial.length > DETAIL_RENDER_SKIP_PREFIX_LENGTH
    val materializeLimit = when {
        partial.length <= 1 -> SHORT_PREFIX_ELEMENT_MATERIALIZE_LIMIT
        partial.length == 2 -> MEDIUM_PREFIX_ELEMENT_MATERIALIZE_LIMIT
        else -> ELEMENT_MATERIALIZE_LIMIT
    }
    val limitedDescriptors = logPerf("completion.elementItems.limit", "partial=$partial limit=$materializeLimit") {
        visibleDescriptors.take(materializeLimit)
    }

    return PreparedElementCompletions(
        descriptors = limitedDescriptors,
        surroundingElement = surroundingElement,
        renderDetails = renderDetails
    )
}

private val callPattern = Regex("(.*)\\((?:\\$\\d+)?\\)(?:\\$0)?")
private val methodSignature = Regex("""(?:fun|constructor) (?:<(?:[a-zA-Z\?\!\: ]+)(?:, [A-Z])*> )?([a-zA-Z]+\(.*\))""")

private fun completionItem(
    d: DeclarationDescriptor,
    surroundingElement: KtElement,
    file: CompiledFile,
    config: CompletionConfiguration,
    renderDetails: Boolean
): CompletionItem {
    val renderWithSnippets = config.snippets.enabled
        && surroundingElement !is KtCallableReferenceExpression
        && surroundingElement !is KtImportDirective
    val result = logPerf("completion.elementItems.render.base", "descriptor=${d::class.simpleName} renderDetails=$renderDetails") {
        d.accept(RenderCompletionItem(renderWithSnippets, renderDetails), null)
    }

    logPerf("completion.elementItems.render.post", "descriptor=${d::class.simpleName} renderDetails=$renderDetails") {
        result.label = methodSignature.find(result.detail ?: "")?.groupValues?.get(1) ?: result.label

        if (isNotStaticJavaMethod(d) && (isGetter(d) || isSetter(d))) {
            val name = extractPropertyName(d)

            result.detail = ((result.detail ?: "") + " (from ${result.label})").trim()
            result.label = name
            result.insertText = name
            result.filterText = name
        }

        if (KotlinBuiltIns.isDeprecated(d)) {
            result.tags = listOf(CompletionItemTag.Deprecated)
        }

        val matchCall = callPattern.matchEntire(result.insertText)
        if (file.lineAfter(surroundingElement.endOffset).startsWith("(") && matchCall != null) {
            result.insertText = matchCall.groups[1]!!.value
        }
    }

    return result
}

private fun isNotStaticJavaMethod(
    descriptor: DeclarationDescriptor
): Boolean {
    val javaMethodDescriptor = descriptor as? JavaMethodDescriptor ?: return true
    val source = javaMethodDescriptor.source as? JavaSourceElement ?: return true
    val javaElement = source.javaElement
    return javaElement is JavaMethod && !javaElement.isStatic
}

private fun extractPropertyName(d: DeclarationDescriptor): String {
    val match = Regex("(get|set)?((?:(?:is)|[A-Z])\\w*)").matchEntire(d.name.identifier)!!
    val upper = match.groups[2]!!.value

    return upper[0].lowercaseChar() + upper.substring(1)
}

private fun isGetter(d: DeclarationDescriptor): Boolean =
        d is CallableDescriptor &&
        !d.name.isSpecial &&
        d.name.identifier.matches(Regex("(get|is)[A-Z]\\w+")) &&
        d.valueParameters.isEmpty()

private fun isSetter(d: DeclarationDescriptor): Boolean =
        d is CallableDescriptor &&
        !d.name.isSpecial &&
        d.name.identifier.matches(Regex("set[A-Z]\\w+")) &&
        d.valueParameters.size == 1

private fun isGlobalCall(el: KtElement) = el is KtBlockExpression || el is KtClassBody || el.parent is KtBinaryExpression

private fun asGlobalCompletable(file: CompiledFile, cursor: Int, el: KtElement): KtElement? {
    val psi =  file.parse.findElementAt(cursor) ?: return null
    val element = when (val e = psi.getPrevSiblingIgnoringWhitespace() ?: psi.parent) {
        is KtProperty -> e.children.lastOrNull()
        is KtBinaryExpression -> el
        else -> e
    }
    return element as? KtReferenceExpression
        ?: element as? KtQualifiedExpression
        ?: element as? KtConstantExpression
}

private fun KtElement.asKtClass(): KtElement? {
    return this.findParent<KtImportDirective>() // import x.y.?
        // package x.y.?
        ?: this.findParent<KtPackageDirective>()
        // :?
        ?: this as? KtUserType
        ?: this.parent as? KtTypeElement
        // .?
        ?: this as? KtQualifiedExpression
        ?: this.parent as? KtQualifiedExpression
        // something::?
        ?: this as? KtCallableReferenceExpression
        ?: this.parent as? KtCallableReferenceExpression
        // something.foo() with cursor in the method
        ?: this.parent?.parent as? KtQualifiedExpression
        // ?
        ?: this as? KtNameReferenceExpression
        // x ? y (infix)
        ?: this.parent as? KtBinaryExpression
        // x()
        ?: this as? KtCallExpression
        // x (constant)
        ?: this as? KtConstantExpression
}

private fun completableElement(file: CompiledFile, cursor: Int): Pair<KtElement, Boolean>? {
    val parsed = file.parseAtPoint(cursor - 1) ?: return null
    val asGlobal = isGlobalCall(parsed)
    val el = (
            if (asGlobal) asGlobalCompletable(file, cursor, parsed) else null
     ) ?: parsed

    return el.asKtClass()?.let {
        Pair(it, asGlobal)
    }
}

@Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod")
private fun elementCompletions(
    file: CompiledFile,
    cursor: Int,
    surroundingElement: KtElement,
    infixCall: Boolean,
    partial: String
): Sequence<DeclarationDescriptor> {
    return when (surroundingElement) {
        // import x.y.?
        is KtImportDirective -> {
            LOG.info("Completing import '{}'", surroundingElement.text)
            val module = file.module
            val match = Regex("import ((\\w+\\.)*)[\\w*]*").matchEntire(surroundingElement.text) ?: return doesntLookLikeImport(surroundingElement)
            val parentDot = if (match.groupValues[1].isNotBlank()) match.groupValues[1] else "."
            val parent = parentDot.substring(0, parentDot.length - 1)
            LOG.debug("Looking for members of package '{}'", parent)
            val parentPackage = module.getPackage(FqName.fromSegments(parent.split('.')))
            parentPackage.memberScope.getContributedDescriptors().asSequence()
        }
        // package x.y.?
        is KtPackageDirective -> {
            LOG.info("Completing package '{}'", surroundingElement.text)
            val module = file.module
            val match = Regex("package ((\\w+\\.)*)[\\w*]*").matchEntire(surroundingElement.text)
                ?: return doesntLookLikePackage(surroundingElement)
            val parentDot = if (match.groupValues[1].isNotBlank()) match.groupValues[1] else "."
            val parent = parentDot.substring(0, parentDot.length - 1)
            LOG.debug("Looking for members of package '{}'", parent)
            val parentPackage = module.getPackage(FqName.fromSegments(parent.split('.')))
            parentPackage.memberScope.getDescriptorsFiltered(DescriptorKindFilter.PACKAGES).asSequence()
        }
        // :?
        is KtTypeElement -> {
            // : Outer.?
            if (surroundingElement is KtUserType && surroundingElement.qualifier != null) {
                val referenceTarget = file.referenceAtPoint(surroundingElement.qualifier!!.startOffset)?.second
                if (referenceTarget is ClassDescriptor) {
                    LOG.info("Completing members of {}", referenceTarget.fqNameSafe)
                    return referenceTarget.getDescriptors()
                } else {
                    LOG.warn("No type reference in '{}'", surroundingElement.text)
                    return emptySequence()
                }
            } else {
                // : ?
                LOG.info("Completing type identifier '{}'", surroundingElement.text)
                val scope = file.scopeAtPoint(cursor) ?: return emptySequence()
                scopeChainTypes(scope)
            }
        }
        // .?
        is KtQualifiedExpression -> {
            LOG.info("Completing member expression '{}'", surroundingElement.text)
            val exp = if (infixCall) surroundingElement else surroundingElement.receiverExpression
            logPerf("completion.elementCompletions.members", "partialElement=${surroundingElement.text.take(LOG_PARTIAL_ELEMENT_PREVIEW_LENGTH)}") {
                completeMembers(file, cursor, exp, surroundingElement is KtSafeQualifiedExpression)
            }
        }
        is KtCallableReferenceExpression -> {
            // something::?
            if (surroundingElement.receiverExpression != null) {
                LOG.info("Completing method reference '{}'", surroundingElement.text)
                completeMembers(file, cursor, surroundingElement.receiverExpression!!)
            }
            // ::?
            else {
                LOG.info("Completing function reference '{}'", surroundingElement.text)
                val scope = file.scopeAtPoint(surroundingElement.startOffset) ?: return noResult("No scope at ${file.describePosition(cursor)}", emptySequence())
                identifiers(scope)
            }
        }
        // ?
        is KtNameReferenceExpression -> {
            LOG.info("Completing identifier '{}'", surroundingElement.text)
            val scope = file.scopeAtPoint(surroundingElement.startOffset) ?: return noResult("No scope at ${file.describePosition(cursor)}", emptySequence())
            if (infixCall && partial.length >= INFIX_COMPLETION_MIN_PARTIAL_LENGTH) {
                logPerf("completion.elementCompletions.members", "partialElement=${surroundingElement.text.take(LOG_PARTIAL_ELEMENT_PREVIEW_LENGTH)} infix=true") {
                    completeMembers(file, surroundingElement.startOffset, surroundingElement)
                }
            } else {
                logPerf("completion.elementCompletions.identifiers", "partialElement=${surroundingElement.text.take(LOG_PARTIAL_ELEMENT_PREVIEW_LENGTH)}") {
                    identifiers(scope)
                }
            }
        }
        // x ? y (infix)
        is KtBinaryExpression -> {
            if (surroundingElement.operationToken == KtTokens.IDENTIFIER) {
                completeMembers(file, cursor, surroundingElement.left!!)
            } else emptySequence()
        }
        is KtCallExpression, is KtConstantExpression -> {
            completeMembers(file, cursor, surroundingElement as KtExpression)
        }
        else -> {
            LOG.info("{} {} didn't look like a type, a member, or an identifier", surroundingElement::class.simpleName, surroundingElement.text)
            emptySequence()
        }
    }
}

private fun completeMembers(file: CompiledFile, cursor: Int, receiverExpr: KtExpression, unwrapNullable: Boolean = false): Sequence<DeclarationDescriptor> {
    // thingWithType.?
    var descriptors = emptySequence<DeclarationDescriptor>()
    file.scopeAtPoint(cursor)?.let { lexicalScope ->
        file.typeOfExpression(receiverExpr, lexicalScope)?.let { expressionType ->
            val receiverType = if (unwrapNullable) try {
                TypeUtils.makeNotNullable(expressionType)
            } catch (e: Exception) {
                LOG.printStackTrace(e)
                expressionType
            } else expressionType

            LOG.debug("Completing members of instance '{}'", receiverType)
            val members = receiverType.memberScope.getContributedDescriptors().asSequence()
            val extensions = extensionFunctions(lexicalScope).filter { isExtensionFor(receiverType, it) }
            descriptors = members + extensions

            if (!isCompanionOfEnum(receiverType) && !isCompanionOfSealed(receiverType)) {
                return descriptors
            }
        }
    }

    // JavaClass.?
    val referenceTarget = file.referenceAtPoint(receiverExpr.endOffset - 1)?.second
    if (referenceTarget is ClassDescriptor) {
        LOG.debug("Completing members of '{}'", referenceTarget.fqNameSafe)
        return descriptors + referenceTarget.getDescriptors()
    }

    LOG.debug("Can't find member scope for {}", receiverExpr.text)
    return emptySequence()
}

private fun ClassDescriptor.getDescriptors(): Sequence<DeclarationDescriptor> {
    val statics = staticScope.getContributedDescriptors().asSequence()
    val classes = unsubstitutedInnerClassesScope.getContributedDescriptors().asSequence()
    val types = unsubstitutedMemberScope.getContributedDescriptors().asSequence()
    val companionDescriptors = if (hasCompanionObject && companionObjectDescriptor != null) companionObjectDescriptor!!.getDescriptors() else emptySequence()

    return (statics + classes + types + companionDescriptors).toSet().asSequence()

}

private fun declarationIsInfix(declaration: DeclarationDescriptor): Boolean {
    val functionDescriptor = declaration as? FunctionDescriptor ?: return false
    return functionDescriptor.isInfix
}

private fun isCompanionOfEnum(kotlinType: KotlinType): Boolean {
    val classDescriptor = TypeUtils.getClassDescriptor(kotlinType)
    val isCompanion = DescriptorUtils.isCompanionObject(classDescriptor)
    if (!isCompanion) {
        return false
    }
    return DescriptorUtils.isEnumClass(classDescriptor?.containingDeclaration)
}

private fun isCompanionOfSealed(kotlinType: KotlinType): Boolean {
    val classDescriptor = TypeUtils.getClassDescriptor(kotlinType)
    val isCompanion = DescriptorUtils.isCompanionObject(classDescriptor)
    if (!isCompanion) {
        return false
    }

    return DescriptorUtils.isSealedClass(classDescriptor?.containingDeclaration)
}

private fun findPartialIdentifier(file: CompiledFile, cursor: Int): String {
    val line = file.lineBefore(cursor)
    if (line.matches(Regex(".*\\."))) return ""
    else if (line.matches(Regex(".*\\.\\w+"))) return line.substringAfterLast(".")
    else return Regex("\\w+").findAll(line).lastOrNull()?.value ?: ""
}

fun memberOverloads(type: KotlinType, identifier: String): Sequence<CallableDescriptor> {
    val nameFilter = equalsIdentifier(identifier)

    return type.memberScope
            .getContributedDescriptors(Companion.CALLABLES).asSequence()
            .filterIsInstance<CallableDescriptor>()
            .filter(nameFilter)
}

private fun completeTypeMembers(type: KotlinType): Sequence<DeclarationDescriptor> =
    type.memberScope.getDescriptorsFiltered(TYPES_FILTER).asSequence()

private fun scopeChainTypes(scope: LexicalScope): Sequence<DeclarationDescriptor> =
        scope.parentsWithSelf.flatMap(::scopeTypes)

private val TYPES_FILTER = DescriptorKindFilter(DescriptorKindFilter.NON_SINGLETON_CLASSIFIERS_MASK or DescriptorKindFilter.TYPE_ALIASES_MASK)

private fun scopeTypes(scope: HierarchicalScope): Sequence<DeclarationDescriptor> =
        scope.getContributedDescriptors(TYPES_FILTER).asSequence()

fun identifierOverloads(scope: LexicalScope, identifier: String): Sequence<CallableDescriptor> {
    val nameFilter = equalsIdentifier(identifier)

    return identifiers(scope)
            .filterIsInstance<CallableDescriptor>()
            .filter(nameFilter)
}

private fun extensionFunctions(scope: LexicalScope): Sequence<CallableDescriptor> =
    scope.parentsWithSelf.flatMap(::scopeExtensionFunctions)

private fun scopeExtensionFunctions(scope: HierarchicalScope): Sequence<CallableDescriptor> =
    scope.getContributedDescriptors(DescriptorKindFilter.CALLABLES).asSequence()
            .filterIsInstance<CallableDescriptor>()
            .filter { it.isExtension }
private fun identifiers(scope: LexicalScope): Sequence<DeclarationDescriptor> =
    logPerf("completion.identifiers.parents", "") {
        scope.parentsWithSelf.toList()
    }.asSequence()
        .flatMap { scopeIdentifiers(it) }
        .flatMap(::explodeConstructors)
private fun scopeIdentifiers(scope: HierarchicalScope): Sequence<DeclarationDescriptor> {
    val isLazyImportScope = scope::class.simpleName == "LazyImportScope"
    if (isLazyImportScope) {
        return emptySequence()
    }

    val locals = logPerf("completion.scopeIdentifiers.locals", "scope=${scope::class.simpleName}") {
        scope.getContributedDescriptors().toList()
    }.asSequence()
    val members = implicitMembers(scope)

    return locals + members
}


private fun explodeConstructors(declaration: DeclarationDescriptor): Sequence<DeclarationDescriptor> {

    return when (declaration) {
        is ClassDescriptor ->
            try {
                declaration.constructors.asSequence() + declaration
            } catch (_: AssertionError) {
                sequenceOf(declaration)
            }
        else ->
            sequenceOf(declaration)
    }
}

private fun implicitMembers(scope: HierarchicalScope): Sequence<DeclarationDescriptor> {
    if (scope !is LexicalScope) return emptySequence()
    val implicit = scope.implicitReceiver ?: return emptySequence()

    return logPerf("completion.implicitMembers", "scope=${scope::class.simpleName}") {
        implicit.type.memberScope.getContributedDescriptors().toList()
    }.asSequence()
}

private fun equalsIdentifier(identifier: String): (DeclarationDescriptor) -> Boolean =
    { name(it) == identifier }

private fun name(d: DeclarationDescriptor): String {
    if (d is ConstructorDescriptor)
        return d.constructedClass.name.identifier
    else
        return d.name.identifier
}

private fun isVisible(file: CompiledFile, cursor: Int): (DeclarationDescriptor) -> Boolean {
    val el = file.elementAtPoint(cursor) ?: return { true }
    val from = el.parentsWithSelf
                       .mapNotNull { file.compile[BindingContext.DECLARATION_TO_DESCRIPTOR, it] }
                       .firstOrNull() ?: return { true }
    fun check(target: DeclarationDescriptor): Boolean {
        val visible = isDeclarationVisible(target, from)

        if (!visible) logHidden(target, from)

        return visible
    }

    return ::check
}

// We can't use the implementations in Visibilities because they don't work with our type of incremental compilation
// Instead, we implement our own "liberal" visibility checker that defaults to visible when in doubt
private fun isDeclarationVisible(target: DeclarationDescriptor, from: DeclarationDescriptor): Boolean =
    target.parentsWithSelf
            .filterIsInstance<DeclarationDescriptorWithVisibility>()
            .none { isNotVisible(it, from) }

private fun isNotVisible(target: DeclarationDescriptorWithVisibility, from: DeclarationDescriptor): Boolean {
    when (target.visibility.delegate) {
        Visibilities.Private, Visibilities.PrivateToThis -> {
            if (DescriptorUtils.isTopLevelDeclaration(target))
                return !sameFile(target, from)
            else
                return !sameParent(target, from)
        }
        Visibilities.Protected -> {
            return !subclassParent(target, from)
        }
        else -> return false
    }
}

private fun sameFile(target: DeclarationDescriptor, from: DeclarationDescriptor): Boolean {
    val targetFile = DescriptorUtils.getContainingSourceFile(target)
    val fromFile = DescriptorUtils.getContainingSourceFile(from)

    if (targetFile == SourceFile.NO_SOURCE_FILE || fromFile == SourceFile.NO_SOURCE_FILE) return true
    else return targetFile.name == fromFile.name
}

private fun sameParent(target: DeclarationDescriptor, from: DeclarationDescriptor): Boolean {
    val targetParent = target.parentsWithSelf.mapNotNull(::isParentClass).firstOrNull() ?: return true
    val fromParents = from.parentsWithSelf.mapNotNull(::isParentClass).toList()

    return fromParents.any { it.fqNameSafe == targetParent.fqNameSafe }
}

private fun subclassParent(target: DeclarationDescriptor, from: DeclarationDescriptor): Boolean {
    val targetParent = target.parentsWithSelf.mapNotNull(::isParentClass).firstOrNull() ?: return true
    val fromParents = from.parentsWithSelf.mapNotNull(::isParentClass).toList()

    if (fromParents.isEmpty()) return true
    else return fromParents.any { DescriptorUtils.isSubclass(it, targetParent) }
}

private fun isParentClass(declaration: DeclarationDescriptor): ClassDescriptor? =
    if (declaration is ClassDescriptor && !DescriptorUtils.isCompanionObject(declaration))
        declaration
    else null

private fun isExtensionFor(type: KotlinType, extensionFunction: CallableDescriptor): Boolean {
    val receiverType = extensionFunction.extensionReceiverParameter?.type?.replaceArgumentsWithStarProjections() ?: return false
    return KotlinTypeChecker.DEFAULT.isSubtypeOf(type, receiverType)
        || (TypeUtils.getTypeParameterDescriptorOrNull(receiverType)?.isGenericExtensionFor(type) ?: false)
}

private fun TypeParameterDescriptor.isGenericExtensionFor(type: KotlinType): Boolean =
    upperBounds.all { KotlinTypeChecker.DEFAULT.isSubtypeOf(type, it) }

private val loggedHidden = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build<Pair<Name, Name>, Unit>()

private fun logHidden(target: DeclarationDescriptor, from: DeclarationDescriptor) {
    val key = Pair(from.name, target.name)

    loggedHidden.get(key, { doLogHidden(target, from )})
}

private fun doLogHidden(target: DeclarationDescriptor, from: DeclarationDescriptor) {
    LOG.debug("Hiding {} because it's not visible from {}", describeDeclaration(target), describeDeclaration(from))
}

private fun describeDeclaration(declaration: DeclarationDescriptor): String {
    val file = declaration.findPsi()?.containingFile?.toPath()?.fileName?.toString() ?: "<unknown-file>"
    val container = declaration.containingDeclaration?.name?.toString() ?: "<top-level>"

    return "($file $container.${declaration.name})"
}

private fun doesntLookLikeImport(importDirective: KtImportDirective): Sequence<DeclarationDescriptor> {
    LOG.debug("{} doesn't look like import a.b...", importDirective.text)

    return emptySequence()
}

private fun doesntLookLikePackage(packageDirective: KtPackageDirective): Sequence<DeclarationDescriptor> {
    LOG.debug("{} doesn't look like package a.b...", packageDirective.text)

    return emptySequence()
}

private fun empty(message: String): CompletionList {
    LOG.debug(message)

    return CompletionList(true, emptyList())
}
