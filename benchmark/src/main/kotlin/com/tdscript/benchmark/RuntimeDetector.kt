package com.tdscript.benchmark

data class RuntimeInfo(
    val name: String,
    val command: String,
    val available: Boolean,
    val path: String?
)

object RuntimeDetector {
    fun detect(command: String): RuntimeInfo {
        return try {
            val process = ProcessBuilder("which", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val code = process.waitFor()
            if (code == 0 && output.isNotEmpty()) {
                RuntimeInfo(command, command, true, output)
            } else {
                RuntimeInfo(command, command, false, null)
            }
        } catch (e: Exception) {
            RuntimeInfo(command, command, false, null)
        }
    }
}
