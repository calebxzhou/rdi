package calebxzau.rdi.client.codeeditor

enum class CodeTokenRole {
    PUNCTUATION,
    JSON_KEY,
    JSON_STRING,
    JSON_NUMBER,
    JSON_LITERAL,
    TOML_TABLE,
    TOML_KEY,
    TOML_STRING,
    TOML_NUMBER,
    TOML_LITERAL,
    TOML_COMMENT,
    YAML_KEY,
    YAML_STRING,
    YAML_NUMBER,
    YAML_LITERAL,
    YAML_COMMENT,
    YAML_ANCHOR,
    CFG_SECTION,
    CFG_TYPE,
    CFG_KEY,
    CFG_VALUE,
    CFG_NUMBER,
    CFG_COMMENT
}

data class CodeHighlightSpan(
    val role: CodeTokenRole,
    val start: Int,
    val endExclusive: Int
)
