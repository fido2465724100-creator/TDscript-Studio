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
        val runner = BenchmarkRunner(
            llmClient = mockLlm,
            mocksDir = File("/home/anton/TDscript-Studio/benchmark/mocks")
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
        assertTrue(report.contains("TDscript Base (v1.4.0)"))
        assertTrue(report.contains("TDscript Experimental (AI-First)"))
        assertTrue(report.contains("Python"))
        assertTrue(report.contains("C++ (Arduino/ESP32 style)"))
        assertTrue(report.contains("Lua"))
        assertTrue(report.contains("JavaScript (Node.js)"))
        assertTrue(report.contains("Kotlin"))

        // Save report to file for user inspection
        File("/home/anton/TDscript-Studio/BENCHMARK_REPORT.md").writeText(report)
        assertTrue(File("/home/anton/TDscript-Studio/BENCHMARK_REPORT.md").exists())
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
            profiles = listOf(fakeProfile)
        )
        val summaries = runner.runBenchmark()
        assertEquals(1, summaries.size)
        assertEquals("SKIPPED_NO_RUNTIME", summaries[0].status)
        assertEquals(0.0, summaries[0].finalSuccessPct, 0.01)
    }
}
