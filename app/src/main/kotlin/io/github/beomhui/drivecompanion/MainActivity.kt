package io.github.beomhui.drivecompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.beomhui.drivecompanion.ui.DriveCompanionTheme
import io.github.beomhui.drivecompanion.ui.HandoverApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DriveCompanionTheme { HandoverApp() } }
    }
}
