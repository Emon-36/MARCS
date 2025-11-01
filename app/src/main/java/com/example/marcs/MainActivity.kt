package com.example.marcs

import android.app.Application
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marcs.ui.theme.MARCSTheme
import org.osmdroid.views.MapView

class MainActivity : ComponentActivity() {

    private val videoStreamViewModel: VideoStreamViewModel by viewModels {
        VideoStreamViewModelFactory(this.application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MARCSTheme {
                DisposableEffect(Unit) {
                    videoStreamViewModel.connectAndStream()
                    onDispose {
                        videoStreamViewModel.disconnectStream()
                    }
                }

                var isDetailPageVisible by remember { mutableStateOf(false) }
                var isMapViewVisible by remember { mutableStateOf(false) } // State for map view

                // --- NAVIGATION LOGIC ---
                when {
                    isDetailPageVisible -> {
                        VideoDetailPage(
                            viewModel = videoStreamViewModel,
                            onBackPressed = { isDetailPageVisible = false }
                        )
                    }
                    isMapViewVisible -> {
                        MapView(onBackPressed = { isMapViewVisible = false })
                    }
                    else -> {
                        MainScreen(
                            videoStreamViewModel = videoStreamViewModel,
                            onNavigateToVideo = { isDetailPageVisible = true },
                            onNavigateToMap = { isMapViewVisible = true } // Pass navigation event
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    videoStreamViewModel: VideoStreamViewModel,
    onNavigateToVideo: () -> Unit,
    onNavigateToMap: () -> Unit // New parameter for map navigation
) {
    var showMenu by remember { mutableStateOf(false) }
    val cardTitles = listOf(
        "Video Stream from External Camera",
        "Location & Speed",
        "System Status",
        "Mission Controls"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MARCS") },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            cardTitles.forEach { title ->
                                DropdownMenuItem(
                                    text = { Text(title) },
                                    onClick = {
                                        when (title) {
                                            "Video Stream from External Camera" -> onNavigateToVideo()
                                            "Location & Speed" -> onNavigateToMap() // Also navigate from menu
                                            else -> Log.d("MenuClick", "$title clicked")
                                        }
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    ) { innerPadding ->
        Surface(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            CardLayout(
                videoStreamViewModel = videoStreamViewModel,
                onVideoCardClick = onNavigateToVideo,
                onLocationCardClick = onNavigateToMap // Pass click handler down
            )
        }
    }
}


@Composable
fun CardLayout(
    videoStreamViewModel: VideoStreamViewModel,
    onVideoCardClick: () -> Unit,
    onLocationCardClick: () -> Unit // New parameter for location card click
) {
    val videoFrameState by videoStreamViewModel.videoFrame.collectAsState()
    val videoFrameBitmap = videoFrameState.bitmap
    val connectionState by videoStreamViewModel.connectionState.collectAsState()

    val centroid by videoStreamViewModel.centroidCoordinate.collectAsState(initial = Offset(30f, 200f))

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Top row with two cards (Location & System Status) ---
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- UPDATED: Made this card clickable ---
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClick = onLocationCardClick) // Make the card clickable
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Location & Speed", style = MaterialTheme.typography.titleMedium)
                    Column {
                        Text("Lat: 12.345678", style = MaterialTheme.typography.bodySmall)
                        Text("Lon: -78.910111", style = MaterialTheme.typography.bodySmall)
                        Text("Altitude: 123.45 m", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("45", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("km/h", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            Card(modifier = Modifier.weight(1f).fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("System Status", style = MaterialTheme.typography.titleMedium)
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    StatusItem("Fuel", "78%", 0.78f)
                    StatusItem("Signal", "Excellent", 0.9f)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Obstacle Detected", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Middle row with one card (VIDEO STREAM)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onVideoCardClick),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (connectionState) {
                        ConnectionState.CONNECTED -> {
                            videoFrameBitmap?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Video feed",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } ?: CircularProgressIndicator()
                        }
                        ConnectionState.CONNECTING, ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "No Signal",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = when (connectionState) {
                                        ConnectionState.CONNECTING -> "CONNECTING..."
                                        ConnectionState.ERROR -> "ERROR"
                                        else -> "NO SIGNAL"
                                    },
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- BOTTOM ROW WITH THERMAL AND MISSION CARDS ---
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.weight(1f).fillMaxSize()) {
                Column {
                    Text(
                        "Thermal Target",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.DarkGray)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val axisColor = Color.White.copy(alpha = 0.4f)

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val (width, height) = size
                            val origin = Offset(width / 2f, height / 2f)

                            drawLine(
                                color = axisColor,
                                start = Offset(origin.x, 0f),
                                end = Offset(origin.x, height),
                                strokeWidth = 2f
                            )
                            drawLine(
                                color = axisColor,
                                start = Offset(0f, origin.y),
                                end = Offset(width, origin.y),
                                strokeWidth = 2f
                            )

                            drawCircle(
                                color = primaryColor,
                                radius = 8f,
                                center = origin
                            )

                            centroid?.let { target ->
                                val displacementX = target.x - 120f
                                val displacementY = target.y - 120f
                                val scaledX = origin.x + (displacementX / 120f) * (width / 2f)
                                val scaledY = origin.y + (displacementY / 120f) * (height / 2f)
                                val scaledTarget = Offset(scaledX, scaledY)

                                drawLine(
                                    color = tertiaryColor,
                                    start = origin,
                                    end = scaledTarget,
                                    strokeWidth = 5f
                                )

                                drawCircle(
                                    color = tertiaryColor,
                                    radius = 12f,
                                    center = scaledTarget
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 12f,
                                    center = scaledTarget,
                                    style = Stroke(width = 3f)
                                )
                            }

                            drawContext.canvas.nativeCanvas.apply {
                                val text = centroid?.let { "X: ${it.x.toInt()}, Y: ${it.y.toInt()}" } ?: "No Target"
                                val textPaint = Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 14.sp.toPx()
                                    isAntiAlias = true
                                    textAlign = Paint.Align.LEFT
                                }
                                drawText(
                                    text,
                                    16f,
                                    32f,
                                    textPaint
                                )
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.weight(1f).fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Mission Controls", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { Log.d("MissionControls", "Start Mission Clicked") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Start Mission")
                    }
                    Button(onClick = { Log.d("MissionControls", "Simulation Clicked") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Simulation")
                    }
                }
            }
        }
    }
}


@Composable
fun StatusItem(name: String, value: String, progress: Float, warningThreshold: Float? = null) {
    val progressColor = if (warningThreshold != null && progress > warningThreshold) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.height(IntrinsicSize.Min)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = progressColor)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    MARCSTheme {
        val factory = VideoStreamViewModelFactory(LocalContext.current.applicationContext as Application)
        val previewViewModel = viewModel<VideoStreamViewModel>(factory = factory)
        MainScreen(
            videoStreamViewModel = previewViewModel,
            onNavigateToVideo = {},
            onNavigateToMap = {} // Add to preview
        )
    }
}
