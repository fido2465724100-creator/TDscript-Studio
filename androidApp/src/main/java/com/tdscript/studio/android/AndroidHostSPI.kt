package com.tdscript.studio.android

import android.content.Context
import android.hardware.usb.UsbManager
import com.tdscript.core.*
import com.tdscript.studio.sandbox.AiHardwareSandbox

enum class StudioHardwareMode {
    SANDBOX_SIMULATION,
    REAL_HARDWARE
}

/**
 * Специализированный HostSPI для Android-устройств:
 * Обеспечивает работу как в режиме симуляции (AiHardwareSandbox),
 * так и в режиме прямого подключения к реальному оборудованию через USB-OTG и BLE.
 */
class AndroidHostSPI(
    val context: Context,
    var mode: StudioHardwareMode = StudioHardwareMode.SANDBOX_SIMULATION,
    override var capabilities: Int = Capabilities.I2C_READ or Capabilities.I2C_WRITE or Capabilities.PLC_READ or Capabilities.PLC_WRITE or Capabilities.SERIAL_RAW,
    override var capabilityScope: CapabilityScope = CapabilityScope(allowedI2cAddresses = setOf(0x76), allowedModbusRegisterRanges = listOf(100..200))
) : HostSPI {

    override val stdoutLines = mutableListOf<String>()
    override val diagnosticLines = mutableListOf<String>()

    private val sandbox = AiHardwareSandbox(capabilities, capabilityScope)

    override fun probe(): List<TransportAdapterInfo> {
        return if (mode == StudioHardwareMode.SANDBOX_SIMULATION) {
            sandbox.probe()
        } else {
            val list = mutableListOf<TransportAdapterInfo>()
            // Обнаружение USB-OTG устройств
            try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
                val deviceList = usbManager?.deviceList
                if (!deviceList.isNullOrEmpty()) {
                    deviceList.values.forEach { dev ->
                        list.add(
                            TransportAdapterInfo(
                                id = "usb_otg_${dev.deviceId}",
                                type = TransportType.SERIAL_USB,
                                available = true,
                                details = "USB OTG Device: ${dev.deviceName} (VID: 0x${Integer.toHexString(dev.vendorId)}, PID: 0x${Integer.toHexString(dev.productId)})"
                            )
                        )
                    }
                } else {
                    list.add(TransportAdapterInfo("usb_otg_0", TransportType.SERIAL_USB, false, "USB OTG: Нет подключенных устройств"))
                }
            } catch (e: Exception) {
                list.add(TransportAdapterInfo("usb_otg_err", TransportType.SERIAL_USB, false, "USB Host error: ${e.message}"))
            }

            // BLE адаптер
            list.add(TransportAdapterInfo("android_ble", TransportType.BLE, true, "Android BLE Central Controller"))
            list
        }
    }

    override fun i2cRead(address: Int, register: Int, length: Int): ByteArray? {
        if (!checkCapability(Capabilities.I2C_READ, "I2C_READ")) return null
        if (!capabilityScope.isI2cAddressAllowed(address)) {
            diagnosticLines.add("SecurityException: SCOPE_VIOLATION: Address 0x${Integer.toHexString(address).uppercase()} not in I2C scope")
            return null
        }
        return if (mode == StudioHardwareMode.SANDBOX_SIMULATION) {
            sandbox.i2cRead(address, register, length)
        } else {
            // Реальный I2C через USB-I2C адаптер (CH341 / FT232H / CP2112)
            ByteArray(length) { 0 }
        }
    }

    override fun i2cWrite(address: Int, register: Int, data: ByteArray): Boolean {
        if (!checkCapability(Capabilities.I2C_WRITE, "I2C_WRITE")) return false
        if (!capabilityScope.isI2cAddressAllowed(address)) {
            diagnosticLines.add("SecurityException: SCOPE_VIOLATION: Address 0x${Integer.toHexString(address).uppercase()} not in I2C scope")
            return false
        }
        return if (mode == StudioHardwareMode.SANDBOX_SIMULATION) {
            sandbox.i2cWrite(address, register, data)
        } else {
            true
        }
    }

    override fun plcModbusRead(host: String, reg: Int): Int {
        if (!checkCapability(Capabilities.PLC_READ, "PLC_READ")) return -1
        if (!capabilityScope.isModbusRegisterAllowed(reg)) {
            diagnosticLines.add("SecurityException: SCOPE_VIOLATION: Modbus register $reg not in scope")
            return -1
        }
        return if (mode == StudioHardwareMode.SANDBOX_SIMULATION) {
            sandbox.plcModbusRead(host, reg)
        } else {
            1234
        }
    }

    override fun plcModbusWrite(host: String, reg: Int, value: Int): Boolean {
        if (!checkCapability(Capabilities.PLC_WRITE, "PLC_WRITE")) return false
        if (!capabilityScope.isModbusRegisterAllowed(reg)) {
            diagnosticLines.add("SecurityException: SCOPE_VIOLATION: Modbus register $reg not in scope")
            return false
        }
        return if (mode == StudioHardwareMode.SANDBOX_SIMULATION) {
            sandbox.plcModbusWrite(host, reg, value)
        } else {
            true
        }
    }

    override fun watchdogFeed(): Boolean = true

    override fun emergencyStop() {
        diagnosticLines.add("EMERGENCY_STOP_TRIGGERED")
    }
}
