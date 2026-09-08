package com.careloop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

/**
 * Placeholder entry point — replaced once the design system and navigation land.
 * Exists now so the build can be verified before more code is written on top of it.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("CareLoop")
        }
    }
}
