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
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {

    private val requiredPermissions = arrayOf(android.Manifest.permission.RECORD_AUDIO)
    private var hasPermission: Boolean = false

    private var recorder: MediaRecorder? = null
    private val dbValRef = 2e-5
    private val dbLimit: Double = 65.0
    private var isRecording = false

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

    @RequiresApi(Build.VERSION_CODES.O)
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun prepareSOSButton() {
        val button = findViewById<Button>(R.id.buttonSOS)
        button.setOnClickListener {
            sendAlert()
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startRecording() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(recordingFilePath)
            prepare()
        }
        recorder?.start()
        startNotifier()
        updateDecibelTextView(calculateDec())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendAlert(){

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val alerta = hashMapOf<String, Any>(
            "criadoEm" to LocalDateTime.now().format(formatter)
        )

        val fireStoreDatabase = FirebaseFirestore.getInstance()
        fireStoreDatabase.collection("alertas")
            .add(alerta)
            .addOnSuccessListener {
                Log.d(TAG, "Added document with ID ${it.id}")
                Toast.makeText(this, "Alerta de suspeito enviado!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error adding document $exception")
            }
    }

    // Thread que ficara ouvindo os dados e enviando ao banco
    private var averageMessenger: Thread? = null
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startNotifier(){
        this.averageMessenger = thread {
            val fireStoreDatabase = FirebaseFirestore.getInstance()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            var decibel = 0.0
            var sum = 0.0
            var counter = 1
            var limiter = 5

            // Enquanto a thread nao for interrompida ela ira:
            // 1 pegar o dado do microfone,
            // 2 efetuar a soma dos valores coletados a cada 1 segundo, 5 vezes
            // 3 calcular a media dos valores coletados
            // 4 enviar o valor calculado para o banco de dados
            while (!Thread.currentThread().isInterrupted) {

                // Le os dados do mic
                val amplitude = recorder?.maxAmplitude?.toDouble()
                if (amplitude != null && amplitude > 0)
                    decibel = 20 * kotlin.math.log10(amplitude?.div(dbValRef) ?: 0.0) - 94

                sum = sum+decibel
                // Verifica se ja eh para enviar os dados
                if(counter == 5){
                    // Prepara a mensagem com a data de insersao, valor decibel atual e valor decibel medio calculado
                    val alerta = hashMapOf<String, Any>(
                        "criadoEm" to LocalDateTime.now().format(formatter),
                        "dbAtual" to decibel,
                        "dbMedia" to sum/limiter
                    )

                    // Envia o alerta para o banco
                    fireStoreDatabase
                        .collection("media")
                        .add(alerta)
                        .addOnSuccessListener {
                            Log.d(TAG, "Added document with ID ${it.id}")
                        }
                        .addOnFailureListener { exception ->
                            Log.w(TAG, "Error adding document $exception")
                        }

                    // Reinicia o contador
                    counter = 1

                    // Reinicia a soma
                    sum = 0.0
                }

                // Faco a thread aguardar 1 segundo para somar os valores de decibeis
                try {
                    Thread.sleep(1000)
                }catch (e: Exception){
                    Thread.currentThread().interrupt()
                }

                // Incrementa o contador
                counter++
            }
        }
    }

    private fun updateDecibelTextView(db: Double) {
        val notificationText = findViewById<TextView>(R.id.textViewNotification) as TextView
        val decibelTextView = findViewById<TextView>(R.id.textViewDecibel) as TextView
        if (db > this.dbLimit) {
            notificationText.visibility = View.VISIBLE
        }
        else{
            notificationText.visibility = View.INVISIBLE
        }

        decibelTextView.text = String.format("%.1f dB", db)
        if (isRecording) {
            Handler().postDelayed({ updateDecibelTextView(calculateDec()) }, 100)
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

        // Encerra a thread que estava ouvindo os dados e enviando
        averageMessenger?.interrupt() // interrompe a thread
        averageMessenger?.join()
    }
}