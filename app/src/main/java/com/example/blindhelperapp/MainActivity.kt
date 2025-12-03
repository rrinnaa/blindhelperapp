package com.example.blindhelperapp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.blindhelperapp.ui.theme.BlindHelperAppTheme
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        setContent {
            BlindHelperAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                navController = navController,
                                onTestVoiceClick = { testVoice() }
                            )
                        }
                        composable("camera") { CameraScreen() }
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.forLanguageTag("ru-RU"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

            if (!ttsReady) showInstallDialog()
        } else {
            showInstallDialog()
        }
    }

    private fun testVoice() {
        if (!ttsReady) {
            showInstallDialog()
            return
        }

        tts?.speak("Синтез речи работает", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun showInstallDialog() {
        AlertDialog.Builder(this)
            .setTitle("Установка голосового модуля")
            .setMessage("Для работы приложения необходимо установить модуль синтеза речи.")
            .setPositiveButton("Установить") { _, _ ->
                val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                val pm = packageManager

                if (intent.resolveActivity(pm) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        this,
                        "Установите его вручную через Google Play.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }


    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
