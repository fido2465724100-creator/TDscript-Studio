import sys

ALLOWED_I2C = {0x76}
ALLOWED_MODBUS = range(100, 201)

class ScopeViolationException(Exception):
    pass

class I2C:
    _regs = {0x76: {0xD0: [0x60], 0xF7: [0x50, 0x00, 0x00, 0x80]}}
    @staticmethod
    def read(addr, reg, length):
        if addr not in ALLOWED_I2C:
            raise ScopeViolationException(f"SCOPE_VIOLATION: Address {hex(addr)} not in I2C scope")
        dev = I2C._regs.get(addr, {})
        return dev.get(reg, [0] * length)
    
    @staticmethod
    def write(addr, reg, data):
        if addr not in ALLOWED_I2C:
            raise ScopeViolationException(f"SCOPE_VIOLATION: Address {hex(addr)} not in I2C scope")
        if addr not in I2C._regs:
            I2C._regs[addr] = {}
        I2C._regs[addr][reg] = data if isinstance(data, list) else [data]
        return True

class PLC:
    _regs = {100: 245, 101: 1}
    @staticmethod
    def modbus_read(host, reg):
        if reg not in ALLOWED_MODBUS:
            raise ScopeViolationException(f"SCOPE_VIOLATION: Modbus register {reg} not in scope")
        return PLC._regs.get(reg, 0)
    
    @staticmethod
    def modbus_write(host, reg, val):
        if reg not in ALLOWED_MODBUS:
            raise ScopeViolationException(f"SCOPE_VIOLATION: Modbus register {reg} not in scope")
        PLC._regs[reg] = val
        return True

class WATCHDOG:
    @staticmethod
    def feed():
        return True
    
    @staticmethod
    def emergency_stop():
        print("EMERGENCY_STOP_TRIGGERED", file=sys.stderr)

class NET:
    @staticmethod
    def ping(host):
        return 12.5 if host == "192.168.1.50" else -1.0

class BT:
    @staticmethod
    def connect(target):
        return True
    @staticmethod
    def send(msg):
        return True
    @staticmethod
    def read_line():
        return "BT_OK: 24.5C"
