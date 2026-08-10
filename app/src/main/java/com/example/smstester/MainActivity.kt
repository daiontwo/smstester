package com.antteam.smstester

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import android.app.AlarmManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

    private val permissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            checkBatteryOptimization()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SmsTesterApp()
        }
    }

    override fun onResume() {
        super.onResume()
        requestRequiredPermissions()
    }

    private fun requestRequiredPermissions() {

        val permissions = mutableListOf<String>()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.SEND_SMS)
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECEIVE_SMS)
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            permissionsLauncher.launch(permissions.toTypedArray())
        } else {
            checkBatteryOptimization()
        }
    }

    private fun checkBatteryOptimization() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        val powerManager =
            getSystemService(POWER_SERVICE) as PowerManager

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {

            try {

                val intent =
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    ).apply {
                        data =
                            Uri.parse(
                                "package:$packageName"
                            )
                    }

                startActivity(intent)

            } catch (_: Exception) {

                startActivity(
                    Intent(
                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                    )
                )
            }

        } else {

            checkExactAlarmPermission()
        }
        return
    }
    private fun checkExactAlarmPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val alarmManager =
                getSystemService(
                    ALARM_SERVICE
                ) as AlarmManager

            if (!alarmManager.canScheduleExactAlarms()) {

                try {
                    val intent =
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                        ).apply {
                            data =
                                Uri.parse(
                                    "package:$packageName"
                                )
                        }

                    startActivity(intent)

                } catch (_: Exception) {

                    val intent =
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                        )

                    startActivity(intent)
                }
            }
        }
    }
}

@Composable

fun SmsTesterApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(
            "sms_tester_prefs",
            android.content.Context.MODE_PRIVATE
        )
    }
    var phone by remember {
        mutableStateOf(
            prefs.getString("phone", "7878") ?: "7878"
        )
    }

    var message by remember {
        mutableStateOf(
            prefs.getString("message", "") ?: ""
        )
    }

    var sendLimitText by remember {
        mutableStateOf(
            prefs.getString("sendLimitText", "5") ?: "5"
        )
    }

    var summ by remember {
        mutableStateOf(
            prefs.getString("summ", "14700") ?: "14700"
        )
    }

    var darkTheme by remember {
        mutableStateOf(
            prefs.getBoolean("darkTheme", false)
        )
    }

    var scheduleMinute by remember {
        mutableStateOf(
            prefs.getString("scheduleMinute", "59") ?: "59"
        )
    }

    var scheduleSecond by remember {
        mutableStateOf(
            prefs.getString("scheduleSecond", "52") ?: "52"
        )
    }

    var smsDelayValue by remember {
        mutableStateOf(
            prefs.getString("smsDelayValue", "1") ?: "1"
        )
    }

    var delayInMs by remember {
        mutableStateOf(
            prefs.getBoolean("delayInMs", false)
        )
    }
    var running by remember {
        mutableStateOf(
            SmsSendingService.isRunning(context)
        )
    }
    var autoReply by remember {
        mutableStateOf(
            prefs.getBoolean("autoReply", false)
        )
    }

    var keyword by remember {
        mutableStateOf(
            prefs.getString("keyword", "8464") ?: "8464"
        )
    }

    var replyText by remember {
        mutableStateOf(
            prefs.getString("replyText", "да") ?: "да"
        )
    }

    var autoReplyLimit by remember {
        mutableStateOf(
            prefs.getString("autoReplyLimit", "2") ?: "2"
        )
    }

    val autoReplyState by SmsStore.autoReplyState.collectAsState()
    val autoReplySuccess by SmsStore.autoReplySuccess.collectAsState()

    val incoming by SmsStore.lastIncoming.collectAsState()
    val failureEvent by
    SmsStore.failureEvent.collectAsState()

    val warningEvent by
    SmsStore.warningEvent.collectAsState()

    val canSend = phone.isNotBlank() &&
            message.isNotBlank() &&
            summ.isNotBlank()

    val canStart = canSend &&
            sendLimitText.isNotBlank() &&
            smsDelayValue.isNotBlank() &&
            scheduleMinute.isNotBlank() &&
            scheduleSecond.isNotBlank()


    var previousAutoReplyEnabled by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(failureEvent) {

        if (failureEvent != null) {
            running = false
            autoReply = false
        }
    }

    LaunchedEffect(
        autoReply,
        keyword,
        replyText,
        autoReplyLimit
    ) {

        val limit = autoReplyLimit
            .toIntOrNull()
            ?.coerceIn(1, 999)
            ?: 1

        SmsStore.autoReplyKeyword = keyword
        SmsStore.autoReplyText = replyText
        SmsStore.autoReplyLimit = limit

        // Новый цикл автоответа:
        // OFF -> ON = начинаем снова с 0 из N
        if (autoReply && !previousAutoReplyEnabled) {
            SmsStore.resetAutoReplyCounter()
        }

        SmsStore.autoReplyEnabled = autoReply

        previousAutoReplyEnabled = autoReply

        prefs.edit()
            .putBoolean("autoReply", autoReply)
            .putString("keyword", keyword)
            .putString("replyText", replyText)
            .putString("autoReplyLimit", autoReplyLimit)
            .apply()
    }

    LaunchedEffect(autoReplyState.completed) {

        if (autoReplyState.completed) {
            autoReply = false
        }
    }

    LaunchedEffect(
        phone,
        message,
        sendLimitText,
        summ,
        darkTheme,
        scheduleMinute,
        scheduleSecond,
        smsDelayValue,
        delayInMs
    ) {
        prefs.edit()
            .putString("phone", phone)
            .putString("message", message)
            .putString("sendLimitText", sendLimitText)
            .putString("summ", summ)
            .putBoolean("darkTheme", darkTheme)
            .putString("scheduleMinute", scheduleMinute)
            .putString("scheduleSecond", scheduleSecond)
            .putString("smsDelayValue", smsDelayValue)
            .putBoolean("delayInMs", delayInMs)
            .apply()
    }

    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkColorScheme()
        } else {
            lightColorScheme()
        }
    ) {
        warningEvent?.let { warning ->

            val timeText =
                java.text.SimpleDateFormat(
                    "HH:mm:ss",
                    java.util.Locale.getDefault()
                ).format(
                    java.util.Date(
                        warning.timeMillis
                    )
                )

            AlertDialog(
                onDismissRequest = {
                    SmsStore.clearWarningEvent()
                },

                icon = {
                    Text(
                        text = "!",
                        color = Color(0xFFF9A825),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                },

                title = {
                    Text(
                        text = "Ожидает ответа",
                        color = Color(0xFFF9A825),
                        fontWeight = FontWeight.Bold
                    )
                },

                text = {

                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFFFFF8E1),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(14.dp)
                        ) {

                            Column(
                                verticalArrangement =
                                    Arrangement.spacedBy(6.dp)
                            ) {

                                Text(
                                    text = "Получено сообщение, требуется ответ",
                                    color = Color(0xFFF57F17),
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "Номер: ${warning.phone}"
                                )

                                Text(
                                    text = "Время: $timeText"
                                )

                                Text(
                                    text = "Сумма: $summ"
                                )

                                Text(
                                    text = "Сообщение:",
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = warning.text
                                )
                            }
                        }
                    }
                },

                confirmButton = {

                    Button(
                        onClick = {
                            SmsStore.clearWarningEvent()
                        },

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF9A825)
                            )
                    ) {
                        Text("OK")
                    }
                }
            )
        }
        failureEvent?.let { failure ->

            val timeText =
                java.text.SimpleDateFormat(
                    "HH:mm:ss",
                    java.util.Locale.getDefault()
                ).format(
                    java.util.Date(
                        failure.timeMillis
                    )
                )

            AlertDialog(
                onDismissRequest = {
                    // Не закрываем нажатием мимо окна.
                    // Пользователь должен нажать OK.
                },

                icon = {
                    Text(
                        text = "✕",
                        color = Color(0xFFC62828),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                },

                title = {
                    Text(
                        text = "Операция отклонена",
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold
                    )
                },

                text = {

                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFFFFEBEE),
                                    shape =
                                        RoundedCornerShape(12.dp)
                                )
                                .padding(14.dp)
                        ) {

                            Column(
                                verticalArrangement =
                                    Arrangement.spacedBy(6.dp)
                            ) {

                                Text(
                                    text = "Все процессы остановлены",
                                    color = Color(0xFFC62828),
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "Номер: ${failure.phone}"
                                )

                                Text(
                                    text = "Время: $timeText"
                                )

                                Text(
                                    text = "Ответ: ${failure.text}"
                                )
                            }
                        }
                    }
                },

                confirmButton = {

                    Button(
                        onClick = {
                            SmsStore.clearFailureEvent()
                        },

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    Color(0xFFC62828)
                            )
                    ) {
                        Text("OK")
                    }
                }
            )
        }

        autoReplySuccess?.let { success ->

            val timeText =
                java.text.SimpleDateFormat(
                    "HH:mm:ss",
                    java.util.Locale.getDefault()
                ).format(
                    java.util.Date(success.timeMillis)
                )

            AlertDialog(
                onDismissRequest = {
                    SmsStore.clearSuccessEvent()
                },

                icon = {
                    Text(
                        text = "✓",
                        color = Color(0xFF2E7D32),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                },

                title = {
                    Text(
                        text = "Успешно!",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                },

                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFFE8F5E9),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(14.dp)
                        ) {

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {

                                Text("Номер: ${success.phone}")
                                Text("Время: $timeText")
                                Text("Сумма: $summ")

                                Text(
                                    text =
                                        if (success.sent >= success.limit) {
                                            "Завершено: ${success.sent} из ${success.limit}"
                                        } else {
                                            "Ответ: ${success.sent} из ${success.limit}"
                                        },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },

                confirmButton = {
                    Button(
                        onClick = {
                            SmsStore.clearSuccessEvent()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32)
                        )
                    ) {
                        Text("OK")
                    }
                }
            )
        }
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

                                val minute = scheduleMinute.toIntOrNull()
                                val second = scheduleSecond.toIntOrNull()
                                val limit = sendLimitText.toIntOrNull()
                                val delayValue = smsDelayValue.toLongOrNull()

                                if (
                                    minute != null &&
                                    minute in 0..59 &&
                                    second != null &&
                                    second in 0..59 &&
                                    limit != null &&
                                    limit > 0 &&
                                    delayValue != null &&
                                    delayValue >= 0
                                ) {

                                    val serviceIntent = Intent(
                                        context,
                                        SmsSendingService::class.java
                                    ).apply {

                                        action = SmsSendingService.ACTION_START

                                        putExtra(
                                            SmsSendingService.EXTRA_PHONE,
                                            phone
                                        )

                                        putExtra(
                                            SmsSendingService.EXTRA_MESSAGE,
                                            message
                                        )

                                        putExtra(
                                            SmsSendingService.EXTRA_SUM,
                                            summ
                                        )

                                        putExtra(
                                            SmsSendingService.EXTRA_MINUTE,
                                            minute
                                        )

                                        putExtra(
                                            SmsSendingService.EXTRA_SECOND,
                                            second
                                        )

                                        putExtra(
                                            SmsSendingService.EXTRA_LIMIT,
                                            limit
                                        )

                                        putExtra(
                                            SmsSendingService.EXTRA_DELAY,
                                            delayValue
                                        )

                                        putExtra(
                                            SmsSendingService.EXTRA_DELAY_IN_MS,
                                            delayInMs
                                        )
                                    }

                                    ContextCompat.startForegroundService(
                                        context,
                                        serviceIntent
                                    )

                                    running = true
                                }

                            } else {

                                val serviceIntent = Intent(
                                    context,
                                    SmsSendingService::class.java
                                ).apply {
                                    action = SmsSendingService.ACTION_STOP
                                }

                                context.startService(serviceIntent)

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

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {

                    Switch(
                        checked = autoReply,
                        onCheckedChange = { autoReply = it }
                    )

                    Spacer(Modifier.width(8.dp))

                    Text("Автоответ")

                    Spacer(Modifier.weight(1f))

                    if (autoReplyState.completed) {

                        Text(
                            text = "Завершено (${autoReplyState.sent} из ${autoReplyState.limit})",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )

                    } else if (autoReply || autoReplyState.sent > 0) {

                        Text(
                            text = "${autoReplyState.sent} из ${autoReplyState.limit}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
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

                        SelectAllTextField(
                            value = autoReplyLimit,
                            onValueChange = {
                                autoReplyLimit =
                                    it.filter(Char::isDigit)
                                        .take(3)
                            },
                            label = "Лимит автоответа",
                            modifier = Modifier.fillMaxWidth(),
                            keyboardType = KeyboardType.Number
                        )

                    }
            }
        }
    }
}
