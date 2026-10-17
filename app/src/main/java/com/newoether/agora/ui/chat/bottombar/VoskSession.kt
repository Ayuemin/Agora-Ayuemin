package com.newoether.agora.ui.chat.bottombar

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/** Offline speech recognition with a model installed under private app storage. */
internal class VoskSession(context: Context) : AutoCloseable {
    private val model = Model(File(context.filesDir, "vosk-model").absolutePath)
    private val recognizer = Recognizer(model, 16000f)

    fun accept(buffer: ByteArray, length: Int): String? {
        if (!recognizer.acceptWaveForm(buffer, length)) return null
        return JSONObject(recognizer.result).optString("text").takeIf { it.isNotBlank() }
    }

    fun finish(): String? =
        JSONObject(recognizer.finalResult).optString("text").takeIf { it.isNotBlank() }

    override fun close() {
        recognizer.close()
        model.close()
    }
}
