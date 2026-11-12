package com.dualcore.chagok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dualcore.chagok.ui.DriveCompanionTheme
import com.dualcore.chagok.ui.HandoverApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DriveCompanionTheme { HandoverApp() } }
    }
}
