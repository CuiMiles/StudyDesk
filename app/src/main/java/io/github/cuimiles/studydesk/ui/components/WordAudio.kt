package io.github.cuimiles.studydesk.ui.components

import io.github.cuimiles.studydesk.R
import androidx.compose.ui.res.painterResource
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

/** A card owns its engine: dispose/background stops speech; only an accepted utterance is marked played. */
@Composable
fun WordAudio(word: String, attemptId: String, alreadyPlayed: () -> Boolean, onAutoPlayed: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    DisposableEffect(context) {
        val tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) ready = true else error = "语音引擎不可用"
        }
        engine = tts
        onDispose { ready = false; tts.stop(); tts.shutdown(); engine = null }
    }
    DisposableEffect(lifecycle, engine) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) engine?.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun speak(): Boolean {
        val tts = engine ?: return false
        val voice = tts.voices?.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
            ?.sortedWith(compareBy({ if (it.locale == Locale.UK) 0 else 1 }, { it.name }))?.firstOrNull()
        if (voice == null) { error = "请安装英语离线语音包"; return false }
        tts.voice = voice
        val accepted = tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, attemptId) == TextToSpeech.SUCCESS
        error = if (accepted) "" else "播放失败，请重试"
        return accepted
    }
    LaunchedEffect(ready, attemptId) {
        if (ready && !alreadyPlayed() && speak()) onAutoPlayed()
    }
    IconButton(enabled = ready, onClick = { speak() }) {
        Icon(painterResource(R.drawable.ic_speaker), contentDescription = if (ready) "播放读音" else "语音准备中")
    }
    if (error.isNotBlank()) {
        Text(error, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = {
            try { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            catch (_: Exception) { error = "请在手机设置中安装英语文字转语音引擎" }
        }) { Text("安装语音") }
    }
}
