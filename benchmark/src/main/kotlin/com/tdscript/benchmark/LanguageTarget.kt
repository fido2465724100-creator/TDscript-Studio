package com.tdscript.benchmark

enum class ErrorRepairStyle {
    FULL_JSON,
    COMPACT_REPAIR_LINE,
    NATIVE_STDERR
}

data class LanguageProfile(
    val id: String,
    val displayName: String,
    val runtimeCommand: String?,
    val errorRepairStyle: ErrorRepairStyle,
    val useGbnf: Boolean,
    val fileExtension: String,
    val systemPromptHeader: String,
    val mockSignatures: String
) {
    companion object {
        val ALL_PROFILES = listOf(
            LanguageProfile(
                id = "tdscript_base",
                displayName = "TDscript Base (v1.4.0)",
                runtimeCommand = null,
                errorRepairStyle = ErrorRepairStyle.FULL_JSON,
                useGbnf = false,
                fileExtension = "tds",
                systemPromptHeader = """
                    You are an expert TDscript Core 1.1 automation engineer.
                    Syntax:
                      let <var> = <expr>;
                      var <var> = <expr>;
                      if (<cond>) { ... } else { ... }
                      while (<cond>) { ... }
                      print(<expr>);
                    Subsystems (use decimal integers):
                      I2C.read(chip, reg, len) -> ByteArray (e.g. BME280 chip=118, reg=208)
                      I2C.write(chip, reg, data) -> Boolean
                      PLC.modbusRead(host, reg) -> Int (host="127.0.0.1", reg=100)
                      PLC.modbusWrite(host, reg, val) -> Boolean
                      WATCHDOG.feed() -> Boolean
                      WATCHDOG.emergencyStop()
                      NET.ping(host) -> Float
                      BT.connect(target) -> Boolean
                      BT.send(data) -> Boolean
                      BT.readLine() -> String
                      LString: L"ALERT_TEMP:Temperature is {temp}" (never concatenate with +)
                    NOTE: No user-defined functions (no fn/def/function); write sequential script statements.
                    Return ONLY executable code inside ```tdscript ``` blocks.
                """.trimIndent(),
                mockSignatures = "TDscript Core 1.1 Standard Runtime"
            ),
            LanguageProfile(
                id = "tdscript_experimental",
                displayName = "TDscript Experimental (AI-First)",
                runtimeCommand = null,
                errorRepairStyle = ErrorRepairStyle.COMPACT_REPAIR_LINE,
                useGbnf = false,
                fileExtension = "tds",
                systemPromptHeader = """
                    You are an expert TDscript AI-First automation engineer.
                    Hexadecimal literals are supported: 0x76, 0xD0, 0xF4.
                    Syntax:
                      let <var> = <expr>;
                      var <var> = <expr>;
                      if (<cond>) { ... } else { ... }
                      while (<cond>) { ... }
                      print(<expr>);
                    NOTE: No user-defined functions (no fn/def/function); write sequential script statements.
                    Subsystems (hex literals allowed):
                      I2C.read(0x76, 0xD0, 1) -> ByteArray
                      I2C.write(0x76, 0xF4, 1) -> Boolean
                      PLC.modbusRead("127.0.0.1", 100) -> Int
                      PLC.modbusWrite("127.0.0.1", 101, 0) -> Boolean
                      WATCHDOG.feed() -> Boolean
                      WATCHDOG.emergencyStop()
                      NET.ping("192.168.1.50") -> Float
                      BT.connect("sensor_01") -> Boolean
                      BT.send("READ") -> Boolean
                      BT.readLine() -> String
                      LString: L"ALERT_TEMP:Temperature is {temp}" (never concatenate with +)

                    Examples:
                    Example 1 (I2C read with hex literal):
                    ```tdscript
                    let chipId = I2C.read(0x76, 0xD0, 1);
                    print(chipId);
                    ```

                    Example 2 (Loop with Watchdog feed):
                    ```tdscript
                    var i = 0;
                    while (i < 5) {
                        WATCHDOG.feed();
                        i = i + 1;
                    }
                    ```

                    Return ONLY executable code inside ```tdscript ``` blocks.
                """.trimIndent(),
                mockSignatures = "TDscript Core 1.1 AI-First Runtime with GBNF & Hex"
            ),
            LanguageProfile(
                id = "python",
                displayName = "Python",
                runtimeCommand = "python3",
                errorRepairStyle = ErrorRepairStyle.NATIVE_STDERR,
                useGbnf = false,
                fileExtension = "py",
                systemPromptHeader = """
                    You are an expert Python automation developer.
                    Import mock hardware: from mock_hw import I2C, PLC, WATCHDOG, NET, BT
                    Do not write an infinite loop (no while True); execute sequentially and terminate.
                    Return ONLY executable code inside ```python ``` blocks.
                """.trimIndent(),
                mockSignatures = """
                    from mock_hw import I2C, PLC, WATCHDOG, NET, BT
                    I2C.read(addr: int, reg: int, length: int) -> list[int]
                    I2C.write(addr: int, reg: int, data) -> bool
                    PLC.modbus_read(host: str, reg: int) -> int
                    PLC.modbus_write(host: str, reg: int, val: int) -> bool
                    WATCHDOG.feed() -> bool
                    WATCHDOG.emergency_stop() -> None
                    NET.ping(host: str) -> float
                    BT.connect(target: str) -> bool
                    BT.send(msg: str) -> bool
                    BT.read_line() -> str
                """.trimIndent()
            ),
            LanguageProfile(
                id = "cpp",
                displayName = "C++ (Arduino/ESP32 style)",
                runtimeCommand = "g++",
                errorRepairStyle = ErrorRepairStyle.NATIVE_STDERR,
                useGbnf = false,
                fileExtension = "cpp",
                systemPromptHeader = """
                    You are an expert C++ embedded automation developer.
                    Include header: #include "mock_hw.hpp"
                    Provide a full main() function: int main() { ... return 0; }
                    Do not write an infinite loop (no while(true)); execute sequentially and return 0.
                    Return ONLY executable code inside ```cpp ``` blocks.
                """.trimIndent(),
                mockSignatures = """
                    #include "mock_hw.hpp"
                    I2C::read(int addr, int reg, int len) -> std::vector<uint8_t>
                    I2C::write(int addr, int reg, uint8_t val) -> bool
                    PLC::modbus_read(std::string host, int reg) -> int
                    PLC::modbus_write(std::string host, int reg, int val) -> bool
                    WATCHDOG::feed() -> bool
                    WATCHDOG::emergency_stop() -> void
                    NET::ping(std::string host) -> float
                    BT::connect(std::string target) -> bool
                    BT::send(std::string msg) -> bool
                    BT::read_line() -> std::string
                """.trimIndent()
            ),
            LanguageProfile(
                id = "lua",
                displayName = "Lua",
                runtimeCommand = "lua",
                errorRepairStyle = ErrorRepairStyle.NATIVE_STDERR,
                useGbnf = false,
                fileExtension = "lua",
                systemPromptHeader = """
                    You are an expert Lua automation developer.
                    Load mock: local hw = require("mock_hw")
                    Do not write an infinite loop; execute sequentially and terminate.
                    Return ONLY executable code inside ```lua ``` blocks.
                """.trimIndent(),
                mockSignatures = """
                    local hw = require("mock_hw")
                    hw.I2C.read(addr, reg, len) -> table
                    hw.I2C.write(addr, reg, val) -> bool
                    hw.PLC.modbus_read(host, reg) -> number
                    hw.PLC.modbus_write(host, reg, val) -> bool
                    hw.WATCHDOG.feed() -> bool
                    hw.WATCHDOG.emergency_stop() -> void
                    hw.NET.ping(host) -> number
                    hw.BT.connect(target) -> bool
                    hw.BT.send(data) -> bool
                    hw.BT.read_line() -> string
                """.trimIndent()
            ),
            LanguageProfile(
                id = "javascript",
                displayName = "JavaScript (Node.js)",
                runtimeCommand = "node",
                errorRepairStyle = ErrorRepairStyle.NATIVE_STDERR,
                useGbnf = false,
                fileExtension = "js",
                systemPromptHeader = """
                    You are an expert Node.js automation developer.
                    Import mock: const { I2C, PLC, WATCHDOG, NET, BT } = require('./mock_hw');
                    Do not write an infinite loop; execute sequentially and terminate.
                    Return ONLY executable code inside ```javascript ``` blocks.
                """.trimIndent(),
                mockSignatures = """
                    const { I2C, PLC, WATCHDOG, NET, BT } = require('./mock_hw');
                    I2C.read(addr, reg, len) -> Array<number>
                    I2C.write(addr, reg, data) -> boolean
                    PLC.modbusRead(host, reg) -> number
                    PLC.modbusWrite(host, reg, val) -> boolean
                    WATCHDOG.feed() -> boolean
                    WATCHDOG.emergencyStop() -> void
                    NET.ping(host) -> number
                    BT.connect(dev) -> boolean
                    BT.send(data) -> boolean
                    BT.readLine() -> string
                """.trimIndent()
            ),
            LanguageProfile(
                id = "kotlin",
                displayName = "Kotlin",
                runtimeCommand = "kotlinc",
                errorRepairStyle = ErrorRepairStyle.NATIVE_STDERR,
                useGbnf = false,
                fileExtension = "kt",
                systemPromptHeader = """
                    You are an expert Kotlin embedded/JVM developer.
                    Import mock: import com.tdscript.benchmark.mock.*
                    Provide a fun main() { ... }
                    Do not write an infinite loop; execute sequentially and terminate.
                    Return ONLY executable code inside ```kotlin ``` blocks.
                """.trimIndent(),
                mockSignatures = """
                    import com.tdscript.benchmark.mock.*
                    I2C.read(addr: Int, reg: Int, len: Int): ByteArray
                    I2C.write(addr: Int, reg: Int, data: ByteArray): Boolean
                    PLC.modbusRead(host: String, reg: Int): Int
                    PLC.modbusWrite(host: String, reg: Int, value: Int): Boolean
                    WATCHDOG.feed(): Boolean
                    WATCHDOG.emergencyStop()
                    NET.ping(host: String): Float
                    BT.connect(target: String): Boolean
                    BT.send(msg: String): Boolean
                    BT.readLine(): String
                """.trimIndent()
            )
        )
    }
}
