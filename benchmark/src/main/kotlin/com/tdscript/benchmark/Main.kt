package com.tdscript.benchmark

import com.tdscript.studio.agent.OpenAiCompatibleLlmClient
import java.io.File

fun main(args: Array<String>) {
    val baseUrl = System.getenv("LLM_BASE_URL")
        ?: args.find { it.startsWith("--url=") }?.substringAfter("--url=")
        ?: "http://127.0.0.1:11434/v1"

    val model = System.getenv("LLM_MODEL")
        ?: args.find { it.startsWith("--model=") }?.substringAfter("--model=")
        ?: "qwen2.5:3b"

    val apiKey = System.getenv("LLM_API_KEY")
        ?: args.find { it.startsWith("--key=") }?.substringAfter("--key=")
        ?: "ollama"

    val reportPath = args.find { it.startsWith("--out=") }?.substringAfter("--out=")
        ?: "BENCHMARK_REPORT.md"

    println("================================================================================")
    println("🚀 TDscript Global LLM Automation Benchmark (3B–9B)")
    println("================================================================================")
    println("Endpoint : $baseUrl")
    println("Model    : $model")
    println("Output   : $reportPath")
    println("--------------------------------------------------------------------------------")

    val client = OpenAiCompatibleLlmClient(
        baseUrl = baseUrl,
        modelName = model,
        apiKey = apiKey
    )

    val runner = BenchmarkRunner(client)
    val summaries = runner.runBenchmark()

    val markdown = runner.generateReportMarkdown(summaries)
    File(reportPath).writeText(markdown)

    println("--------------------------------------------------------------------------------")
    println("✅ Benchmark complete! Report saved to $reportPath")
    println(markdown)
}
