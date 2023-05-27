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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.PolyUtil
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val fireStoreDatabase = FirebaseFirestore.getInstance()
    private val regionLimiter = mutableListOf<LatLng>()

    private var hasAudioPermission: Boolean = false
    private var hasLocationPermission: Boolean = false
    private var averageMessenger: Thread? = null
    private var recorder: MediaRecorder? = null
    private val dbValRef = 2e-5
    private val dbLimit: Double = 65.0
    private var isRecording = false
    private val recordingFilePath: String by lazy {
        "${externalCacheDir?.absolutePath}/recording.3gp"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var currentLocation: LatLng
    private val locationRequest = LocationRequest.create().apply {
        interval = 800
        fastestInterval = 500
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                currentLocation = LatLng(location.latitude, location.longitude)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setContentView(R.layout.activity_main)
        requestPermissions(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        prepareOnListenerLocation()
        prepareRecButton()
        prepareSOSButton()
        prepareRegionLimiter()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1 && grantResults.isNotEmpty()) {
            // Sempre
            // 1 - permissao do microfone
            // 2 - permissoes de localizacao
            // 3 - permissoes do microfone e de localizacao
            when (permissions.size) {
                1 -> {
                    this.hasAudioPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED
                }

                2 -> {
                    this.hasLocationPermission =
                        grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED
                }

                3 -> {
                    this.hasAudioPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    this.hasLocationPermission =
                        grantResults[1] == PackageManager.PERMISSION_GRANTED && grantResults[2] == PackageManager.PERMISSION_GRANTED
                }
            }
        } else {
            this.hasAudioPermission = false
            this.hasLocationPermission = false
        }
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
                requestPermissions(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO
                    )
                )
            }
        }
    }

    private fun prepareSOSButton() {
        val button = findViewById<Button>(R.id.buttonSOS)
        button.setOnClickListener {
            if (hasLocationPermission) {
                this.prepareSosAlertAndSend()
            } else {
                // Solicita permissoes de localizacao
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun prepareRegionLimiter() {
        val query = fireStoreDatabase.collection("RegiaoLimite")
        query.get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    for (document in querySnapshot.documents) {
                        val limiter = document.get("limite") as List<GeoPoint>
                        limiter.forEach { limit ->
                            regionLimiter.add(LatLng(limit.latitude, limit.longitude))
                        }
                    }
                } else {
                    // Nenhum documento encontrado dentro do raio informado
                    println("Nenhum limite de regiao encontrado.")
                }
            }
            .addOnFailureListener { exception ->
                // Trate os erros adequadamente
                println("Erro ao buscar os dados de limite de regiao: ${exception.message}")
            }
    }

    private fun prepareOnListenerLocation() {
        // Checa se as permissoes de localizacao foram aceitas, se nao solicita.
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null /* Looper */
            )

        } else {
            // Solicita as permissoes de localizacao
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun prepareAverageAndSend(average: Double) {
        //if (isLatLngInLimiter(currentLocation)) {
        val payload = hashMapOf<String, Any>(
            "criadoEm" to Timestamp.now(),
            "dbMedia" to average,
            "latLng" to GeoPoint(
                currentLocation.latitude,
                currentLocation.longitude
            )
        )
        this.sendToFirebase(payload, "MediaCapturada")
        //} else {
        //    Toast.makeText(this, "Fora de área!", Toast.LENGTH_SHORT).show()
        //}
    }

    private fun prepareSosAlertAndSend() {
        //if (isLatLngInLimiter(currentLocation)) {
        val payload = hashMapOf<String, Any>(
            "criadoEm" to Timestamp.now(),
            "latLng" to GeoPoint(
                currentLocation.latitude,
                currentLocation.longitude
            )
        )
        this.sendToFirebase(payload, "AlertaCapturado")
        Toast.makeText(this, "Enviado!", Toast.LENGTH_SHORT).show()
        //} else {
        //    Toast.makeText(this, "Fora de área!", Toast.LENGTH_SHORT).show()
        //}
    }

    private fun requestPermissions(permissions: Array<out String>) {
        ActivityCompat.requestPermissions(
            this, permissions, 1
        )
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
        startAverageCalc()
        updateDecibelTextView(calculateDec())
    }

    private fun startAverageCalc() {
        this.averageMessenger = thread {

            var decibel = 0.0
            var sum = 0.0
            var counter = 1
            val limiter = 5

            val handler = Handler(Looper.getMainLooper())
            while (!Thread.currentThread().isInterrupted) {

                val amplitude = recorder?.maxAmplitude?.toDouble()
                if (amplitude != null && amplitude > 0) {
                    decibel =
                        20 * kotlin.math.log10(amplitude.div(dbValRef)) - 94
                }

                sum += decibel
                // Verifica se ja eh para enviar os dados
                if (counter == limiter) {
                    val average = sum / limiter

                    handler.post {
                        if (this.currentLocation != null) {
                            this.prepareAverageAndSend(average)
                        }
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

    private fun sendToFirebase(alert: HashMap<String, Any>, collectionPath: String) {

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
        val notificationText = findViewById<TextView>(R.id.textViewNotification)
        val decibelTextView = findViewById<TextView>(R.id.textViewDecibel)
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
            amplitude.div(
                dbValRef
            )
        ) - 94
        return 0.0
    }

    private fun isLatLngInLimiter(latLng: LatLng): Boolean {
        return PolyUtil.containsLocation(
            LatLng(latLng.latitude, latLng.longitude),
            regionLimiter,
            false
        )
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