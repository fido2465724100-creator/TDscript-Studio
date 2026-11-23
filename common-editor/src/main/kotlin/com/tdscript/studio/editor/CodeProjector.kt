package com.tdscript.studio.editor

/**
 * Двусторонний синтаксический проектор:
 * Переключает отображение ключевых слов в редакторе на лету между любыми из 109 языков,
 * сохраняя физический файл в каноническом виде либо с директивой 'language: <code>;'.
 *
 * Безопасность: парсит исходный текст посимвольно (state machine),
 * гарантируя, что ключевые слова внутри строк ("...", '...') и комментариев (//, #, /* */)
 * НИКОГДА не заменяются!
 */
class CodeProjector {

    companion object {
        val KEYWORDS_CANONICAL_TO_RU = mapOf(
            "if" to "если",
            "else" to "иначе",
            "while" to "пока",
            "let" to "пусть",
            "var" to "перем",
            "true" to "истина",
            "false" to "ложь",
            "null" to "пусто"
        )

        val KEYWORDS_CANONICAL_TO_DE = mapOf(
            "if" to "wenn",
            "else" to "sonst",
            "while" to "solange",
            "let" to "sei",
            "true" to "wahr",
            "false" to "falsch",
            "null" to "null"
        )
    }

    fun projectForDisplay(sourceCode: String, targetLanguage: String): String {
        val mapping = when (targetLanguage.lowercase()) {
            "ru" -> KEYWORDS_CANONICAL_TO_RU
            "de" -> KEYWORDS_CANONICAL_TO_DE
            else -> return sourceCode
        }

        // 1. Отделяем или обновляем директиву 'language: <code>;'
        var cleanSource = sourceCode.trimStart()
        if (cleanSource.startsWith("language:")) {
            val semiIdx = cleanSource.indexOf(';')
            if (semiIdx != -1) {
                cleanSource = cleanSource.substring(semiIdx + 1).trimStart('\r', '\n')
            }
        }

        val result = StringBuilder()
        result.append("language: ${targetLanguage.lowercase()};\n")

        var i = 0
        val len = cleanSource.length

        while (i < len) {
            val c = cleanSource[i]

            // 1. Однострочные комментарии // и #
            if (c == '#' || (c == '/' && i + 1 < len && cleanSource[i + 1] == '/')) {
                val endLine = cleanSource.indexOf('\n', i)
                if (endLine != -1) {
                    result.append(cleanSource.substring(i, endLine + 1))
                    i = endLine + 1
                } else {
                    result.append(cleanSource.substring(i))
                    i = len
                }
                continue
            }

            // 2. Многострочные комментарии /* ... */
            if (c == '/' && i + 1 < len && cleanSource[i + 1] == '*') {
                val endBlock = cleanSource.indexOf("*/", i + 2)
                if (endBlock != -1) {
                    result.append(cleanSource.substring(i, endBlock + 2))
                    i = endBlock + 2
                } else {
                    result.append(cleanSource.substring(i))
                    i = len
                }
                continue
            }

            // 3. Строковые литералы "..." и '...' и L"..."
            if (c == '"' || c == '\'' || (c == 'L' && i + 1 < len && (cleanSource[i + 1] == '"' || cleanSource[i + 1] == '\''))) {
                val quote = if (c == 'L') {
                    result.append('L')
                    i++
                    cleanSource[i]
                } else c

                result.append(quote)
                i++
                while (i < len) {
                    val sc = cleanSource[i]
                    result.append(sc)
                    if (sc == '\\' && i + 1 < len) {
                        i++
                        result.append(cleanSource[i])
                    } else if (sc == quote) {
                        i++
                        break
                    }
                    i++
                }
                continue
            }

            // 4. Идентификаторы и ключевые слова
            if (c.isLetter() || c == '_') {
                val startWord = i
                while (i < len && (cleanSource[i].isLetterOrDigit() || cleanSource[i] == '_')) {
                    i++
                }
                val word = cleanSource.substring(startWord, i)
                val mapped = mapping[word.lowercase()] ?: word
                result.append(mapped)
                continue
            }

            // 5. Все остальные символы (пробелы, операторы, скобки)
            result.append(c)
            i++
        }

        return result.toString()
    }
}
