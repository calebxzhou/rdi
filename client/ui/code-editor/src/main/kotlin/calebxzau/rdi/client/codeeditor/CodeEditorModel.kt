package calebxzau.rdi.client.codeeditor

enum class CodeLanguage(val label: String) {
    PLAIN_TEXT("文本"),
    JSON("JSON"),
    JSON5("JSON5"),
    TOML("TOML"),
    YAML("YAML"),
    FORGE_CFG("CFG");

    companion object {
        fun fromPath(path: String?): CodeLanguage = when {
            path.isNullOrBlank() -> PLAIN_TEXT
            path.endsWith(".json5", ignoreCase = true) -> JSON5
            path.endsWith(".json", ignoreCase = true) -> JSON
            path.endsWith(".toml", ignoreCase = true) -> TOML
            path.endsWith(".yaml", ignoreCase = true) || path.endsWith(".yml", ignoreCase = true) -> YAML
            path.endsWith(".cfg", ignoreCase = true) -> FORGE_CFG
            else -> PLAIN_TEXT
        }
    }
}

data class CodeEditorValidation(
    val language: CodeLanguage,
    val isValid: Boolean,
    val message: String
)
