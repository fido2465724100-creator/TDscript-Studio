package com.tdscript.benchmark.mock

object I2C {
    private val regs = mutableMapOf<Int, MutableMap<Int, ByteArray>>(
        0x76 to mutableMapOf(0xD0 to byteArrayOf(0x60.toByte()))
    )

    fun read(addr: Int, reg: Int, len: Int): ByteArray {
        if (addr != 0x76) throw RuntimeException("SCOPE_VIOLATION: Address 0x${Integer.toHexString(addr).uppercase()} not in I2C scope")
        return regs[addr]?.get(reg) ?: ByteArray(len)
    }

    fun write(addr: Int, reg: Int, data: ByteArray): Boolean {
        if (addr != 0x76) throw RuntimeException("SCOPE_VIOLATION: Address 0x${Integer.toHexString(addr).uppercase()} not in I2C scope")
        regs.getOrPut(addr) { mutableMapOf() }[reg] = data
        return true
    }
}

object PLC {
    private val regs = mutableMapOf(100 to 245, 101 to 1)

    fun modbusRead(host: String, reg: Int): Int {
        if (reg !in 100..200) throw RuntimeException("SCOPE_VIOLATION: Modbus register $reg not in scope")
        return regs[reg] ?: 0
    }

    fun modbusWrite(host: String, reg: Int, value: Int): Boolean {
        if (reg !in 100..200) throw RuntimeException("SCOPE_VIOLATION: Modbus register $reg not in scope")
        regs[reg] = value
        return true
    }
}

object WATCHDOG {
    fun feed(): Boolean = true
    fun emergencyStop() {
        System.err.println("EMERGENCY_STOP_TRIGGERED")
    }
}

object NET {
    fun ping(host: String): Float = if (host == "192.168.1.50") 12.5f else -1.0f
}

object BT {
    fun connect(target: String): Boolean = true
    fun send(msg: String): Boolean = true
    fun readLine(): String = "BT_OK: 24.5C"
}
