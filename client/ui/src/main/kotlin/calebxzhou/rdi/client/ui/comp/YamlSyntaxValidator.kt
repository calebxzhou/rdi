package calebxzhou.rdi.client.service.codeeditor

import org.yaml.snakeyaml.Yaml

internal fun validateYamlSyntaxMessage(text: String): String? = runCatching {
    for (ignored in Yaml().loadAll(text)) {
        // Force SnakeYAML to parse the whole stream for diagnostics.
    }
    null
}.getOrElse {
    it.message
        ?.lineSequence()
        ?.firstOrNull()
        ?.trim()
        ?: "未知错误"
}
