package calebxzhou.rdi.client.ui.comp

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import calebxzau.rdi.client.codeeditor.CodeLanguage
import calebxzau.rdi.client.codeeditor.CodeTokenRole
import calebxzau.rdi.client.codeeditor.highlightCode
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.service.codeeditor.coerceToText

private val punctuationStyle = SpanStyle(color = MaterialColor.BLUE_GRAY_500.color)
private val jsonKeyStyle = SpanStyle(color = MaterialColor.BLUE_800.color, fontWeight = FontWeight.SemiBold)
private val jsonStringStyle = SpanStyle(color = MaterialColor.GREEN_800.color)
private val jsonNumberStyle = SpanStyle(color = MaterialColor.DEEP_ORANGE_700.color)
private val jsonLiteralStyle = SpanStyle(color = MaterialColor.PURPLE_700.color, fontWeight = FontWeight.Medium)
private val tomlTableStyle = SpanStyle(color = MaterialColor.BLUE_800.color, fontWeight = FontWeight.SemiBold)
private val tomlKeyStyle = SpanStyle(color = MaterialColor.TEAL_800.color, fontWeight = FontWeight.SemiBold)
private val tomlStringStyle = SpanStyle(color = MaterialColor.GREEN_800.color)
private val tomlNumberStyle = SpanStyle(color = MaterialColor.DEEP_ORANGE_700.color)
private val tomlLiteralStyle = SpanStyle(color = MaterialColor.PURPLE_700.color, fontWeight = FontWeight.Medium)
private val tomlCommentStyle = SpanStyle(color = MaterialColor.GRAY_600.color)
private val yamlKeyStyle = SpanStyle(color = MaterialColor.BLUE_800.color, fontWeight = FontWeight.SemiBold)
private val yamlStringStyle = SpanStyle(color = MaterialColor.GREEN_800.color)
private val yamlNumberStyle = SpanStyle(color = MaterialColor.DEEP_ORANGE_700.color)
private val yamlLiteralStyle = SpanStyle(color = MaterialColor.PURPLE_700.color, fontWeight = FontWeight.Medium)
private val yamlCommentStyle = SpanStyle(color = MaterialColor.GRAY_600.color)
private val yamlAnchorStyle = SpanStyle(color = MaterialColor.TEAL_800.color, fontWeight = FontWeight.Medium)
private val cfgSectionStyle = SpanStyle(color = MaterialColor.BLUE_800.color, fontWeight = FontWeight.SemiBold)
private val cfgTypeStyle = SpanStyle(color = MaterialColor.PURPLE_700.color, fontWeight = FontWeight.Medium)
private val cfgKeyStyle = SpanStyle(color = MaterialColor.TEAL_800.color, fontWeight = FontWeight.SemiBold)
private val cfgValueStyle = SpanStyle(color = MaterialColor.GREEN_800.color)
private val cfgNumberStyle = SpanStyle(color = MaterialColor.DEEP_ORANGE_700.color)
private val cfgCommentStyle = SpanStyle(color = MaterialColor.GRAY_600.color)

fun buildEditorValue(
    text: String,
    selection: TextRange,
    language: CodeLanguage,
    composition: TextRange? = null
): TextFieldValue = TextFieldValue(
    annotatedString = buildAnnotatedString {
        append(text)
        highlightCode(text, language).forEach { span ->
            addStyle(span.role.toSpanStyle(), span.start, span.endExclusive)
        }
    },
    selection = selection.coerceToText(text.length),
    composition = composition?.coerceToText(text.length)
)

private fun CodeTokenRole.toSpanStyle(): SpanStyle = when (this) {
    CodeTokenRole.PUNCTUATION -> punctuationStyle
    CodeTokenRole.JSON_KEY -> jsonKeyStyle
    CodeTokenRole.JSON_STRING -> jsonStringStyle
    CodeTokenRole.JSON_NUMBER -> jsonNumberStyle
    CodeTokenRole.JSON_LITERAL -> jsonLiteralStyle
    CodeTokenRole.TOML_TABLE -> tomlTableStyle
    CodeTokenRole.TOML_KEY -> tomlKeyStyle
    CodeTokenRole.TOML_STRING -> tomlStringStyle
    CodeTokenRole.TOML_NUMBER -> tomlNumberStyle
    CodeTokenRole.TOML_LITERAL -> tomlLiteralStyle
    CodeTokenRole.TOML_COMMENT -> tomlCommentStyle
    CodeTokenRole.YAML_KEY -> yamlKeyStyle
    CodeTokenRole.YAML_STRING -> yamlStringStyle
    CodeTokenRole.YAML_NUMBER -> yamlNumberStyle
    CodeTokenRole.YAML_LITERAL -> yamlLiteralStyle
    CodeTokenRole.YAML_COMMENT -> yamlCommentStyle
    CodeTokenRole.YAML_ANCHOR -> yamlAnchorStyle
    CodeTokenRole.CFG_SECTION -> cfgSectionStyle
    CodeTokenRole.CFG_TYPE -> cfgTypeStyle
    CodeTokenRole.CFG_KEY -> cfgKeyStyle
    CodeTokenRole.CFG_VALUE -> cfgValueStyle
    CodeTokenRole.CFG_NUMBER -> cfgNumberStyle
    CodeTokenRole.CFG_COMMENT -> cfgCommentStyle
}
