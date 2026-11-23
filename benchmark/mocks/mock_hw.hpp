#pragma once
#include <iostream>
#include <vector>
#include <string>
#include <map>
#include <cstdint>
#include <stdexcept>

class ScopeViolationException : public std::runtime_error {
public:
    explicit ScopeViolationException(const std::string& msg) : std::runtime_error(msg) {}
};

struct I2C {
    static inline std::map<int, std::map<int, std::vector<uint8_t>>> regs = {
        {0x76, {{0xD0, {0x60}}}}
    };

    static std::vector<uint8_t> read(int addr, int reg, int len) {
        if (addr != 0x76) {
            throw ScopeViolationException("SCOPE_VIOLATION: Address not in I2C scope");
        }
        if (regs[addr].count(reg)) return regs[addr][reg];
        return std::vector<uint8_t>(len, 0);
    }

    static bool write(int addr, int reg, uint8_t val) {
        if (addr != 0x76) {
            throw ScopeViolationException("SCOPE_VIOLATION: Address not in I2C scope");
        }
        regs[addr][reg] = {val};
        return true;
    }
};

struct PLC {
    static inline std::map<int, int> regs = {{100, 245}, {101, 1}};

    static int modbus_read(const std::string& host, int reg) {
        if (reg < 100 || reg > 200) {
            throw ScopeViolationException("SCOPE_VIOLATION: Modbus register not in scope");
        }
        return regs.count(reg) ? regs[reg] : 0;
    }

    static bool modbus_write(const std::string& host, int reg, int val) {
        if (reg < 100 || reg > 200) {
            throw ScopeViolationException("SCOPE_VIOLATION: Modbus register not in scope");
        }
        regs[reg] = val;
        return true;
    }
};

struct WATCHDOG {
    static bool feed() { return true; }
    static void emergency_stop() { std::cerr << "EMERGENCY_STOP_TRIGGERED\n"; }
};

struct NET {
    static float ping(const std::string& host) {
        return (host == "192.168.1.50") ? 12.5f : -1.0f;
    }
};

struct BT {
    static bool connect(const std::string& target) { return true; }
    static bool send(const std::string& msg) { return true; }
    static std::string read_line() { return "BT_OK: 24.5C"; }
};
