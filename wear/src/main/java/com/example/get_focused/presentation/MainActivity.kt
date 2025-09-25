/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.get_focused.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.get_focused.presentation.theme.Get_FocusedTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CountdownViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val time by viewModel.time.collectAsState()
            val progress by viewModel.progress.collectAsState()
            Get_FocusedTheme {
                CountdownScreen(
                    progress = progress,
                    time = time
                )
            }
        }
    }
}

@Composable
fun CountdownScreen(progress: Float, time: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 8.dp
        )
        Text(
            text = time,
            textAlign = TextAlign.Center,
            fontSize = 48.sp,
            color = MaterialTheme.colors.primary
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    Get_FocusedTheme {
        CountdownScreen(
            progress = 0.75f,
            time = "11:23"
        )
    }
}