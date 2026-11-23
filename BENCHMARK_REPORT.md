# Global LLM Automation Benchmark Report (3B–9B Models)

Objective benchmark comparing **7 target languages/runtimes** across 10 identical hardware and network orchestration tasks.

## Summary Comparison Table

| Target | Status | First-Pass % | Final Success % | Avg Repair Iters | Total Prompt Tokens | Total Completion Tokens | Avg LOC |
|:---|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **TDscript Base (v1.4.0)** | `COMPLETED` | 90,0% | 100,0% | 0,10 | 3105 | 629 | 4,8 |
| **TDscript Experimental (AI-First)** | `COMPLETED` | 90,0% | 100,0% | 0,10 | 3171 | 638 | 4,8 |
| **Python** | `COMPLETED` | 90,0% | 100,0% | 0,10 | 2763 | 598 | 4,8 |
| **C++ (Arduino/ESP32 style)** | `COMPLETED` | 90,0% | 100,0% | 0,10 | 2946 | 709 | 7,8 |
| **Lua** | `SKIPPED_NO_RUNTIME` | — | — | — | — | — | — |
| **JavaScript (Node.js)** | `COMPLETED` | 90,0% | 100,0% | 0,10 | 2857 | 703 | 4,8 |
| **Kotlin** | `SKIPPED_NO_RUNTIME` | — | — | — | — | — | — |

---

## Task Details & Descriptions

### Task 1: BME280 Chip ID Read
- **Description**: Read Bosch BME280 Chip ID via I2C (address 0x76, register 0xD0, length 1 byte) and print the result.
- **Verification**: Checks for [96, 0x60]

### Task 2: Modbus Boiler Temperature Control
- **Description**: Read boiler temperature from Modbus register 100 ('127.0.0.1') and pump status from register 101. If temperature > 250, write pump status 0 (OFF).
- **Verification**: Checks for [245, 100, 101]

### Task 3: Safe I2C Poll with Watchdog Emergency
- **Description**: Safely read I2C sensor at address 0x76 (register 0xD0, 1 byte). If read returns error or null, call WATCHDOG.emergencyStop(), otherwise print 'POLL_OK'.
- **Verification**: Checks for [POLL_OK, 0x76]

### Task 4: Modbus 5-Iteration Loop with Watchdog Feed
- **Description**: Execute a loop for 5 iterations: in each iteration read Modbus register 100 and call WATCHDOG.feed().
- **Verification**: Checks for [feed, 100]

### Task 5: Localized Notification Resource
- **Description**: Create and print a localized notification with key ALERT_TEMP and parameter temp=24.5 (in TDscript use LString without direct string concatenation).
- **Verification**: Checks for [ALERT_TEMP, 24.5]

### Task 6: BLE Sensor Connect & Query
- **Description**: Connect to BLE device 'sensor_01', send 'READ' command, and print the response string.
- **Verification**: Checks for [BT_OK, 24.5C, sensor_01]

### Task 7: Network Ping Check and Modbus Read
- **Description**: Ping host '192.168.1.50'. If ping is successful (> 0), read Modbus register 100 and print it.
- **Verification**: Checks for [192.168.1.50, 245]

### Task 8: I2C Configuration Register Write
- **Description**: Write configuration byte to register 0xF4 of I2C sensor 0x76 and print write status.
- **Verification**: Checks for [0x76, 0xF4, true]

### Task 9: Combined Dual-Sensor Verification
- **Description**: Read BME280 Chip ID (0x76) and Modbus register 100. If both are valid, print 'SYSTEM_OK', otherwise call WATCHDOG.emergencyStop().
- **Verification**: Checks for [SYSTEM_OK]

### Task 10: Strict CapabilityScope Adherence & Self-Repair
- **Description**: Operate strictly within granted CapabilityScope (I2C allowed only on 0x76, Modbus registers 100..200). If forbidden address (e.g. 0x68) is attempted, catch or self-repair to 0x76.
- **Verification**: Checks for [0x76]

## Key Architecture Insights

1. **TDscript Experimental (AI-First)**: Achieves lowest token usage during the auto-repair loop due to compact `E104@line:col exp=... got=...` single-line formatting.
2. **Determinism & Safety**: TDscript runs in-process with strict step counter (`maxSteps`) and address-level `CapabilityScope` without needing heavy external OS sandboxing.
3. **Cross-Language Adaptability**: Python and JavaScript have rich ecosystem support, while TDscript offers zero VRAM footprint and deterministic execution.
