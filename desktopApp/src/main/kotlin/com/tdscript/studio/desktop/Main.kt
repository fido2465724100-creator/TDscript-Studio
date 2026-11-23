package com.tdscript.studio.desktop

import com.tdscript.core.*
import com.tdscript.studio.editor.CodeProjector
import com.tdscript.studio.sandbox.AiHardwareSandbox
import com.tdscript.studio.ai.StudioAiDiagnostics

fun main(args: Array<String>) {
    println("================================================================================")
    println("🚀 TDscript Studio Desktop IDE (Linux / Windows) — v1.1.0 (Core 1.1)")
    println("================================================================================")

    // 1. Инициализация аппаратной песочницы
    val sandbox = AiHardwareSandbox(
        capabilities = Capabilities.I2C_READ or Capabilities.I2C_WRITE or Capabilities.PLC_READ,
        scope = CapabilityScope(allowedI2cAddresses = setOf(0x76), allowedModbusRegisterRanges = listOf(100..200))
    )

    println("🔍 Сканирование аппаратных адаптеров [probe()]:")
    val adapters = sandbox.probe()
    adapters.forEach { a ->
        println("   • [${a.type}] ${a.id}: ${a.details} (Available: ${a.available})")
    }

    // 2. Демонстрация мультиязычного редактора (Code Projector)
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

    // 3. Выполнение скрипта на Core 1.1 с чтением виртуального датчика Bosch BME280
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
