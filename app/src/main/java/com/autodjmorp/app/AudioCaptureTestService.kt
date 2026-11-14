package com.autodjmorp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.content.pm.ServiceInfo
import kotlin.math.sqrt

class AudioCaptureTestService : Service() {

    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("projection_result_code", 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("projection_data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("projection_data")
        }

        if (resultCode == 0 || data == null) {
            saveResult("NO_AUDIO", "No se recibió autorización para probar el audio.")
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto DJ: prueba de audio")
            .setContentText("Comprobando si Morphe permite capturar audio")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            beginCaptureTest()
        } catch (e: Exception) {
            saveResult("NO_AUDIO", "Android no permitió iniciar la captura de audio: ${e.message ?: "error"}")
            stopNow()
        }
        return START_NOT_STICKY
    }

    private fun beginCaptureTest() {
        val activeProjection = projection ?: throw IllegalStateException("No hay permiso de captura")
        val sampleRate = 44100
        val channelMask = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)
        if (minBuffer <= 0) throw IllegalStateException("Formato de audio no disponible")

        val format = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()

        val config = android.media.AudioPlaybackCaptureConfiguration.Builder(activeProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val audioRecord = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuffer * 2, sampleRate))
            .setAudioPlaybackCaptureConfig(config)
            .build()

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IllegalStateException("Android no pudo inicializar el capturador")
        }

        recorder = audioRecord
        audioRecord.startRecording()
        running = true

        Thread {
            val buffer = ShortArray(2048)
            var capturedSamples = 0L
            var peakRms = 0.0
            val start = System.currentTimeMillis()

            try {
                while (running && System.currentTimeMillis() - start < 8000) {
                    val count = audioRecord.read(buffer, 0, buffer.size)
                    if (count > 0) {
                        var sum = 0.0
                        for (i in 0 until count) {
                            val sample = buffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / count)
                        if (rms > peakRms) peakRms = rms
                        if (rms > 80.0) capturedSamples += count
                    }
                }

                if (capturedSamples > 0L) {
                    saveResult("CAPTURED", "Se recibió señal de audio de la reproducción.")
                } else {
                    saveResult(
                        "NO_AUDIO",
                        "No se recibió audio de Morphe. Puede estar bloqueando la captura o no había sonido durante la prueba."
                    )
                }
            } catch (e: Exception) {
                saveResult("NO_AUDIO", "Falló la lectura de audio: ${e.message ?: "error"}")
            } finally {
                stopNow()
            }
        }.start()
    }

    private fun saveResult(result: String, detail: String) {
        getSharedPreferences("AutoDJMusic", MODE_PRIVATE).edit()
            .putString("capture_test_result", result)
            .putString("capture_test_detail", detail)
            .apply()
    }

    private fun stopNow() {
        running = false
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Prueba de audio",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        running = false
        try { recorder?.release() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "autodj_audio_test"
        private const val NOTIFICATION_ID = 4102
    }
}
