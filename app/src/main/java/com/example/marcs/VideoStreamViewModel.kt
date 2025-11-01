package com.example.marcs

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Build
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.DataInputStream
import java.io.EOFException
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

// --- DATA CLASSES (Unchanged) ---
data class GpsData(val latitude: Double = 0.0, val longitude: Double = 0.0)
data class AltitudeData(val pressure: Float = 0f, val altitude: Float = 0f)
data class BatteryData(val voltage: Float = 0f, val percentage: Int = 0)
enum class SignalStrength { NONE, POOR, FAIR, GOOD, EXCELLENT }
data class ClassificationResult(val label: String, val score: Float)
data class VideoFrameState(val bitmap: Bitmap? = null)
enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, ERROR }

class VideoStreamViewModel(application: Application) : AndroidViewModel(application) {

    @SuppressLint("StaticFieldLeak")
    private val context: Context = application.applicationContext
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // --- STATE FLOWS (Unchanged) ---
    private val _videoFrame = MutableStateFlow(VideoFrameState())
    val videoFrame: StateFlow<VideoFrameState> = _videoFrame.asStateFlow()
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _classifications = MutableStateFlow<List<ClassificationResult>>(emptyList())
    val classifications: StateFlow<List<ClassificationResult>> = _classifications.asStateFlow()
    private val _centroidCoordinate = MutableStateFlow<Offset?>(null)
    val centroidCoordinate: StateFlow<Offset?> = _centroidCoordinate.asStateFlow()
    private val _gpsData = MutableStateFlow(GpsData())
    val gpsData: StateFlow<GpsData> = _gpsData.asStateFlow()
    private val _altitudeData = MutableStateFlow(AltitudeData())
    val altitudeData: StateFlow<AltitudeData> = _altitudeData.asStateFlow()
    private val _batteryData = MutableStateFlow(BatteryData())
    val batteryData: StateFlow<BatteryData> = _batteryData.asStateFlow()
    private val _signalStrength = MutableStateFlow(SignalStrength.NONE)
    val signalStrength: StateFlow<SignalStrength> = _signalStrength.asStateFlow()

    // --- CLASS MEMBERS (Unchanged) ---
    private var objectDetector: ObjectDetector? = null
    @Volatile
    private var isStreamActive = false
    private val isProcessingFrame = AtomicBoolean(false)
    private var lastDetectionTime = 0L
    private var signalStrengthJob: Job? = null

    // --- IMPROVEMENT: Constants for Robust Protocol ---
    companion object {
        const val START_MARKER = 0xAABBCC
        const val END_MARKER = 0xDDEEFF
        const val TYPE_VIDEO = 1
        const val TYPE_CENTROID = 2
        const val TYPE_PRESSURE = 3
        const val TYPE_GPS = 4
        const val TYPE_BATTERY = 5
    }

    init {
        setupObjectDetector()
    }

    private fun setupObjectDetector() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setMaxResults(5)
                    .setScoreThreshold(0.5f)
                    .build()
                objectDetector = ObjectDetector.createFromFileAndOptions(context, "1.tflite", options)
            } catch (e: Exception) {
                Log.e("VideoStreamViewModel", "Error setting up ObjectDetector: ${e.message}", e)
            }
        }
    }

    fun connectAndStream() {
        if (connectionState.value != ConnectionState.DISCONNECTED || isStreamActive) return

        isStreamActive = true
        startSignalStrengthMonitoring()

        viewModelScope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.CONNECTING
            try {
                Socket("192.168.4.1", 8888).use { socket ->
                    socket.soTimeout = 5000
                    _connectionState.value = ConnectionState.CONNECTED
                    val inputStream = DataInputStream(socket.getInputStream())

                    // --- THIS IS THE NEW ROBUST READING LOGIC ---
                    while (isStreamActive && isActive && socket.isConnected) {
                        try {
                            // 1. Search for the 3-byte Start Marker
                            var markerCheck = 0
                            while (markerCheck != START_MARKER) {
                                val byte = inputStream.readByte().toInt() and 0xFF
                                markerCheck = ((markerCheck shl 8) or byte) and 0xFFFFFF
                            }

                            // 2. Read Data Type (1 byte) and Data Size (4 bytes, little-endian)
                            val dataType = inputStream.readByte().toInt()
                            val dataSize = Integer.reverseBytes(inputStream.readInt()) // Correct for endianness

                            // 3. Validate data size. Raw QVGA RGB565 is 153600 bytes.
                            if (dataSize <= 0 || dataSize > 200000) {
                                Log.w("TCP", "Invalid data size $dataSize for type $dataType. Resyncing...")
                                continue // Skip packet and look for next start marker
                            }

                            // 4. Read the actual data payload
                            val dataBytes = ByteArray(dataSize)
                            inputStream.readFully(dataBytes)

                            // 5. Verify the 3-byte End Marker
                            val endMarkerCheck = (inputStream.readByte().toInt() and 0xFF shl 16) or
                                    (inputStream.readByte().toInt() and 0xFF shl 8) or
                                    (inputStream.readByte().toInt() and 0xFF)
                            if (endMarkerCheck != END_MARKER) {
                                Log.w("TCP", "Invalid end marker. Resyncing...")
                                continue // Skip packet and look for next start marker
                            }

                            // 6. If all checks pass, handle the data
                            when (dataType) {
                                TYPE_VIDEO -> handleVideoFrame(dataBytes)
                                TYPE_CENTROID -> handleCentroidData(dataBytes)
                                TYPE_PRESSURE -> handlePressureData(dataBytes)
                                TYPE_GPS -> handleGpsData(dataBytes)
                                TYPE_BATTERY -> handleBatteryData(dataBytes)
                                else -> Log.w("TCP", "Unknown data type received: $dataType")
                            }

                        } catch (e: EOFException) {
                            Log.w("TCP", "End of stream reached cleanly.")
                            break // Exit loop
                        } catch (e: Exception) {
                            Log.e("TCP", "Error in read loop, resyncing... Error: ${e.message}")
                            // Any other error will just cause the loop to continue and resync
                        }
                    }
                }
            } catch (e: Exception) {
                if (isStreamActive) {
                    _connectionState.value = ConnectionState.ERROR
                    Log.e("VideoStreamViewModel", "TCP Connection error: ${e.message}", e)
                }
            } finally {
                _connectionState.value = ConnectionState.DISCONNECTED
                isStreamActive = false
                stopSignalStrengthMonitoring()
                // Reset all data
                _centroidCoordinate.value = null
                _gpsData.value = GpsData()
                _altitudeData.value = AltitudeData()
                _batteryData.value = BatteryData()
            }
        }
    }

    // --- DATA HANDLERS ---

    // --- THIS IS THE NEW LOGIC FOR RAW RGB565 DATA ---
    private fun handleVideoFrame(data: ByteArray) {
        val width = 320 // Must match ESP32 camera frame size (QVGA)
        val height = 240
        val expectedSize = width * height * 2 // RGB565 uses 2 bytes per pixel

        if (data.size != expectedSize) {
            Log.w("Video", "Received RAW data of size ${data.size}, but expected $expectedSize. Skipping frame.")
            return
        }

        try {
            // Create a mutable bitmap configured for RGB565
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            // Copy the raw byte array directly into the bitmap's pixel buffer
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(data))

            _videoFrame.update { it.copy(bitmap = bitmap) }
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastDetectionTime > 250) { // Limit detection to ~4Hz
                processVideoFrame(bitmap)
                lastDetectionTime = currentTime
            }
        } catch (e: Exception) {
            Log.e("Video", "Error processing raw RGB565 frame: ${e.message}", e)
        }
    }

    // Other handlers are unchanged...
    private fun handleCentroidData(data: ByteArray) {
        val message = String(data, Charsets.UTF_8) // e.g., "X:120,Y:80"
        try {
            val parts = message.split(',')
            val x = parts[0].substringAfter("X:").trim().toFloat()
            val y = parts[1].substringAfter("Y:").trim().toFloat()
            _centroidCoordinate.value = Offset(x, y)
        } catch (e: Exception) {
            Log.e("Centroid", "Failed to parse coordinate string: $message", e)
        }
    }
    // ... (handlePressureData, handleGpsData, handleBatteryData are also unchanged)

    private fun handlePressureData(data: ByteArray) {
        val message = String(data, Charsets.UTF_8) // e.g., "P:101325.5"
        try {
            val pressure = message.substringAfter("P:").trim().toFloat()
            val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
            _altitudeData.value = AltitudeData(pressure, altitude)
        } catch (e: Exception) {
            Log.e("Pressure", "Failed to parse pressure string: $message", e)
        }
    }

    private fun handleGpsData(data: ByteArray) {
        val message = String(data, Charsets.UTF_8) // e.g., "LAT:12.345,LON:-78.910"
        try {
            val parts = message.split(',')
            val lat = parts[0].substringAfter("LAT:").trim().toDouble()
            val lon = parts[1].substringAfter("LON:").trim().toDouble()
            _gpsData.value = GpsData(lat, lon)
        } catch (e: Exception) {
            Log.e("GPS", "Failed to parse GPS string: $message", e)
        }
    }

    private fun handleBatteryData(data: ByteArray) {
        val message = String(data, Charsets.UTF_8) // e.g., "V:4.1"
        try {
            val voltage = message.substringAfter("V:").trim().toFloat()
            val percentage = ((voltage - 3.2f) / (4.2f - 3.2f) * 100).toInt().coerceIn(0, 100)
            _batteryData.value = BatteryData(voltage, percentage)
        } catch (e: Exception) {
            Log.e("Battery", "Failed to parse battery string: $message", e)
        }
    }

    // --- SIGNAL STRENGTH & OTHER METHODS (Unchanged) ---
    @SuppressLint("MissingPermission")
    private fun startSignalStrengthMonitoring() {
        signalStrengthJob?.cancel()
        signalStrengthJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    capabilities?.transportInfo as? WifiInfo
                } else {
                    @Suppress("DEPRECATION")
                    connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.let {
                        if (it.isConnected) (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).connectionInfo else null
                    }
                }
                val rssi = wifiInfo?.rssi ?: -100
                _signalStrength.value = when {
                    rssi >= -55 -> SignalStrength.EXCELLENT
                    rssi >= -67 -> SignalStrength.GOOD
                    rssi >= -80 -> SignalStrength.FAIR
                    else -> SignalStrength.POOR
                }
                delay(2000)
            }
        }
    }

    private fun stopSignalStrengthMonitoring() {
        signalStrengthJob?.cancel()
        _signalStrength.value = SignalStrength.NONE
    }

    private fun processVideoFrame(bitmap: Bitmap) {
        if (objectDetector == null || !isProcessingFrame.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.Default) {
                    objectDetector?.detect(TensorImage.fromBitmap(bitmap))
                        ?.mapNotNull { det -> det.categories.firstOrNull()?.let { cat -> ClassificationResult(cat.label, cat.score) } }
                        ?: emptyList()
                }
                _classifications.update { results }
            } catch (e: Exception) {
                Log.e("ObjectDetection", "Error during processing: ${e.message}", e)
            } finally {
                isProcessingFrame.set(false)
            }
        }
    }

    fun disconnectStream() {
        isStreamActive = false
    }

    override fun onCleared() {
        super.onCleared()
        disconnectStream()
        objectDetector?.close()
    }
}

class VideoStreamViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VideoStreamViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VideoStreamViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
