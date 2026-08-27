package com.athea.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.athea.app.di.AppContainer
import com.athea.app.ui.MainViewModel
import com.athea.app.ui.main.MainScreen
import com.athea.app.ui.theme.AtheaTheme

class MainActivity : ComponentActivity() {

    private val appContainer by lazy { AppContainer(this) }
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(application, appContainer) }

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
