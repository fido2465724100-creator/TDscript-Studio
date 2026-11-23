package com.tdscript.studio.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.tdscript.core.*
import com.tdscript.studio.agent.AiAgentController
import com.tdscript.studio.agent.OpenAiCompatibleLlmClient
import com.tdscript.studio.editor.CodeProjector
import com.tdscript.studio.sandbox.AiHardwareSandbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment

fun main(args: Array<String>) {
    val isHeadless = GraphicsEnvironment.isHeadless() || args.contains("--cli")

    if (isHeadless) {
        runCliDemo()
    } else {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "TDscript Studio — AI Automation & Robotics IDE (Core 1.1)",
                state = WindowState(width = 1280.dp, height = 850.dp)
            ) {
                MaterialTheme(
                    colors = darkColors(
                        primary = Color(0xFF00E676),
                        primaryVariant = Color(0xFF00B0FF),
                        surface = Color(0xFF1E1E1E),
                        background = Color(0xFF121212),
                        onSurface = Color(0xFFE0E0E0),
                        onBackground = Color(0xFFE0E0E0)
                    )
                ) {
                    StudioDesktopApp()
                }
            }
        }
    }
}

fun runCliDemo() {
    println("================================================================================")
    println("🚀 TDscript Studio Desktop IDE (Linux / Windows) — v1.1.0 (Core 1.1)")
    println("================================================================================")

    val sandbox = AiHardwareSandbox(
        capabilities = Capabilities.I2C_READ or Capabilities.I2C_WRITE or Capabilities.PLC_READ or Capabilities.PLC_WRITE or Capabilities.SERIAL_RAW,
        scope = CapabilityScope(allowedI2cAddresses = setOf(0x76), allowedModbusRegisterRanges = listOf(100..200))
    )

    println("🔍 Сканирование аппаратных адаптеров [probe()]:")
    val adapters = sandbox.probe()
    adapters.forEach { a ->
        println("   • [${a.type}] ${a.id}: ${a.details} (Available: ${a.available})")
    }

    println("\n🌐 Демонстрация проекции кода на родной язык разработчика (RU):")
    val projector = CodeProjector()
    val canonicalCode = """
        let chip = 118
        let status = true
        if (status) {
            let data = I2C.read(chip, 208, 1)
        }
    """.trimIndent()
    val projectedRu = projector.projectForDisplay(canonicalCode, "ru")
    println(projectedRu)

    println("\n⚡ Выполнение скрипта в безопасной песочнице (AI Sandbox):")
    val lexer = Lexer(projectedRu)
    val tokens = lexer.tokenize()
    val parser = Parser(tokens)
    val program = parser.parse()
    val interpreter = Interpreter(sandbox)
    interpreter.execute(program)

    println("   • Global vars: ${interpreter.globals}")
    println("   • Diagnostics log: ${sandbox.diagnosticLines}")
    println("\n✅ TDscript Studio Desktop Engine успешно запущен и готов к работе!")
}

@Composable
fun StudioDesktopApp() {
    val coroutineScope = rememberCoroutineScope()
    val projector = remember { CodeProjector() }

    val sandbox = remember {
        AiHardwareSandbox(
            capabilities = Capabilities.I2C_READ or Capabilities.I2C_WRITE or Capabilities.PLC_READ or Capabilities.PLC_WRITE or Capabilities.SERIAL_RAW,
            scope = CapabilityScope(allowedI2cAddresses = setOf(0x76), allowedModbusRegisterRanges = listOf(100..200))
        )
    }

    var codeText by remember {
        mutableStateOf(
            """
            language: ru;
            // Сценарий автоматизации котла и климат-контроля
            пусть chip = 118
            пусть boilerTemp = PLC.modbusRead("127.0.0.1", 100)
            пусть pumpStatus = PLC.modbusRead("127.0.0.1", 101)
            
            если (boilerTemp > 240) {
                print("Температура в норме, опрос BME280...")
                пусть bmeId = I2C.read(chip, 208, 1)
                print("Chip ID прочитан успешно")
            }
            """.trimIndent()
        )
    }

    var currentLang by remember { mutableStateOf("ru") }
    var stdoutText by remember { mutableStateOf("Готов к выполнению скриптов...") }
    var diagnosticsText by remember { mutableStateOf("Ошибок не обнаружено. Scope: I2C (0x76), Modbus (100..200)") }
    var bottomTab by remember { mutableStateOf("stdout") }

    // Настройки LLM
    var llmUrl by remember { mutableStateOf("http://127.0.0.1:11434/v1") }
    var llmModel by remember { mutableStateOf("llama3") }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Агент
    var agentTask by remember { mutableStateOf("Прочитай температуру с BME280 и статус насоса по Modbus") }
    var agentStatus by remember { mutableStateOf("Агент готов") }
    var isAgentRunning by remember { mutableStateOf(false) }
    var lastAgentCode by remember { mutableStateOf("") }
    var agentStepsSummary by remember { mutableStateOf("") }

    fun runCurrentCode() {
        sandbox.stdoutLines.clear()
        sandbox.diagnosticLines.clear()
        try {
            val lexer = Lexer(codeText)
            val tokens = lexer.tokenize()
            val parser = Parser(tokens)
            val program = parser.parse()
            val interpreter = Interpreter(sandbox)
            interpreter.execute(program)

            stdoutText = if (sandbox.stdoutLines.isEmpty()) {
                "Скрипт выполнен успешно (нет вывода print).\nГлобальные переменные: ${interpreter.globals}"
            } else {
                sandbox.stdoutLines.joinToString("\n") + "\n\nГлобальные переменные: ${interpreter.globals}"
            }

            diagnosticsText = if (sandbox.diagnosticLines.isEmpty()) {
                "Диагностический канал чист. Нарушений Scope и прав нет."
            } else {
                sandbox.diagnosticLines.joinToString("\n")
            }
        } catch (e: Throwable) {
            diagnosticsText = "ОШИБКА ВЫПОЛНЕНИЯ:\n${e.message ?: e.toString()}"
            stdoutText = "Выполнение прервано из-за ошибки."
            bottomTab = "diag"
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF181818))) {
        // 1. Верхняя панель (Toolbar)
        TopAppBar(
            backgroundColor = Color(0xFF212121),
            contentColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛠️ TDscript Studio", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF00E676))
                    Spacer(Modifier.width(12.dp))
                    Text("v1.1.0 (Core 1.1)", fontSize = 12.sp, color = Color.Gray)
                }
            },
            actions = {
                // Переключатель языка проекции
                Text("Язык кода:", fontSize = 13.sp, color = Color.LightGray, modifier = Modifier.align(Alignment.CenterVertically))
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = {
                        codeText = projector.projectForDisplay(codeText, "ru")
                        currentLang = "ru"
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = if (currentLang == "ru") Color(0xFF00B0FF) else Color(0xFF333333)),
                    modifier = Modifier.padding(horizontal = 2.dp).height(32.dp)
                ) {
                    Text("RU 🇷🇺", fontSize = 12.sp, color = Color.White)
                }
                Button(
                    onClick = {
                        codeText = projector.projectForDisplay(codeText, "en")
                        currentLang = "en"
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = if (currentLang == "en") Color(0xFF00B0FF) else Color(0xFF333333)),
                    modifier = Modifier.padding(horizontal = 2.dp).height(32.dp)
                ) {
                    Text("EN 🇬🇧", fontSize = 12.sp, color = Color.White)
                }
                Button(
                    onClick = {
                        codeText = projector.projectForDisplay(codeText, "de")
                        currentLang = "de"
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = if (currentLang == "de") Color(0xFF00B0FF) else Color(0xFF333333)),
                    modifier = Modifier.padding(horizontal = 2.dp).height(32.dp)
                ) {
                    Text("DE 🇩🇪", fontSize = 12.sp, color = Color.White)
                }

                Spacer(Modifier.width(16.dp))

                // Кнопка запуска
                Button(
                    onClick = { runCurrentCode() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF00E676)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("▶ Запуск (Sandbox)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(Modifier.width(8.dp))

                // Настройки LLM
                Button(
                    onClick = { showSettingsDialog = true },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF424242)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("⚙ LLM", fontSize = 12.sp, color = Color.White)
                }
                Spacer(Modifier.width(8.dp))
            }
        )

        // 2. Рабочая область (Split View: Редактор + ИИ-Агент)
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Левая часть: Редактор кода (60%)
            Column(
                modifier = Modifier.weight(0.6f).fillMaxHeight()
                    .border(1.dp, Color(0xFF2A2A2A))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text("📄 Исходный код [Проекция: ${currentLang.uppercase()}]", color = Color(0xFF00E676), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${codeText.lines().size} строк", color = Color.Gray, fontSize = 11.sp)
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    BasicTextField(
                        value = codeText,
                        onValueChange = { codeText = it },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFFD4D4D4),
                            lineHeight = 18.sp
                        ),
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    )
                }
            }

            // Правая часть: Автономный ИИ-агент (40%)
            Column(
                modifier = Modifier.weight(0.4f).fillMaxHeight()
                    .background(Color(0xFF161616))
                    .border(1.dp, Color(0xFF2A2A2A))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text("🤖 Автономный ИИ-агент", color = Color(0xFF00B0FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(llmModel, color = Color.Gray, fontSize = 11.sp)
                }
                Text("Замкнутый цикл: Генерация ➔ Песочница ➔ Самоисправление", color = Color.DarkGray, fontSize = 11.sp)

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = agentTask,
                    onValueChange = { agentTask = it },
                    label = { Text("Инженерная задача", fontSize = 11.sp) },
                    textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                    modifier = Modifier.fillMaxWidth().height(85.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        focusedBorderColor = Color(0xFF00B0FF),
                        unfocusedBorderColor = Color(0xFF424242)
                    )
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        isAgentRunning = true
                        agentStatus = "Генерация и аудит в AiHardwareSandbox..."
                        coroutineScope.launch {
                            val client = OpenAiCompatibleLlmClient(
                                baseUrl = llmUrl,
                                modelName = llmModel
                            )
                            val controller = AiAgentController(sandbox = sandbox, llmClient = client, maxFixAttempts = 3)
                            val result = withContext(Dispatchers.IO) {
                                controller.solveTask(agentTask)
                            }
                            isAgentRunning = false
                            if (result.success) {
                                agentStatus = "✅ Успех! Код проверен в песочнице за ${result.iterations} итерации."
                                lastAgentCode = result.finalCode
                                agentStepsSummary = result.steps.joinToString("\n") { step ->
                                    "Итерация ${step.iteration}: " + if (step.success) "Чистый прогон без ошибок" else "Ошибка поймана и передана на исправление"
                                }
                            } else {
                                agentStatus = "❌ Ошибка: ${result.error ?: "Не удалось решить задачу за ${result.iterations} попыток"}"
                                lastAgentCode = result.finalCode
                                agentStepsSummary = result.steps.joinToString("\n") { step ->
                                    "Итерация ${step.iteration}: " + (step.errorDiagnostic ?: "Сбой")
                                }
                            }
                        }
                    },
                    enabled = !isAgentRunning,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF00B0FF)),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text(if (isAgentRunning) "⏳ Исполнение Agentic Loop..." else "✨ Сгенерировать и проверить", color = Color.White, fontSize = 12.sp)
                }

                Spacer(Modifier.height(6.dp))

                Text(agentStatus, color = if (agentStatus.startsWith("✅")) Color(0xFF00E676) else if (agentStatus.startsWith("❌")) Color(0xFFFF5252) else Color.LightGray, fontSize = 11.sp)

                if (agentStepsSummary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Журнал шагов автоисправления:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(agentStepsSummary, color = Color(0xFFBBBBBB), fontSize = 10.sp, modifier = Modifier.padding(vertical = 2.dp))
                }

                if (lastAgentCode.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            codeText = lastAgentCode
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF333333)),
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    ) {
                        Text("📋 Вставить проверенный код в редактор", color = Color(0xFF00E676), fontSize = 11.sp)
                    }
                }
            }
        }

        // 3. Нижняя консоль и монитор оборудования
        Column(
            modifier = Modifier.fillMaxWidth().height(210.dp)
                .background(Color(0xFF141414))
                .border(1.dp, Color(0xFF2A2A2A))
        ) {
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1F1F1F))) {
                TextButton(
                    onClick = { bottomTab = "stdout" },
                    colors = ButtonDefaults.textButtonColors(contentColor = if (bottomTab == "stdout") Color(0xFF00E676) else Color.Gray)
                ) {
                    Text("Вывод (Stdout)", fontSize = 12.sp)
                }
                TextButton(
                    onClick = { bottomTab = "diag" },
                    colors = ButtonDefaults.textButtonColors(contentColor = if (bottomTab == "diag") Color(0xFFFF5252) else Color.Gray)
                ) {
                    Text("Диагностика & Безопасность", fontSize = 12.sp)
                }
                TextButton(
                    onClick = { bottomTab = "hardware" },
                    colors = ButtonDefaults.textButtonColors(contentColor = if (bottomTab == "hardware") Color(0xFF00B0FF) else Color.Gray)
                ) {
                    Text("Аппаратные шины & Регистры", fontSize = 12.sp)
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())) {
                when (bottomTab) {
                    "stdout" -> {
                        Text(
                            text = stdoutText,
                            color = Color(0xFFEEEEEE),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                    "diag" -> {
                        Text(
                            text = diagnosticsText,
                            color = if (diagnosticsText.contains("ОШИБКА") || diagnosticsText.contains("VIOLATION")) Color(0xFFFF5252) else Color(0xFF81C784),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                    "hardware" -> {
                        Column {
                            Text("🔍 Доступные шины и адаптеры:", color = Color(0xFF00B0FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            sandbox.probe().forEach { a ->
                                Text("   • [${a.type}] ${a.id}: ${a.details} (Статус: ${if (a.available) "Активен" else "Недоступен"})", color = Color.White, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("📊 Состояние виртуальных регистров песочницы:", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("   • Bosch BME280 @ 0x76 (118): Chip ID = 0x60 (в регистре 0xD0), Temp/Pres в 0xF7..0xFE", color = Color.LightGray, fontSize = 11.sp)
                            Text("   • Modbus PLC Gateway @ 127.0.0.1:502: Reg 100 = ${sandbox.virtualModbusRegisters[100]} (24.5°C), Reg 101 = ${sandbox.virtualModbusRegisters[101]} (Насос: ВКЛ)", color = Color.LightGray, fontSize = 11.sp)
                            Text("   • Права доступа (CapabilityScope): I2C={0x76}, Modbus={100..200}", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // Диалог настроек LLM
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Настройки подключения к LLM", color = Color.White) },
            text = {
                Column {
                    Text("OpenAI-совместимый эндпоинт (Ollama, LM Studio или внешнее API):", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = llmUrl,
                        onValueChange = { llmUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = llmModel,
                        onValueChange = { llmModel = it },
                        label = { Text("Имя модели (напр. llama3, qwen, gpt-4o)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("Сохранить")
                }
            },
            backgroundColor = Color(0xFF252525)
        )
    }
}
