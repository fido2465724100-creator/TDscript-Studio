package com.tdscript.benchmark

import com.tdscript.studio.agent.ChatMessage
import com.tdscript.studio.agent.ChatResponse
import com.tdscript.studio.agent.LlmClient
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BenchmarkRunnerTest {

    private class MockBenchmarkLlm : LlmClient {
        var callCount = 0

        override fun chat(messages: List<ChatMessage>): ChatResponse {
            callCount++
            val userMsg = messages.lastOrNull { it.role == "user" }?.content ?: ""
            val sysMsg = messages.firstOrNull { it.role == "system" }?.content ?: ""

            // Test Auto-Repair for Task 10 or errors
            if (userMsg.contains("failed verification") || userMsg.contains("E104") || userMsg.contains("SCOPE_VIOLATION")) {
                val fixCode = when {
                    sysMsg.contains("Python") -> """
                        ```python
                        from mock_hw import I2C
                        data = I2C.read(0x76, 0xD0, 1)
                        print("Fixed: 0x76")
                        ```
                    """.trimIndent()
                    sysMsg.contains("Node.js") -> """
                        ```javascript
                        const { I2C } = require('./mock_hw');
                        const data = I2C.read(0x76, 0xD0, 1);
                        console.log("Fixed: 0x76");
                        ```
                    """.trimIndent()
                    sysMsg.contains("C++") -> """
                        ```cpp
                        #include "mock_hw.hpp"
                        int main() {
                            auto data = I2C::read(0x76, 0xD0, 1);
                            std::cout << "Fixed: 0x76\n";
                            return 0;
                        }
                        ```
                    """.trimIndent()
                    sysMsg.contains("Lua") -> """
                        ```lua
                        local hw = require("mock_hw")
                        local data = hw.I2C.read(0x76, 0xD0, 1)
                        print("Fixed: 0x76")
                        ```
                    """.trimIndent()
                    sysMsg.contains("Kotlin") -> """
                        ```kotlin
                        import com.tdscript.benchmark.mock.*
                        fun main() {
                            val data = I2C.read(0x76, 0xD0, 1)
                            println("Fixed: 0x76")
                        }
                        ```
                    """.trimIndent()
                    else -> """
                        ```tdscript
                        let chip = 0x76;
                        let data = I2C.read(chip, 0xD0, 1);
                        print("Fixed: 0x76");
                        ```
                    """.trimIndent()
                }
                return ChatResponse(fixCode)
            }

            // Task 10 initial attempt: deliberately violate scope to test auto-repair
            if (userMsg.contains("Task ID: 10")) {
                val badCode = when {
                    sysMsg.contains("Python") -> """
                        ```python
                        from mock_hw import I2C
                        data = I2C.read(0x68, 0xD0, 1)
                        ```
                    """.trimIndent()
                    sysMsg.contains("Node.js") -> """
                        ```javascript
                        const { I2C } = require('./mock_hw');
                        const data = I2C.read(0x68, 0xD0, 1);
                        ```
                    """.trimIndent()
                    sysMsg.contains("C++") -> """
                        ```cpp
                        #include "mock_hw.hpp"
                        int main() {
                            auto data = I2C::read(0x68, 0xD0, 1);
                            return 0;
                        }
                        ```
                    """.trimIndent()
                    sysMsg.contains("Lua") -> """
                        ```lua
                        local hw = require("mock_hw")
                        local data = hw.I2C.read(0x68, 0xD0, 1)
                        ```
                    """.trimIndent()
                    sysMsg.contains("Kotlin") -> """
                        ```kotlin
                        import com.tdscript.benchmark.mock.*
                        fun main() {
                            val data = I2C.read(0x68, 0xD0, 1)
                        }
                        ```
                    """.trimIndent()
                    else -> """
                        ```tdscript
                        let forbidden = 104; // 0x68
                        let data = I2C.read(forbidden, 208, 1);
                        ```
                    """.trimIndent()
                }
                return ChatResponse(badCode)
            }

            // Normal valid code generation per language
            val code = when {
                sysMsg.contains("Python") -> """
                    ```python
                    from mock_hw import I2C, PLC, WATCHDOG, NET, BT
                    chip = I2C.read(0x76, 0xD0, 1)
                    temp = PLC.modbus_read("127.0.0.1", 100)
                    WATCHDOG.feed()
                    print(f"96 245 100 101 POLL_OK true SYSTEM_OK sensor_01 192.168.1.50 24.5C 0x76 ALERT_TEMP: 24.5")
                    ```
                """.trimIndent()

                sysMsg.contains("Node.js") -> """
                    ```javascript
                    const { I2C, PLC, WATCHDOG, NET, BT } = require('./mock_hw');
                    const chip = I2C.read(0x76, 0xD0, 1);
                    const temp = PLC.modbusRead("127.0.0.1", 100);
                    WATCHDOG.feed();
                    console.log("96 245 100 101 POLL_OK true SYSTEM_OK sensor_01 192.168.1.50 24.5C 0x76 ALERT_TEMP: 24.5");
                    ```
                """.trimIndent()

                sysMsg.contains("C++") -> """
                    ```cpp
                    #include "mock_hw.hpp"
                    int main() {
                        auto chip = I2C::read(0x76, 0xD0, 1);
                        int temp = PLC::modbus_read("127.0.0.1", 100);
                        WATCHDOG::feed();
                        std::cout << "96 245 100 101 POLL_OK true SYSTEM_OK sensor_01 192.168.1.50 24.5C 0x76 ALERT_TEMP: 24.5\n";
                        return 0;
                    }
                    ```
                """.trimIndent()

                sysMsg.contains("Lua") -> """
                    ```lua
                    local hw = require("mock_hw")
                    local chip = hw.I2C.read(0x76, 0xD0, 1)
                    local temp = hw.PLC.modbus_read("127.0.0.1", 100)
                    hw.WATCHDOG.feed()
                    print("96 245 100 101 POLL_OK true SYSTEM_OK sensor_01 192.168.1.50 24.5C 0x76 ALERT_TEMP: 24.5")
                    ```
                """.trimIndent()

                sysMsg.contains("Kotlin") -> """
                    ```kotlin
                    import com.tdscript.benchmark.mock.*
                    fun main() {
                        val chip = I2C.read(0x76, 0xD0, 1)
                        val temp = PLC.modbusRead("127.0.0.1", 100)
                        WATCHDOG.feed()
                        println("96 245 100 101 POLL_OK true SYSTEM_OK sensor_01 192.168.1.50 24.5C 0x76 ALERT_TEMP: 24.5")
                    }
                    ```
                """.trimIndent()

                sysMsg.contains("AI-First") -> """
                    ```tdscript
                    let chipId = I2C.read(0x76, 0xD0, 1);
                    let temp = PLC.modbusRead("127.0.0.1", 100);
                    WATCHDOG.feed();
                    let alert = L"ALERT_TEMP:Temperature is {temp}";
                    print("96 245 100 101 POLL_OK true SYSTEM_OK sensor_01 192.168.1.50 24.5C 0x76 ALERT_TEMP 24.5");
                    ```
                """.trimIndent()

                else -> """
                    ```tdscript
                    let chipId = I2C.read(118, 208, 1);
                    let temp = PLC.modbusRead("127.0.0.1", 100);
                    WATCHDOG.feed();
                    let alert = L"ALERT_TEMP:Temperature is {temp}";
                    print("96 245 100 101 POLL_OK true SYSTEM_OK sensor_01 192.168.1.50 24.5C 0x76 ALERT_TEMP 24.5");
                    ```
                """.trimIndent()
            }
            return ChatResponse(code)
        }
    }

    @Test
    fun testDryRunExecutionAndReportGeneration() {
        val mockLlm = MockBenchmarkLlm()
        val dryRunReport = File("build/dry_run_report.md")
        val dryRunFailures = File("build/dry_run_failures.log")
        val dryRunTrace = File("build/dry_run_trace.jsonl")
        dryRunReport.parentFile?.mkdirs()

        val runner = BenchmarkRunner(
            llmClient = mockLlm,
            mocksDir = File("/home/anton/TDscript-Studio/benchmark/mocks"),
            failuresLogFile = dryRunFailures,
            traceJsonlFile = dryRunTrace
        )

        val summaries = runner.runBenchmark()

        // 7 profiles evaluated
        assertEquals(7, summaries.size)

        // All 7 profiles must be COMPLETED now that python3, g++, node, lua, kotlinc are installed
        for (s in summaries) {
            assertEquals("Profile ${s.profile.id} should be COMPLETED", "COMPLETED", s.status)
            assertEquals(100.0, s.finalSuccessPct, 0.01)
            assertEquals(90.0, s.firstPassPct, 0.01) // Task 10 triggered repair
            assertTrue(s.avgRepairIters > 0.0)
            assertTrue(s.totalPromptTokens > 0)
            assertTrue(s.totalCompletionTokens > 0)
            assertTrue(s.avgLoc > 0.0)
        }

        // Generate markdown report and verify contents
        val report = runner.generateReportMarkdown(summaries)
        assertTrue(report.contains("# Global LLM Automation Benchmark Report"))
        assertTrue(report.contains("Cost / Success"))
        assertTrue(report.contains("## Failure Breakdown"))
        assertTrue(report.contains("TDscript Base (v1.4.0)"))
        assertTrue(report.contains("TDscript Experimental (AI-First)"))
        assertTrue(report.contains("Python"))
        assertTrue(report.contains("C++ (Arduino/ESP32 style)"))
        assertTrue(report.contains("Lua"))
        assertTrue(report.contains("JavaScript (Node.js)"))
        assertTrue(report.contains("Kotlin"))

        // Save report strictly to build/dry_run_report.md (never overwriting root BENCHMARK_REPORT.md)
        dryRunReport.writeText(report)
        assertTrue(dryRunReport.exists())
        assertTrue(dryRunTrace.exists())
    }

    @Test
    fun testTruncatedMarkdownExtraction() {
        val runner = BenchmarkRunner(llmClient = MockBenchmarkLlm())
        val truncatedCpp = "```cpp\nint main() {\n    return 0;\n"
        val extracted = runner.extractCodeBlock(truncatedCpp, "cpp")
        assertFalse(extracted.startsWith("```"))
        assertTrue(extracted.contains("int main()"))
        assertTrue(extracted.contains("return 0;"))
    }

    @Test
    fun testCppRepairDoesNotReuseLinkerErrorAsCode() {
        val runner = BenchmarkRunner(llmClient = MockBenchmarkLlm())
        val linkerError = """
            /usr/bin/x86_64-linux-gnu-ld.bfd: (.text+0x1b): undefined reference to `main'
            collect2: error: ld returned 1 exit status
        """.trimIndent()
        // If LLM returned the linker error as text, extractCodeBlock should not make it valid C++ code
        val extracted = runner.extractCodeBlock(linkerError, "cpp")
        assertFalse(extracted.contains("int main()"))
        // And when given code inside fences, extractCodeBlock extracts only the code, ignoring errors
        val validCodeWithPreamble = """
            $linkerError
            ```cpp
            #include "mock_hw.hpp"
            int main() { return 0; }
            ```
        """.trimIndent()
        val extractedValid = runner.extractCodeBlock(validCodeWithPreamble, "cpp")
        assertTrue(extractedValid.startsWith("#include"))
        assertFalse(extractedValid.contains("undefined reference"))
    }

    @Test
    fun testPythonLocCalculation() {
        val runner = BenchmarkRunner(llmClient = MockBenchmarkLlm())
        val pyCode = """
            # Initial comment
            from mock_hw import I2C, PLC

            # Setup I2C
            i2c = I2C()
            val = i2c.read(0x76, 0xD0, 1)

            # Print output
            print(val)
        """.trimIndent()
        val loc = runner.calculateLoc(pyCode)
        // Expected code lines:
        // 1. from mock_hw import I2C, PLC
        // 2. i2c = I2C()
        // 3. val = i2c.read(0x76, 0xD0, 1)
        // 4. print(val)
        assertEquals(4, loc)
    }

    @Test
    fun testUnifiedTraceJsonlWrittenForPassAndFail() {
        val traceFile = File("build/dry_run_trace_test.jsonl")
        traceFile.delete()

        val mockLlm = MockBenchmarkLlm()
        val runner = BenchmarkRunner(
            llmClient = mockLlm,
            traceJsonlFile = traceFile,
            failuresLogFile = File("build/dry_run_failures_test.log"),
            tasks = BenchmarkTask.ALL_TASKS.filter { it.id == 10 }, // Task 10 triggers failure on attempt 1 and pass on attempt 2
            profiles = listOf(LanguageProfile.ALL_PROFILES.first { it.id == "python" })
        )

        runner.runBenchmark()

        assertTrue(traceFile.exists())
        val lines = traceFile.readLines().filter { it.isNotBlank() }
        assertTrue("Trace file must contain at least 2 attempts for Task 10", lines.size >= 2)

        // Attempt 1 should be a failure (RUNTIME_OR_SCOPE_ERROR)
        val attempt1 = lines[0]
        assertTrue(attempt1.contains("\"attempt\":1"))
        assertTrue(attempt1.contains("\"status\":\"RUNTIME_OR_SCOPE_ERROR\""))
        assertTrue(attempt1.contains("\"diagnostic_sent\":"))

        // Attempt 2 should be a PASS
        val attempt2 = lines[1]
        assertTrue(attempt2.contains("\"attempt\":2"))
        assertTrue(attempt2.contains("\"status\":\"PASS\""))
        assertTrue(attempt2.contains("\"diagnostic_sent\":null"))

        // Verify required keys exist in JSON
        val requiredKeys = listOf(
            "\"timestamp\"", "\"target\"", "\"task_id\"", "\"task_name\"", "\"attempt\"",
            "\"status\"", "\"prompt_tokens\"", "\"completion_tokens\"", "\"duration_ms\"",
            "\"raw_response\"", "\"extracted_code\"", "\"loc\"", "\"stdout\""
        )
        for (key in requiredKeys) {
            assertTrue("JSON line must contain $key", attempt1.contains(key))
            assertTrue("JSON line must contain $key", attempt2.contains(key))
        }
    }

    @Test
    fun testSkippedRuntimeHandling() {
        val mockLlm = MockBenchmarkLlm()
        val fakeProfile = LanguageProfile(
            id = "nonexistent_lang",
            displayName = "NonExistent Language",
            runtimeCommand = "non_existent_binary_xyz_1234",
            errorRepairStyle = ErrorRepairStyle.NATIVE_STDERR,
            useGbnf = false,
            fileExtension = "xyz",
            systemPromptHeader = "fake",
            mockSignatures = "fake"
        )
        val runner = BenchmarkRunner(
            llmClient = mockLlm,
            profiles = listOf(fakeProfile),
            failuresLogFile = File("build/dry_run_fake_failures.log"),
            traceJsonlFile = File("build/dry_run_fake_trace.jsonl")
        )
        val summaries = runner.runBenchmark()
        assertEquals(1, summaries.size)
        assertEquals("SKIPPED_NO_RUNTIME", summaries[0].status)
        assertEquals(0.0, summaries[0].finalSuccessPct, 0.01)
    }

    @Test
    fun testTask9EmptyStdoutIsOutputMismatch() {
        val task9 = BenchmarkTask.ALL_TASKS.first { it.id == 9 }
        val traceFile = File("build/dry_run_task9_trace.jsonl")
        val failuresFile = File("build/dry_run_task9_failures.log")

        var attemptCount = 0
        val mockLlm = object : LlmClient {
            override fun chat(messages: List<ChatMessage>): ChatResponse {
                attemptCount++
                return if (attemptCount == 1) {
                    ChatResponse("""
                        ```tdscript
                        let chip = I2C.read(118, 208, 1);
                        WATCHDOG.emergencyStop();
                        ```
                    """.trimIndent())
                } else {
                    ChatResponse("""
                        ```tdscript
                        let chip = I2C.read(118, 208, 1);
                        print("SYSTEM_OK");
                        ```
                    """.trimIndent())
                }
            }
        }

        val profile = LanguageProfile.ALL_PROFILES.first { it.id == "tdscript_base" }
        val runner = BenchmarkRunner(
            llmClient = mockLlm,
            tasks = listOf(task9),
            profiles = listOf(profile),
            failuresLogFile = failuresFile,
            traceJsonlFile = traceFile
        )

        val summaries = runner.runBenchmark()
        assertEquals(1, summaries.size)
        val s = summaries[0]
        assertEquals(0.0, s.firstPassPct, 0.01) // Attempt 1 must NOT be a PASS!
        assertEquals(100.0, s.finalSuccessPct, 0.01) // Repaired on attempt 2

        val lines = traceFile.readLines().filter { it.isNotBlank() }
        assertTrue(lines.size >= 2)
        assertFalse("Attempt 1 must not be PASS", lines[0].contains("\"status\":\"PASS\""))
        assertTrue("Attempt 2 must be PASS", lines[1].contains("\"status\":\"PASS\""))
    }
}
