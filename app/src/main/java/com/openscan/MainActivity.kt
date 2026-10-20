package com.openscan

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.openscan.ui.ScanScreen
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // OpenCV 4.11 ships the native libs in the AAR; initLocal loads them in-process.
        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenScan", "OpenCV failed to load")
        }
        setContent {
            MaterialTheme {
                Surface { ScanScreen() }
            }
        }
    }
}
