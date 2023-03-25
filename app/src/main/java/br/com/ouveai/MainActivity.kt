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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.lang.Math.log10
import kotlin.math.pow

class MainActivity : AppCompatActivity() {
    private lateinit var mediaRecorder: MediaRecorder
    private var haPermission = false;
    private val referencia = 2e-5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (!this.haPermission) {
            // Solicita permissão do usuário
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    1
                )
            } else {
                this.haPermission = true
            }
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
                Toast.makeText(this, "Permission allowed to record audio", Toast.LENGTH_SHORT)
                    .show()
                this.haPermission = true
                this.startRecording()
            } else {
                Toast.makeText(this, "Permission denied to record audio", Toast.LENGTH_SHORT).show()
                this.haPermission = false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (this.haPermission) {
            this.startRecording()
        }
    }

    private fun startRecording() {
        mediaRecorder = MediaRecorder()
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        mediaRecorder.setOutputFile("/dev/null")
        mediaRecorder.prepare()
        mediaRecorder.start()
        updateDecibelTextView()

    }

    private fun updateDecibelTextView() {
        val amplitude = mediaRecorder.maxAmplitude.toDouble()
        //val db = 20 * kotlin.math.log10(amplitude / referencia) - 94
        val db = 10 * kotlin.math.log10(amplitude / 10.0.pow(-12))
        val decibelText = String.format("%.1f dB", db)
        val textView = findViewById<TextView>(R.id.textViewDecibel) as TextView
        textView.text = decibelText
        Handler().postDelayed({ updateDecibelTextView() }, 1000)
    }
}