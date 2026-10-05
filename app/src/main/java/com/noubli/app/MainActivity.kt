package com.noubli.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.noubli.app.ui.NoubliNavHost
import com.noubli.app.ui.theme.NoubliTheme

/** Unique activité : héberge toute l'interface Compose et sa navigation. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NoubliApp).container
        setContent {
            NoubliTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoubliNavHost(container)
                }
            }
        }
    }
}
