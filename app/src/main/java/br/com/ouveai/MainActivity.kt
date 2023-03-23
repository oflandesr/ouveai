package br.com.ouveai

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.lang.Math.log10

class MainActivity : AppCompatActivity() {
    private lateinit var mediaRecorder: MediaRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mediaRecorder = MediaRecorder()
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        mediaRecorder.setOutputFile("/dev/null")
    }

    override fun onStart() {
        super.onStart()
        // Solicita permissão do usuário
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissão concedida, inicie a gravação do áudio
                mediaRecorder.prepare()
                mediaRecorder.start()
                updateDecibelTextView()
            } else {
                // Permissão negada, não é possível gravar o áudio
            }
        }
    }

    private fun updateDecibelTextView() {
        val amplitude = mediaRecorder.maxAmplitude.toDouble()
        val db = 20 * kotlin.math.log10(amplitude / 32767.0)
        val decibelText = String.format("%.1f dB", db)
        val textView = findViewById<TextView>(R.id.textViewDecibel) as TextView
        textView.text = decibelText
        Handler().postDelayed({ updateDecibelTextView() }, 100)
    }
}