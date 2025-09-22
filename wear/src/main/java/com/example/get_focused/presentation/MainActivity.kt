/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.get_focused.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.app.Application
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.get_focused.presentation.theme.Get_FocusedTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val countdownViewModel: CountdownViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            val navController = rememberSwipeDismissableNavController()
            Get_FocusedTheme {
                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = "countdown"
                ) {
                    composable("countdown") {
                        CountdownScreen(countdownViewModel, onConfigureClick = {
                            navController.navigate("time_configuration")
                        })
                    }
                    composable("time_configuration") {
                        TimeConfigurationScreen(
                            onConfirm = { minutes, seconds ->
                                val duration = (minutes * 60 + seconds) * 1000L
                                countdownViewModel.startCountdown(duration)
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.android.horologist.composables.picker.Picker
import com.google.android.horologist.composables.picker.rememberPickerState

@Composable
fun TimeConfigurationScreen(onConfirm: (Int, Int) -> Unit) {
    val selectedMinute = remember { mutableStateOf(0) }
    val selectedSecond = remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row {
            Picker(
                modifier = Modifier.weight(1f),
                state = rememberPickerState(initialNumberOfOptions = 60, initiallySelectedOption = selectedMinute.value),
                onStateChanged = { selectedMinute.value = it }
            ) {
                Text(text = "%02d".format(it))
            }
            Picker(
                modifier = Modifier.weight(1f),
                state = rememberPickerState(initialNumberOfOptions = 60, initiallySelectedOption = selectedSecond.value),
                onStateChanged = { selectedSecond.value = it }
            ) {
                Text(text = "%02d".format(it))
            }
        }
        Button(onClick = { onConfirm(selectedMinute.value, selectedSecond.value) }) {
            Text("Start")
        }
    }
}

@Composable
fun CountdownScreen(countdownViewModel: CountdownViewModel, onConfigureClick: () -> Unit) {
    val remainingTime by countdownViewModel.remainingTime.collectAsState()
    val isRunning by countdownViewModel.isRunning.collectAsState()
    val isPaused by countdownViewModel.isPaused.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatTime(remainingTime),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isRunning) {
                Button(onClick = { countdownViewModel.pauseCountdown() }) {
                    Text(text = "Pause")
                }
            } else if (isPaused) {
                Button(onClick = { countdownViewModel.resumeCountdown() }) {
                    Text(text = "Resume")
                }
            } else {
                Button(onClick = onConfigureClick) {
                    Text(text = "Start")
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { countdownViewModel.resetCountdown() }) {
                Text(text = "Reset")
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format("%02d:%02d", minutes, seconds)
}