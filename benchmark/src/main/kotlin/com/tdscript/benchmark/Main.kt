package com.tdscript.benchmark

import com.tdscript.studio.agent.OpenAiCompatibleLlmClient
import java.io.File

fun main(args: Array<String>) {
    val baseUrl = System.getenv("LLM_BASE_URL")
        ?: args.find { it.startsWith("--url=") }?.substringAfter("--url=")
        ?: "http://127.0.0.1:8765/v1"

    val model = System.getenv("LLM_MODEL")
        ?: args.find { it.startsWith("--model=") }?.substringAfter("--model=")
        ?: "qwen3.5-4b-unloop"

    val apiKey = System.getenv("LLM_API_KEY")
        ?: args.find { it.startsWith("--key=") }?.substringAfter("--key=")
        ?: ""

    val reportPath = args.find { it.startsWith("--out=") }?.substringAfter("--out=")
        ?: "/home/anton/TDscript-Studio/BENCHMARK_REPORT_TEST4.md"

    val maxTokens = args.find { it.startsWith("--max-tokens=") }?.substringAfter("--max-tokens=")?.toIntOrNull()
        ?: 1000

    val failuresLogPath = args.find { it.startsWith("--failures-out=") }?.substringAfter("--failures-out=")
        ?: "/home/anton/TDscript-Studio/benchmark_failures_test4.log"

    val tracePath = args.find { it.startsWith("--trace-out=") }?.substringAfter("--trace-out=")
        ?: "/home/anton/TDscript-Studio/benchmark_full_trace_test4.jsonl"

    val enableGbnf = args.any { it == "--use-gbnf" }

    val tasksArg = args.find { it.startsWith("--tasks=") }?.substringAfter("--tasks=")
    val selectedTasks = if (tasksArg != null && tasksArg != "all") {
        val ids = tasksArg.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        BenchmarkTask.ALL_TASKS.filter { it.id in ids }
    } else {
        BenchmarkTask.ALL_TASKS
    }

    val profilesArg = args.find { it.startsWith("--profiles=") }?.substringAfter("--profiles=")
    val selectedProfiles = if (profilesArg != null && profilesArg != "all") {
        val ids = profilesArg.split(",").map { it.trim().lowercase() }.toSet()
        LanguageProfile.ALL_PROFILES.filter { it.id in ids }
    } else {
        LanguageProfile.ALL_PROFILES
    }

    println("================================================================================")
    println("🚀 TDscript Global LLM Automation Benchmark (3B–9B)")
    println("================================================================================")
    println("Endpoint : $baseUrl")
    println("Model    : $model")
    println("MaxTokens: $maxTokens")
    println("Tasks    : ${selectedTasks.size} tasks (${selectedTasks.map { it.id }})")
    println("Profiles : ${selectedProfiles.size} profiles (${selectedProfiles.map { it.id }})")
    println("Output   : $reportPath")
    println("Failures : $failuresLogPath")
    println("Trace    : $tracePath")
    println("GBNF     : $enableGbnf")
    println("--------------------------------------------------------------------------------")

    val client = OpenAiCompatibleLlmClient(
        baseUrl = baseUrl,
        modelName = model,
        apiKey = apiKey,
        maxTokens = maxTokens
    )

    val progressLabel = args.find { it.startsWith("--progress-label=") }?.substringAfter("--progress-label=")
        ?: "TEST #4"

    val title = args.find { it.startsWith("--title=") }?.substringAfter("--title=")
        ?: "TDscript Global LLM Benchmark — Тест №4 (Test #4)"

    val runner = BenchmarkRunner(
        llmClient = client,
        tasks = selectedTasks,
        profiles = selectedProfiles,
        failuresLogFile = File(failuresLogPath),
        traceJsonlFile = File(tracePath),
        enableGbnf = enableGbnf,
        progressLabel = progressLabel
    )
    val summaries = runner.runBenchmark()

    val markdown = runner.generateReportMarkdown(summaries, title)
    File(reportPath).writeText(markdown)

    println("--------------------------------------------------------------------------------")
    println("✅ Benchmark complete! Report saved to $reportPath")
    println(markdown)
}
