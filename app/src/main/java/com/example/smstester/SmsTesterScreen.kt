package com.antteam.smstester
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // После 7 секунд без ввода снимаем фокус. Изменение текста автоматически
    // перезапускает effect, поэтому старый таймер отменяется Compose.
    LaunchedEffect(focused, fieldValue.text) {
        if (focused) {
            delay(7_000)
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    LaunchedEffect(value) {
        // Не перезаписываем selection при обычном вводе: иначе внешний state
        // и курсор TextField начинают обновлять друг друга и поле мерцает.
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        label = { Text(label) },
        modifier = modifier.onFocusChanged { state ->
            // Выделяем текст только при переходе из unfocused в focused.
            // Повторные события focus не двигают курсор.
            if (state.isFocused && !focused) {
                fieldValue = fieldValue.copy(
                    selection = TextRange(0, fieldValue.text.length)
                )
            }
            focused = state.isFocused
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType
        )
    )
}

@Composable
fun ScheduleBlock(
    index: Int,
    schedule: ScheduleConfig,
    delayInMs: Boolean,
    canDelete: Boolean,
    onChange: (ScheduleConfig) -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (index == 0) "Основное расписание" else "Расписание ${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val minute = schedule.minute.padStart(2, '0')
                    val second = schedule.second.padStart(2, '0')
                    Text(
                        text = "Каждый час в $minute:$second",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectAllTextField(
                    value = schedule.minute,
                    onValueChange = { value ->
                        onChange(schedule.copy(minute = value.filter(Char::isDigit).take(2)))
                    },
                    label = "Минута",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number
                )
                SelectAllTextField(
                    value = schedule.second,
                    onValueChange = { value ->
                        onChange(schedule.copy(second = value.filter(Char::isDigit).take(2)))
                    },
                    label = "Секунда",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectAllTextField(
                    value = schedule.interval,
                    onValueChange = { value ->
                        onChange(schedule.copy(interval = value.filter(Char::isDigit)))
                    },
                    label = if (delayInMs) "Интервал мс" else "Интервал сек",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number
                )
                SelectAllTextField(
                    value = schedule.count,
                    onValueChange = { value ->
                        onChange(schedule.copy(count = value.filter(Char::isDigit)))
                    },
                    label = "Кол-во",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number
                )
            }

            if (!schedule.isValidSchedule()) {
                Text(
                    text = "Проверь значения: минута/секунда 0–59, количество от 1.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SmsTesterApp(onRequestCriticalPermissions: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---------------------------------------------------------
    // ЛИЦЕНЗИЯ / ТОКЕН
    // ---------------------------------------------------------
    var licenseChecked by remember { mutableStateOf(false) }
    var licenseActive by remember { mutableStateOf(false) }
    var licenseTokenInput by remember { mutableStateOf("") }
    var licenseError by remember { mutableStateOf("") }
    var licenseLoading by remember { mutableStateOf(false) }

    val deviceId = remember {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
    }
    var sendFlashVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SmsStore.smsSendEvents.collect {
            sendFlashVisible = true
            delay(180)
            sendFlashVisible = false
            delay(80)
        }
    }
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

    var schedules by remember {
        val saved = schedulesFromJson(prefs.getString("schedulesJson", null))
        mutableStateOf(
            if (saved.isNotEmpty()) saved
            else listOf(
                ScheduleConfig(
                    id = 1L,
                    minute = prefs.getString("scheduleMinute", "59") ?: "59",
                    second = prefs.getString("scheduleSecond", "52") ?: "52",
                    interval = prefs.getString("smsDelayValue", "1") ?: "1",
                    count = prefs.getString("sendLimitText", "5") ?: "5"
                )
            )
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

    var remoteConfigStatus by remember { mutableStateOf("Ожидание конфигурации") }
    var remoteConfigVersion by remember { mutableStateOf<Long?>(null) }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateLoading by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf("") }

    // Один раз при запуске проверяем уже сохранённый токен.
    LaunchedEffect(Unit) {
        val hasSavedToken = LicenseManager.getSavedToken(context) != null
        licenseActive = try {
            LicenseManager.validateDevice(context)
        } catch (_: Exception) {
            // Потеря интернета, таймаут или временный INTERNAL не должны
            // выбрасывать уже активированное устройство на экран токена.
            hasSavedToken
        }

        licenseChecked = true
    }

    // Пока лицензия активна, повторно проверяем её раз в минуту.
    LaunchedEffect(licenseActive) {
        if (!licenseActive) {
            RemoteCommandService.stop(context)
            return@LaunchedEffect
        }

        RemoteCommandService.start(context)

        availableUpdate = try {
            AppUpdateManager.check(context)
        } catch (_: Exception) {
            // Ошибка проверки обновления не должна блокировать запуск приложения.
            null
        }

        while (true) {
            delay(60_000)

            val stillActive = try {
                LicenseManager.validateDevice(context)
            } catch (_: Exception) {
                // При кратковременной сетевой ошибке не блокируем приложение.
                true
            }

            if (!stillActive) {
                licenseActive = false
                autoReply = false

                val stopIntent = Intent(
                    context,
                    SmsSendingService::class.java
                ).apply {
                    action = SmsSendingService.ACTION_STOP
                }

                context.startService(stopIntent)
                running = false
                break
            }
        }
    }

    // Пока проверка не закончилась, основной интерфейс не показываем.
    if (!licenseChecked) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        return
    }

    // Если устройство не активировано — показываем только экран токена.
    if (!licenseActive) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SMS Tester",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = licenseTokenInput,
                onValueChange = {
                    licenseTokenInput = LicenseManager.normalizeToken(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Токен доступа") },
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !licenseLoading && licenseTokenInput.isNotBlank(),
                onClick = {
                    scope.launch {
                        licenseLoading = true
                        licenseError = ""

                        try {
                            val success = LicenseManager.activateDevice(
                                context,
                                licenseTokenInput
                            )

                            if (success) {
                                licenseActive = true
                            } else {
                                licenseError = "Не удалось активировать токен"
                            }
                        } catch (e: Exception) {
                            licenseError =
                                e.message ?: "Ошибка активации"
                        }

                        licenseLoading = false
                    }
                }
            ) {
                if (licenseLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text("Активировать")
                }
            }

            if (licenseError.isNotBlank()) {
                Spacer(Modifier.height(12.dp))

                Text(
                    text = licenseError,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        return
    }

    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = {
                if (!update.required && !updateLoading) availableUpdate = null
            },
            title = {
                Text("Доступно обновление ${update.versionName}")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (update.required) {
                        Text(
                            "Это обязательное обновление.",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (update.releaseNotes.isNotBlank()) {
                        Text(update.releaseNotes)
                    }
                    if (updateLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Загрузка APK…")
                    }
                    if (updateError.isNotBlank()) {
                        Text(updateError, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !updateLoading,
                    onClick = {
                        scope.launch {
                            updateLoading = true
                            updateError = ""
                            try {
                                when (AppUpdateManager.downloadAndInstall(context, update)) {
                                    UpdateInstallResult.InstallerOpened -> Unit
                                    UpdateInstallResult.PermissionRequested -> {
                                        updateError = "Разреши установку из этого источника и нажми «Обновить» ещё раз."
                                    }
                                }
                            } catch (e: Exception) {
                                updateError = e.message ?: "Не удалось загрузить обновление"
                            } finally {
                                updateLoading = false
                            }
                        }
                    }
                ) {
                    Text("Обновить")
                }
            },
            dismissButton = if (!update.required) {
                {
                    TextButton(
                        enabled = !updateLoading,
                        onClick = { availableUpdate = null }
                    ) {
                        Text("Позже")
                    }
                }
            } else null
        )
    }

    // Регистрируем устройство в Firebase и слушаем его персональную конфигурацию
    // только после успешной проверки токена.
    DisposableEffect(deviceId) {
        DeviceConfigSync.registerDevice(context, deviceId)

        val subscription = DeviceConfigSync.listen(
            deviceId = deviceId,
            onConfig = { config ->
                config.phone?.let { phone = it }
                config.message?.let { message = it }
                config.sum?.let { summ = it }
                config.delayInMs?.let { delayInMs = it }
                config.autoReply?.let { autoReply = it }
                config.schedules?.let { remoteSchedules ->
                    if (remoteSchedules.isNotEmpty() &&
                        remoteSchedules.all { it.isValidSchedule() }
                    ) {
                        schedules = remoteSchedules
                    }
                }

                remoteConfigVersion = config.version
                remoteConfigStatus = "Конфигурация получена"
            },
            onError = { error ->
                remoteConfigStatus = "Ошибка Firebase: $error"
            }
        )

        onDispose {
            subscription?.stop()
        }
    }

    DisposableEffect(deviceId, licenseActive) {
        val commandPrefs = context.getSharedPreferences("remote_commands", android.content.Context.MODE_PRIVATE)
        val subscription = DeviceConfigSync.listenCommands(
            deviceId = deviceId,
            onCommand = { command ->
                if (commandPrefs.getString("lastCommandId", "") == command.commandId) {
                    return@listenCommands
                }

                if (command.action == "GET_PHONE") {
                    // Эту команду выполняет RemoteCommandService, в том числе
                    // когда экран приложения закрыт.
                    return@listenCommands
                }

                if (command.action == "VERIFY_PHONE") {
                    // Эту команду выполняет RemoteCommandService.
                    return@listenCommands
                }

                if (command.running == true) {
                    val valid = phone.isNotBlank() && message.isNotBlank() && summ.isNotBlank() &&
                        schedules.isNotEmpty() && schedules.all { it.isValidSchedule() }
                    if (!valid) {
                        DeviceConfigSync.reportRuntime(
                            deviceId, command.commandId, false,
                            "Нельзя запустить: сначала примени полный корректный конфиг"
                        )
                        return@listenCommands
                    }
                    val intent = Intent(context, SmsSendingService::class.java).apply {
                        action = SmsSendingService.ACTION_START
                        putExtra(SmsSendingService.EXTRA_PHONE, phone)
                        putExtra(SmsSendingService.EXTRA_MESSAGE, message)
                        putExtra(SmsSendingService.EXTRA_SUM, summ)
                        putExtra(SmsSendingService.EXTRA_SCHEDULES_JSON, schedulesToJson(schedules))
                        putExtra(SmsSendingService.EXTRA_DELAY_IN_MS, delayInMs)
                    }
                    ContextCompat.startForegroundService(context, intent)
                    running = true
                } else {
                    context.startService(Intent(context, SmsSendingService::class.java).apply {
                        action = SmsSendingService.ACTION_STOP
                    })
                    running = false
                }

                commandPrefs.edit().putString("lastCommandId", command.commandId).apply()
                DeviceConfigSync.reportRuntime(deviceId, command.commandId, running)
            },
            onError = { error -> remoteConfigStatus = "Ошибка команды Firebase: $error" }
        )
        onDispose { subscription?.stop() }
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
            schedules.isNotEmpty() &&
            schedules.all { it.isValidSchedule() }


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

        SmsStore.autoReplyKeyword = keyword
        SmsStore.autoReplyText = replyText
        if (limit != null) {
            SmsStore.updateAutoReplyLimit(limit)
        }

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
        summ,
        darkTheme,
        schedules,
        delayInMs
    ) {
        prefs.edit()
            .putString("phone", phone)
            .putString("message", message)
            .putString("summ", summ)
            .putBoolean("darkTheme", darkTheme)
            .putString("schedulesJson", schedulesToJson(schedules))
            .putBoolean("delayInMs", delayInMs)
            .apply()
    }

    // Если автоматическая отправка уже включена, изменения расписаний
    // автоматически применяются к сервису без Stop -> Start.
    LaunchedEffect(
        phone,
        message,
        summ,
        schedules,
        delayInMs,
        running
    ) {
        if (running && canStart) {
            delay(400)
            val updateIntent = Intent(context, SmsSendingService::class.java).apply {
                action = SmsSendingService.ACTION_UPDATE
                putExtra(SmsSendingService.EXTRA_PHONE, phone)
                putExtra(SmsSendingService.EXTRA_MESSAGE, message)
                putExtra(SmsSendingService.EXTRA_SUM, summ)
                putExtra(SmsSendingService.EXTRA_SCHEDULES_JSON, schedulesToJson(schedules))
                putExtra(SmsSendingService.EXTRA_DELAY_IN_MS, delayInMs)
            }
            context.startService(updateIntent)
        }
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
                                    color = Color(0xFFFCFCFC),
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
                                    text = "Номер: ${warning.phone}",
                                            color = Color(0xFFF57F17),

                                )

                                Text(
                                    text = "Время: $timeText"
                                    ,
                                    color = Color(0xFFF57F17),
                                )

                                Text(
                                    text = "Сумма: $summ"
                                    ,
                                    color = Color(0xFFF57F17),
                                )

                                Text(
                                    text = "Сообщение:",
                                    fontWeight = FontWeight.Bold
                                    ,
                                    color = Color(0xFFF57F17),
                                )

                                Text(
                                    text = warning.text,
                                            color = Color(0xFFF57F17),

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
                                    text = "Номер: ${failure.phone}",
                                    color = Color(0xFFC62828),
                                )

                                Text(
                                    text = "Время: $timeText",
                                    color = Color(0xFFC62828),
                                )

                                Text(
                                    text = "Ответ: ${failure.text}",
                                    color = Color(0xFFC62828),
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

                                Text("Номер: ${success.phone}",
                                    color = Color(0xFF2E7D32),)
                                Text("Время: $timeText",
                                    color = Color(0xFF2E7D32),)
                                Text("Сумма: $summ",
                                    color = Color(0xFF2E7D32),)

                                Text(
                                    color = Color(0xFF2E7D32),
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
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (sendFlashVisible) {
                        Modifier.border(4.dp, Color(0xFF4CAF50))
                    } else {
                        Modifier
                    }
                ),
            color = MaterialTheme.colorScheme.background
        ) {
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


                // Независимые расписания
                schedules.forEachIndexed { index, schedule ->
                    ScheduleBlock(
                        index = index,
                        schedule = schedule,
                        delayInMs = delayInMs,
                        canDelete = index != 0,
                        onChange = { updated ->
                            schedules = schedules.map { current ->
                                if (current.id == updated.id) updated else current
                            }
                        },
                        onDelete = {
                            if (index != 0) {
                                schedules = schedules.filterNot { it.id == schedule.id }
                            }
                        }
                    )
                }

                if (schedules.size < MAX_SCHEDULES) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val nextId = (schedules.maxOfOrNull { it.id } ?: 0L) + 1L
                            schedules = schedules + ScheduleConfig(id = nextId)
                        }
                    ) {
                        Text("+ Добавить расписание")
                    }
                } else {
                    Text(
                        text = "Максимум: $MAX_SCHEDULES расписаний",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Switch(
                        checked = delayInMs,
                        onCheckedChange = { delayInMs = it }
                    )
                    Text(
                        modifier = Modifier.padding(10.dp),
                        text = if (delayInMs) "Интервал в миллисекундах" else "Интервал в секундах",
                        color = Color(0xFF666666)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = canStart || running,
                        onClick = {
                            if (!running) {
                                if (canStart) {
                                    val serviceIntent = Intent(
                                        context,
                                        SmsSendingService::class.java
                                    ).apply {
                                        action = SmsSendingService.ACTION_START
                                        putExtra(SmsSendingService.EXTRA_PHONE, phone)
                                        putExtra(SmsSendingService.EXTRA_MESSAGE, message)
                                        putExtra(SmsSendingService.EXTRA_SUM, summ)
                                        putExtra(
                                            SmsSendingService.EXTRA_SCHEDULES_JSON,
                                            schedulesToJson(schedules)
                                        )
                                        putExtra(SmsSendingService.EXTRA_DELAY_IN_MS, delayInMs)
                                    }

                                    ContextCompat.startForegroundService(context, serviceIntent)
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

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()

                Text(
                    text = "ID устройства: $deviceId",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = buildString {
                        append(remoteConfigStatus)
                        remoteConfigVersion?.let { append(" • v$it") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
