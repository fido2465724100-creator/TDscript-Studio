package com.tdscript.benchmark

import com.tdscript.core.*
import com.tdscript.studio.sandbox.AiHardwareSandbox
import org.junit.Assert.*
import org.junit.Test

class NativeCapabilityScopeTraceTest {

    @Test
    fun testNativeVmCapabilityScopeRefusalTrace() {
        println("================================================================================")
        println(" [NATIVE VM TRACE] TDscript Core 1.1 CapabilityScope Enforcement in Real Runtime")
        println("================================================================================")

        // 1. Configure strict physical hardware sandbox CapabilityScope
        val scope = CapabilityScope(
            allowedI2cAddresses = setOf(0x76), // Only Bosch BME280 @ 0x76 allowed
            allowedModbusRegisterRanges = listOf(100..200), // Only PLC registers 100..200 allowed
            allowedNetworkHosts = setOf("127.0.0.1", "192.168.1.50")
        )

        val sandbox = AiHardwareSandbox(
            capabilities = Capabilities.HARDWARE or Capabilities.I2C_READ or Capabilities.I2C_WRITE or
                    Capabilities.PLC_READ or Capabilities.PLC_WRITE or Capabilities.NET_READ,
            scope = scope
        )

        println("[CONFIG] Granted CapabilityScope:")
        println("         - allowedI2cAddresses: [0x76 (118)]")
        println("         - allowedModbusRegisterRanges: [100..200]")
        println("         - allowedNetworkHosts: [127.0.0.1, 192.168.1.50]")
        println()

        // 2. TDscript Source Code with both authorized and unauthorized calls
        val scriptSource = """
            // 1. Authorized read from allowed I2C address 0x76
            let sensorData = I2C.read(0x76, 0xD0, 1);
            print("Authorized I2C read @ 0x76: " + sensorData);

            // 2. FORBIDDEN read from unapproved address 0x68 (e.g. MPU6050 or wrong jumper)
            let forbiddenData = I2C.read(0x68, 0xD0, 1);

            // 3. Authorized Modbus PLC read (register 100 in range 100..200)
            let boilerTemp = PLC.modbusRead("127.0.0.1", 100);
            print("Authorized Modbus reg 100: " + boilerTemp);

            // 4. FORBIDDEN Modbus PLC read (register 999 outside range 100..200)
            let badReg = PLC.modbusRead("127.0.0.1", 999);
        """.trimIndent()

        println("[SOURCE CODE .tds]:")
        println(scriptSource)
        println("--------------------------------------------------------------------------------")

        // 3. Execute in native VM (Lexer -> Parser -> Interpreter -> HostSPI)
        val lexer = Lexer(scriptSource)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val program = parser.parse()
        val interpreter = Interpreter(sandbox, maxSteps = 100_000L)

        interpreter.execute(program)

        // 4. Print live execution output
        println("[VM STDOUT]:")
        sandbox.stdoutLines.forEach { println("  [OUT] $it") }

        println()
        println("[VM DIAGNOSTICS LOG (HostSPI)]:")
        sandbox.diagnosticLines.forEach { println("  [SECURITY] $it") }

        // 5. Assertions: Authorized calls succeed
        assertNotNull(interpreter.globals["sensorData"])
        assertEquals(245, interpreter.globals["boilerTemp"])

        // Assertions: Unauthorized calls are blocked at the HostSPI boundary and return null / -1
        assertNull(interpreter.globals["forbiddenData"])
        assertEquals(-1, interpreter.globals["badReg"])

        // Assertions: Security violations are recorded
        val i2cViolation = sandbox.diagnosticLines.any { it.contains("SCOPE_VIOLATION: Address 0x68 not in I2C scope") }
        val modbusViolation = sandbox.diagnosticLines.any { it.contains("SCOPE_VIOLATION: Modbus register 999 not in scope") }

        assertTrue("I2C 0x68 scope violation must be recorded", i2cViolation)
        assertTrue("Modbus 999 scope violation must be recorded", modbusViolation)

        // 6. Compact Diagnostic Formatter for AI Agent auto-repair
        val aiDiagnostic = DiagnosticError.scopeViolation(
            line = 6,
            column = 22,
            expected = "0x76",
            actual = "0x68",
            message = "Address 0x68 not in I2C scope"
        )
        val compactRepair = aiDiagnostic.toRepairFormat()
        println()
        println("[AI AGENT COMPACT REPAIR DIAGNOSTIC]:")
        println("  $compactRepair")
        assertEquals("E104@6:22 exp=0x76 got=0x68", compactRepair)

        println("================================================================================")
        println(" [VERIFIED] Native VM enforcement of CapabilityScope successfully demonstrated!")
        println("================================================================================")
    }
}
