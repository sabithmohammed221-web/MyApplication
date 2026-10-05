package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.ui.LudoScreen
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var game: LudoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        game = LudoViewModel(applicationContext)

        setContent {
            MyApplicationTheme(
                darkTheme = true,
                dynamicColor = false
            ) {
                LudoScreen(game)
            }
        }
    }

    override fun onDestroy() {
        game.close()
        super.onDestroy()
    }
}
