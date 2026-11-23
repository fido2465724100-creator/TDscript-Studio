package com.tdscript.studio

import com.tdscript.core.*
import com.tdscript.studio.editor.CodeProjector
import com.tdscript.studio.sandbox.AiHardwareSandbox
import org.junit.Assert.*
import org.junit.Test

class StudioEngineTest {

    @Test
    fun testCodeProjectorAndExecution() {
        val projector = CodeProjector()
        val canonical = """
            let temp = 25
            var alert = false
            if (temp > 20) {
                alert = true
            }
        """.trimIndent()

        val ruCode = projector.projectForDisplay(canonical, "ru")
        assertTrue(ruCode.startsWith("language: ru;"))
        assertTrue(ruCode.contains("пусть temp = 25"))
        assertTrue(ruCode.contains("если (temp > 20)"))

        // Проверяем исполнение спроецированного кода ядром Core 1.1
        val lexer = Lexer(ruCode)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val program = parser.parse()
        val interpreter = Interpreter()
        interpreter.execute(program)

        assertEquals(25, (interpreter.globals["temp"] as Number).toInt())
        assertEquals(true, interpreter.globals["alert"])
    }

    @Test
    fun testAiHardwareSandboxI2cAndModbus() {
        // Тест эмуляции датчика BME280 и Modbus в песочнице
        val sandbox = AiHardwareSandbox(
            capabilities = Capabilities.I2C_READ or Capabilities.I2C_WRITE or Capabilities.PLC_READ,
            scope = CapabilityScope(allowedI2cAddresses = setOf(0x76), allowedModbusRegisterRanges = listOf(100..200))
        )

        val code = """
            let bmeChipId = I2C.read(118, 208, 1)
            let boilerTemp = PLC.modbusRead("127.0.0.1", 100)
            let pumpStatus = PLC.modbusRead("127.0.0.1", 101)
        """.trimIndent()

        val lexer = Lexer(code)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val program = parser.parse()
        val interpreter = Interpreter(sandbox)
        interpreter.execute(program)

        val chipIdBytes = interpreter.globals["bmeChipId"] as ByteArray
        assertEquals(1, chipIdBytes.size)
        assertEquals(0x60.toByte(), chipIdBytes[0]) // Chip ID BME280 = 0x60
        assertEquals(245, interpreter.globals["boilerTemp"])
        assertEquals(1, interpreter.globals["pumpStatus"])
    }
}
