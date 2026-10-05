package com.example.myapplication.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.LudoViewModel
import com.example.myapplication.model.ludoPlayers

@Composable
fun LudoScreen(
    viewModel: LudoViewModel
) {
    var joinCode by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            pendingAction?.invoke()
            pendingAction = null
        }

    fun requestPermissionsAndRun(
        host: Boolean,
        action: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            action()
            return
        }

        pendingAction = action
        permissionLauncher.launch(
            viewModel.requiredPermissions(host)
        )
    }

    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF090A0F)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderCard()

            ConnectionCard(
                viewModel = viewModel,
                joinCode = joinCode,
                onJoinCodeChange = { joinCode = it },
                onCreate = {
                    requestPermissionsAndRun(true) {
                        viewModel.createGame()
                    }
                },
                onJoin = {
                    requestPermissionsAndRun(false) {
                        viewModel.joinGame(joinCode)
                    }
                }
            )

            PlayersCard(viewModel)

            DiceCard(viewModel)

            HistoryCard(viewModel.history)

            Text(
                text = "This is the Ludo game in this app. Two phones can sync using a 4-digit connection code.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = Color(0xFF6F7482),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HeaderCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF151821))
            .border(
                2.dp,
                Color(0xFF7658FF),
                RoundedCornerShape(28.dp)
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "LUDO LAB",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "PHONE • PLAY • CONNECT",
                color = Color(0xFF9A90C9),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    viewModel: LudoViewModel,
    joinCode: String,
    onJoinCodeChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF151821)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "BLUETOOTH CONNECTION",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Phone 1 creates a 4-digit code. Phone 2 enters the same code. No MAC address.",
                color = Color(0xFFB7BBC7),
                fontSize = 12.sp
            )

            Text(
                text = "Status: " + viewModel.linkStatus,
                color = if (
                    viewModel.linkStatus.startsWith("Connected")
                ) {
                    Color(0xFF69E58A)
                } else {
                    Color(0xFFFFC857)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCreate,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7658FF)
                    )
                ) {
                    Text("CREATE GAME")
                }

                Button(
                    onClick = onJoin,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4E3A9E)
                    )
                ) {
                    Text("JOIN GAME")
                }
            }

            if (viewModel.gameCode.isNotEmpty()) {
                Text(
                    text = "GAME CODE",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFB7BBC7),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = viewModel.gameCode,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }

            OutlinedTextField(
                value = joinCode,
                onValueChange = { value ->
                    if (
                        value.length <= 4 &&
                        value.all(Char::isDigit)
                    ) {
                        onJoinCodeChange(value)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("4-digit game code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                )
            )
        }
    }
}

@Composable
private fun PlayersCard(
    viewModel: LudoViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF151821)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "PLAYERS",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ludoPlayers.indices.toList()) { index ->
                    val selected =
                        index == viewModel.selectedPlayer

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(
                                if (selected) {
                                    Color(0xFF29223F)
                                } else {
                                    Color(0xFF20232D)
                                }
                            )
                            .border(
                                1.dp,
                                if (selected) {
                                    ludoPlayers[index].color
                                } else {
                                    Color.Transparent
                                },
                                RoundedCornerShape(50.dp)
                            )
                            .clickable {
                                viewModel.selectPlayer(index)
                            }
                            .padding(
                                horizontal = 12.dp,
                                vertical = 8.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    ludoPlayers[index].color
                                )
                        )

                        Spacer(Modifier.width(6.dp))

                        Text(
                            text = ludoPlayers[index].name,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "Game #" + viewModel.gameNumber +
                    " • Turn: " +
                    ludoPlayers[viewModel.selectedPlayer].name,
                color = Color(0xFFB7BBC7),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DiceCard(
    viewModel: LudoViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF151821)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "NEXT DICE",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (viewModel.forcedDice == null) {
                    "Random"
                } else {
                    "Selected: " +
                        viewModel.forcedDice
                },
                color = Color(0xFFB9A4FF),
                fontSize = 12.sp
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items((1..6).toList()) { value ->
                    val selected =
                        viewModel.forcedDice == value

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) {
                                    Color(0xFF7658FF)
                                } else {
                                    Color(0xFF20232D)
                                }
                            )
                            .clickable {
                                viewModel.selectDice(value)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = value.toString(),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Button(
                onClick = viewModel::rollDice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7658FF)
                )
            ) {
                Text(
                    text = "ROLL DICE  •  " +
                        viewModel.lastDice,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Button(
                onClick = viewModel::newGame,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF282D3A)
                )
            ) {
                Text("NEW GAME")
            }
        }
    }
}

@Composable
private fun HistoryCard(
    history: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF151821)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "HISTORY",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            if (history.isEmpty()) {
                Text(
                    text = "No rolls yet.",
                    color = Color(0xFF8E94A3),
                    fontSize = 12.sp
                )
            } else {
                history.forEachIndexed { index, item ->
                    Text(
                        text = (index + 1).toString() +
                            ". " + item,
                        color = Color(0xFFB7BBC7),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
