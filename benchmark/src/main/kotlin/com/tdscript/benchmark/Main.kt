package com.tdscript.benchmark

import com.tdscript.studio.agent.OpenAiCompatibleLlmClient
import java.io.File

fun main(args: Array<String>) {
    val baseUrl = System.getenv("LLM_BASE_URL")
        ?: args.find { it.startsWith("--url=") }?.substringAfter("--url=")
        ?: "http://127.0.0.1:8765/v1"

    val model = System.getenv("LLM_MODEL")
        ?: args.find { it.startsWith("--model=") }?.substringAfter("--model=")
        ?: "qwen3.5-9b"

    val apiKey = System.getenv("LLM_API_KEY")
        ?: args.find { it.startsWith("--key=") }?.substringAfter("--key=")
        ?: ""

    val reportPath = args.find { it.startsWith("--out=") }?.substringAfter("--out=")
        ?: "BENCHMARK_REPORT.md"

    val maxTokens = args.find { it.startsWith("--max-tokens=") }?.substringAfter("--max-tokens=")?.toIntOrNull()
        ?: 350

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
    println("Tasks    : ${selectedTasks.size} tasks (${selectedTasks.map { it.id }})")
    println("Profiles : ${selectedProfiles.size} profiles (${selectedProfiles.map { it.id }})")
    println("Output   : $reportPath")
    println("--------------------------------------------------------------------------------")

    val client = OpenAiCompatibleLlmClient(
        baseUrl = baseUrl,
        modelName = model,
        apiKey = apiKey,
        maxTokens = maxTokens
    )

    val runner = BenchmarkRunner(
        llmClient = client,
        tasks = selectedTasks,
        profiles = selectedProfiles
    )
    val summaries = runner.runBenchmark()

    val markdown = runner.generateReportMarkdown(summaries)
    File(reportPath).writeText(markdown)

    println("--------------------------------------------------------------------------------")
    println("✅ Benchmark complete! Report saved to $reportPath")
    println(markdown)
}
