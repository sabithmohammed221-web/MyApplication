package com.example.myapplication

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.myapplication.model.ludoPlayers
import com.example.myapplication.network.BluetoothGameConnection
import kotlin.random.Random

class LudoViewModel(context: Context) {

    private val connection =
        BluetoothGameConnection(context.applicationContext)

    var selectedPlayer by mutableIntStateOf(0)
        private set

    var forcedDice by mutableStateOf<Int?>(null)
        private set

    var lastDice by mutableIntStateOf(1)
        private set

    var gameNumber by mutableIntStateOf(1)
        private set

    var linkStatus by mutableStateOf("Not connected")
        private set

    var gameCode by mutableStateOf("")
        private set

    var history by mutableStateOf<List<String>>(emptyList())
        private set

    fun requiredPermissions(host: Boolean): Array<String> {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            return emptyArray()
        }

        return if (host) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN
            )
        }
    }

    fun createGame() {
        connection.createGame(
            onCode = { gameCode = it },
            onStatus = { linkStatus = it },
            onMessage = ::handleMessage
        )
    }

    fun joinGame(code: String) {
        if (code.length != 4) {
            linkStatus = "Enter the 4-digit game code"
            return
        }

        connection.joinGame(
            code = code,
            onStatus = { linkStatus = it },
            onMessage = ::handleMessage
        )
    }

    fun selectPlayer(index: Int) {
        if (index !in ludoPlayers.indices) return

        selectedPlayer = index
        connection.send("PLAYER|" + index)
    }

    fun selectDice(value: Int) {
        if (value !in 1..6) return

        forcedDice = value
        connection.send("DICE|" + value)
    }

    fun rollDice() {
        val value =
            forcedDice ?: Random.nextInt(1, 7)

        lastDice = value
        addHistory(
            ludoPlayers[selectedPlayer].name +
                " rolled " + value
        )

        connection.send(
            "ROLL|" + value + "|" + selectedPlayer
        )

        forcedDice = null
    }

    fun newGame() {
        gameNumber++
        lastDice = 1
        selectedPlayer = 0
        forcedDice = null
        history = emptyList()

        connection.send("NEW")
    }

    fun close() {
        connection.close()
    }

    private fun handleMessage(message: String) {
        val parts = message.split("|")

        when (parts.firstOrNull()) {
            "DICE" -> {
                val value =
                    parts.getOrNull(1)?.toIntOrNull()

                if (value != null && value in 1..6) {
                    forcedDice = value
                }
            }

            "PLAYER" -> {
                val value =
                    parts.getOrNull(1)?.toIntOrNull()

                if (value != null &&
                    value in ludoPlayers.indices
                ) {
                    selectedPlayer = value
                }
            }

            "NEW" -> {
                gameNumber++
                lastDice = 1
                selectedPlayer = 0
                forcedDice = null
                history = emptyList()
            }

            "ROLL" -> {
                val value =
                    parts.getOrNull(1)?.toIntOrNull()
                        ?: return

                val playerIndex =
                    parts.getOrNull(2)?.toIntOrNull()
                        ?: 0

                lastDice = value

                val safeIndex =
                    playerIndex.coerceIn(
                        0,
                        ludoPlayers.lastIndex
                    )

                selectedPlayer = safeIndex

                addHistory(
                    ludoPlayers[safeIndex].name +
                        " rolled " + value
                )
            }
        }
    }

    private fun addHistory(item: String) {
        history =
            (listOf(item) + history).take(8)
    }
}
