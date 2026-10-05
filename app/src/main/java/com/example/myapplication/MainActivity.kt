package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    val link = remember { PhoneLink() }
    var selectedPlayer by remember { mutableIntStateOf(0) }
    var forcedDice by remember { mutableStateOf<Int?>(null) }
    var lastDice by remember { mutableIntStateOf(1) }
    var turn by remember { mutableIntStateOf(0) }
    var gameNumber by remember { mutableIntStateOf(1) }
    var linkStatus by remember { mutableStateOf("Not connected") }
    var ipAddress by remember { mutableStateOf("") }
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
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Lower, more game-like header with a circular Ludo-style layer.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(68.dp).clip(CircleShape)
                        .background(Color(0xFF171A23))
                        .border(3.dp, Color(0xFF7658FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(Color(0xFF0D0F15))
                            .border(2.dp, Color(0xFFB9A4FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("L", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("LUDO LAB", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("PHONE • PLAY • CONNECT", color = Color(0xFF8E94A3), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text("2P", color = Color(0xFFB9A4FF), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF11131A)), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PHONE-TO-PHONE CONNECTION", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "Put both phones on the same Wi-Fi or one phone's hotspot. Phone 1 hosts; Phone 2 joins using the host IP.",
                        color = Color(0xFF9AA0AC), fontSize = 11.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                link.host({ linkStatus = it }, { applyRemote(it) })
                                linkStatus = "Starting host…"
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C4DFF)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("HOST") }
                        OutlinedButton(
                            onClick = {
                                if (ipAddress.isNotBlank()) link.join(ipAddress, { linkStatus = it }, { applyRemote(it) })
                                else linkStatus = "Enter the host IP first"
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("JOIN") }
                    }
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Host IP address") },
                        placeholder = { Text("Example: 192.168.1.25") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    Text(
                        "This phone IP: " + (localIpAddress() ?: "Unavailable") + "   •   Status: " + linkStatus,
                        color = if (linkStatus == "Connected") Color(0xFF6EE7B7) else Color(0xFF9AA0AC),
                        fontSize = 11.sp
                    )
                }
            }

            Text("PLAYER", color = Color(0xFF8E94A3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(players.indices.toList()) { index ->
                    PlayerChip(players[index], selectedPlayer == index) {
                        selectedPlayer = index
                        link.send("PLAYER|" + index)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF11131A)), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("FORCE NEXT DICE — YOUR LOCAL GAME", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("The selected value is synchronized to the second copy of this app.", color = Color(0xFF8E94A3), fontSize = 11.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        (1..6).forEach { value ->
                            val selected = forcedDice == value
                            Box(
                                modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Color(0xFF7658FF) else Color(0xFF1A1D26))
                                    .border(1.dp, if (selected) Color(0xFFB9A4FF) else Color(0xFF2A2E3A), RoundedCornerShape(10.dp))
                                    .clickable {
                                        forcedDice = if (selected) null else value
                                        link.send("DICE|" + (forcedDice ?: 0))
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(value.toString(), color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { forcedDice = null; link.send("DICE|0") },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                        ) { Text("Clear forced") }
                        Button(
                            onClick = { rollDice() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = player.color),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("ROLL DICE", color = Color.Black, fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("TURN", player.name, player.color, Modifier.weight(1f))
                StatCard("LAST DICE", lastDice.toString(), Color(0xFFB9A4FF), Modifier.weight(1f))
                StatCard("GAME", "#" + gameNumber, Color(0xFF6EE7B7), Modifier.weight(1f))
            }

            Text("BOARD PREVIEW", color = Color(0xFF8E94A3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            LudoBoard(lastDice, selectedPlayer)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { history = emptyList() }, modifier = Modifier.weight(1f)) { Text("Clear history") }
                Button(
                    onClick = { newGame() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242834)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("NEW GAME", color = Color.White) }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF11131A)), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("ROLL HISTORY", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (history.isEmpty()) Text("No rolls yet.", color = Color(0xFF777D8A), fontSize = 12.sp)
                    else history.forEachIndexed { index, item ->
                        Text(item, color = if (index == 0) Color.White else Color(0xFF858B98), fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }

            Text(
                "Two-phone mode works for this app's own local simulator. It does not alter a third-party Ludo game.",
                color = Color(0xFF626875), fontSize = 10.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )
        }
    }
}

private fun localIpAddress(): String? {
    return try {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.hostAddress?.startsWith("192.168.") == true }
            ?.hostAddress
    } catch (_: Exception) { null }
}

@Composable
private fun PlayerChip(player: Player, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) player.color.copy(alpha = 0.20f) else Color(0xFF141720))
            .border(1.dp, if (selected) player.color else Color(0xFF252936), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(player.color))
        Spacer(Modifier.width(7.dp))
        Text(player.name, color = Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun StatCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF11131A)), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = Color(0xFF777D8A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun LudoBoard(lastDice: Int, selectedPlayer: Int) {
    val colors = players.map { it.color }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF11131A)), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                val board = size.minDimension
                val left = (size.width - board) / 2f
                val top = (size.height - board) / 2f
                val cell = board / 15f
                drawRect(Color(0xFF1B1E27), Offset(left, top), Size(board, board), style = Stroke(2f))
                drawRect(colors[0], Offset(left, top), Size(cell * 6, cell * 6))
                drawRect(colors[1], Offset(left + cell * 9, top), Size(cell * 6, cell * 6))
                drawRect(colors[2], Offset(left, top + cell * 9), Size(cell * 6, cell * 6))
                drawRect(colors[3], Offset(left + cell * 9, top + cell * 9), Size(cell * 6, cell * 6))
                drawRect(Color(0xFFF5F5F5), Offset(left + cell * 6, top + cell * 6), Size(cell * 3, cell * 3))
                for (i in 0..15) {
                    val x = left + i * cell
                    val y = top + i * cell
                    drawLine(Color(0x33262A35), Offset(x, top), Offset(x, top + board))
                    drawLine(Color(0x33262A35), Offset(left, y), Offset(left + board, y))
                }
                val tokenCenters = listOf(
                    Offset(left + cell * 2f, top + cell * 2f),
                    Offset(left + cell * 12f, top + cell * 2f),
                    Offset(left + cell * 2f, top + cell * 12f),
                    Offset(left + cell * 12f, top + cell * 12f)
                )
                tokenCenters.forEachIndexed { index, center ->
                    drawCircle(colors[index], radius = cell * 0.85f, center = center)
                    drawCircle(Color.White, radius = cell * 0.85f, center = center, style = Stroke(width = 3f))
                    drawCircle(Color.Black.copy(alpha = 0.22f), radius = cell * 0.30f, center = center)
                }
                drawCircle(Color.White, radius = cell * 0.18f + lastDice * 0.7f, center = tokenCenters[selectedPlayer], style = Stroke(width = 4f))
            }
            Spacer(Modifier.height(6.dp))
            Text("Selected: " + players[selectedPlayer].name + "   •   Last dice: " + lastDice, color = Color(0xFF9AA0AC), fontSize = 11.sp)
        }
    }
}
