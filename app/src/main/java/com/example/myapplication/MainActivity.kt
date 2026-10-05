package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.Executors
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlin.random.Random

private data class Player(val name: String, val color: Color)
private val players = listOf(
    Player("Red", Color(0xFFE53935)),
    Player("Green", Color(0xFF43A047)),
    Player("Yellow", Color(0xFFFFC107)),
    Player("Cyan", Color(0xFF00ACC1))
)

private class PhoneLink(private val context: android.content.Context) {
    private val executor = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private var server: android.bluetooth.BluetoothServerSocket? = null
    private var socket: android.bluetooth.BluetoothSocket? = null
    private var writer: java.io.PrintWriter? = null
    private val uuid = java.util.UUID.fromString("8c5b6e10-6a9e-4e43-9c0d-4d1a8c5a4b21")

    private fun adapter(): android.bluetooth.BluetoothAdapter? =
        (context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter

    fun host(onStatus: (String) -> Unit, onMessage: (String) -> Unit) {
        executor.execute {
            try {
                val a = adapter() ?: throw IllegalStateException("Bluetooth not supported")
                server?.close()
                server = a.listenUsingRfcommWithServiceRecord("LudoLab", uuid)
                main.post { onStatus("Waiting for Phone 2…") }
                val accepted = server!!.accept()
                socket = accepted
                writer = java.io.PrintWriter(accepted.outputStream, true)
                main.post { onStatus("Connected") }
                listen(accepted, onMessage, onStatus)
            } catch (e: Exception) {
                main.post { onStatus("Host error: " + (e.message ?: "check Bluetooth permission")) }
            }
        }
    }

    fun join(address: String, onStatus: (String) -> Unit, onMessage: (String) -> Unit) {
        executor.execute {
            try {
                val a = adapter() ?: throw IllegalStateException("Bluetooth not supported")
                a.cancelDiscovery()
                val device = a.getRemoteDevice(address.trim())
                main.post { onStatus("Connecting…") }
                val connected = device.createRfcommSocketToServiceRecord(uuid)
                connected.connect()
                socket = connected
                writer = java.io.PrintWriter(connected.outputStream, true)
                main.post { onStatus("Connected") }
                listen(connected, onMessage, onStatus)
            } catch (e: Exception) {
                main.post { onStatus("Join error: " + (e.message ?: "pair phones and check MAC address")) }
            }
        }
    }

    private fun listen(target: android.bluetooth.BluetoothSocket, onMessage: (String) -> Unit, onStatus: (String) -> Unit) {
        try {
            val reader = target.inputStream.bufferedReader()
            while (target.isConnected) {
                val line = reader.readLine() ?: break
                main.post { onMessage(line) }
            }
        } catch (_: Exception) {
        } finally {
            main.post { onStatus("Disconnected") }
        }
    }

    fun send(message: String) { executor.execute { writer?.println(message) } }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        writer = null
    }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) { LudoControllerApp() }
        }
    }
}

private fun bluetoothReady(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        (context as? Activity)?.requestPermissions(
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 1001
        )
        return false
    }
    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    if (adapter == null) return false
    if (!adapter.isEnabled) {
        context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        return false
    }
    return true
}

private fun bluetoothReady(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        (context as? Activity)?.requestPermissions(
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 1001
        )
        return false
    }
    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    if (adapter == null) return false
    if (!adapter.isEnabled) {
        context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        return false
    }
    return true
}

@Composable
private fun LudoControllerApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val link = remember { PhoneLink(context) }
    var selectedPlayer by remember { mutableIntStateOf(0) }
    var forcedDice by remember { mutableStateOf<Int?>(null) }
    var lastDice by remember { mutableIntStateOf(1) }
    var turn by remember { mutableIntStateOf(0) }
    var gameNumber by remember { mutableIntStateOf(1) }
    var linkStatus by remember { mutableStateOf("Not connected") }
    var bluetoothAddress by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(listOf<String>()) }
    val scrollState = rememberScrollState()
    val player = players[selectedPlayer]

    DisposableEffect(Unit) { onDispose { link.close() } }

    fun applyRemote(message: String) {
        val parts = message.split("|")
        when (parts.firstOrNull()) {
            "DICE" -> parts.getOrNull(1)?.toIntOrNull()?.let { if (it in 1..6) forcedDice = it else forcedDice = null }
            "PLAYER" -> parts.getOrNull(1)?.toIntOrNull()?.let { if (it in players.indices) { selectedPlayer = it; turn = it } }
            "NEW" -> {
                gameNumber++
                lastDice = 1
                selectedPlayer = 0
                turn = 0
                forcedDice = null
                history = emptyList()
            }
            "ROLL" -> {
                val value = parts.getOrNull(1)?.toIntOrNull() ?: return
                val p = parts.getOrNull(2)?.toIntOrNull() ?: 0
                lastDice = value
                if (p in players.indices) selectedPlayer = p
                history = (listOf(players[p.coerceIn(0, 3)].name + " rolled " + value) + history).take(8)
            }
        }
    }

    fun rollDice() {
        val value = forcedDice ?: Random.nextInt(1, 7)
        lastDice = value
        history = (listOf(player.name + " rolled " + value) + history).take(8)
        link.send("ROLL|" + value + "|" + selectedPlayer)
        forcedDice = null
        turn = (turn + 1) % players.size
        selectedPlayer = turn
        link.send("PLAYER|" + turn)
    }

    fun newGame() {
        gameNumber++
        lastDice = 1
        turn = 0
        selectedPlayer = 0
        forcedDice = null
        history = emptyList()
        link.send("NEW")
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090A0F)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF151722))
                    .border(1.dp, Color(0xFF7658FF), RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("LUDO LAB", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        Text("PHONE • PLAY • CONNECT", color = Color(0xFF8E94A3), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("2P", color = Color(0xFFB9A4FF), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }ackage com.example.myapplication

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.Executors
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlin.random.Random

private data class Player(val name: String, val color: Color)
private val players = listOf(
    Player("Red", Color(0xFFE53935)),
    Player("Green", Color(0xFF43A047)),
    Player("Yellow", Color(0xFFFFC107)),
    Player("Cyan", Color(0xFF00ACC1))
)

private class PhoneLink(private val context: Context) {
    private val executor = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private var server: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var writer: java.io.PrintWriter? = null

    companion object {
        private const val SERVICE_NAME = "LudoLab"
        private val SERVICE_UUID = UUID.fromString("8c5b6e10-6a9e-4e43-9c0d-4d1a8c5a4b21")
    }

    private fun adapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter
    }

    fun host(onStatus: (String) -> Unit, onMessage: (String) -> Unit) {
        executor.execute {
            try {
                val adapter = adapter() ?: throw IllegalStateException("Bluetooth is not supported")
                server?.close()
                server = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                main.post { onStatus("Waiting for Phone 2…") }
                val accepted = server!!.accept()
                socket = accepted
                writer = java.io.PrintWriter(accepted.outputStream, true)
                main.post { onStatus("Connected") }
                listen(accepted, onMessage, onStatus)
            } catch (e: SecurityException) {
                main.post { onStatus("Bluetooth permission required") }
            } catch (e: Exception) {
                main.post { onStatus("Host error: " + (e.message ?: "connection failed")) }
            }
        }
    }

    fun join(address: String, onStatus: (String) -> Unit, onMessage: (String) -> Unit) {
        executor.execute {
            try {
                val adapter = adapter() ?: throw IllegalStateException("Bluetooth is not supported")
                main.post { onStatus("Connecting…") }
                adapter.cancelDiscovery()
                val device = adapter.getRemoteDevice(address.trim())
                val connected = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                connected.connect()
                socket = connected
                writer = java.io.PrintWriter(connected.outputStream, true)
                main.post { onStatus("Connected") }
                listen(connected, onMessage, onStatus)
            } catch (e: SecurityException) {
                main.post { onStatus("Bluetooth permission required") }
            } catch (e: Exception) {
                main.post { onStatus("Join error: " + (e.message ?: "check the Bluetooth MAC address")) }
            }
        }
    }

    private fun listen(target: BluetoothSocket, onMessage: (String) -> Unit, onStatus: (String) -> Unit) {
        try {
            val reader = target.inputStream.bufferedReader()
            while (target.isConnected) {
                val line = reader.readLine() ?: break
                main.post { onMessage(line) }
            }
        } catch (_: Exception) {
        } finally {
            main.post { onStatus("Disconnected") }
        }
    }

    fun send(message: String) { executor.execute { writer?.println(message) } }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        writer = null
    }
}ackage com.example.myapplication

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.Executors
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlin.random.Random

private data class Player(val name: String, val color: Color)
private val players = listOf(
    Player("Red", Color(0xFFE53935)),
    Player("Green", Color(0xFF43A047)),
    Player("Yellow", Color(0xFFFFC107)),
    Player("Cyan", Color(0xFF00ACC1))
)

private class PhoneLink {
    private val executor = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private var server: ServerSocket? = null
    private var socket: Socket? = null
    private var writer: java.io.PrintWriter? = null

    fun host(onStatus: (String) -> Unit, onMessage: (String) -> Unit) {
        executor.execute {
            try {
                server?.close()
                server = ServerSocket(8765)
                main.post { onStatus("Waiting for Phone 2…") }
                val accepted = server!!.accept()
                socket = accepted
                writer = java.io.PrintWriter(accepted.getOutputStream(), true)
                main.post { onStatus("Connected") }
                listen(accepted, onMessage, onStatus)
            } catch (e: Exception) {
                main.post { onStatus("Host error: " + (e.message ?: "connection failed")) }
            }
        }
    }

    fun join(address: String, onStatus: (String) -> Unit, onMessage: (String) -> Unit) {
        executor.execute {
            try {
                main.post { onStatus("Connecting…") }
                val connected = Socket(address.trim(), 8765)
                socket = connected
                writer = java.io.PrintWriter(connected.getOutputStream(), true)
                main.post { onStatus("Connected") }
                listen(connected, onMessage, onStatus)
            } catch (e: Exception) {
                main.post { onStatus("Join error: " + (e.message ?: "check IP and Wi-Fi")) }
            }
        }
    }

    private fun listen(target: Socket, onMessage: (String) -> Unit, onStatus: (String) -> Unit) {
        try {
            val reader = target.getInputStream().bufferedReader()
            while (!target.isClosed) {
                val line = reader.readLine() ?: break
                main.post { onMessage(line) }
            }
        } catch (_: Exception) {
        } finally {
            main.post { onStatus("Disconnected") }
        }
    }

    fun send(message: String) { executor.execute { writer?.println(message) } }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        writer = null
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) { LudoControllerApp() }
        }
    }
}

@Composable
private fun LudoControllerApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val link = remember { PhoneLink(context) }
    var selectedPlayer by remember { mutableIntStateOf(0) }
    var forcedDice by remember { mutableStateOf<Int?>(null) }
    var lastDice by remember { mutableIntStateOf(1) }
    var turn by remember { mutableIntStateOf(0) }
    var gameNumber by remember { mutableIntStateOf(1) }
    var linkStatus by remember { mutableStateOf("Not connected") }
    var bluetoothAddress by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(listOf<String>()) }
    val scrollState = rememberScrollState()
    val player = players[selectedPlayer]

    DisposableEffect(Unit) { onDispose { link.close() } }

    fun applyRemote(message: String) {
        val parts = message.split("|")
        when (parts.firstOrNull()) {
            "DICE" -> parts.getOrNull(1)?.toIntOrNull()?.let { if (it in 1..6) forcedDice = it else forcedDice = null }
            "PLAYER" -> parts.getOrNull(1)?.toIntOrNull()?.let { if (it in players.indices) { selectedPlayer = it; turn = it } }
            "NEW" -> {
                gameNumber++
                lastDice = 1
                selectedPlayer = 0
                turn = 0
                forcedDice = null
                history = emptyList()
            }
            "ROLL" -> {
                val value = parts.getOrNull(1)?.toIntOrNull() ?: return
                val p = parts.getOrNull(2)?.toIntOrNull() ?: 0
                lastDice = value
                if (p in players.indices) selectedPlayer = p
                history = (listOf(players[p.coerceIn(0, 3)].name + " rolled " + value) + history).take(8)
            }
        }
    }

    fun rollDice() {
        val value = forcedDice ?: Random.nextInt(1, 7)
        lastDice = value
        history = (listOf(player.name + " rolled " + value) + history).take(8)
        link.send("ROLL|" + value + "|" + selectedPlayer)
        forcedDice = null
        turn = (turn + 1) % players.size
        selectedPlayer = turn
        link.send("PLAYER|" + turn)
    }

    fun newGame() {
        gameNumber++
        lastDice = 1
        turn = 0
        selectedPlayer = 0
        forcedDice = null
        history = emptyList()
        link.send("NEW")
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090A0F)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF151722))
                    .border(1.dp, Color(0xFF7658FF), RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("LUDO LAB", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        Text("PHONE • PLAY • CONNECT", color = Color(0xFF8E94A3), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("2P", color = Color(0xFFB9A4FF), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }            // Lower, single-piece Ludo Lab header.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF151821))
                    .border(2.dp, Color(0xFF7658FF), RoundedCornerShape(28.dp))
                    .padding(horizontal = 20.dp, vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LUDO LAB", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text("PHONE • PLAY • CONNECT", color = Color(0xFF9A90C9), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
