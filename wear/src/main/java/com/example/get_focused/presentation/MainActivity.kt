/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.get_focused.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.get_focused.presentation.theme.Get_FocusedTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CountdownViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setContent {
            val time by viewModel.time.collectAsState()
            val progress by viewModel.progress.collectAsState()
            val currentTime by viewModel.currentTime.collectAsState()
            Get_FocusedTheme {
                CountdownScreen(
                    progress = progress,
                    time = time,
                    currentTime = currentTime,
                    eventDescription = "Coffee Break" // Placeholder
                )
            }
        }
    }
}

@Composable
fun CountdownScreen(
    progress: Float,
    time: String,
    currentTime: String,
    eventDescription: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 8.dp, // Thinner progress bar
            indicatorColor = Color(0xFF00BCD4), // Cyan color
            trackColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f)
        )

        // A container for the content that should be inside the circle
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) { // Reduced padding

            // Clock time, slightly lowered
            Text(
                text = currentTime,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp), // Lowered
                textAlign = TextAlign.Center,
                fontSize = 18.sp // Smaller font
            )

            // Central content: description and icon
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = eventDescription,
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp, // Smaller font
                )
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Default.Coffee,
                    contentDescription = "Coffee break icon",
                    modifier = Modifier.size(38.dp) // Smaller icon
                )
            }

            // Countdown timer at the bottom
            Text(
                text = time,
                modifier = Modifier.align(Alignment.BottomCenter),
                textAlign = TextAlign.Center,
                fontSize = 34.sp, // Smaller font
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00BCD4) // Cyan color
            )
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    Get_FocusedTheme {
        CountdownScreen(
            progress = 0.75f,
            time = "04:51",
            currentTime = "10:09 AM",
            eventDescription = "Coffee Break"
        )
    }
}