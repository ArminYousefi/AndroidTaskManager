package com.google.mytaskmanager

import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.mytaskmanager.ui.theme.MyTaskManagerTheme
import com.google.mytaskmanager.presentation.ui.NavGraph

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyTaskManagerTheme {
                NavGraph()
            }
        }
    }
}
