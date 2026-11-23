package com.tdscript.studio.agent

import com.tdscript.core.Capabilities
import com.tdscript.core.CapabilityScope
import com.tdscript.studio.sandbox.AiHardwareSandbox
import org.junit.Assert.*
import org.junit.Test

class MockLlmClient(
    private val scriptSequence: List<(List<ChatMessage>) -> String>
) : LlmClient {
    private var callIndex = 0

    override fun chat(messages: List<ChatMessage>): ChatResponse {
        assertTrue("Unexpected extra call to MockLlmClient", callIndex < scriptSequence.size)
        val generator = scriptSequence[callIndex++]
        val responseContent = generator(messages)
        return ChatResponse(content = responseContent, model = "mock-model")
    }
}

class AiAgentControllerTest {

    @Test
    fun testAutoFixI2cScopeViolation() {
        val sandbox = AiHardwareSandbox(
            capabilities = Capabilities.I2C_READ or Capabilities.I2C_WRITE or Capabilities.PLC_READ,
            scope = CapabilityScope(allowedI2cAddresses = setOf(0x76), allowedModbusRegisterRanges = listOf(100..200))
        )

        val mockClient = MockLlmClient(
            listOf(
                // 1-й запрос: модель генерирует обращение к запрещенному I2C адресу 0x68 (104)
                { messages ->
                    """
                    ```tdscript
                    // Чтение неизвестного датчика по адресу 0x68
                    let chip = I2C.read(104, 0, 1)
                    print("DONE")
                    ```
                    """.trimIndent()
                },
                // 2-й запрос: модель получает JSON с ошибкой SCOPE_VIOLATION и исправляет адрес на 0x76 (118)
                { messages ->
                    val lastUserMsg = messages.last().content
                    assertTrue("Expected diagnostic error in prompt", lastUserMsg.contains("SCOPE_VIOLATION"))
                    assertTrue("Expected mention of address 0x68 in prompt", lastUserMsg.contains("0x68"))

                    """
                    ```tdscript
                    // Исправленный скрипт с разрешенным адресом BME280 (118)
                    let bmeChip = I2C.read(118, 208, 1)
                    print("BME280_READ_OK")
                    ```
                    """.trimIndent()
                }
            )
        )

        val controller = AiAgentController(sandbox = sandbox, llmClient = mockClient, maxFixAttempts = 3)
        val result = controller.solveTask("Прочитай идентификатор чипа BME280 по шине I2C")

        assertTrue("Execution should succeed after auto-fix", result.success)
        assertEquals("Should succeed on iteration 2", 2, result.iterations)
        assertEquals(2, result.steps.size)
        assertFalse("Step 1 must be failed", result.steps[0].success)
        assertTrue("Step 2 must be success", result.steps[1].success)
        assertTrue("Diagnostic must record SCOPE_VIOLATION on step 1", result.steps[0].errorDiagnostic!!.contains("SCOPE_VIOLATION"))
        assertTrue("Stdout should contain BME280_READ_OK", result.stdout.contains("BME280_READ_OK"))
    }

    @Test
    fun testAutoFixLStringConcatenation() {
        val sandbox = AiHardwareSandbox()

        val mockClient = MockLlmClient(
            listOf(
                // 1-й запрос: модель пытается сделать конкатенацию с LString
                { _ ->
                    """
                    ```tdscript
                    let msg = L"MSG_ALERT:Warning!"
                    let bad = msg + " System anomaly"
                    print(bad)
                    ```
                    """.trimIndent()
                },
                // 2-й запрос: модель получает ошибку TDS_E105 и исправляет на безопасную строку
                { messages ->
                    val lastUserMsg = messages.last().content
                    assertTrue("Expected TDS_E105 in diagnostic", lastUserMsg.contains("TDS_E105"))

                    """
                    ```tdscript
                    let msg = "Warning! System anomaly"
                    print(msg)
                    ```
                    """.trimIndent()
                }
            )
        )

        val controller = AiAgentController(sandbox = sandbox, llmClient = mockClient, maxFixAttempts = 3)
        val result = controller.solveTask("Сформируй предупреждение об аномалии")

        assertTrue(result.success)
        assertEquals(2, result.iterations)
        assertTrue(result.stdout.contains("Warning! System anomaly"))
    }

    @Test
    fun testDirectCleanExecutionSingleTurn() {
        val sandbox = AiHardwareSandbox()

        val mockClient = MockLlmClient(
            listOf(
                { _ ->
                    """
                    ```tdscript
                    let boilerTemp = PLC.modbusRead("127.0.0.1", 100)
                    let pumpStatus = PLC.modbusRead("127.0.0.1", 101)
                    print("BOILER_OK")
                    ```
                    """.trimIndent()
                }
            )
        )

        val controller = AiAgentController(sandbox = sandbox, llmClient = mockClient, maxFixAttempts = 3)
        val result = controller.solveTask("Проверь статус котла и насоса")

        assertTrue(result.success)
        assertEquals(1, result.iterations)
        assertEquals(1, result.steps.size)
        assertTrue(result.stdout.contains("BOILER_OK"))
    }
}
