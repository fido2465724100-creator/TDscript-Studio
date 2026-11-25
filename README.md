# 🛠️ TDscript Studio (Desktop & Mobile IDE + LLM Benchmark)

Официальная кроссплатформенная интегрированная среда разработки (IDE), эмуляции аппаратуры и сравнительного бенчмаркинга для языка **TDscript Core 1.1 (v1.4.0)**.

* **Ядро языка и VM**: [`TDModules/core-tdscript`](https://github.com/fido2465724100-creator/TDModules) (Lexer, Parser, AST, Interpreter, CapabilityScope).
* **Официальный веб-портал**: [https://fido2465724100-creator.github.io/my-developer-site/tdscript/](https://fido2465724100-creator.github.io/my-developer-site/tdscript/)
* **Формальная GBNF-грамматика**: [`grammar.gbnf`](https://fido2465724100-creator.github.io/my-developer-site/tdscript/grammar.gbnf)

---

## 🏗️ Архитектура репозитория

```
TDscript-Studio/
├── common-editor/             # Общее ядро: парсер, проектор языков, песочница, агент
│   └── src/main/kotlin/com/tdscript/studio/
│       ├── agent/             # Closed-Loop ИИ-агент (Discovery -> Gen -> Sandboxed Run -> Auto-Fix)
│       ├── editor/            # CodeProjector (двусторонняя проекция на 109 языков на уровне AST)
│       └── sandbox/           # AiHardwareSandbox (виртуальные регистры I2C, Modbus, Watchdog)
├── desktopApp/                # GUI-приложение для Linux и Windows (Compose Desktop)
│   └── src/main/kotlin/com/tdscript/studio/desktop/Main.kt
├── androidApp/                # Мобильная инженерная IDE (Compose Android, USB-OTG, BLE)
│   └── src/main/java/com/tdscript/studio/android/StudioMainActivity.kt
├── benchmark/                 # Объективный ИИ-бенчмарк (7 языков × 10 аппаратно-сетевых задач)
│   ├── src/main/kotlin/com/tdscript/benchmark/
│   │   ├── BenchmarkRunner.kt # Раннер с честным подсчётом токенов, First-Pass, Final Success
│   │   ├── BenchmarkTask.kt   # 10 эталонных задач автоматизации
│   │   └── RuntimeDetector.kt # Автоопределение окружений (python3, g++, node, lua, kotlinc)
│   ├── mocks/                 # Контрактные моки шин для честного соревнования внешних языков
│   └── tasks.json             # Машиночитаемая спецификация всех 10 задач
├── tasks.json                 # Корневая копия датасета задач для LLM-инференса
├── BENCHMARK_REPORT_TEST3.md  # Эталонный отчёт Теста №3 (Qwen 3.5 9B, 70 задач, 148 попыток)
├── BENCHMARK_REPORT_TEST4.md  # Скоростной отчёт Теста №4 (Qwen 3.5 4B Unloop, 70 задач, 199 попыток)
├── benchmark_full_trace_test3.jsonl # Полная трассировка попыток Теста №3 (456 КБ)
├── benchmark_full_trace_test4.jsonl # Полная трассировка попыток Теста №4 (800 КБ)
├── benchmark_failures_test3.log     # Журнал ошибок и ремонтов Теста №3
└── benchmark_failures_test4.log     # Журнал ошибок и ремонтов Теста №4
```

---

## 🔒 Нативная верификация изоляции `CapabilityScope` (VM Trace)

В отличие от языков общего назначения, в TDscript аппаратный мандат безопасности `CapabilityScope` встроен в виртуальную машину `core-tdscript` и проверяется на уровне транзакций шин `HostSPI`.

Вы можете запустить нативный тест прямо из репозитория:
```bash
./gradlew :benchmark:test --tests NativeCapabilityScopeTraceTest
```

### Фактический вывод консоли нативной виртуальной машины:
```text
================================================================================
 [NATIVE VM TRACE] TDscript Core 1.1 CapabilityScope Enforcement in Real Runtime
================================================================================
[CONFIG] Granted CapabilityScope:
         - allowedI2cAddresses: [0x76 (118)]
         - allowedModbusRegisterRanges: [100..200]
         - allowedNetworkHosts: [127.0.0.1, 192.168.1.50]

[SOURCE CODE .tds]:
// 1. Authorized read from allowed I2C address 0x76
let sensorData = I2C.read(0x76, 0xD0, 1);
print("Authorized I2C read @ 0x76: " + sensorData);

// 2. FORBIDDEN read from unapproved address 0x68 (e.g. MPU6050 or wrong jumper)
let forbiddenData = I2C.read(0x68, 0xD0, 1);

// 3. Authorized Modbus PLC read (register 100 in range 100..200)
let boilerTemp = PLC.modbusRead("127.0.0.1", 100);
print("Authorized Modbus reg 100: " + boilerTemp);

// 4. FORBIDDEN Modbus PLC read (register 999 outside range 100..200)
let badReg = PLC.modbusRead("127.0.0.1", 999);
--------------------------------------------------------------------------------
[VM STDOUT]:
  [OUT] Authorized I2C read @ 0x76: [0x60]
  [OUT] Authorized Modbus reg 100: 245

[VM DIAGNOSTICS LOG (HostSPI)]:
  [SECURITY] SecurityException: SCOPE_VIOLATION: Address 0x68 not in I2C scope
  [SECURITY] SecurityException: SCOPE_VIOLATION: Modbus register 999 not in scope

[AI AGENT COMPACT REPAIR DIAGNOSTIC]:
  E104@6:22 exp=0x76 got=0x68
================================================================================
 [VERIFIED] Native VM enforcement of CapabilityScope successfully demonstrated!
================================================================================
```

---

## 📊 Инструкция по 100% воспроизведению бенчмарка

### 1. Требования к окружению
- **JDK**: Java 17+ (OpenJDK или Oracle).
- **LLM-сервер**: `llama-server` (билд llama.cpp 2.73.0 или новее) либо Ollama / LM Studio с поддержкой OpenAI-совместимого эндпоинта `/v1/chat/completions`.
- **Внешние компиляторы/рантаймы** (для сравниваемых языков): `python3`, `g++`, `node`, `lua`, `kotlinc`. Если какой-то рантайм отсутствует, BenchmarkRunner автоматически присвоит профилю статус `SKIPPED_NO_RUNTIME`.

### 2. Запуск локальной LLM модели
Запустите сервер инференса с моделью Qwen 3.5 (например, на порту 8765):
```bash
# Пример для Qwen 3.5 4B Unloop (CUDA GPU оффлоад 100%):
llama-server -m Qwen3.5-4B-Unloop-Q4_K_M.gguf \
  --host 127.0.0.1 --port 8765 \
  -ngl 36 -c 4096 -t 8 --parallel 1 -rea on
```

### 3. Запуск полного прогона бенчмарка (7 языков × 10 задач)
```bash
./gradlew :benchmark:run
```
Раннер выполняет:
1. Опрос доступности локальной LLM на `http://127.0.0.1:8765/v1`.
2. Последовательную подачу 10 аппаратных задач на 7 языковых профилей (`tdscript_base`, `tdscript_experimental`, `python`, `cpp`, `lua`, `javascript`, `kotlin`).
3. Замкнутый цикл ремонта (Agentic Loop до 3 попыток): если процесс вернул синтаксическую ошибку, `SCOPE_VIOLATION` или неверный вывод, модель получает диагностику (`E104@line:col` для TDscript Experimental, многострочный JSON для Base, либо `stderr` для внешних языков) и производит автоисправление.
4. Экспорт сводной таблицы, журнала сбоев (`benchmark_failures.log`) и полной трассировки каждого запроса (`benchmark_full_trace.jsonl`).

### 4. Оффлайн-тесты раннера (Dry Run)
Для проверки логики раннера и моков без подключения к LLM:
```bash
./gradlew :benchmark:test
```

---

## 📐 Формальное определение метрик бенчмарка

| Метрика | Определение и формула расчёта |
|:---|:---|
| **First-Pass %** | Доля задач, решённых моделью с 1-й попытки без единой ошибки: $\frac{N_{\text{first-pass}}}{N_{\text{total}}} \times 100\%$ |
| **Final Success %** | Итоговая доля задач, успешно сданных за $\le 3$ попыток: $\frac{N_{\text{success}}}{N_{\text{total}}} \times 100\%$ |
| **Avg Repair Iters** | Среднее число итераций исправления на успешно сданную задачу: $$\text{AvgRepairIters} = \frac{\sum_{i \in \text{Success}} (\text{attemptsCount}_i - 1)}{N_{\text{success}}}$$ *Примечание*: для задачи, сданной с 1-й попытки, число ремонтов равно **0** ($1 - 1 = 0$). Поэтому при высоком First-Pass (как у JS 60%) среднее значение закономерно меньше 1.0 (например, **0.56**). |
| **Cost / Success** | Средний расход токенов (Prompt + Completion) на одно успешное решение: $\frac{\text{Total Prompt Tokens} + \text{Total Completion Tokens}}{N_{\text{success}}}$ |
| **Avg LOC** | Среднее количество строк чистого кода (без комментариев и пустых строк) среди решённых задач. |

---

## 📦 Сборка и дистрибуция TDscript Studio

### 1. Запуск в режиме рабочего стола (Linux / Windows)
```bash
./gradlew :desktopApp:run
```

### 2. Запуск в консольном Headless CLI режиме
```bash
./gradlew :desktopApp:run --args="--cli"
```

### 3. Сборка автономного исполняемого JAR для Linux
```bash
./gradlew :desktopApp:packageUberJarForCurrentOS
```
Готовый артефакт: `desktopApp/build/compose/jars/tdscript-studio-linux-x64-1.1.0.jar`.

### 4. Сборка мобильного APK для Android (USB-OTG + BLE)
```bash
./gradlew :androidApp:assembleDebug
```
Готовый артефакт: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
