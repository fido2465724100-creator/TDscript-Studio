package com.tdscript.studio.agent

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val content: String,
    val rawJson: String? = null,
    val model: String? = null
)

interface LlmClient {
    fun chat(messages: List<ChatMessage>): ChatResponse
}

/**
 * Универсальный HTTP-клиент к OpenAI-совместимому API (/v1/chat/completions)
 * без тяжелых внешних зависимостей.
 * Поддерживает работу с локальными серверами (Ollama: http://127.0.0.1:11434/v1,
 * LM Studio: http://127.0.0.1:1234/v1, vLLM) и внешними облачными API.
 */
class OpenAiCompatibleLlmClient(
    val baseUrl: String = "http://127.0.0.1:11434/v1",
    val modelName: String = "llama3",
    val apiKey: String = "",
    val timeoutMs: Int = 120_000,
    val maxTokens: Int = 400
) : LlmClient {

    override fun chat(messages: List<ChatMessage>): ChatResponse {
        val endpoint = "${baseUrl.trimEnd('/')}/chat/completions"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection

        conn.requestMethod = "POST"
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setRequestProperty("Accept", "application/json")
        if (apiKey.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
        }

        val requestBody = buildJsonPayload(messages)

        OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(requestBody)
            writer.flush()
        }

        val responseCode = conn.responseCode
        val isSuccess = responseCode in 200..299
        val inputStream = if (isSuccess) conn.inputStream else (conn.errorStream
            ?: throw RuntimeException("HTTP $responseCode from $endpoint with empty body"))

        val responseText = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { it.readText() }

        if (!isSuccess) {
            throw RuntimeException("LLM request failed (HTTP $responseCode): $responseText")
        }

        val extractedContent = parseContentFromResponse(responseText)
        return ChatResponse(content = extractedContent, rawJson = responseText, model = modelName)
    }

    private fun buildJsonPayload(messages: List<ChatMessage>): String {
        val messagesJson = messages.joinToString(",") { msg ->
            """{"role":"${escapeJson(msg.role)}","content":"${escapeJson(msg.content)}"}"""
        }
        return """{"model":"${escapeJson(modelName)}","messages":[$messagesJson],"temperature":0.1,"max_tokens":$maxTokens}"""
    }

    private fun escapeJson(s: String): String {
        return buildString {
            for (c in s) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (c.code in 0x00..0x1F) {
                            append(String.format("\\u%04x", c.code))
                        } else {
                            append(c)
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun parseContentFromResponse(json: String): String {
            val contentIdx = json.indexOf("\"content\"")
            if (contentIdx == -1) return json

            val colonIdx = json.indexOf(':', contentIdx + 9)
            if (colonIdx == -1) return json

            var startQuote = -1
            for (i in colonIdx + 1 until json.length) {
                if (json[i] == '"') {
                    startQuote = i
                    break
                }
            }
            if (startQuote == -1) return json

            val sb = StringBuilder()
            var i = startQuote + 1
            while (i < json.length) {
                val c = json[i]
                if (c == '\\' && i + 1 < json.length) {
                    val next = json[i + 1]
                    when (next) {
                        '"' -> { sb.append('"'); i += 2; continue }
                        '\\' -> { sb.append('\\'); i += 2; continue }
                        '/' -> { sb.append('/'); i += 2; continue }
                        'b' -> { sb.append('\b'); i += 2; continue }
                        'f' -> { sb.append('\u000c'); i += 2; continue }
                        'n' -> { sb.append('\n'); i += 2; continue }
                        'r' -> { sb.append('\r'); i += 2; continue }
                        't' -> { sb.append('\t'); i += 2; continue }
                        'u' -> {
                            if (i + 5 < json.length) {
                                val hex = json.substring(i + 2, i + 6)
                                try {
                                    sb.append(hex.toInt(16).toChar())
                                } catch (e: Exception) {
                                    sb.append("\\u").append(hex)
                                }
                                i += 6
                                continue
                            }
                        }
                    }
                } else if (c == '"') {
                    break
                }
                sb.append(c)
                i++
            }
            return sb.toString()
        }
    }
}
