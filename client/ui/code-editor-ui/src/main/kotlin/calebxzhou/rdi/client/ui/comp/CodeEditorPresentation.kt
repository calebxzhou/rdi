package calebxzhou.rdi.client.ui.comp

// Compose presentation for the code-editor-ui module.

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import calebxzau.rdi.client.codeeditor.CodeLanguage
import calebxzau.rdi.client.codeeditor.CodeTokenRole
import calebxzau.rdi.client.codeeditor.highlightCode
import calebxzhou.rdi.client.service.codeeditor.coerceToText
import calebxzau.rdi.client.ui.themeNow

internal data class CodeEditorColors(
    val punctuation: SpanStyle,
    val key: SpanStyle,
    val string: SpanStyle,
    val number: SpanStyle,
    val literal: SpanStyle,
    val comment: SpanStyle,
    val anchor: SpanStyle
)

@Composable
internal fun codeEditorColors() = CodeEditorColors(
    punctuation = SpanStyle(color = themeNow.onSurfaceVariant),
    key = SpanStyle(color = themeNow.primary, fontWeight = FontWeight.SemiBold),
    string = SpanStyle(color = themeNow.secondary),
    number = SpanStyle(color = themeNow.tertiary),
    literal = SpanStyle(color = themeNow.tertiary, fontWeight = FontWeight.Medium),
    comment = SpanStyle(color = themeNow.onSurfaceVariant.copy(alpha = 0.72f)),
    anchor = SpanStyle(color = themeNow.secondary, fontWeight = FontWeight.Medium)
)

internal fun buildEditorValue(
    text: String,
    selection: TextRange,
    language: CodeLanguage,
    colors: CodeEditorColors,
    composition: TextRange? = null
): TextFieldValue = TextFieldValue(
    annotatedString = buildAnnotatedString {
        append(text)
        highlightCode(text, language).forEach { span ->
            addStyle(span.role.toSpanStyle(colors), span.start, span.endExclusive)
        }
    },
    selection = selection.coerceToText(text.length),
    composition = composition?.coerceToText(text.length)
)

private fun CodeTokenRole.toSpanStyle(colors: CodeEditorColors): SpanStyle = when (this) {
    CodeTokenRole.PUNCTUATION -> colors.punctuation
    CodeTokenRole.JSON_KEY -> colors.key
    CodeTokenRole.JSON_STRING -> colors.string
    CodeTokenRole.JSON_NUMBER -> colors.number
    CodeTokenRole.JSON_LITERAL -> colors.literal
    CodeTokenRole.TOML_TABLE -> colors.key
    CodeTokenRole.TOML_KEY -> colors.anchor
    CodeTokenRole.TOML_STRING -> colors.string
    CodeTokenRole.TOML_NUMBER -> colors.number
    CodeTokenRole.TOML_LITERAL -> colors.literal
    CodeTokenRole.TOML_COMMENT -> colors.comment
    CodeTokenRole.YAML_KEY -> colors.key
    CodeTokenRole.YAML_STRING -> colors.string
    CodeTokenRole.YAML_NUMBER -> colors.number
    CodeTokenRole.YAML_LITERAL -> colors.literal
    CodeTokenRole.YAML_COMMENT -> colors.comment
    CodeTokenRole.YAML_ANCHOR -> colors.anchor
    CodeTokenRole.CFG_SECTION -> colors.key
    CodeTokenRole.CFG_TYPE -> colors.literal
    CodeTokenRole.CFG_KEY -> colors.anchor
    CodeTokenRole.CFG_VALUE -> colors.string
    CodeTokenRole.CFG_NUMBER -> colors.number
    CodeTokenRole.CFG_COMMENT -> colors.comment
}
