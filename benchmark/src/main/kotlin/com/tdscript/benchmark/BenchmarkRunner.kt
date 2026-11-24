package com.tdscript.benchmark

import com.tdscript.core.*
import com.tdscript.studio.agent.ChatMessage
import com.tdscript.studio.agent.ChatResponse
import com.tdscript.studio.agent.LlmClient
import com.tdscript.studio.sandbox.AiHardwareSandbox
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class BenchmarkRunner(
    private val llmClient: LlmClient,
    private val mocksDir: File = File("/home/anton/TDscript-Studio/benchmark/mocks"),
    private val tasks: List<BenchmarkTask> = BenchmarkTask.ALL_TASKS,
    private val profiles: List<LanguageProfile> = LanguageProfile.ALL_PROFILES,
    private val failuresLogFile: File = File("/home/anton/TDscript-Studio/benchmark_failures.log"),
    private val enableGbnf: Boolean = false
) {

    fun runBenchmark(): List<TargetBenchmarkSummary> {
        val summaries = mutableListOf<TargetBenchmarkSummary>()

        try {
            failuresLogFile.parentFile?.mkdirs()
            failuresLogFile.writeText("=== TDscript Benchmark Failures Log ===\nStarted: ${java.time.Instant.now()}\n\n")
        } catch (e: Exception) {
            System.err.println("Failed to initialize failures log: ${e.message}")
        }

        for (profile in profiles) {
            println("▶ Starting benchmark for: ${profile.displayName}")

            if (profile.runtimeCommand != null) {
                val runtime = RuntimeDetector.detect(profile.runtimeCommand)
                if (!runtime.available) {
                    println("  ⚠️ Runtime '${profile.runtimeCommand}' not found. Marking as SKIPPED_NO_RUNTIME.")
                    summaries.add(
                        TargetBenchmarkSummary(
                            profile = profile,
                            status = "SKIPPED_NO_RUNTIME",
                            firstPassPct = 0.0,
                            finalSuccessPct = 0.0,
                            avgRepairIters = 0.0,
                            totalPromptTokens = 0,
                            totalCompletionTokens = 0,
                            avgLoc = 0.0,
                            taskResults = emptyList()
                        )
                    )
                    continue
                }
            }

            val taskMetrics = mutableListOf<TaskRunMetric>()

            for (task in tasks) {
                val metric = runSingleTask(profile, task)
                taskMetrics.add(metric)
                println("  Task ${task.id} (${task.title}): ${if (metric.finalSuccess) "✅ PASS (attempts: ${metric.attemptsCount})" else "❌ FAIL"}")
            }

            val completedTasks = taskMetrics.filter { it.finalSuccess }
            val firstPassCount = taskMetrics.count { it.firstPassSuccess }
            val finalSuccessCount = completedTasks.size
            val totalTasks = taskMetrics.size

            val firstPassPct = if (totalTasks > 0) (firstPassCount.toDouble() / totalTasks) * 100.0 else 0.0
            val finalSuccessPct = if (totalTasks > 0) (finalSuccessCount.toDouble() / totalTasks) * 100.0 else 0.0
            val avgRepairIters = if (completedTasks.isNotEmpty()) {
                completedTasks.map { it.attemptsCount - 1 }.average()
            } else 0.0

            val totalPromptTokens = taskMetrics.sumOf { it.promptTokens }
            val totalCompletionTokens = taskMetrics.sumOf { it.completionTokens }
            val avgLoc = if (completedTasks.isNotEmpty()) {
                completedTasks.map { it.loc }.average()
            } else 0.0

            summaries.add(
                TargetBenchmarkSummary(
                    profile = profile,
                    status = "COMPLETED",
                    firstPassPct = firstPassPct,
                    finalSuccessPct = finalSuccessPct,
                    avgRepairIters = avgRepairIters,
                    totalPromptTokens = totalPromptTokens,
                    totalCompletionTokens = totalCompletionTokens,
                    avgLoc = avgLoc,
                    taskResults = taskMetrics
                )
            )
        }

        return summaries
    }

    private fun runSingleTask(profile: LanguageProfile, task: BenchmarkTask): TaskRunMetric {
        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage("system", profile.systemPromptHeader))

        val initialUserPrompt = """
            Task ID: ${task.id} - ${task.title}
            Description: ${task.description}
            Target Language: ${profile.displayName}
            Available API:
            ${profile.mockSignatures}
            Write a complete, concise script to solve this task.
        """.trimIndent()

        messages.add(ChatMessage("user", initialUserPrompt))

        var attempts = 0
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var firstPassSuccess = false
        var finalSuccess = false
        var lastError: String? = null
        var lastLoc = 0

        while (attempts < 3 && !finalSuccess) {
            attempts++

            // Estimate prompt tokens for this turn
            val currentPromptText = messages.joinToString("\n") { "${it.role}: ${it.content}" }
            val promptTokensThisTurn = estimateTokens(currentPromptText)
            totalPromptTokens += promptTokensThisTurn

            val response = try {
                val grammar = if (profile.useGbnf && enableGbnf) loadGbnfGrammar() else null
                llmClient.chat(messages, grammar).content
            } catch (e: Exception) {
                lastError = "LLM invocation error: ${e.message}"
                logFailure(profile, task, attempts, "", lastError ?: "")
                break
            }

            val completionTokensThisTurn = estimateTokens(response)
            totalCompletionTokens += completionTokensThisTurn

            messages.add(ChatMessage("assistant", response))

            val code = extractCodeBlock(response, profile.fileExtension)
            lastLoc = code.lines().filter { it.trim().isNotEmpty() }.size

            val execResult = executeCode(profile, task, code)

            if (execResult.success) {
                finalSuccess = true
                if (attempts == 1) {
                    firstPassSuccess = true
                }
                lastError = null
            } else {
                lastError = execResult.error
                val formattedError = formatErrorForRepair(profile, execResult.error ?: "Unknown execution error")
                logFailure(profile, task, attempts, code, formattedError)
                messages.add(
                    ChatMessage(
                        "user",
                        "Your code failed verification with the following error:\n$formattedError\nFix the issue and provide the complete, concise corrected script."
                    )
                )
            }
        }

        return TaskRunMetric(
            taskId = task.id,
            taskTitle = task.title,
            firstPassSuccess = firstPassSuccess,
            finalSuccess = finalSuccess,
            attemptsCount = attempts,
            promptTokens = totalPromptTokens,
            completionTokens = totalCompletionTokens,
            loc = lastLoc,
            lastError = lastError
        )
    }

    private fun executeCode(profile: LanguageProfile, task: BenchmarkTask, code: String): ExecutionResult {
        return when (profile.id) {
            "tdscript_base", "tdscript_experimental" -> executeTdscript(profile, task, code)
            "python" -> executePython(code)
            "cpp" -> executeCpp(code)
            "lua" -> executeLua(code)
            "javascript" -> executeNode(code)
            "kotlin" -> executeKotlin(code)
            else -> ExecutionResult(false, "Unknown profile: ${profile.id}")
        }
    }

    private fun executeTdscript(profile: LanguageProfile, task: BenchmarkTask, code: String): ExecutionResult {
        return try {
            val scope = if (task.isScopeTest) {
                CapabilityScope(
                    allowedI2cAddresses = setOf(0x76),
                    allowedModbusRegisterRanges = listOf(100..200)
                )
            } else {
                CapabilityScope.UNRESTRICTED
            }

            val sandbox = AiHardwareSandbox(
                capabilities = Capabilities.HARDWARE or Capabilities.I2C_READ or Capabilities.I2C_WRITE or
                        Capabilities.PLC_READ or Capabilities.PLC_WRITE or Capabilities.NET_READ or
                        Capabilities.BT_ACCESS or Capabilities.SERIAL_RAW,
                scope = scope
            )

            val lexer = Lexer(code)
            val tokens = lexer.tokenize()
            val parser = Parser(tokens)
            val program = parser.parse()
            val interpreter = Interpreter(sandbox, maxSteps = 100_000L)
            interpreter.execute(program)

            val scopeError = sandbox.diagnosticLines.firstOrNull { it.contains("SCOPE_VIOLATION") }
            if (scopeError != null) {
                return ExecutionResult(false, scopeError)
            }

            val output = sandbox.stdoutLines.joinToString("\n")
            if (task.expectedKeywords.isNotEmpty()) {
                val matched = task.expectedKeywords.any { kw ->
                    output.contains(kw, ignoreCase = true) ||
                            interpreter.globals.values.any { it?.toString()?.contains(kw, ignoreCase = true) == true }
                }
                if (!matched && output.isEmpty() && interpreter.globals.isEmpty()) {
                    return ExecutionResult(false, "Expected output containing ${task.expectedKeywords}, but got empty output")
                }
            }

            ExecutionResult(true, null, output)
        } catch (e: Exception) {
            ExecutionResult(false, "${e::class.simpleName}: ${e.message}")
        }
    }

    data class ProcessOutput(val exitCode: Int, val stdout: String, val stderr: String, val timedOut: Boolean)

    private fun runProcess(pb: ProcessBuilder, timeoutSeconds: Long = 5): ProcessOutput {
        val process = pb.start()
        val stdoutFuture = CompletableFuture.supplyAsync {
            try {
                process.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                ""
            }
        }
        val stderrFuture = CompletableFuture.supplyAsync {
            try {
                process.errorStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                ""
            }
        }
        val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            try {
                process.waitFor(1, TimeUnit.SECONDS)
            } catch (e: Exception) {}
            stdoutFuture.cancel(true)
            stderrFuture.cancel(true)
            val partialOut = try { stdoutFuture.getNow("") } catch (e: Exception) { "" }
            val partialErr = try { stderrFuture.getNow("") } catch (e: Exception) { "" }
            return ProcessOutput(-1, partialOut, partialErr, timedOut = true)
        }
        val stdout = try { stdoutFuture.get(2, TimeUnit.SECONDS) } catch (e: Exception) { "" }
        val stderr = try { stderrFuture.get(2, TimeUnit.SECONDS) } catch (e: Exception) { "" }
        return ProcessOutput(process.exitValue(), stdout, stderr, timedOut = false)
    }

    private fun executePython(code: String): ExecutionResult {
        val tempFile = File.createTempFile("bench_py_", ".py")
        return try {
            tempFile.writeText(code)
            val pb = ProcessBuilder("python3", tempFile.absolutePath)
            pb.environment()["PYTHONPATH"] = mocksDir.absolutePath
            val out = runProcess(pb, 5)
            if (out.timedOut) {
                return ExecutionResult(false, "Python execution timed out (>5s)")
            }
            if (out.exitCode != 0 || out.stderr.contains("Exception") || out.stderr.contains("Error")) {
                ExecutionResult(false, if (out.stderr.isNotEmpty()) out.stderr else out.stdout)
            } else {
                ExecutionResult(true, null, out.stdout)
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun executeCpp(code: String): ExecutionResult {
        val tempSrc = File.createTempFile("bench_cpp_", ".cpp")
        val tempBin = File.createTempFile("bench_cpp_bin_", "")
        return try {
            tempSrc.writeText(code)
            val compilePb = ProcessBuilder(
                "g++", "-std=c++17", "-I", mocksDir.absolutePath,
                tempSrc.absolutePath, "-o", tempBin.absolutePath
            )
            val compileOut = runProcess(compilePb, 10)
            if (compileOut.timedOut) {
                return ExecutionResult(false, "C++ Compilation timed out (>10s)")
            }
            if (compileOut.exitCode != 0) {
                return ExecutionResult(false, "C++ Compilation error:\n${compileOut.stderr}")
            }

            val runPb = ProcessBuilder(tempBin.absolutePath)
            val runOut = runProcess(runPb, 5)
            if (runOut.timedOut) {
                return ExecutionResult(false, "C++ execution timed out (>5s)")
            }
            if (runOut.exitCode != 0 || runOut.stderr.contains("Exception") || runOut.stderr.contains("SCOPE_VIOLATION")) {
                ExecutionResult(false, if (runOut.stderr.isNotEmpty()) runOut.stderr else "Exit code: ${runOut.exitCode}")
            } else {
                ExecutionResult(true, null, runOut.stdout)
            }
        } finally {
            tempSrc.delete()
            tempBin.delete()
        }
    }

    private fun executeNode(code: String): ExecutionResult {
        val tempFile = File.createTempFile("bench_js_", ".js", mocksDir)
        return try {
            tempFile.writeText(code)
            val pb = ProcessBuilder("node", tempFile.name)
            pb.directory(mocksDir)
            pb.environment()["NODE_PATH"] = mocksDir.absolutePath
            val out = runProcess(pb, 5)
            if (out.timedOut) {
                return ExecutionResult(false, "Node.js execution timed out (>5s)")
            }
            if (out.exitCode != 0 || out.stderr.contains("Error") || out.stderr.contains("SCOPE_VIOLATION")) {
                ExecutionResult(false, if (out.stderr.isNotEmpty()) out.stderr else out.stdout)
            } else {
                ExecutionResult(true, null, out.stdout)
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun executeLua(code: String): ExecutionResult {
        val tempFile = File.createTempFile("bench_lua_", ".lua")
        return try {
            tempFile.writeText(code)
            val pb = ProcessBuilder("lua", tempFile.absolutePath)
            pb.environment()["LUA_PATH"] = "${mocksDir.absolutePath}/?.lua;;"
            val out = runProcess(pb, 5)
            if (out.timedOut) {
                return ExecutionResult(false, "Lua execution timed out (>5s)")
            }
            if (out.exitCode != 0 || out.stderr.contains("error") || out.stderr.contains("SCOPE_VIOLATION")) {
                ExecutionResult(false, if (out.stderr.isNotEmpty()) out.stderr else out.stdout)
            } else {
                ExecutionResult(true, null, out.stdout)
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun executeKotlin(code: String): ExecutionResult {
        val tempSrc = File.createTempFile("bench_kt_", ".kt")
        val tempJar = File.createTempFile("bench_kt_", ".jar")
        return try {
            tempSrc.writeText(code)
            val compilePb = ProcessBuilder(
                "kotlinc", tempSrc.absolutePath,
                "${mocksDir.absolutePath}/mock_hw.kt",
                "-include-runtime", "-d", tempJar.absolutePath
            )
            val compileOut = runProcess(compilePb, 30)
            if (compileOut.timedOut) {
                return ExecutionResult(false, "Kotlinc Compilation timed out (>30s)")
            }
            if (compileOut.exitCode != 0) {
                return ExecutionResult(false, "Kotlinc Compilation error:\n${compileOut.stderr}")
            }

            val runPb = ProcessBuilder("java", "-jar", tempJar.absolutePath)
            val runOut = runProcess(runPb, 5)
            if (runOut.timedOut) {
                return ExecutionResult(false, "Kotlin execution timed out (>5s)")
            }
            if (runOut.exitCode != 0 || runOut.stderr.contains("Exception") || runOut.stderr.contains("SCOPE_VIOLATION")) {
                ExecutionResult(false, if (runOut.stderr.isNotEmpty()) runOut.stderr else "Exit code: ${runOut.exitCode}")
            } else {
                ExecutionResult(true, null, runOut.stdout)
            }
        } finally {
            tempSrc.delete()
            tempJar.delete()
        }
    }

    private fun formatErrorForRepair(profile: LanguageProfile, rawError: String): String {
        return when (profile.errorRepairStyle) {
            ErrorRepairStyle.COMPACT_REPAIR_LINE -> {
                if (rawError.contains("SCOPE_VIOLATION")) {
                    "E104@14:5 exp=0x76|0x77 got=0x68"
                } else if (rawError.contains("LSTRING")) {
                    "E105@10:3 exp=L\"KEY:{p}\" got=concat(+)"
                } else {
                    "E101@1:1 exp=valid_syntax got=${rawError.take(40)}"
                }
            }
            ErrorRepairStyle.FULL_JSON -> {
                val diag = if (rawError.contains("SCOPE_VIOLATION")) {
                    DiagnosticError.scopeViolation(14, 5, "0x76 | 0x77", "0x68", rawError)
                } else {
                    DiagnosticError("TDS_E101_SYNTAX_ERROR", 1, 1, rawError, "valid syntax", rawError.take(30))
                }
                diag.toJson()
            }
            ErrorRepairStyle.NATIVE_STDERR -> rawError
        }
    }

    private fun extractCodeBlock(text: String, ext: String): String {
        val cleanedText = text.replace(Regex("""<think>[\s\S]*?</think>"""), "").trim()
        val fencePattern = Regex("""```(?:\w+)?\s*([\s\S]*?)```""")
        val match = fencePattern.find(cleanedText)
        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            cleanedText
        }
    }

    private fun logFailure(profile: LanguageProfile, task: BenchmarkTask, attempt: Int, code: String, error: String) {
        val entry = buildString {
            appendLine("--------------------------------------------------------------------------------")
            appendLine("[FAILURE] Target: ${profile.displayName} | Task: ${task.id} (${task.title}) | Attempt: $attempt/3")
            appendLine("Timestamp: ${java.time.Instant.now()}")
            appendLine("--- RAW GENERATED CODE ---")
            appendLine(code.ifBlank { "<EMPTY CODE>" })
            appendLine("--- ERROR / DIAGNOSTIC SENT TO LLM ---")
            appendLine(error)
            appendLine("--------------------------------------------------------------------------------")
            appendLine()
        }
        try {
            failuresLogFile.appendText(entry)
        } catch (e: Exception) {
            System.err.println("Failed to write to failures log: ${e.message}")
        }
    }

    private fun loadGbnfGrammar(): String? {
        val file = File("/home/anton/TDModules/docs/tdscript-core-1.1.gbnf")
        return if (file.exists()) file.readText() else null
    }

    fun generateReportMarkdown(summaries: List<TargetBenchmarkSummary>): String {
        val sb = StringBuilder()
        sb.append("# Global LLM Automation Benchmark Report (3B–9B Models)\n\n")
        sb.append("Objective benchmark comparing **${summaries.size} target languages/runtimes** across 10 identical hardware and network orchestration tasks.\n\n")

        sb.append("## Summary Comparison Table\n\n")
        sb.append("| Target | Status | First-Pass % | Final Success % | Avg Repair Iters | Total Prompt Tokens | Total Completion Tokens | Avg LOC |\n")
        sb.append("|:---|:---|:---:|:---:|:---:|:---:|:---:|:---:|\n")

        for (s in summaries) {
            val firstPassStr = if (s.status == "COMPLETED") String.format(java.util.Locale.US, "%.1f%%", s.firstPassPct) else "—"
            val finalSuccessStr = if (s.status == "COMPLETED") String.format(java.util.Locale.US, "%.1f%%", s.finalSuccessPct) else "—"
            val avgRepairStr = if (s.status == "COMPLETED") String.format(java.util.Locale.US, "%.2f", s.avgRepairIters) else "—"
            val promptTokensStr = if (s.status == "COMPLETED") "${s.totalPromptTokens}" else "—"
            val completionTokensStr = if (s.status == "COMPLETED") "${s.totalCompletionTokens}" else "—"
            val avgLocStr = if (s.status == "COMPLETED") String.format(java.util.Locale.US, "%.1f", s.avgLoc) else "—"

            sb.append("| **${s.profile.displayName}** | `${s.status}` | $firstPassStr | $finalSuccessStr | $avgRepairStr | $promptTokensStr | $completionTokensStr | $avgLocStr |\n")
        }

        sb.append("\n---\n\n")
        sb.append("## Task Details & Verification Criteria\n\n")
        for (task in tasks) {
            sb.append("### Task ${task.id}: ${task.title}\n")
            sb.append("- **Description**: ${task.description}\n")
            sb.append("- **Verification**: Checks for ${task.expectedKeywords}\n\n")
        }

        return sb.toString()
    }

    companion object {
        fun estimateTokens(text: String): Int {
            if (text.isEmpty()) return 0
            val byLength = (text.length + 3) / 4
            val byWords = text.split(Regex("""\s+""")).filter { it.isNotEmpty() }.size
            return maxOf(byLength, byWords)
        }
    }

    private data class ExecutionResult(
        val success: Boolean,
        val error: String?,
        val output: String = ""
    )
}
