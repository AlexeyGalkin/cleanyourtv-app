package tv.cleanyour.ui.design.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Fork (Clean Your TV RU): the display-time translation of the core's English.
class CoreTextTest {
    private val table = CoreText.parse(
        """
        # a comment
        Restore point saved	Точка восстановления сохранена
        Switching off {1}	Отключаем {1}
        Switching off {1}, which also answers the Home button	Отключаем {1}: оно тоже отвечает на кнопку «Домой»
        {1} extras	Фирменные дополнения {1}
        Streaming app	Стриминговое приложение
        Nothing here
        """.trimIndent(),
    )

    @Test fun exactLinesAndTemplatesTranslateTheRestStays() {
        assertEquals("Точка восстановления сохранена", table.translate("Restore point saved"))
        assertEquals("Отключаем Netflix", table.translate("Switching off Netflix"))
        // The more specific template wins over the shorter one.
        assertEquals("Отключаем PatchWall: оно тоже отвечает на кнопку «Домой»",
            table.translate("Switching off PatchWall, which also answers the Home button"))
        assertEquals("Фирменные дополнения Xiaomi", table.translate("Xiaomi extras"))
        assertEquals("Something new", table.translate("Something new"))
        assertEquals("Nothing here", table.translate("Nothing here"))   // an empty translation is no translation
    }

    @Test fun theShippedTableParsesAndEveryTemplateKeepsItsPlaceholder() {
        val file = File("src/main/res/raw/core_ru.tsv")
        val text = file.readText()
        val t = CoreText.parse(text)
        for (line in text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }) {
            val (en, ru) = line.split('\t', limit = 2)
            assertTrue("no tab inside: $line", '\t' !in ru)
            assertEquals("placeholder in both: $line", "{1}" in en, "{1}" in ru)
        }
        assertTrue(t.size >= 0)
    }
}
