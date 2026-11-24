package com.tdscript.benchmark

enum class AttemptStatus {
    PASS,
    EMPTY_HTTP_RESPONSE,
    TRUNCATED_IN_THINKING,
    EXTRACTION_EMPTY,
    COMPILE_OR_SYNTAX_ERROR,
    RUNTIME_OR_SCOPE_ERROR,
    OUTPUT_MISMATCH
}

data class AttemptTraceRecord(
    val timestamp: String,
    val target: String,
    val task_id: Int,
    val task_name: String,
    val attempt: Int,
    val status: AttemptStatus,
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val duration_ms: Long,
    val raw_response: String,
    val reasoning_text: String?,
    val extracted_code: String,
    val loc: Int,
    val stdout: String,
    val diagnostic_sent: String?
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"timestamp\":\"").append(escape(timestamp)).append("\",")
            append("\"target\":\"").append(escape(target)).append("\",")
            append("\"task_id\":").append(task_id).append(",")
            append("\"task_name\":\"").append(escape(task_name)).append("\",")
            append("\"attempt\":").append(attempt).append(",")
            append("\"status\":\"").append(status.name).append("\",")
            append("\"prompt_tokens\":").append(prompt_tokens).append(",")
            append("\"completion_tokens\":").append(completion_tokens).append(",")
            append("\"duration_ms\":").append(duration_ms).append(",")
            append("\"raw_response\":\"").append(escape(raw_response)).append("\",")
            if (reasoning_text != null) {
                append("\"reasoning_text\":\"").append(escape(reasoning_text)).append("\",")
            } else {
                append("\"reasoning_text\":null,")
            }
            append("\"extracted_code\":\"").append(escape(extracted_code)).append("\",")
            append("\"loc\":").append(loc).append(",")
            append("\"stdout\":\"").append(escape(stdout)).append("\",")
            if (diagnostic_sent != null) {
                append("\"diagnostic_sent\":\"").append(escape(diagnostic_sent)).append("\"")
            } else {
                append("\"diagnostic_sent\":null")
            }
            append("}")
        }
    }

    private fun escape(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code in 0x00..0x1F) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }
}

data class TaskRunMetric(
    val taskId: Int,
    val taskTitle: String,
    val firstPassSuccess: Boolean,
    val finalSuccess: Boolean,
    val attemptsCount: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val loc: Int,
    val lastError: String? = null
)

data class TargetBenchmarkSummary(
    val profile: LanguageProfile,
    val status: String, // "COMPLETED" or "SKIPPED_NO_RUNTIME"
    val firstPassPct: Double,
    val finalSuccessPct: Double,
    val avgRepairIters: Double,
    val totalPromptTokens: Int,
    val totalCompletionTokens: Int,
    val costPerSuccess: Double = 0.0,
    val avgLoc: Double,
    val taskResults: List<TaskRunMetric>,
    val failureBreakdown: Map<AttemptStatus, Int> = emptyMap()
)
