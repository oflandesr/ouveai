package br.com.ouveai

import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat


class MainActivity : AppCompatActivity() {

    private val requiredPermissions = arrayOf(android.Manifest.permission.RECORD_AUDIO)
    private var hasPermission: Boolean = false

    private var recorder: MediaRecorder? = null
    private val dbValRef = 2e-5
    private var isRecording = false

    companion object {
        const val REQUEST_RECORD_AUDIO_PERMISSION = 201
    }

    private val recordingFilePath: String by lazy {
        "${externalCacheDir?.absolutePath}/recording.3gp"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestAudioPermission()
        prepareRecButton()
    }

    private fun prepareRecButton() {
        val button = findViewById<Button>(R.id.buttonRec)
        button.setOnClickListener {
            if (hasPermission) {
                if (!isRecording) {
                    isRecording = true
                    button.text = "Parar"
                    startRecording()
                    Toast.makeText(this, "Recording started!", Toast.LENGTH_SHORT).show()
                } else {
                    isRecording = false
                    button.text = "Iniciar"
                    stopRecording()
                    Toast.makeText(this, "Recording stop!", Toast.LENGTH_SHORT).show()
                }
            } else {
                requestAudioPermission()
            }
        }
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this, requiredPermissions, REQUEST_RECORD_AUDIO_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val audioRecordPermissionGranted =
            requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED

        hasPermission = if (!audioRecordPermissionGranted) {
            Toast.makeText(this, "Permission denied to record audio", Toast.LENGTH_SHORT).show()
            //finish()
            false
        } else {
            Toast.makeText(this, "Permission allowed to record audio", Toast.LENGTH_SHORT).show()
            true
            //startRecording()
        }
    }

    private fun startRecording() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(recordingFilePath)
            prepare()
        }
        recorder?.start()
        updateDecibelTextView(calculateDec())
    }

    private fun updateDecibelTextView(db: Double) {
        val textView = findViewById<TextView>(R.id.textViewDecibel) as TextView
        var decibelText = String.format("%.1f dB", db)
        textView.text = decibelText
        if (isRecording) {
            Handler().postDelayed({ updateDecibelTextView(calculateDec()) }, 200)
        }
    }

    private fun calculateDec(): Double {
        val amplitude = recorder?.maxAmplitude?.toDouble()
        if (amplitude != null && amplitude > 0)
            return 20 * kotlin.math.log10(amplitude?.div(dbValRef) ?: 0.0) - 94
        return 0.0
    }

    private fun stopRecording() {
        recorder?.run {
            stop()
            release()
        }
        recorder = null
    }
}