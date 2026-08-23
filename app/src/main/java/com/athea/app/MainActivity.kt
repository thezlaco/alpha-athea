package com.athea.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.athea.app.ui.MainViewModel
import com.athea.app.ui.main.MainScreen
import com.athea.app.ui.theme.AtheaTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AtheaTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onPause() {
        // Drafts and metadata are cheap to flush here and precious to lose.
        viewModel.persistTransientState()
        super.onPause()
    }
}
