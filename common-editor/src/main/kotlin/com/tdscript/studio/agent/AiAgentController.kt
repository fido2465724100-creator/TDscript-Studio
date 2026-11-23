package com.tdscript.studio.agent

import com.tdscript.core.Interpreter
import com.tdscript.core.Lexer
import com.tdscript.core.Parser
import com.tdscript.studio.ai.CompilerErrorDiagnostic
import com.tdscript.studio.ai.StudioAiDiagnostics
import com.tdscript.studio.sandbox.AiHardwareSandbox

data class AgentStepLog(
    val iteration: Int,
    val promptSent: String,
    val modelResponse: String,
    val codeExtracted: String,
    val success: Boolean,
    val errorDiagnostic: String? = null
)

data class AgentRunResult(
    val success: Boolean,
    val finalCode: String,
    val iterations: Int,
    val steps: List<AgentStepLog>,
    val stdout: List<String>,
    val diagnostics: List<String>,
    val error: String? = null
)

/**
 * Автономный контроллер ИИ-агента (Agentic Loop):
 * Реализует замкнутый цикл Generate -> Test in Sandbox -> Diagnostic Capture -> Auto-Fix.
 */
class AiAgentController(
    val sandbox: AiHardwareSandbox,
    val llmClient: LlmClient,
    val maxFixAttempts: Int = 3
) {

    private val diagnosticsService = StudioAiDiagnostics()

    fun solveTask(userTask: String): AgentRunResult {
        val steps = mutableListOf<AgentStepLog>()
        val messages = mutableListOf<ChatMessage>()

        // 1. Discovery: формируем снимок окружения и системный промпт
        val systemPrompt = buildSystemPrompt()
        messages.add(ChatMessage("system", systemPrompt))
        messages.add(ChatMessage("user", userTask))

        var currentCode = ""
        var iteration = 0

        while (iteration < maxFixAttempts) {
            iteration++

            // 2. Генерация / исправление кода
            val promptSent = messages.last().content
            val response = try {
                llmClient.chat(messages)
            } catch (e: Exception) {
                return AgentRunResult(
                    success = false,
                    finalCode = currentCode,
                    iterations = iteration,
                    steps = steps,
                    stdout = sandbox.stdoutLines.toList(),
                    diagnostics = sandbox.diagnosticLines.toList(),
                    error = "LLM Communication failure: ${e.message}"
                )
            }

            val extractedCode = extractCode(response.content)
            currentCode = extractedCode

            // 3. Проверка в безопасной аппаратной песочнице
            sandbox.stdoutLines.clear()
            sandbox.diagnosticLines.clear()

            val runError = executeInSandbox(extractedCode)

            if (runError == null) {
                // Успешный запуск!
                steps.add(
                    AgentStepLog(
                        iteration = iteration,
                        promptSent = promptSent,
                        modelResponse = response.content,
                        codeExtracted = extractedCode,
                        success = true
                    )
                )
                return AgentRunResult(
                    success = true,
                    finalCode = extractedCode,
                    iterations = iteration,
                    steps = steps,
                    stdout = sandbox.stdoutLines.toList(),
                    diagnostics = sandbox.diagnosticLines.toList()
                )
            }

            // 4. Ошибка обнаружена: формируем JSON-диагностику для автоисправления
            val diagnostic = buildDiagnostic(runError, extractedCode)
            val diagnosticJson = diagnostic.toJson()

            steps.add(
                AgentStepLog(
                    iteration = iteration,
                    promptSent = promptSent,
                    modelResponse = response.content,
                    codeExtracted = extractedCode,
                    success = false,
                    errorDiagnostic = diagnosticJson
                )
            )

            // Добавляем ответ ассистента и требование исправления в контекст диалога
            messages.add(ChatMessage("assistant", response.content))
            messages.add(
                ChatMessage(
                    "user",
                    """
                    Execution in AiHardwareSandbox failed!
                    Diagnostic Error:
                    $diagnosticJson

                    Please analyze this error and provide the complete fixed TDscript code inside a ```tdscript code block.
                    """.trimIndent()
                )
            )
        }

        return AgentRunResult(
            success = false,
            finalCode = currentCode,
            iterations = iteration,
            steps = steps,
            stdout = sandbox.stdoutLines.toList(),
            diagnostics = sandbox.diagnosticLines.toList(),
            error = "Max auto-fix attempts ($maxFixAttempts) reached without resolving error"
        )
    }

    private fun executeInSandbox(code: String): String? {
        try {
            val lexer = Lexer(code)
            val tokens = lexer.tokenize()
            val parser = Parser(tokens)
            val program = parser.parse()
            val interpreter = Interpreter(host = sandbox)
            interpreter.execute(program)
        } catch (e: Throwable) {
            return e.message ?: e.toString()
        }

        // Проверяем нарушения прав доступа и Scope в журнале песочницы
        val scopeViolation = sandbox.diagnosticLines.firstOrNull { it.contains("SCOPE_VIOLATION") }
        if (scopeViolation != null) {
            return scopeViolation
        }

        val permDenied = sandbox.diagnosticLines.firstOrNull { it.contains("PERMISSION_DENIED") }
        if (permDenied != null) {
            return permDenied
        }

        val eStop = sandbox.diagnosticLines.firstOrNull { it.contains("EMERGENCY_STOP_TRIGGERED") }
        if (eStop != null) {
            return "FailSafe Watchdog: EMERGENCY_STOP_TRIGGERED"
        }

        return null
    }

    private fun buildDiagnostic(rawError: String, code: String): CompilerErrorDiagnostic {
        return when {
            rawError.contains("SCOPE_VIOLATION") -> {
                CompilerErrorDiagnostic(
                    errorCode = "SCOPE_VIOLATION",
                    file = "script.tds",
                    line = findErrorLine(code, rawError),
                    column = 1,
                    message = rawError,
                    expected = "Access only to allowed addresses/registers in CapabilityScope",
                    actual = rawError,
                    suggestion = "Consult Hardware Discovery. For I2C use Bosch BME280 address 0x76 (118). For Modbus use registers 100-200."
                )
            }
            rawError.contains("TDS_E105_LSTRING_CONCAT_PROHIBITED") -> {
                CompilerErrorDiagnostic(
                    errorCode = "TDS_E105",
                    file = "script.tds",
                    line = findErrorLine(code, "+"),
                    column = 1,
                    message = rawError,
                    expected = "LString format with parameters: L\"KEY:Text {param}\"",
                    actual = "Direct '+' concatenation with LString",
                    suggestion = "Do not use + with LString. Use parameter substitution or plain string literals."
                )
            }
            rawError.contains("TDS_E106_STEP_LIMIT_EXCEEDED") -> {
                CompilerErrorDiagnostic(
                    errorCode = "TDS_E106",
                    file = "script.tds",
                    line = findErrorLine(code, "while"),
                    column = 1,
                    message = rawError,
                    expected = "Loops must terminate within step limit",
                    actual = "Execution step limit (10,000,000) exceeded",
                    suggestion = "Check loop termination condition or update step counter."
                )
            }
            rawError.contains("PERMISSION_DENIED") -> {
                CompilerErrorDiagnostic(
                    errorCode = "PERMISSION_DENIED",
                    file = "script.tds",
                    line = 1,
                    column = 1,
                    message = rawError,
                    expected = "Granted capability mask in HostSPI",
                    actual = rawError,
                    suggestion = "Request required capability bit or avoid forbidden subsystem call."
                )
            }
            else -> {
                CompilerErrorDiagnostic(
                    errorCode = "SYNTAX_OR_RUNTIME_ERROR",
                    file = "script.tds",
                    line = 1,
                    column = 1,
                    message = rawError,
                    expected = "Valid TDscript Core 1.1 syntax",
                    actual = rawError,
                    suggestion = "Check language directive, brackets, variable declarations and API methods."
                )
            }
        }
    }

    private fun findErrorLine(code: String, hint: String): Int {
        val lines = code.lines()
        val match = if (hint.contains("0x")) {
            lines.indexOfFirst { it.contains("read", true) || it.contains("write", true) }
        } else {
            lines.indexOfFirst { it.contains(hint, true) }
        }
        return if (match != -1) match + 1 else 1
    }

    private fun buildSystemPrompt(): String {
        val adapters = sandbox.probe()
        val activeCaps = listOf("I2C_READ", "I2C_WRITE", "PLC_READ", "PLC_WRITE", "SERIAL_RAW")
        val scope = sandbox.capabilityScope
        val allowedI2c = scope.allowedI2cAddresses.joinToString { "0x" + Integer.toHexString(it).uppercase() }
        val allowedModbus = scope.allowedModbusRegisterRanges.joinToString { "${it.first}..${it.last}" }
        val scopeSummary = "I2C: [${allowedI2c.ifEmpty { "ALL" }}], Modbus: [${allowedModbus.ifEmpty { "ALL" }}]"

        val discoveryJson = diagnosticsService.generateDiscoveryJson(adapters, activeCaps, scopeSummary)

        return """
            You are an expert autonomous embedded automation and robotics engineer generating TDscript Core 1.1 code.

            Hardware Discovery & Environment:
            $discoveryJson

            TDscript Core 1.1 Quick Syntax Reference:
            - Optional language header: 'language: ru;' or canonical English keywords.
            - Variable declarations: 'let a = 10', 'var b = 20'.
            - Control flow: 'if (cond) { ... } else { ... }', 'while (cond) { ... }'.
            - I2C Bus API:
              - I2C.read(address, register, length) -> ByteArray? (e.g. I2C.read(118, 208, 1) for BME280 @ 0x76)
              - I2C.write(address, register, data) -> Boolean
              CRITICAL: Only use granted I2C addresses (0x76 = 118). Accessing other addresses triggers SCOPE_VIOLATION!
            - Modbus PLC API:
              - PLC.modbusRead("127.0.0.1", register) -> Int (registers 100=boiler temp, 101=pump)
              - PLC.modbusWrite("127.0.0.1", register, value) -> Boolean
            - Watchdog & Safety:
              - WATCHDOG.feed()
              - WATCHDOG.emergencyStop()
            - Console:
              - print(value)
            - Localization Strings:
              - L"KEY:Default Text"
              CRITICAL: Direct '+' concatenation with LString is strictly prohibited (TDS_E105)!

            Instruction:
            Return the complete, working TDscript code strictly inside a ```tdscript ... ``` block.
        """.trimIndent()
    }

    companion object {
        fun extractCode(text: String): String {
            val tdscriptBlock = Regex("""```tdscript\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE).find(text)
            if (tdscriptBlock != null) {
                return tdscriptBlock.groupValues[1].trim()
            }
            val genericBlock = Regex("""```\s*([\s\S]*?)```""").find(text)
            if (genericBlock != null) {
                return genericBlock.groupValues[1].trim()
            }
            return text.trim()
        }
    }
}
