package br.com.ouveai

import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.lang.Exception
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MainActivity : AppCompatActivity() {

    private val requiredPermissions = arrayOf(android.Manifest.permission.RECORD_AUDIO)
    private var hasPermission: Boolean = false

    private var recorder: MediaRecorder? = null
    private val dbValRef = 2e-5
    private val dbLimit: Double = 65.0
    private var isRecording = false

    private fun prepareSOSButton(){
        val button = findViewById<Button>(R.id.sosButton)

        button.setOnClickListener{
            Toast.makeText(this, "Um alerta foi enviado!", Toast.LENGTH_SHORT).show()

            val fireStoreDatabase = FirebaseFirestore.getInstance()

            // create a dummy data
            val hashMap = hashMapOf<String, Any>(
                "name" to "John doe",
                "city" to "Nairobi",
                "age" to 24
            )

            // use the add() method to create a document inside users collection
            fireStoreDatabase.collection("users")
                .add(hashMap)
                .addOnSuccessListener {
                    Log.d(TAG, "Added document with ID ${it.id}")
                }
                .addOnFailureListener { exception ->
                    Log.w(TAG, "Error adding document $exception")
                }
            Toast.makeText(this, "Dado enviado!", Toast.LENGTH_SHORT).show()
        }
    }



    companion object {
        const val REQUEST_RECORD_AUDIO_PERMISSION = 201
    }

    private val recordingFilePath: String by lazy {
        "${externalCacheDir?.absolutePath}/recording.3gp"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestAudioPermission()
        prepareRecButton()
        prepareSOSButton()
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
        var dec = calculateDec()
        textView.text = decibelText
        if (isRecording) {
            Handler().postDelayed({ notification(dec,dbLimit) }, 1000)
            Handler().postDelayed({ updateDecibelTextView(dec) }, 1000)
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

    private fun notification(db: Double, reference: Double) {

        val notificationText = findViewById<TextView>(R.id.textViewNotification) as TextView

        if (db > reference) {
            notificationText.visibility = View.VISIBLE
        }
        else{
            notificationText.visibility = View.INVISIBLE
        }
    }
}