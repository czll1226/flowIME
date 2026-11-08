package io.justcodeit.smartime.context

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import io.justcodeit.smartime.model.ContextSnapshot
import io.justcodeit.smartime.model.ContextType

class PsiContextResolver : ContextResolver {
    private val logger = Logger.getInstance(PsiContextResolver::class.java)

    override fun resolve(project: Project, editor: Editor, psiFile: PsiFile, caretOffset: Int): ContextSnapshot {
        val normalizedOffset = normalizeOffset(psiFile, caretOffset)
        val element = findElement(psiFile, normalizedOffset)
        val contextType = classifyContext(psiFile, element)

        return ContextSnapshot(
            fileType = psiFile.fileType.name,
            language = psiFile.language.id,
            offset = normalizedOffset,
            contextType = contextType,
            psiElementType = element?.node?.elementType?.toString() ?: element?.javaClass?.simpleName ?: "UNKNOWN",
            timestamp = System.currentTimeMillis(),
        )
    }

    private fun normalizeOffset(psiFile: PsiFile, caretOffset: Int): Int {
        val textLength = psiFile.textLength
        if (textLength <= 0) {
            return 0
        }
        return caretOffset.coerceIn(0, textLength - 1)
    }

    private fun findElement(psiFile: PsiFile, offset: Int): PsiElement? {
        val current = psiFile.findElementAt(offset)
        val previous = if (offset > 0) psiFile.findElementAt(offset - 1) else null

        if (current == null) {
            return previous
        }

        if (current is PsiWhiteSpace && previous != null) {
            if (current.text.contains('\n') || current.text.contains('\r')) {
                return previous
            }

            val nextOffset = current.textRange.endOffset.takeIf { it < psiFile.textLength }
            val next = nextOffset?.let { psiFile.findElementAt(it) }
            return next ?: previous
        }

        return current
    }

    private fun classifyContext(psiFile: PsiFile, element: PsiElement?): ContextType {
        if (element == null) {
            return ContextType.UNKNOWN
        }

        // git commit message: switch to chinese by default
        if (psiFile.name.contains("COMMIT_EDITMSG", ignoreCase = true)) {
            return ContextType.DOC_COMMENT
        }

        return try {
            val comment = PsiTreeUtil.getParentOfType(element, PsiComment::class.java, false)
            if (comment != null) {
                classifyComment(comment.text)
            } else if (isKotlinInterpolationExpression(psiFile, element)) {
                ContextType.CODE
            } else if (isKotlinStringLiteralContent(psiFile, element)) {
                ContextType.STRING_LITERAL
            } else if (isStringLiteral(psiFile, element)) {
                ContextType.STRING_LITERAL
            } else {
                ContextType.CODE
            }
        } catch (t: Throwable) {
            // shortcut: Kotlin PSI check may fail if plugin not loaded, fallback to CODE
            logger.warn("Failed to resolve PSI context", t)
            ContextType.CODE
        }
    }

    private fun classifyComment(text: String): ContextType {
        val normalized = text.trimStart()
        return when {
            normalized.startsWith("<!--") -> ContextType.XML_COMMENT
            normalized.startsWith("/**") -> ContextType.DOC_COMMENT
            normalized.startsWith("//") -> ContextType.LINE_COMMENT
            normalized.startsWith("/*") -> ContextType.BLOCK_COMMENT
            else -> ContextType.UNKNOWN
        }
    }

    private fun isStringLiteral(psiFile: PsiFile, element: PsiElement): Boolean {
        if (psiFile.language.id.equals("kotlin", ignoreCase = true)) {
            return false
        }

        val injectionHost = PsiTreeUtil.getParentOfType(element, PsiLanguageInjectionHost::class.java, false) ?: return false
        if (injectionHost is PsiComment) {
            return false
        }

        val text = injectionHost.text.trim()
        return text.startsWith("\"") || text.startsWith("'") || text.startsWith("\"\"\"")
    }

    private fun isJavaOrKotlin(languageId: String): Boolean {
        return languageId.equals("JAVA", ignoreCase = true) || languageId.equals("kotlin", ignoreCase = true)
    }

    private fun isKotlinInterpolationExpression(psiFile: PsiFile, element: PsiElement): Boolean {
        if (!psiFile.language.id.equals("kotlin", ignoreCase = true)) {
            return false
        }

        // shortcut: only check if Kotlin plugin loaded, dynamic class resolution
        return try {
            val ktStringTemplateEntryClass = Class.forName("org.jetbrains.kotlin.psi.KtStringTemplateEntry")
            @Suppress("UNCHECKED_CAST")
            val entry = PsiTreeUtil.getParentOfType(element, ktStringTemplateEntryClass as Class<out PsiElement>, false) ?: return false
            val className = entry.javaClass.simpleName
            className == "KtBlockStringTemplateEntry" || className == "KtSimpleNameStringTemplateEntry"
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun isKotlinStringLiteralContent(psiFile: PsiFile, element: PsiElement): Boolean {
        if (!psiFile.language.id.equals("kotlin", ignoreCase = true)) {
            return false
        }

        return try {
            val ktStringTemplateEntryClass = Class.forName("org.jetbrains.kotlin.psi.KtStringTemplateEntry")
            val ktStringTemplateExpressionClass = Class.forName("org.jetbrains.kotlin.psi.KtStringTemplateExpression")
            @Suppress("UNCHECKED_CAST")
            val entry = PsiTreeUtil.getParentOfType(element, ktStringTemplateEntryClass as Class<out PsiElement>, false)
            if (entry != null) {
                val className = entry.javaClass.simpleName
                if (className == "KtLiteralStringTemplateEntry" || className == "KtEscapeStringTemplateEntry") {
                    return true
                }
            }
            @Suppress("UNCHECKED_CAST")
            val templateExpression = PsiTreeUtil.getParentOfType(element, ktStringTemplateExpressionClass as Class<out PsiElement>, false)
            templateExpression != null && entry == null
        } catch (e: ClassNotFoundException) {
            false
        }
    }
}
