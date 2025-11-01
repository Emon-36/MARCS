// In file: D:/MARCS/app/src/main/java/com/example/marcs/VideoDetailPage.kt

package com.example.marcs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailPage(
    viewModel: VideoStreamViewModel, // This is the STABLE instance from MainActivity
    onBackPressed: () -> Unit
) {
    val videoFrameState by viewModel.videoFrame.collectAsState()
    val videoFrameBitmap = videoFrameState.bitmap
    val connectionState by viewModel.connectionState.collectAsState()
    val classifications by viewModel.classifications.collectAsState()

    // No DisposableEffect is needed here. The page is just a viewer.

    // Handle system back button to go back to the dashboard
    BackHandler { onBackPressed() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Feed & Analysis") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            // Video Feed Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (connectionState == ConnectionState.CONNECTED && videoFrameBitmap != null) {
                    Image(
                        bitmap = videoFrameBitmap.asImageBitmap(),
                        contentDescription = "Full-screen video feed",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Show a status message
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (connectionState == ConnectionState.CONNECTING) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(16.dp))
                            Text("CONNECTING...", color = Color.White.copy(0.7f), style = MaterialTheme.typography.headlineSmall)
                        } else {
                            Icon(Icons.Filled.Warning, "No Signal", tint = Color.White.copy(0.7f), modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("DISCONNECTED", color = Color.White.copy(0.7f), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }

            // Object Classification Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Object Classification", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (classifications.isNotEmpty()) {
                        classifications.forEach { result ->
                            val scorePercentage = (result.score * 100).toInt()
                            Text(
                                "• ${result.label} ($scorePercentage%)",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        Text(
                            "• Searching...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
