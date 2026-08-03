package calebxzau.rdi.client.codeeditor

import calebxzau.rdi.client.codeeditor.CodeLanguage
import calebxzau.rdi.client.codeeditor.validateCodeContent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyntaxValidatorTest {
    @Test
    fun validatesJsonJson5TomlYamlAndCfg() {
        assertTrue(validateCodeContent("{\"value\": 1}", CodeLanguage.JSON)!!.isValid)
        assertTrue(validateCodeContent("{value: 1,}", CodeLanguage.JSON5)!!.isValid)
        assertTrue(validateCodeContent("[section]\nvalue = 1", CodeLanguage.TOML)!!.isValid)
        assertTrue(validateCodeContent("name: value", CodeLanguage.YAML)!!.isValid)
        assertTrue(validateCodeContent("general {\nB:enabled=true\n}", CodeLanguage.FORGE_CFG)!!.isValid)
    }

    @Test
    fun reportsInvalidSyntaxAndIgnoresPlainText() {
        assertFalse(validateCodeContent("{", CodeLanguage.JSON)!!.isValid)
        assertFalse(validateCodeContent("name:", CodeLanguage.FORGE_CFG)!!.isValid)
        assertNull(validateCodeContent("anything", CodeLanguage.PLAIN_TEXT))
    }
}
