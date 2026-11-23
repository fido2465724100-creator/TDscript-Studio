package com.tdscript.studio.android

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tdscript.core.Interpreter
import com.tdscript.core.Lexer
import com.tdscript.core.Parser
import com.tdscript.studio.agent.AiAgentController
import com.tdscript.studio.agent.OpenAiCompatibleLlmClient
import com.tdscript.studio.editor.CodeProjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StudioMainActivity : AppCompatActivity() {

    private val projector = CodeProjector()
    private lateinit var hostSpi: AndroidHostSPI

    private lateinit var editorView: EditText
    private lateinit var stdoutView: TextView
    private lateinit var diagView: TextView
    private lateinit var hardwareView: TextView
    private lateinit var agentStatusView: TextView
    private lateinit var agentTaskInput: EditText
    private lateinit var llmUrlInput: EditText
    private lateinit var llmModelInput: EditText

    private var currentLang = "ru"
    private var lastAgentCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hostSpi = AndroidHostSPI(context = this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // Header
        val header = TextView(this).apply {
            text = "🛠️ TDscript Studio Mobile (Core 1.1)"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(24, 24, 24, 16)
        }
        root.addView(header)

        // Tab Buttons
        val tabLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(8, 8, 8, 8)
        }

        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val pageEditor = createEditorPage()
        val pageAgent = createAgentPage()
        val pageHardware = createHardwarePage()
        val pageConsole = createConsolePage()

        container.addView(pageEditor)
        container.addView(pageAgent)
        container.addView(pageHardware)
        container.addView(pageConsole)

        fun selectTab(page: View) {
            pageEditor.visibility = View.GONE
            pageAgent.visibility = View.GONE
            pageHardware.visibility = View.GONE
            pageConsole.visibility = View.GONE
            page.visibility = View.VISIBLE
        }

        selectTab(pageEditor)

        fun createTabBtn(label: String, target: View) = Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(4, 0, 4, 0)
            layoutParams = lp
            setOnClickListener { selectTab(target) }
        }

        tabLayout.addView(createTabBtn("Код", pageEditor))
        tabLayout.addView(createTabBtn("ИИ-Агент", pageAgent))
        tabLayout.addView(createTabBtn("Шины", pageHardware))
        tabLayout.addView(createTabBtn("Вывод", pageConsole))

        root.addView(tabLayout)
        root.addView(container)

        setContentView(root)
    }

    private fun createEditorPage(): View {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Language projection row
        val langRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val langLabel = TextView(this).apply {
            text = "Проекция языка:"
            setTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(0, 8, 16, 8)
        }
        langRow.addView(langLabel)

        fun createLangBtn(lang: String, flag: String) = Button(this).apply {
            text = "$lang $flag"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            setOnClickListener {
                editorView.setText(projector.projectForDisplay(editorView.text.toString(), lang))
                currentLang = lang
                Toast.makeText(this@StudioMainActivity, "Проекция: $lang", Toast.LENGTH_SHORT).show()
            }
        }
        langRow.addView(createLangBtn("ru", "🇷🇺"))
        langRow.addView(createLangBtn("en", "🇬🇧"))
        langRow.addView(createLangBtn("de", "🇩🇪"))
        layout.addView(langRow)

        // Code Editor
        editorView = EditText(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(Color.parseColor("#00E676"))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(16, 16, 16, 16)
            minLines = 14
            setText(
                """
                language: ru;
                // Чтение температуры котла и датчика BME280
                пусть chip = 118
                пусть boilerTemp = PLC.modbusRead("127.0.0.1", 100)
                пусть pumpStatus = PLC.modbusRead("127.0.0.1", 101)
                
                если (boilerTemp > 200) {
                    пусть bme = I2C.read(chip, 208, 1)
                    print("Температура и датчик в норме")
                }
                """.trimIndent()
            )
        }
        layout.addView(editorView)

        // Run button
        val runBtn = Button(this).apply {
            text = "▶ Запустить в песочнице"
            setBackgroundColor(Color.parseColor("#00E676"))
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { runCode() }
        }
        layout.addView(runBtn)

        scroll.addView(layout)
        return scroll
    }

    private fun createAgentPage(): View {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(this).apply {
            text = "🤖 Автономный ИИ-агент (Agentic Loop)"
            setTextColor(Color.parseColor("#00B0FF"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        layout.addView(title)

        val desc = TextView(this).apply {
            text = "Генерирует код, тестирует в песочнице и устраняет ошибки сам."
            setTextColor(Color.GRAY)
            textSize = 12f
            setPadding(0, 4, 0, 12)
        }
        layout.addView(desc)

        // LLM URL & Model
        llmUrlInput = EditText(this).apply {
            hint = "LLM URL (напр. http://10.0.2.2:11434/v1)"
            setText("http://10.0.2.2:11434/v1")
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(16, 16, 16, 16)
        }
        layout.addView(llmUrlInput)

        llmModelInput = EditText(this).apply {
            hint = "Модель (llama3, qwen, etc.)"
            setText("llama3")
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(16, 16, 16, 16)
        }
        layout.addView(llmModelInput)

        // Task input
        agentTaskInput = EditText(this).apply {
            hint = "Инженерная задача"
            setText("Прочитай температуру с BME280 и статус насоса по Modbus")
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#222222"))
            setPadding(16, 16, 16, 16)
            minLines = 3
        }
        layout.addView(agentTaskInput)

        val generateBtn = Button(this).apply {
            text = "✨ Сгенерировать и проверить в песочнице"
            setBackgroundColor(Color.parseColor("#00B0FF"))
            setTextColor(Color.WHITE)
            setOnClickListener { runAgentLoop() }
        }
        layout.addView(generateBtn)

        agentStatusView = TextView(this).apply {
            text = "Агент ожидает задачу..."
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(8, 12, 8, 12)
        }
        layout.addView(agentStatusView)

        val pasteBtn = Button(this).apply {
            text = "📋 Вставить проверенный код в редактор"
            setBackgroundColor(Color.parseColor("#333333"))
            setTextColor(Color.parseColor("#00E676"))
            setOnClickListener {
                if (lastAgentCode.isNotBlank()) {
                    editorView.setText(lastAgentCode)
                    Toast.makeText(this@StudioMainActivity, "Код перенесен в редактор", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(pasteBtn)

        scroll.addView(layout)
        return scroll
    }

    private fun createHardwarePage(): View {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(this).apply {
            text = "🔍 Аппаратные шины и регистры"
            setTextColor(Color.parseColor("#00B0FF"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        layout.addView(title)

        val probeBtn = Button(this).apply {
            text = "Обновить список шин [probe()]"
            setBackgroundColor(Color.parseColor("#2979FF"))
            setTextColor(Color.WHITE)
            setOnClickListener { updateHardwareInfo() }
        }
        layout.addView(probeBtn)

        hardwareView = TextView(this).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(8, 12, 8, 12)
        }
        layout.addView(hardwareView)

        updateHardwareInfo()
        scroll.addView(layout)
        return scroll
    }

    private fun createConsolePage(): View {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val outTitle = TextView(this).apply {
            text = "📤 Вывод консоли (Stdout):"
            setTextColor(Color.parseColor("#00E676"))
            setTypeface(null, Typeface.BOLD)
        }
        layout.addView(outTitle)

        stdoutView = TextView(this).apply {
            text = "Вывод пуст"
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(8, 8, 8, 16)
        }
        layout.addView(stdoutView)

        val diagTitle = TextView(this).apply {
            text = "🛡️ Диагностика и безопасность:"
            setTextColor(Color.parseColor("#FF5252"))
            setTypeface(null, Typeface.BOLD)
        }
        layout.addView(diagTitle)

        diagView = TextView(this).apply {
            text = "Ошибок нет"
            setTextColor(Color.LTGRAY)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(8, 8, 8, 16)
        }
        layout.addView(diagView)

        scroll.addView(layout)
        return scroll
    }

    private fun runCode() {
        hostSpi.stdoutLines.clear()
        hostSpi.diagnosticLines.clear()

        try {
            val code = editorView.text.toString()
            val lexer = Lexer(code)
            val tokens = lexer.tokenize()
            val parser = Parser(tokens)
            val program = parser.parse()
            val interpreter = Interpreter(hostSpi)
            interpreter.execute(program)

            stdoutView.text = if (hostSpi.stdoutLines.isEmpty()) {
                "Успешно выполнен.\nПеременные: ${interpreter.globals}"
            } else {
                hostSpi.stdoutLines.joinToString("\n") + "\n\nПеременные: ${interpreter.globals}"
            }
            diagView.text = if (hostSpi.diagnosticLines.isEmpty()) "Чисто" else hostSpi.diagnosticLines.joinToString("\n")
            Toast.makeText(this, "Скрипт выполнен", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            stdoutView.text = "Сбой выполнения"
            diagView.text = "ОШИБКА: ${e.message}"
            Toast.makeText(this, "Ошибка выполнения", Toast.LENGTH_LONG).show()
        }
    }

    private fun runAgentLoop() {
        val task = agentTaskInput.text.toString()
        val url = llmUrlInput.text.toString()
        val model = llmModelInput.text.toString()

        agentStatusView.text = "⏳ Запуск Agentic Loop (Генерация ➔ Тестирование ➔ Автоисправление)..."

        lifecycleScope.launch {
            val client = OpenAiCompatibleLlmClient(baseUrl = url, modelName = model)
            val sandbox = com.tdscript.studio.sandbox.AiHardwareSandbox()
            val controller = AiAgentController(sandbox = sandbox, llmClient = client, maxFixAttempts = 3)

            val result = withContext(Dispatchers.IO) {
                controller.solveTask(task)
            }

            if (result.success) {
                lastAgentCode = result.finalCode
                agentStatusView.text = "✅ Успешно решено за ${result.iterations} итерации!\nИтоговый код:\n${result.finalCode}"
            } else {
                agentStatusView.text = "❌ Не удалось решить: ${result.error}"
            }
        }
    }

    private fun updateHardwareInfo() {
        val adapters = hostSpi.probe()
        val sb = StringBuilder()
        sb.append("Режим: ${hostSpi.mode}\n\n")
        sb.append("Обнаруженные адаптеры:\n")
        adapters.forEach { a ->
            sb.append("• [${a.type}] ${a.id}: ${a.details} (Статус: ${a.available})\n")
        }
        sb.append("\nВиртуальные регистры:\n")
        sb.append("• BME280 @ 0x76 (118): Chip ID = 0x60\n")
        sb.append("• Modbus PLC: Reg 100 = 245 (24.5°C), Reg 101 = 1 (Pump ON)\n")
        sb.append("• CapabilityScope: I2C={0x76}, Modbus={100..200}")
        hardwareView.text = sb.toString()
    }
}
