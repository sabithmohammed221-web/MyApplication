package com.example.myapplication.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.PrintWriter
import java.util.UUID
import java.util.concurrent.Executors

class BluetoothGameConnection(
    private val context: Context
) {
    companion object {
        private const val SERVICE_NAME = "LudoLabGame"
        private const val DISCOVERY_TIMEOUT_MS = 20_000L
        private val SERVICE_UUID =
            UUID.fromString("8c5b6e10-6a9e-4e43-9c0d-4d1a8c5a4b21")
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool()

    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var writer: PrintWriter? = null
    private var discoveryReceiver: BroadcastReceiver? = null

    private val adapter: BluetoothAdapter?
        get() {
            val manager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            return manager.adapter
        }

    fun createGame(
        onCode: (String) -> Unit,
        onStatus: (String) -> Unit,
        onMessage: (String) -> Unit
    ) {
        executor.execute {
            try {
                val bluetooth =
                    adapter ?: error("Bluetooth is not supported")

                val code = (1000..9999).random()

                bluetooth.name = "LUDO-" + code

                serverSocket?.close()
                serverSocket =
                    bluetooth.listenUsingRfcommWithServiceRecord(
                        SERVICE_NAME,
                        SERVICE_UUID
                    )

                post {
                    onCode(code.toString())
                    onStatus("Waiting for Player 2…")
                }

                requestDiscoverable()

                val socket =
                    serverSocket?.accept()
                        ?: error("Unable to open Bluetooth server")

                clientSocket = socket
                writer = PrintWriter(socket.outputStream, true)

                post { onStatus("Connected ✓") }

                listen(socket, onMessage, onStatus)
            } catch (security: SecurityException) {
                post {
                    onStatus(
                        security.message ?: "Bluetooth permission required"
                    )
                }
            } catch (error: Exception) {
                post {
                    onStatus(
                        "Create error: " +
                            (error.message ?: "Unable to create game")
                    )
                }
            }
        }
    }

    fun joinGame(
        code: String,
        onStatus: (String) -> Unit,
        onMessage: (String) -> Unit
    ) {
        executor.execute {
            try {
                val bluetooth =
                    adapter ?: error("Bluetooth is not supported")

                val targetName = "LUDO-" + code.trim()

                bluetooth.cancelDiscovery()
                post { onStatus("Searching for " + targetName + "…") }

                for (device in bluetooth.bondedDevices) {
                    if (
                        connectToDevice(
                            device,
                            targetName,
                            onMessage,
                            onStatus
                        )
                    ) {
                        return@execute
                    }
                }

                registerDiscovery(
                    targetName,
                    onMessage,
                    onStatus
                )

                if (!bluetooth.startDiscovery()) {
                    unregisterDiscovery()
                    post {
                        onStatus(
                            "Bluetooth discovery could not start"
                        )
                    }
                    return@execute
                }

                mainHandler.postDelayed(
                    {
                        if (clientSocket == null) {
                            try {
                                bluetooth.cancelDiscovery()
                            } catch (_: Exception) {
                            }
                            unregisterDiscovery()
                            onStatus(
                                "Game " + code +
                                    " not found. Keep both phones close and try again."
                            )
                        }
                    },
                    DISCOVERY_TIMEOUT_MS
                )
            } catch (security: SecurityException) {
                post {
                    onStatus(
                        security.message ?: "Bluetooth permission required"
                    )
                }
            } catch (error: Exception) {
                post {
                    onStatus(
                        "Join error: " +
                            (error.message ?: "Unable to join game")
                    )
                }
            }
        }
    }

    fun send(message: String) {
        executor.execute {
            try {
                writer?.println(message)
                writer?.flush()
            } catch (_: Exception) {
            }
        }
    }

    fun close() {
        unregisterDiscovery()

        try {
            adapter?.cancelDiscovery()
        } catch (_: Exception) {
        }

        try {
            clientSocket?.close()
        } catch (_: Exception) {
        }

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }

        clientSocket = null
        serverSocket = null
        writer = null
        executor.shutdownNow()
    }

    private fun requestDiscoverable() {
        try {
            context.startActivity(
                Intent(
                    BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE
                ).apply {
                    putExtra(
                        BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                        300
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
        }
    }

    private fun connectToDevice(
        device: BluetoothDevice,
        targetName: String,
        onMessage: (String) -> Unit,
        onStatus: (String) -> Unit
    ): Boolean {
        return try {
            if (device.name != targetName) {
                false
            } else {
                adapter?.cancelDiscovery()

                val socket =
                    device.createRfcommSocketToServiceRecord(
                        SERVICE_UUID
                    )

                socket.connect()

                clientSocket = socket
                writer = PrintWriter(socket.outputStream, true)

                unregisterDiscovery()
                post { onStatus("Connected ✓") }

                listen(socket, onMessage, onStatus)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun registerDiscovery(
        targetName: String,
        onMessage: (String) -> Unit,
        onStatus: (String) -> Unit
    ) {
        unregisterDiscovery()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (
                    intent?.action != BluetoothDevice.ACTION_FOUND
                ) {
                    return
                }

                val device = getBluetoothDevice(intent)
                    ?: return

                executor.execute {
                    connectToDevice(
                        device,
                        targetName,
                        onMessage,
                        onStatus
                    )
                }
            }
        }

        discoveryReceiver = receiver

        val filter = IntentFilter(
            BluetoothDevice.ACTION_FOUND
        )

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
        ) {
            context.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(
                receiver,
                filter
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun getBluetoothDevice(
        intent: Intent
    ): BluetoothDevice? {
        return if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
        ) {
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java
            )
        } else {
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE
            )
        }
    }

    private fun unregisterDiscovery() {
        val receiver = discoveryReceiver
            ?: return

        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }

        discoveryReceiver = null
    }

    private fun listen(
        socket: BluetoothSocket,
        onMessage: (String) -> Unit,
        onStatus: (String) -> Unit
    ) {
        executor.execute {
            try {
                val reader =
                    socket.inputStream.bufferedReader()

                while (socket.isConnected) {
                    val message =
                        reader.readLine() ?: break
                    post {
                        onMessage(message)
                    }
                }
            } catch (_: Exception) {
            } finally {
                post {
                    onStatus("Disconnected")
                }
            }
        }
    }

    private fun post(action: () -> Unit) {
        mainHandler.post(action)
    }
}
