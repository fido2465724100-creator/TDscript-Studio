package com.tdscript.studio.ai

import com.tdscript.core.TransportAdapterInfo

data class CompilerErrorDiagnostic(
    val status: String = "ERROR",
    val compilerVersion: String = "1.1.0",
    val errorCode: String,
    val file: String,
    val line: Int,
    val column: Int,
    val message: String,
    val expected: String,
    val actual: String,
    val suggestion: String
) {
    fun toJson(): String {
        return """
            {
              "status": "$status",
              "compiler_version": "$compilerVersion",
              "error_code": "$errorCode",
              "file": "$file",
              "line": $line,
              "column": $column,
              "message": "$message",
              "expected": "$expected",
              "actual": "$actual",
              "suggestion": "$suggestion"
            }
        """.trimIndent()
    }
}

class StudioAiDiagnostics {
    fun generateDiscoveryJson(
        adapters: List<TransportAdapterInfo>,
        activeCapabilities: List<String>,
        grantedScopeSummary: String
    ): String {
        val adapterJsonList = adapters.joinToString(",") { a ->
            """{"id":"${a.id}","type":"${a.type}","available":${a.available},"details":"${a.details}"}"""
        }
        val capsJson = activeCapabilities.joinToString(",") { """"$it"""" }

        return """
            {
              "runtime": {
                "name": "TDscript Studio AI Environment",
                "version": "1.1.0",
                "target": "Desktop/Android Hybrid"
              },
              "capabilities": {
                "active": [$capsJson],
                "scope_summary": "$grantedScopeSummary"
              },
              "hardware_adapters": [$adapterJsonList]
            }
        """.trimIndent()
    }
}
