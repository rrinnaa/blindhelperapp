package com.example.blindhelperapp

import android.graphics.Bitmap
import android.graphics.Rect
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Size
import android.widget.FrameLayout
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.*

// ЗАМЕНИТЕ НА ВАШ IP АДРЕС
private const val SERVER_URL = "http://IP:8000/describe"

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val client = remember {
        OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    var ttsReady by remember { mutableStateOf(false) }

    val tts = remember {
        var ttsObj: TextToSpeech? = null

        ttsObj = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {

                val langRes = ttsObj?.setLanguage(Locale("ru", "RU"))

                if (langRes == TextToSpeech.LANG_MISSING_DATA ||
                    langRes == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e("BLIP_LOG", "Русский язык не поддерживается")
                } else {
                    Log.d("BLIP_LOG", "TTS готов")
                    ttsReady = true
                }

            } else {
                Log.e("BLIP_LOG", "Ошибка инициализации TTS $status")
            }
        }

        ttsObj
    }

    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    var lastSpokenTime by remember { mutableStateOf(0L) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(factory = { ctx ->
        val previewView = PreviewView(ctx)
        previewView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val analyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            var isProcessingFrame = false

            analyzer.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { image ->
                val now = System.currentTimeMillis()
                if (now - lastSpokenTime < 5000 || isProcessingFrame) {
                    image.close()
                    return@setAnalyzer
                }

                isProcessingFrame = true
                val bitmap = image.toBitmap() ?: run {
                    image.close()
                    isProcessingFrame = false
                    return@setAnalyzer
                }

                scope.launch(Dispatchers.IO) {
                    try {
                        Log.d("BLIP_LOG", "Отправка на сервер...")
                        val description = sendToServer(bitmap, client)
                            ?: "Не удалось получить описание"
                        Log.d("BLIP_LOG", "Ответ: $description")

                        if (ttsReady) {
                            withContext(Dispatchers.Main) {
                                val id = UUID.randomUUID().toString()
                                tts.speak(description, TextToSpeech.QUEUE_FLUSH, null, id)
                            }
                        } else {
                            Log.e("BLIP_LOG", "TTS не готов, озвучка пропущена")
                        }

                        lastSpokenTime = System.currentTimeMillis()

                    } catch (e: Exception) {
                        Log.e("BLIP_LOG", "Ошибка: ${e.message}", e)
                    } finally {
                        image.close()
                        isProcessingFrame = false
                    }
                }
            }

            preview.setSurfaceProvider(previewView.surfaceProvider)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analyzer)
                Log.d("BLIP_LOG", "Камера запущена")
            } catch (e: Exception) {
                Log.e("BLIP_LOG", "Ошибка камеры: ${e.message}", e)
            }

        }, ContextCompat.getMainExecutor(ctx))

        previewView
    }, modifier = Modifier.fillMaxSize())
}

suspend fun sendToServer(bitmap: Bitmap, client: OkHttpClient): String? {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
    val byteArray = stream.toByteArray()

    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", "frame.jpg", byteArray.toRequestBody("image/jpeg".toMediaType()))
        .build()

    val request = Request.Builder()
        .url(SERVER_URL)
        .post(requestBody)
        .build()

    client.newCall(request).execute().use { response ->
        val body = response.body?.string()
        if (!response.isSuccessful || body == null) return null

        val json = JSONObject(body)
        return json.optString("caption_ru", json.optString("caption_en", "Нет описания"))
    }
}

fun ImageProxy.toBitmap(): Bitmap? {
    if (format != android.graphics.ImageFormat.YUV_420_888) return null
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val yuv = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), 100, out)
    return android.graphics.BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}
