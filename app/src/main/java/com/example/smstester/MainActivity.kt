package com.antteam.smstester

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
@Composable
fun SelectAllTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value))
    }

    LaunchedEffect(value) {
        fieldValue = fieldValue.copy(
            text = value,
            selection = TextRange(value.length)
        )
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        label = { Text(label) },
        modifier = modifier.onFocusChanged { focus ->

            if (focus.isFocused) {
                fieldValue = fieldValue.copy(
                    selection = TextRange(
                        0,
                        fieldValue.text.length
                    )
                )
            }

        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType
        )
    )
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmsTesterApp() }
    }
}

@Composable

fun SmsTesterApp() {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var sendLimitText by remember { mutableStateOf("5") }
    var summ by remember { mutableStateOf("14700") }
    var darkTheme by remember { mutableStateOf(false) }
    var scheduleMinute by remember { mutableStateOf("59") }
    var scheduleSecond by remember { mutableStateOf("52") }
    var smsDelayValue by remember { mutableStateOf("1") }
    var delayInMs by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var sendJob by remember { mutableStateOf<Job?>(null) }

    var autoReply by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("8464") }
    var replyText by remember { mutableStateOf("да") }

    val incoming by SmsStore.lastIncoming.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val canSend = phone.isNotBlank() &&
            message.isNotBlank() &&
            summ.isNotBlank()

    val canStart = canSend &&
            sendLimitText.isNotBlank() &&
            smsDelayValue.isNotBlank() &&
            scheduleMinute.isNotBlank() &&
            scheduleSecond.isNotBlank()

    var lastConfig by remember { mutableStateOf("") }

    val currentConfig =
        "$phone|$message|$summ|$scheduleMinute|$scheduleSecond|$smsDelayValue|$delayInMs|$sendLimitText"


    LaunchedEffect(currentConfig) {

        if (running && lastConfig.isNotEmpty() && currentConfig != lastConfig) {

            sendJob?.cancel()
            sendJob = null
            running = false
        }

        lastConfig = currentConfig
    }


    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS
            )
        )
    }

    LaunchedEffect(autoReply, keyword, replyText) {
        SmsStore.autoReplyEnabled = autoReply
        SmsStore.autoReplyKeyword = keyword
        SmsStore.autoReplyText = replyText
    }

    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkColorScheme()
        } else {
            lightColorScheme()
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .padding(20.dp, 50.dp , 20.dp , 70.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {

                    Text(
                        "SMS Tester",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        modifier = Modifier.padding(5.dp),
                        text = if (darkTheme)
                            "🌙"
                        else
                            "☀️",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = {
                            darkTheme = it
                        }
                    )
                }

                SelectAllTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "Номер",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Phone,
                )

                SelectAllTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = "Реквизит",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Phone,
                )

                SelectAllTextField(
                    value = summ,
                    onValueChange = { summ = it },
                    label = "Сумма отправки",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Phone,
                    )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = canSend,
                        onClick = {
                            SmsSender.send(phone, "$message $summ")
                        }
                    ) {
                        Text("Отправить")
                    }
                }


                // Минута запуска и секунда
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {


                    Spacer(Modifier.width(8.dp))

                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),) {
                    SelectAllTextField(
                        value = scheduleMinute,
                        onValueChange = { scheduleMinute = it.filter(Char::isDigit) },
                        label = "Минута запуска",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Phone,)

                    SelectAllTextField(
                        value = scheduleSecond,
                        onValueChange = { scheduleSecond = it.filter(Char::isDigit) },
                        label = "Секунда запуска",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Phone,)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    SelectAllTextField(
                        value = smsDelayValue,
                        onValueChange = {
                            smsDelayValue = it.filter(Char::isDigit)
                        },
                        label = if (delayInMs) "Интервал мс" else "Интервал сек",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number
                    )

                    SelectAllTextField(
                        value = sendLimitText,
                        onValueChange = {
                            sendLimitText = it.filter(Char::isDigit)
                        },
                        label = "Количество отправок",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number
                    )
                }

                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Switch(
                        checked = delayInMs,

                        onCheckedChange = {
                            delayInMs = it
                        }
                    )
                    Text(
                        modifier = Modifier.padding(10.dp),
                        text = if (delayInMs) "миллисекунды" else "секунды",
                        color = Color(0xFF666666),
                    )

                }

                Text(
                    text =
                        "Каждый час в $scheduleMinute минут $scheduleSecond секунд\n" +
                        "Отправок: $sendLimitText\n" +
                        "Интервал: $smsDelayValue ${if(delayInMs) "мс" else "сек"}",
                    color = Color(0xFF666666),
                    style = MaterialTheme.typography.bodyMedium
                )


                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = canStart || running,
                        onClick = {
                            if (!running) {
                                running = true
                                sendJob = scope.launch {

                                    val minute = scheduleMinute.toIntOrNull() ?: 59
                                    val second = scheduleSecond.toIntOrNull() ?: 50
                                    val limit = sendLimitText.toIntOrNull() ?: 5

                                    while (isActive) {

                                        val now = LocalDateTime.now()

                                        var next = now
                                            .withMinute(minute)
                                            .withSecond(second)
                                            .withNano(0)

                                        // если время уже прошло — ждём следующий час
                                        if (!next.isAfter(now)) {
                                            next = next.plusHours(1)
                                        }

                                        val waitMillis = ChronoUnit.MILLIS.between(now, next)

                                        delay(waitMillis)


                                        // отправляем пачку SMS
                                        val smsDelay = smsDelayValue.toLongOrNull() ?: 1L

                                        val delayMillis = if (delayInMs) {
                                            smsDelay
                                        } else {
                                            smsDelay * 1000
                                        }

                                        repeat(limit) {

                                            SmsSender.send(
                                                phone,
                                                "$message $summ"
                                            )

                                            delay(delayMillis)
                                        }

                                        running = false
                                        sendJob = null
                                    }
                                }
                            } else {
                                sendJob?.cancel()
                                sendJob = null
                                running = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (running) Color.Red else Color(0xFF4CAF50)
                        ),

                    ) {
                        Text(
                            if (running) "Стоп" else "Старт"
                        )
                    }
                }

                HorizontalDivider()

                Text("Последнее входящее SMS")

                if (incoming.from == "8464") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFFDFF7DF),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "УСПЕШНО!",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text("От: ${incoming.from.ifBlank { "—" }}")
                Text("Текст: ${incoming.text.ifBlank { "—" }}")

                HorizontalDivider()

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = autoReply, onCheckedChange = { autoReply = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Автоответ")
                }
                    if (autoReply) {
                        SelectAllTextField(
                            value = keyword,
                            onValueChange = { keyword = it.filter(Char::isDigit) },
                            label = "Номер отправителя",
                            modifier = Modifier.fillMaxWidth(),
                            keyboardType = KeyboardType.Phone,
                        )

                        SelectAllTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            label = "Ответить текстом",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

            }
        }
    }
}
