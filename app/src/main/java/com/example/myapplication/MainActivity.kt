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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.io.PrintWriter
import java.util.UUID
import java.util.concurrent.Executors
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.graphics.Color
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
    private var writer: PrintWriter? = null
    private var discoveryReceiver: BroadcastReceiver? = null

    companion object {
        private const val SERVICE_NAME = "LudoLabGame"
        private val SERVICE_UUID = UUID.fromString("8c5b6e10-6a9e-4e43-9c0d-4d1a8c5a4b21")
    }

    private fun adapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    fun createGame(onCode: (String) -> Unit, onStatus: (String) -> Unit, onMessage: (String) -> Unit) {
        executor.execute {
            try {
                val a = adapter() ?: throw IllegalStateException("Bluetooth is not supported")
                val code = Random.nextInt(1000, 10000).toString()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) throw SecurityException("Bluetooth advertise permission required")
                a.name = "LUDO-" + code
                server?.close()
                server = a.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                main.post { onCode(code); onStatus("Waiting for Player 2…") }
                try { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply { putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) { }
                val accepted = server!!.accept()
                socket = accepted
                writer = PrintWriter(accepted.outputStream, true)
                main.post { onStatus("Connected ✓") }
                listen(accepted, onMessage, onStatus)
            } catch (e: SecurityException) { main.post { onStatus(e.message ?: "Bluetooth permission required") } }
            catch (e: Exception) { main.post { onStatus("Create error: " + (e.message ?: "connection failed")) } }
        }
    }

    fun joinGame(code: String, onStatus: (String) -> Unit, onMessage: (String) -> Unit) {
        executor.execute {
            try {
                val a = adapter() ?: throw IllegalStateException("Bluetooth is not supported")
                val targetName = "LUDO-" + code.trim()
                a.cancelDiscovery()
                main.post { onStatus("Searching for " + targetName + "…") }
                fun tryDevice(device: BluetoothDevice): Boolean = try {
                    if (device.name != targetName) return false
                    val s = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                    s.connect(); socket = s; writer = PrintWriter(s.outputStream, true)
                    main.post { onStatus("Connected ✓") }; listen(s, onMessage, onStatus); true
                } catch (_: Exception) { false }
                for (device in a.bondedDevices.toList()) if (tryDevice(device)) return@execute
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (intent?.action != BluetoothDevice.ACTION_FOUND) return
                        val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        executor.execute { if (device != null && tryDevice(device)) { try { a.cancelDiscovery() } catch (_: Exception) {}; unregisterDiscoveryReceiver() } }
                    }
                }
                discoveryReceiver = receiver
                val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
                a.startDiscovery()
                main.postDelayed({ try { a.cancelDiscovery(); unregisterDiscoveryReceiver() } catch (_: Exception) {}; if (socket == null) onStatus("Game " + code + " not found. Keep phones close and try again.") }, 20000)
            } catch (e: SecurityException) { main.post { onStatus("Bluetooth permission required") } }
            catch (e: Exception) { main.post { onStatus("Join error: " + (e.message ?: "try again")) } }
        }
    }

    private fun unregisterDiscoveryReceiver() { try { discoveryReceiver?.let { context.unregisterReceiver(it) } } catch (_: Exception) {}; discoveryReceiver = null }
    private fun listen(target: BluetoothSocket, onMessage: (String) -> Unit, onStatus: (String) -> Unit) {
        try { val reader = target.inputStream.bufferedReader(); while (target.isConnected) { val line = reader.readLine() ?: break; main.post { onMessage(line) } } }
        catch (_: Exception) {} finally { main.post { onStatus("Disconnected") } }
    }
    fun send(message: String) { executor.execute { writer?.println(message) } }
    fun close() { unregisterDiscoveryReceiver(); try { socket?.close() } catch (_: Exception) {}; try { server?.close() } catch (_: Exception) {}; writer = null }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                LudoControllerApp()
            }
        }
    }
}

private fun bluetoothReady(context: Context, needAdvertise: Boolean = false): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val permissions = mutableListOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        if (needAdvertise) permissions += Manifest.permission.BLUETOOTH_ADVERTISE
        val missing = permissions.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            (context as? Activity)?.requestPermissions(missing.toTypedArray(), 1001)
            return false
        }    }
    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return false
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
    var gameCode by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(listOf<String>()) }
    val scrollState = rememberScrollState()
    val player = players[selectedPlayer]

    DisposableEffect(Unit) { onDispose { link.close() } }

    fun applyRemote(message: String) {
        val parts = message.split("|")
        when (parts.firstOrNull()) {
            "DICE" -> {
                val value = parts.getOrNull(1)?.toIntOrNull()
                forcedDice = if (value != null && value in 1..6) value else null
            }
            "PLAYER" -> {
                val value = parts.getOrNull(1)?.toIntOrNull()
                if (value != null && value in players.indices) {
                    selectedPlayer = value
                    turn = value
                }
            }
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
                val name = players[p.coerceIn(0, players.lastIndex)].name
                history = (listOf(name + " rolled " + value) + history).take(8)
            }
        }
    }

    fun createGame() { if (bluetoothReady(context, true)) link.createGame({ gameCode = it }, { linkStatus = it }, ::applyRemote) }

    fun joinGame() {
        if (joinCode.length != 4) { linkStatus = "Enter the 4-digit game code"; return }
        if (bluetoothReady(context)) link.joinGame(joinCode, { linkStatus = it }, ::applyRemote)
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151821)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("BLUETOOTH CONNECTION", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Phone 1 creates a 4-digit code. Phone 2 enters the same code. No MAC address.", color = Color(0xFFB7BBC7), fontSize = 12.sp)
                    Text("Status: " + linkStatus, color = if (linkStatus == "Connected") Color(0xFF69E58A) else Color(0xFFFFC857), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = ::createGame, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7658FF))) { Text("CREATE GAME") }
                        Button(onClick = ::joinGame, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4E3A9E))) { Text("JOIN GAME") }
                    }
                    if (gameCode.isNotEmpty()) {
                        Text("GAME CODE", modifier = Modifier.fillMaxWidth(), color = Color(0xFFB7BBC7), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(gameCode, modifier = Modifier.fillMaxWidth(), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    }
                    OutlinedTextField(value = joinCode, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) joinCode = it }, modifier = Modifier.fillMaxWidth(), label = { Text("4-digit game code") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151821)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PLAYERS", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(players.indices.toList()) { index ->
                            val selected = index == selectedPlayer
                            Row(
                                modifier = Modifier.clip(RoundedCornerShape(50.dp))
                                    .background(if (selected) Color(0xFF29223F) else Color(0xFF20232D))
                                    .border(1.dp, if (selected) players[index].color else Color.Transparent, RoundedCornerShape(50.dp))
                                    .clickable {
                                        selectedPlayer = index
                                        turn = index
                                        link.send("PLAYER|" + index)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(players[index].color))
                                Spacer(Modifier.width(6.dp))
                                Text(players[index].name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        "Game #" + gameNumber + " • Turn: " + player.name,
                        color = Color(0xFFB7BBC7), fontSize = 12.sp
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151821)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("NEXT DICE", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (forcedDice == null) "Random" else "Forced: " + forcedDice,
                        color = Color(0xFFB9A4FF), fontSize = 12.sp
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((1..6).toList()) { value ->
                            val selected = forcedDice == value
                            Box(
                                modifier = Modifier.size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Color(0xFF7658FF) else Color(0xFF20232D))
                                    .clickable {
                                        forcedDice = value
                                        link.send("DICE|" + value)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(value.toString(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Button(
                        onClick = ::rollDice,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7658FF))
                    ) {
                        Text("ROLL DICE  •  " + lastDice, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Button(
                        onClick = ::newGame,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282D3A))
                    ) { Text("NEW GAME") }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151821)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("HISTORY", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (history.isEmpty()) {
                        Text("No rolls yet.", color = Color(0xFF8E94A3), fontSize = 12.sp)
                    } else {
                        history.forEachIndexed { index, item ->
                            Text((index + 1).toString() + ". " + item, color = Color(0xFFB7BBC7), fontSize = 12.sp)
                        }
                    }
                }
            }

            Text(
                "This is the Ludo game in this app. Two phones can sync using the 4-digit connection code.",
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = Color(0xFF6F7482),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
