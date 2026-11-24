# Global LLM Automation Benchmark Report (3B–9B Models)

Objective benchmark comparing **7 target languages/runtimes** across 10 identical hardware and network orchestration tasks.

## Summary Comparison Table

| Target | Status | First-Pass % | Final Success % | Avg Repair Iters | Cost / Success | Total Prompt Tokens | Total Completion Tokens | Avg LOC |
|:---|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **TDscript Base (v1.4.0)** | `COMPLETED` | 20.0% | 100.0% | 1.30 | 2249.3 | 9133 | 13360 | 8.1 |
| **TDscript Experimental (AI-First)** | `COMPLETED` | 30.0% | 90.0% | 0.78 | 1653.8 | 7250 | 7634 | 7.4 |
| **Python** | `COMPLETED` | 0.0% | 90.0% | 1.11 | 2449.7 | 7890 | 14157 | 6.6 |
| **C++ (Arduino/ESP32 style)** | `COMPLETED` | 30.0% | 100.0% | 0.80 | 1647.0 | 6045 | 10425 | 10.3 |
| **Lua** | `COMPLETED` | 70.0% | 100.0% | 0.40 | 1240.1 | 5350 | 7051 | 11.3 |
| **JavaScript (Node.js)** | `COMPLETED` | 80.0% | 90.0% | 0.11 | 1626.4 | 8523 | 6115 | 8.4 |
| **Kotlin** | `COMPLETED` | 70.0% | 100.0% | 0.30 | 1026.9 | 4480 | 5789 | 12.8 |

---

## Failure Breakdown

| Target | TRUNCATED_IN_THINKING | COMPILE_OR_SYNTAX_ERROR | RUNTIME_OR_SCOPE_ERROR | OUTPUT_MISMATCH | Total Failures |
|:---|:---:|:---:|:---:|:---:|:---:|
| **TDscript Base (v1.4.0)** | 12 | 0 | 1 | 0 | 13 |
| **TDscript Experimental (AI-First)** | 6 | 0 | 1 | 0 | 8 |
| **Python** | 10 | 3 | 0 | 0 | 13 |
| **C++ (Arduino/ESP32 style)** | 6 | 2 | 0 | 0 | 8 |
| **Lua** | 1 | 0 | 3 | 0 | 4 |
| **JavaScript (Node.js)** | 0 | 2 | 1 | 0 | 4 |
| **Kotlin** | 0 | 3 | 0 | 0 | 3 |

---

## Task Details & Verification Criteria

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

