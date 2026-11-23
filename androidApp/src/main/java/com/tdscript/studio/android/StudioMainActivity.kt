package com.tdscript.studio.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tdscript.studio.editor.CodeProjector
import com.tdscript.studio.sandbox.AiHardwareSandbox

class StudioMainActivity : AppCompatActivity() {

    private val projector = CodeProjector()
    private val sandbox = AiHardwareSandbox()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Инициализация мобильного редактора TDscript Studio
    }
}
