package com.tdscript.benchmark

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
    val avgLoc: Double,
    val taskResults: List<TaskRunMetric>
)
