package calebxzau.rdi.client.codeeditor

import calebxzau.rdi.client.codeeditor.CodeLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeLanguageTest {
    @Test
    fun detectsSupportedFileExtensions() {
        assertEquals(CodeLanguage.JSON5, CodeLanguage.fromPath("config.JSON5"))
        assertEquals(CodeLanguage.JSON, CodeLanguage.fromPath("config.json"))
        assertEquals(CodeLanguage.TOML, CodeLanguage.fromPath("config.toml"))
        assertEquals(CodeLanguage.YAML, CodeLanguage.fromPath("config.yml"))
        assertEquals(CodeLanguage.FORGE_CFG, CodeLanguage.fromPath("config.CFG"))
    }

    @Test
    fun fallsBackToPlainText() {
        assertEquals(CodeLanguage.PLAIN_TEXT, CodeLanguage.fromPath(null))
        assertEquals(CodeLanguage.PLAIN_TEXT, CodeLanguage.fromPath("README.txt"))
    }
}
