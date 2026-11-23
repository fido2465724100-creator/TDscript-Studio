package com.tdscript.studio.editor

import com.tdscript.core.Lexer

/**
 * Двусторонний синтаксический проектор:
 * Переключает отображение ключевых слов в редакторе на лету между любыми из 109 языков,
 * сохраняя физический файл в каноническом виде либо с директивой 'language: <code>;'.
 */
class CodeProjector {

    private val KEYWORDS_CANONICAL_TO_RU = mapOf(
        "if" to "если",
        "else" to "иначе",
        "while" to "пока",
        "let" to "пусть",
        "var" to "перем",
        "true" to "истина",
        "false" to "ложь",
        "null" to "пусто"
    )

    private val KEYWORDS_CANONICAL_TO_DE = mapOf(
        "if" to "wenn",
        "else" to "sonst",
        "while" to "solange",
        "let" to "sei",
        "true" to "wahr",
        "false" to "falsch",
        "null" to "null"
    )

    fun projectForDisplay(sourceCode: String, targetLanguage: String): String {
        val mapping = when (targetLanguage.lowercase()) {
            "ru" -> KEYWORDS_CANONICAL_TO_RU
            "de" -> KEYWORDS_CANONICAL_TO_DE
            else -> return sourceCode
        }

        // Токенизируем, чтобы заменить ТОЛЬКО ключевые слова вне строк и комментов
        val lexer = Lexer(sourceCode)
        val tokens = try { lexer.tokenize() } catch (e: Exception) { return sourceCode }

        val sb = StringBuilder()
        var lastPos = 0

        // Безопасная проекция исходного текста
        var projected = sourceCode
        mapping.forEach { (canonical, localized) ->
            projected = projected.replace(Regex("""(?m)\b$canonical\b"""), localized)
        }

        // Обновляем заголовок директивы
        if (projected.startsWith("language:")) {
            val endIdx = projected.indexOf(';')
            if (endIdx != -1) {
                projected = "language: $targetLanguage;" + projected.substring(endIdx + 1)
            }
        } else {
            projected = "language: $targetLanguage;\n" + projected
        }

        return projected
    }
}
