package com.newoether.agora.ui.chat.bottombar

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun OfflineMic(enabled: Boolean, onText: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recording by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) recording = true
    }
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        try {
            withContext(Dispatchers.IO) {
                VoskSession(context).use { session ->
                    val size = maxOf(4096, AudioRecord.getMinBufferSize(
                        16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))
                    val audio = AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
                    try {
                        check(audio.state == AudioRecord.STATE_INITIALIZED)
                        val buffer = ByteArray(size)
                        audio.startRecording()
                        while (recording) {
                            val count = audio.read(buffer, 0, buffer.size)
                            if (count > 0) session.accept(buffer, count)?.let {
                                withContext(Dispatchers.Main) { onText(it) }
                            }
                        }
                        session.finish()?.let { withContext(Dispatchers.Main) { onText(it) } }
                    } finally {
                        if (audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) audio.stop()
                        audio.release()
                    }
                }
            }
        } catch (e: Exception) {
            message = "Vosk model unavailable or microphone failed"
        } finally {
            recording = false
        }
    }
    IconButton(enabled = enabled, onClick = {
        if (recording) recording = false
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) recording = true
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }, modifier = Modifier.size(40.dp)) {
        Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (recording) "Stop dictation" else "Start offline dictation")
    }
    if (message != null) androidx.compose.material3.AlertDialog(
        onDismissRequest = { message = null },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { message = null }) {
            androidx.compose.material3.Text("OK")
        } },
        text = { androidx.compose.material3.Text(message.orEmpty()) })
}
