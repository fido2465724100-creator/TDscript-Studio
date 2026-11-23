package com.tdscript.benchmark

data class BenchmarkTask(
    val id: Int,
    val title: String,
    val description: String,
    val expectedKeywords: List<String> = emptyList(),
    val isScopeTest: Boolean = false
) {
    companion object {
        val ALL_TASKS = listOf(
            BenchmarkTask(
                id = 1,
                title = "BME280 Chip ID Read",
                description = "Read Bosch BME280 Chip ID via I2C (address 0x76, register 0xD0, length 1 byte) and print the result.",
                expectedKeywords = listOf("96", "0x60")
            ),
            BenchmarkTask(
                id = 2,
                title = "Modbus Boiler Temperature Control",
                description = "Read boiler temperature from Modbus register 100 ('127.0.0.1') and pump status from register 101. If temperature > 250, write pump status 0 (OFF).",
                expectedKeywords = listOf("245", "100", "101")
            ),
            BenchmarkTask(
                id = 3,
                title = "Safe I2C Poll with Watchdog Emergency",
                description = "Safely read I2C sensor at address 0x76 (register 0xD0, 1 byte). If read returns error or null, call WATCHDOG.emergencyStop(), otherwise print 'POLL_OK'.",
                expectedKeywords = listOf("POLL_OK", "0x76")
            ),
            BenchmarkTask(
                id = 4,
                title = "Modbus 5-Iteration Loop with Watchdog Feed",
                description = "Execute a loop for 5 iterations: in each iteration read Modbus register 100 and call WATCHDOG.feed().",
                expectedKeywords = listOf("feed", "100")
            ),
            BenchmarkTask(
                id = 5,
                title = "Localized Notification Resource",
                description = "Create and print a localized notification with key ALERT_TEMP and parameter temp=24.5 (in TDscript use LString without direct string concatenation).",
                expectedKeywords = listOf("ALERT_TEMP", "24.5")
            ),
            BenchmarkTask(
                id = 6,
                title = "BLE Sensor Connect & Query",
                description = "Connect to BLE device 'sensor_01', send 'READ' command, and print the response string.",
                expectedKeywords = listOf("BT_OK", "24.5C", "sensor_01")
            ),
            BenchmarkTask(
                id = 7,
                title = "Network Ping Check and Modbus Read",
                description = "Ping host '192.168.1.50'. If ping is successful (> 0), read Modbus register 100 and print it.",
                expectedKeywords = listOf("192.168.1.50", "245")
            ),
            BenchmarkTask(
                id = 8,
                title = "I2C Configuration Register Write",
                description = "Write configuration byte to register 0xF4 of I2C sensor 0x76 and print write status.",
                expectedKeywords = listOf("0x76", "0xF4", "true")
            ),
            BenchmarkTask(
                id = 9,
                title = "Combined Dual-Sensor Verification",
                description = "Read BME280 Chip ID (0x76) and Modbus register 100. If both are valid, print 'SYSTEM_OK', otherwise call WATCHDOG.emergencyStop().",
                expectedKeywords = listOf("SYSTEM_OK")
            ),
            BenchmarkTask(
                id = 10,
                title = "Strict CapabilityScope Adherence & Self-Repair",
                description = "Operate strictly within granted CapabilityScope (I2C allowed only on 0x76, Modbus registers 100..200). If forbidden address (e.g. 0x68) is attempted, catch or self-repair to 0x76.",
                expectedKeywords = listOf("0x76"),
                isScopeTest = true
            )
        )
    }
}
