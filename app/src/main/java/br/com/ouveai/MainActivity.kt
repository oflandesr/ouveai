package br.com.ouveai

import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {

    // Inicia a instancia do firebase para insersao na base
    private val fireStoreDatabase = FirebaseFirestore.getInstance()

    // Variavel de controle de permissao de gravacao
    private var hasAudioPermission: Boolean = false

    // Variavel de controle de permissao de localizacao
    private var hasLocationPermission: Boolean = false

    // Thread que ficara ouvindo os dados e enviando ao banco
    private var averageMessenger: Thread? = null

    // Variaveis para leitura de audio e calculo de conversao de decibel
    private var recorder: MediaRecorder? = null
    private val dbValRef = 2e-5
    private val dbLimit: Double = 65.0
    private var isRecording = false
    private val recordingFilePath: String by lazy {
        "${externalCacheDir?.absolutePath}/recording.3gp"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissions(arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ))
        prepareRecButton()
        prepareSOSButton()
    }

    private fun prepareRecButton() {
        val button = findViewById<Button>(R.id.buttonRec)
        button.setOnClickListener {
            if (hasAudioPermission) {
                if (!isRecording) {
                    isRecording = true
                    button.text = "Parar"
                    startRecording()
                } else {
                    isRecording = false
                    button.text = "Iniciar"
                    stopRecording()
                }
            } else {
                requestPermissions(arrayOf(
                    android.Manifest.permission.RECORD_AUDIO
                ))
            }
        }
    }

    private fun prepareSOSButton() {
        val button = findViewById<Button>(R.id.buttonSOS)
        button.setOnClickListener {
            if (hasLocationPermission){
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                val alert = hashMapOf<String, Any>(
                    "criadoEm" to LocalDateTime.now().format(formatter)
                )
                this.addLatLngAndSendToFirebase(alert, "alerta")
            }else{
                // Solicita permissoes de localizacao
                requestPermissions(arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    private fun requestPermissions(permissions: Array<out String>) {
        ActivityCompat.requestPermissions(
            this, permissions, 1
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1 && grantResults.isNotEmpty()){
            // Sempre
            // 1 - permissao do microfone
            // 2 - permissoes de localizacao
            // 3 - permissoes do microfone e de localizacao
            when (permissions.size) {
                1 -> {
                    this.hasAudioPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED
                }
                2 -> {
                    this.hasLocationPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED
                }
                3 -> {
                    this.hasAudioPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    this.hasLocationPermission = grantResults[1] == PackageManager.PERMISSION_GRANTED && grantResults[2] == PackageManager.PERMISSION_GRANTED
                }
            }
        }else{
            this.hasAudioPermission = false
            this.hasLocationPermission = false
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
        startNotifier()
        updateDecibelTextView(calculateDec())
    }

    private fun startNotifier() {
        this.averageMessenger = thread {

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            var decibel = 0.0
            var sum = 0.0
            var counter = 1
            var limiter = 5

            // Cria o Handler para ser executado na thread principal
            // Ele eh responsavel por aguardar o carregamento da localizacao
            // e o posterior envio ao firebase.
            // Este handler foi necessario por conta do calculo da media ter de ser
            // executado enquanto o valor esta sendo exibido, ou seja, paralelamente
            val handler = Handler(Looper.getMainLooper())

            while (!Thread.currentThread().isInterrupted) {

                // Le os dados do mic
                val amplitude = recorder?.maxAmplitude?.toDouble()
                if (amplitude != null && amplitude > 0) decibel =
                    20 * kotlin.math.log10(amplitude?.div(dbValRef) ?: 0.0) - 94

                sum += decibel
                // Verifica se ja eh para enviar os dados
                if (counter == 5) {

                    val alert = hashMapOf<String, Any>(
                        "criadoEm" to LocalDateTime.now().format(formatter),
                        "dbAtual" to decibel,
                        "dbMedia" to sum / limiter
                    )

                    // Inicia a funcao que popula o alerta com os dados da localizacao
                    // e envia ao firebase. Esta eh uma operacao que precisa ser executada
                    // dentro do handler por conta da necessidade de aguardar o resultado
                    // da geolocalizacao.
                    handler.post {
                        this.addLatLngAndSendToFirebase(alert, "media")
                    }

                    // Reinicia o contador
                    counter = 0
                    // Reinicia a soma
                    sum = 0.0
                }

                // Faco a thread aguardar 1 segundo para somar os valores de decibeis
                try {
                    Thread.sleep(1000)
                } catch (e: Exception) {
                    Thread.currentThread().interrupt()
                }

                // Incrementa o contador
                counter++
            }
        }
    }

    private fun addLatLngAndSendToFirebase(alert: HashMap<String, Any>, collectionPath: String) {

        // Checa se as permissoes de localizacao foram aceitas
        // Se nao, solicita.
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            var latLng = hashMapOf<String, Any>(
                "latitude" to 0.0,
                "longitude" to 0.0
            )

            // Funcao callback que aguarda o resultado da geracao da geolocalizacao
            var mLocationCallback: LocationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    // Se o resultado da geolocalizacao for nulo significa que
                    // ela esta desabilitada ou as permissoes nao estao aceitas
                    if (locationResult == null) {
                        return
                    }
                    // Itera dentro das localizacoes carregadas (pode ser mais de uma por conta
                    // do dispositivo estar em movimento) e salva a primeira latitude e longitude
                    // que nao seja nula
                    for (location in locationResult.locations) {
                        if (location != null) {
                            latLng["latitude"] = location.latitude
                            latLng["longitude"] = location.longitude
                            return
                        }
                    }
                }
            }

            // Inicia o listner da localizacao do dispositivo informando uma requisicao
            // localizacao e tratando a resposta no callback anterior informado como parametro
            // do listner
            LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(
                LocationRequest.create(),
                mLocationCallback,
                null
            ).addOnCompleteListener() {
                // Ao finalizar o listner logo a latitude e longitude capturados e
                // chamo a funcao de envio para o firebase
                Log.d(TAG, "Lat and Long results: $latLng")
                alert["latLng"] = latLng
                this.sendToFirebase(alert, collectionPath)
            }
        }else{
            // Solicita as permissoes de localizacao
            requestPermissions(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun sendToFirebase(alert: HashMap<String, Any>, collectionPath: String) {

        // Cria um formatador para datas
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        // Adiciono a data atual ao payload
        alert["criadoEm"] = LocalDateTime.now().format(formatter)

        // Envia o dado para o firebase
        fireStoreDatabase.collection(collectionPath)
            .add(alert).addOnSuccessListener {

                // Informa no log que a criacao da informacao no firebase funcionou
                Log.d(TAG, "Added document with ID ${it.id}")
                //Toast.makeText(this, "Dado enviado!", Toast.LENGTH_SHORT).show()

            }.addOnFailureListener { exception ->

                // Informa no log que a criacao da informacao no firebase falhou
                Log.w(TAG, "Error adding document $exception")

            }
    }

    private fun updateDecibelTextView(db: Double) {
        val notificationText = findViewById<TextView>(R.id.textViewNotification) as TextView
        val decibelTextView = findViewById<TextView>(R.id.textViewDecibel) as TextView
        if (db > this.dbLimit) {
            notificationText.visibility = View.VISIBLE
        } else {
            notificationText.visibility = View.INVISIBLE
        }

        decibelTextView.text = String.format("%.1f dB", db)
        if (isRecording) {
            Handler().postDelayed({ updateDecibelTextView(calculateDec()) }, 100)
        }
    }

    private fun calculateDec(): Double {
        val amplitude = recorder?.maxAmplitude?.toDouble()
        if (amplitude != null && amplitude > 0) return 20 * kotlin.math.log10(
            amplitude?.div(
                dbValRef
            ) ?: 0.0
        ) - 94
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