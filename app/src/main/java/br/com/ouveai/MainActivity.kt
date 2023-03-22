package br.com.ouveai

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.log10

class MainActivity : AppCompatActivity() {
    private lateinit var mediaRecorder: MediaRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mediaRecorder = MediaRecorder()
    }

    override fun onStart() {
        super.onStart()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
        }
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        mediaRecorder.setOutputFile("/dev/null")
        mediaRecorder.prepare()
        mediaRecorder.start()
        updateDecibelTextView()
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
            } else {
                // Permissão negada, não é possível gravar o áudio
            }
        }
    }

    private fun updateDecibelTextView() {
        val amplitude = mediaRecorder.maxAmplitude.toDouble()
        val db = 20 * log10(amplitude / 32767.0)
        val decibelText = String.format("%.1f dB", db)
        val textView = findViewById<TextView>(R.id.textViewDecibel) as TextView
        textView.text = decibelText
        Handler().postDelayed({ updateDecibelTextView() }, 100)
    }
}


/*class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}*/