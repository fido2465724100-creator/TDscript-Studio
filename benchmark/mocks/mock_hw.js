const I2C = {
  regs: { 0x76: { 0xD0: [0x60] } },
  read(addr, reg, len) {
    if (addr !== 0x76) throw new Error(`SCOPE_VIOLATION: Address ${addr} not in I2C scope`);
    return (this.regs[addr] && this.regs[addr][reg]) || new Array(len).fill(0);
  },
  write(addr, reg, data) {
    if (addr !== 0x76) throw new Error(`SCOPE_VIOLATION: Address ${addr} not in I2C scope`);
    if (!this.regs[addr]) this.regs[addr] = {};
    this.regs[addr][reg] = Array.isArray(data) ? data : [data];
    return true;
  }
};

const PLC = {
  regs: { 100: 245, 101: 1 },
  modbusRead(host, reg) {
    if (reg < 100 || reg > 200) throw new Error(`SCOPE_VIOLATION: Modbus register ${reg} not in scope`);
    return this.regs[reg] !== undefined ? this.regs[reg] : 0;
  },
  modbusWrite(host, reg, val) {
    if (reg < 100 || reg > 200) throw new Error(`SCOPE_VIOLATION: Modbus register ${reg} not in scope`);
    this.regs[reg] = val;
    return true;
  }
};

const WATCHDOG = {
  feed() { return true; },
  emergencyStop() { console.error("EMERGENCY_STOP_TRIGGERED"); }
};

const NET = {
  ping(host) { return host === "192.168.1.50" ? 12.5 : -1.0; }
};

const BT = {
  connect(dev) { return true; },
  send(data) { return true; },
  readLine() { return "BT_OK: 24.5C"; }
};

module.exports = { I2C, PLC, WATCHDOG, NET, BT };
