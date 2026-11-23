local M = {}

local i2c_regs = { [0x76] = { [0xD0] = { 0x60 } } }
local modbus_regs = { [100] = 245, [101] = 1 }

M.I2C = {
    read = function(addr, reg, len)
        if addr ~= 0x76 then error("SCOPE_VIOLATION: Address not in I2C scope") end
        if i2c_regs[addr] and i2c_regs[addr][reg] then return i2c_regs[addr][reg] end
        local res = {}
        for i = 1, len do res[i] = 0 end
        return res
    end,
    write = function(addr, reg, val)
        if addr ~= 0x76 then error("SCOPE_VIOLATION: Address not in I2C scope") end
        if not i2c_regs[addr] then i2c_regs[addr] = {} end
        i2c_regs[addr][reg] = val
        return true
    end
}

M.PLC = {
    modbus_read = function(host, reg)
        if reg < 100 or reg > 200 then error("SCOPE_VIOLATION: Modbus register not in scope") end
        return modbus_regs[reg] or 0
    end,
    modbus_write = function(host, reg, val)
        if reg < 100 or reg > 200 then error("SCOPE_VIOLATION: Modbus register not in scope") end
        modbus_regs[reg] = val
        return true
    end
}

M.WATCHDOG = {
    feed = function() return true end,
    emergency_stop = function() io.stderr:write("EMERGENCY_STOP_TRIGGERED\n") end
}

M.NET = {
    ping = function(host)
        if host == "192.168.1.50" then return 12.5 else return -1.0 end
    end
}

M.BT = {
    connect = function(dev) return true end,
    send = function(data) return true end,
    read_line = function() return "BT_OK: 24.5C" end
}

return M
