class I2C {
  static regs = { 0x76: { 0xD0: [0x60], 0xF7: [0x50, 0x00] } };
  constructor() {}
  static read(addr, reg, len = 1) {
    if (addr !== 0x76) throw new Error(`SCOPE_VIOLATION: Address ${addr} not in I2C scope`);
    return (I2C.regs[addr] && I2C.regs[addr][reg]) || new Array(len).fill(0);
  }
  read(addr, reg, len) { return I2C.read(addr, reg, len); }

  static write(addr, reg, data) {
    if (addr !== 0x76) throw new Error(`SCOPE_VIOLATION: Address ${addr} not in I2C scope`);
    if (!I2C.regs[addr]) I2C.regs[addr] = {};
    I2C.regs[addr][reg] = Array.isArray(data) ? data : [data];
    return true;
  }
  write(addr, reg, data) { return I2C.write(addr, reg, data); }
}

class PLC {
  static regs = { 100: 245, 101: 1 };
  constructor() {}
  static modbusRead(host, reg) {
    if (reg < 100 || reg > 200) throw new Error(`SCOPE_VIOLATION: Modbus register ${reg} not in scope`);
    return PLC.regs[reg] !== undefined ? PLC.regs[reg] : 0;
  }
  static modbus_read(host, reg) { return PLC.modbusRead(host, reg); }
  modbusRead(host, reg) { return PLC.modbusRead(host, reg); }
  modbus_read(host, reg) { return PLC.modbusRead(host, reg); }

  static modbusWrite(host, reg, val) {
    if (reg < 100 || reg > 200) throw new Error(`SCOPE_VIOLATION: Modbus register ${reg} not in scope`);
    PLC.regs[reg] = val;
    return true;
  }
  static modbus_write(host, reg, val) { return PLC.modbusWrite(host, reg, val); }
  modbusWrite(host, reg, val) { return PLC.modbusWrite(host, reg, val); }
  modbus_write(host, reg, val) { return PLC.modbusWrite(host, reg, val); }
}

class WATCHDOG {
  constructor() {}
  static feed() { return true; }
  feed() { return true; }
  static emergencyStop() { console.error("EMERGENCY_STOP_TRIGGERED"); }
  static emergency_stop() { console.error("EMERGENCY_STOP_TRIGGERED"); }
  emergencyStop() { WATCHDOG.emergencyStop(); }
  emergency_stop() { WATCHDOG.emergencyStop(); }
}

class NET {
  constructor() {}
  static ping(host) { return host === "192.168.1.50" ? 12.5 : -1.0; }
  ping(host) { return NET.ping(host); }
}

class BT {
  constructor() {}
  static connect(dev) { return true; }
  connect(dev) { return true; }
  static send(data) { return true; }
  send(data) { return true; }
  static readLine() { return "BT_OK: 24.5C"; }
  static read_line() { return "BT_OK: 24.5C"; }
  readLine() { return "BT_OK: 24.5C"; }
  read_line() { return "BT_OK: 24.5C"; }
}

module.exports = { I2C, PLC, WATCHDOG, NET, BT };
