package com.tdscript.studio.sandbox

import com.tdscript.core.*

/**
 * Встроенный симулятор и безопасная песочница AI Sandbox:
 * Эмулирует реальные I2C датчики, Modbus PLC и USB/BLE шины,
 * позволяя разработчику или AI отладить скрипт и драйвер без физического оборудования.
 */
open class AiHardwareSandbox(
    capabilities: Int = Capabilities.I2C_READ or Capabilities.I2C_WRITE or Capabilities.PLC_READ or Capabilities.PLC_WRITE or Capabilities.SERIAL_RAW,
    scope: CapabilityScope = CapabilityScope.UNRESTRICTED
) : StandardHostSPI(capabilities, scope) {

    // Виртуальные регистры эмулируемых чипов
    val virtualI2cRegisters = mutableMapOf<Int, MutableMap<Int, ByteArray>>()
    val virtualModbusRegisters = mutableMapOf<Int, Int>()

    init {
        // Эмуляция Bosch BME280 на адресе 0x76: Chip ID = 0x60 в регистре 0xD0
        virtualI2cRegisters.getOrPut(0x76) { mutableMapOf() }[0xD0] = byteArrayOf(0x60.toByte())
        // Регистры температуры/давления
        virtualI2cRegisters[0x76]?.put(0xF7, byteArrayOf(0x50, 0x00, 0x00, 0x80.toByte(), 0x00, 0x00, 0x80.toByte(), 0x00))

        // Эмуляция регистров Modbus PLC
        virtualModbusRegisters[100] = 245 // Температура теплоносителя 24.5 C
        virtualModbusRegisters[101] = 1   // Статус насоса = ON
    }

    override fun probe(): List<TransportAdapterInfo> {
        return listOf(
            TransportAdapterInfo("sim_i2c_0", TransportType.I2C, true, "Virtual I2C Bus (BME280 @ 0x76)"),
            TransportAdapterInfo("sim_modbus_tcp", TransportType.NETWORK_TCP, true, "Virtual Modbus PLC Gateway @ 127.0.0.1:502"),
            TransportAdapterInfo("sim_ble_hci", TransportType.BLE, true, "Virtual BLE GATT Host")
        )
    }

    override fun i2cRead(address: Int, register: Int, length: Int): ByteArray? {
        if (!checkCapability(Capabilities.I2C_READ, "I2C_READ")) return null
        if (!capabilityScope.isI2cAddressAllowed(address)) {
            diagnosticLines.add("SecurityException: SCOPE_VIOLATION: Address 0x${Integer.toHexString(address).uppercase()} not in I2C scope")
            return null
        }
        val devMap = virtualI2cRegisters[address] ?: return ByteArray(length) { 0 }
        return devMap[register] ?: ByteArray(length) { 0 }
    }

    override fun i2cWrite(address: Int, register: Int, data: ByteArray): Boolean {
        if (!checkCapability(Capabilities.I2C_WRITE, "I2C_WRITE")) return false
        if (!capabilityScope.isI2cAddressAllowed(address)) {
            diagnosticLines.add("SecurityException: SCOPE_VIOLATION: Address 0x${Integer.toHexString(address).uppercase()} not in I2C scope")
            return false
        }
        virtualI2cRegisters.getOrPut(address) { mutableMapOf() }[register] = data
        return true
    }

    override fun plcModbusRead(host: String, reg: Int): Int {
        if (!checkCapability(Capabilities.PLC_READ, "PLC_READ")) return -1
        if (!capabilityScope.isModbusRegisterAllowed(reg)) {
            diagnosticLines.add("SecurityException: SCOPE_VIOLATION: Modbus register $reg not in scope")
            return -1
        }
        return virtualModbusRegisters[reg] ?: 0
    }

    override fun plcModbusWrite(host: String, reg: Int, value: Int): Boolean {
        if (!checkCapability(Capabilities.PLC_WRITE, "PLC_WRITE")) return false
        if (!capabilityScope.isModbusRegisterAllowed(reg)) {
            diagnosticLines.add("SecurityException: SCOPE_VIOLATION: Modbus register $reg not in scope")
            return false
        }
        virtualModbusRegisters[reg] = value
        return true
    }
}
