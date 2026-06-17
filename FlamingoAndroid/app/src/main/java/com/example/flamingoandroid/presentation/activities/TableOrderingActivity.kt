package com.example.flamingoandroid.presentation.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.flamingoandroid.presentation.screens.menu.TableOrderingActivityContent
import com.example.flamingoandroid.presentation.theme.FlamingoTheme

class TableOrderingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serverId = intent.getStringExtra(EXTRA_SERVER_ID).orEmpty()
        val serverName = intent.getStringExtra(EXTRA_SERVER_NAME).orEmpty().ifBlank { "Serveur" }

        setContent {
            FlamingoTheme {
                TableOrderingActivityContent(
                    serverId = serverId,
                    serverName = serverName,
                    onExit = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_SERVER_ID = "extra_server_id"
        const val EXTRA_SERVER_NAME = "extra_server_name"
    }
}
