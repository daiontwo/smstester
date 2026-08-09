package com.antteam.smstester

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.Job
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

    val focusManager = LocalFocusManager.current
    val keyboardController =
        LocalSoftwareKeyboardController.current

    val scope = rememberCoroutineScope()

    var focusJob by remember {
        mutableStateOf<Job?>(null)
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

            focusJob?.cancel()

            focusJob = scope.launch {

                delay(7000)

                focusManager.clearFocus(
                    force = true
                )

                keyboardController?.hide()
            }
        },
        label = {
            Text(label)
        },
        modifier = modifier.onFocusChanged { focus ->

            if (focus.isFocused) {

                fieldValue =
                    fieldValue.copy(
                        selection =
                            TextRange(
                                0,
                                fieldValue.text.length
                            )
                    )

            } else {

                focusJob?.cancel()
            }
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

    ElevatedCard(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Column(
            modifier =
                Modifier.padding(14.dp),

            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    androidx.compose.ui.Alignment.CenterVertically
            ) {

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            "Расписание ${index + 1}",

                        style =
                            MaterialTheme
                                .typography
                                .titleSmall,

                        fontWeight =
                            FontWeight.Bold
                    )

                    val minute =
                        schedule.minute
                            .padStart(2, '0')

                    val second =
                        schedule.second
                            .padStart(2, '0')

                    Text(
                        text =
                            "Каждый час в $minute:$second",

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }

                if (canDelete) {

                    TextButton(
                        onClick = onDelete
                    ) {

                        Text(
                            text = "Удалить",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error
                        )
                    }
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                SelectAllTextField(
                    value =
                        schedule.minute,

                    onValueChange = {
                        value ->

                        onChange(
                            schedule.copy(
                                minute =
                                    value
                                        .filter(
                                            Char::isDigit
                                        )
                                        .take(2)
                            )
                        )
                    },

                    label =
                        "Минута",

                    modifier =
                        Modifier.weight(1f),

                    keyboardType =
                        KeyboardType.Number
                )

                SelectAllTextField(
                    value =
                        schedule.second,

                    onValueChange = {
                        value ->

                        onChange(
                            schedule.copy(
                                second =
                                    value
                                        .filter(
                                            Char::isDigit
                                        )
                                        .take(2)
                            )
                        )
                    },

                    label =
                        "Секунда",

                    modifier =
                        Modifier.weight(1f),

                    keyboardType =
                        KeyboardType.Number
                )
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                SelectAllTextField(
                    value =
                        schedule.interval,

                    onValueChange = {
                        value ->

                        onChange(
                            schedule.copy(
                                interval =
                                    value.filter(
                                        Char::isDigit
                                    )
                            )
                        )
                    },

                    label =
                        if (delayInMs) {
                            "Интервал мс"
                        } else {
                            "Интервал сек"
                        },

                    modifier =
                        Modifier.weight(1f),

                    keyboardType =
                        KeyboardType.Number
                )

                SelectAllTextField(
                    value =
                        schedule.count,

                    onValueChange = {
                        value ->

                        onChange(
                            schedule.copy(
                                count =
                                    value.filter(
                                        Char::isDigit
                                    )
                            )
                        )
                    },

                    label =
                        "Кол-во",

                    modifier =
                        Modifier.weight(1f),

                    keyboardType =
                        KeyboardType.Number
                )
            }

            if (
                !schedule.isValidSchedule()
            ) {

                Text(
                    text =
                        "Проверь значения: " +
                            "минута/секунда 0–59, " +
                            "количество от 1.",

                    color =
                        MaterialTheme
                            .colorScheme
                            .error,

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )
            }
        }
    }
}


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SmsTesterApp()
        }
    }
}


@Composable
fun SmsTesterApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoReply by remember { mutableStateOf(false) }

    var licenseChecked by remember {
        mutableStateOf(false)

    }

    var licenseActive by remember {
        mutableStateOf(false)
    }

    var licenseTokenInput by remember {
        mutableStateOf("")
    }

    var licenseError by remember {
        mutableStateOf("")
    }

    var licenseLoading by remember {
        mutableStateOf(false)
    }
    LaunchedEffect(Unit) {

        licenseActive =
            try {
                LicenseManager.validateDevice(
                    context
                )
            } catch (e: Exception) {
                false
            }

        licenseChecked = true
    }


    LaunchedEffect(licenseActive) {

        if (!licenseActive) {
            return@LaunchedEffect
        }

        while (true) {

            delay(60_000)

            val stillActive =
                try {
                    LicenseManager.validateDevice(context)
                } catch (e: Exception) {
                    true
                }

            if (!stillActive) {

                licenseActive = false

                autoReply = false

                val stopIntent =
                    Intent(
                        context,
                        SmsSendingService::class.java
                    ).apply {
                        action =
                            SmsSendingService.ACTION_STOP
                    }

                context.startService(stopIntent)

                break
            }
        }
    }
    if (!licenseChecked) {

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        return
    }
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

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            OutlinedTextField(
                value = licenseTokenInput,

                onValueChange = {
                    licenseTokenInput =
                        it.uppercase()
                            .trim()
                },

                modifier = Modifier.fillMaxWidth(),

                label = {
                    Text("Токен доступа")
                },

                singleLine = true
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Button(
                modifier = Modifier.fillMaxWidth(),

                enabled =
                    !licenseLoading &&
                            licenseTokenInput.isNotBlank(),

                onClick = {

                    scope.launch {

                        licenseLoading = true
                        licenseError = ""

                        try {

                            val success =
                                LicenseManager.activateDevice(
                                    context,
                                    licenseTokenInput
                                )

                            if (success) {

                                licenseActive = true

                            } else {

                                licenseError =
                                    "Не удалось активировать токен"
                            }

                        } catch (e: Exception) {

                            licenseError =
                                e.message
                                    ?: "Ошибка активации"
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

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = licenseError,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        return
    }

    val scrollState = rememberScrollState()
    var phone by remember { mutableStateOf("7878") }
    var message by remember { mutableStateOf("") }
    var summ by remember { mutableStateOf("14700") }

    var darkTheme by remember { mutableStateOf(false) }

    var schedules by remember {
        mutableStateOf(
            listOf(
                ScheduleConfig(id = 1L)
            )
        )
    }

    var delayInMs by remember {
        mutableStateOf(false)
    }

    val running by SmsSendingState.running.collectAsState()

    val sendingPulse by
    SmsSendingState.sendingPulse.collectAsState()

    var smsBorderActive by remember {
        mutableStateOf(false)
    }

    // Короткая зелёная вспышка по периметру при каждой отправке SMS.
    // Никакой постоянной анимации: рамка существует только 350 мс.
    LaunchedEffect(sendingPulse) {

        if (sendingPulse > 0) {

            smsBorderActive = true

            delay(350)

            smsBorderActive = false
        }
    }
    var keyword by remember { mutableStateOf("8464") }
    var replyText by remember { mutableStateOf("да") }
    var autoReplyLimit by remember { mutableStateOf("1") }

    // Не сохраняем значения обратно, пока сначала не загрузили их из DataStore.
    var settingsLoaded by remember { mutableStateOf(false) }

    /*
     * ---------------------------------------------------------
     * ОБНОВЛЕНИЯ
     * ---------------------------------------------------------
     */

    var showUpdateDialog by remember {
        mutableStateOf(false)
    }

    var updateVersionName by remember {
        mutableStateOf("")
    }

    var updateApkUrl by remember {
        mutableStateOf("")
    }

    val incoming by SmsStore.lastIncoming.collectAsState()


    /*
     * ---------------------------------------------------------
     * СОХРАНЕНИЕ НАСТРОЕК
     * ---------------------------------------------------------
     */

    // Один раз при запуске восстанавливаем все сохранённые поля.
    LaunchedEffect(Unit) {

        val saved =
            SettingsDataStore
                .settingsFlow(context)
                .first()

        phone =
            saved.phone.ifBlank {
                "7878"
            }

        message = saved.message
        summ = saved.summ
        darkTheme = saved.darkTheme

        schedules =
            saved.schedules
                .ifEmpty {
                    listOf(
                        ScheduleConfig(id = 1L)
                    )
                }
                .take(MAX_SCHEDULES)

        delayInMs = saved.delayInMs

        autoReply = saved.autoReply
        keyword = saved.keyword
        replyText = saved.replyText
        autoReplyLimit = saved.autoReplyCount

        settingsLoaded = true
    }

    // После загрузки автоматически сохраняем любое изменение.
    LaunchedEffect(
        phone,
        message,
        summ,
        darkTheme,
        schedules,
        delayInMs,
        autoReply,
        keyword,
        replyText,
        autoReplyLimit,
        settingsLoaded
    ) {

        if (settingsLoaded) {

            SettingsDataStore.save(
                context = context,
                settings = AppSettings(
                    phone = phone,
                    message = message,
                    summ = summ,
                    darkTheme = darkTheme,
                    schedules = schedules,
                    delayInMs = delayInMs,
                    autoReply = autoReply,
                    keyword = keyword,
                    replyText = replyText,
                    autoReplyCount = autoReplyLimit
                )
            )
        }
    }


    /*
     * ---------------------------------------------------------
     * РАЗРЕШЕНИЯ SMS
     * ---------------------------------------------------------
     */

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }


    /*
     * ---------------------------------------------------------
     * ПРОВЕРКА ОБНОВЛЕНИЯ FIREBASE
     * ---------------------------------------------------------
     */

    LaunchedEffect(Unit) {

        val permissions = buildList {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.RECEIVE_SMS)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        permissionLauncher.launch(permissions)


        /*
         * Получаем versionCode именно установленного приложения.
         * BuildConfig.VERSION_CODE здесь не используется.
         */

        try {

            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                0
            )

            val currentVersionCode: Long =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                    packageInfo.longVersionCode

                } else {

                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }


            Log.d(
                "FirebaseUpdate",
                "Текущая версия приложения: $currentVersionCode"
            )


            /*
             * ВАЖНО:
             * Используем именно URL твоей европейской Firebase Database.
             */

            val database = FirebaseDatabase.getInstance(
                "https://smstester-29fb6-default-rtdb.europe-west1.firebasedatabase.app"
            )


            database
                .getReference("update")
                .get()
                .addOnSuccessListener { snapshot ->

                    val remoteVersionCode =
                        snapshot
                            .child("versionCode")
                            .getValue(Long::class.java)
                            ?: 0L


                    val remoteVersionName =
                        snapshot
                            .child("versionName")
                            .getValue(String::class.java)
                            .orEmpty()


                    val remoteApkUrl =
                        snapshot
                            .child("apkUrl")
                            .getValue(String::class.java)
                            .orEmpty()


                    Log.d(
                        "FirebaseUpdate",
                        "local=$currentVersionCode, " +
                                "remote=$remoteVersionCode, " +
                                "name=$remoteVersionName, " +
                                "url=$remoteApkUrl"
                    )


                    /*
                     * Если версия Firebase выше установленной —
                     * показываем окно.
                     */

                    if (
                        remoteVersionCode > currentVersionCode &&
                        remoteApkUrl.isNotBlank()
                    ) {

                        updateVersionName = remoteVersionName
                        updateApkUrl = remoteApkUrl
                        showUpdateDialog = true

                    } else {

                        Log.d(
                            "FirebaseUpdate",
                            "Установлена актуальная версия"
                        )
                    }
                }
                .addOnFailureListener { error ->

                    /*
                     * Если Firebase недоступен —
                     * приложение всё равно продолжает работать.
                     */

                    Log.e(
                        "FirebaseUpdate",
                        "Не удалось проверить обновление",
                        error
                    )
                }

        } catch (e: Exception) {

            Log.e(
                "FirebaseUpdate",
                "Ошибка проверки версии приложения",
                e
            )
        }
    }


    /*
     * ---------------------------------------------------------
     * ОСНОВНАЯ ЛОГИКА
     * ---------------------------------------------------------
     */

    val canSend =
        phone.isNotBlank() &&
            message.isNotBlank() &&
            summ.isNotBlank()

    val allSchedulesValid =
        schedules.isNotEmpty() &&
            schedules.all {
                it.isValidSchedule()
            }

    val canStart =
        canSend &&
            allSchedulesValid

    /*
     * Если сервис уже запущен и пользователь добавил,
     * удалил или изменил расписание — отправляем сервису
     * новую конфигурацию.
     *
     * Задержка 400 мс нужна, чтобы не дёргать сервис
     * на каждую цифру во время быстрого ввода.
     */
    LaunchedEffect(
        phone,
        message,
        summ,
        schedules,
        delayInMs,
        running,
        settingsLoaded
    ) {

        if (
            running &&
            settingsLoaded &&
            canStart
        ) {

            delay(400)

            val updateIntent =
                Intent(
                    context,
                    SmsSendingService::class.java
                ).apply {

                    action =
                        SmsSendingService.ACTION_UPDATE

                    putExtra(
                        SmsSendingService.EXTRA_PHONE,
                        phone
                    )

                    putExtra(
                        SmsSendingService.EXTRA_MESSAGE,
                        message
                    )

                    putExtra(
                        SmsSendingService.EXTRA_SUMM,
                        summ
                    )

                    putExtra(
                        SmsSendingService.EXTRA_SCHEDULES_JSON,
                        schedulesToJson(schedules)
                    )

                    putExtra(
                        SmsSendingService.EXTRA_DELAY_MS,
                        delayInMs
                    )
                }

            context.startService(updateIntent)
        }
    }


    var previousAutoReplyEnabled by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(
        autoReply,
        keyword,
        replyText,
        autoReplyLimit
    ) {

        SmsStore.autoReplyKeyword = keyword
        SmsStore.autoReplyText = replyText

        SmsStore.autoReplyLimit =
            autoReplyLimit
                .toIntOrNull()
                ?.coerceIn(1, 100)
                ?: 1

        // При новом включении автоответа начинаем счётчик заново.
        if (
            autoReply &&
            !previousAutoReplyEnabled
        ) {
            SmsStore.resetAutoReplyCounter()
        }

        SmsStore.autoReplyEnabled = autoReply
        previousAutoReplyEnabled = autoReply
    }


    /*
     * ---------------------------------------------------------
     * UI
     * ---------------------------------------------------------
     */

    MaterialTheme(
        colorScheme =
            if (darkTheme) {
                darkColorScheme()
            } else {
                lightColorScheme()
            }
    ) {


        /*
         * -----------------------------------------------------
         * ОКНО ОБНОВЛЕНИЯ
         * -----------------------------------------------------
         */

        if (showUpdateDialog) {

            AlertDialog(

                onDismissRequest = {
                    showUpdateDialog = false
                },

                title = {
                    Text(
                        text = "Доступна новая версия"
                    )
                },

                text = {

                    Column {

                        Text(
                            text =
                                if (updateVersionName.isNotBlank()) {
                                    "Доступна версия $updateVersionName."
                                } else {
                                    "Доступна новая версия приложения."
                                }
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            text = "Хотите обновить приложение?"
                        )
                    }
                },

                confirmButton = {

                    Button(
                        onClick = {

                            try {

                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(updateApkUrl)
                                )

                                context.startActivity(intent)

                            } catch (e: Exception) {

                                Log.e(
                                    "FirebaseUpdate",
                                    "Не удалось открыть ссылку",
                                    e
                                )
                            }
                        }
                    ) {

                        Text("Обновить")
                    }
                },

                dismissButton = {

                    TextButton(
                        onClick = {
                            showUpdateDialog = false
                        }
                    ) {

                        Text("Позже")
                    }
                }
            )
        }


        /*
         * -----------------------------------------------------
         * ОСНОВНОЙ ЭКРАН
         * -----------------------------------------------------
         */

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {

                Column(
                modifier = Modifier
                    .padding(
                        20.dp,
                        50.dp,
                        20.dp,
                        30.dp
                    )
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .fillMaxWidth(),

                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {


                /*
                 * -------------------------------------------------
                 * ЗАГОЛОВОК
                 * -------------------------------------------------
                 */

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        androidx.compose.ui.Alignment.CenterVertically
                ) {

                    Text(
                        text = "SMS Tester",
                        style =
                            MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )


                    Text(
                        modifier =
                            Modifier.padding(5.dp),

                        text =
                            if (darkTheme) {
                                "🌙"
                            } else {
                                "☀️"
                            },

                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )


                    Switch(
                        checked = darkTheme,

                        onCheckedChange = {
                            darkTheme = it
                        }
                    )
                }


                /*
                 * -------------------------------------------------
                 * НОМЕР
                 * -------------------------------------------------
                 */

                SelectAllTextField(
                    value = phone,

                    onValueChange = {
                        phone = it
                    },

                    label = "Номер",

                    modifier =
                        Modifier.fillMaxWidth(),

                    keyboardType =
                        KeyboardType.Phone
                )


                /*
                 * -------------------------------------------------
                 * РЕКВИЗИТ
                 * -------------------------------------------------
                 */

                SelectAllTextField(
                    value = message,

                    onValueChange = {
                        message = it
                    },

                    label = "Реквизит",

                    modifier =
                        Modifier.fillMaxWidth(),

                    keyboardType =
                        KeyboardType.Phone
                )


                /*
                 * -------------------------------------------------
                 * СУММА
                 * -------------------------------------------------
                 */

                SelectAllTextField(
                    value = summ,

                    onValueChange = {
                        summ = it
                    },

                    label = "Сумма отправки",

                    modifier =
                        Modifier.fillMaxWidth(),

                    keyboardType =
                        KeyboardType.Phone
                )


                /*
                 * -------------------------------------------------
                 * ОТПРАВИТЬ
                 * -------------------------------------------------
                 */

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Button(
                        modifier =
                            Modifier.weight(1f),

                        enabled = canSend,

                        onClick = {

                            SmsSender.send(
                                phone,
                                "$message $summ"
                            )

                            // Коротко подсвечиваем периметр экрана.
                            SmsSendingState.pulse()
                        }
                    ) {

                        Text("Отправить")
                    }
                }


                /*
                 * -------------------------------------------------
                 * НЕЗАВИСИМЫЕ РАСПИСАНИЯ
                 * -------------------------------------------------
                 */

                schedules.forEachIndexed {
                        index,
                        schedule ->

                    ScheduleBlock(
                        index = index,
                        schedule = schedule,
                        delayInMs = delayInMs,

                        canDelete =
                            schedules.size > 1,

                        onChange = { updated ->

                            schedules =
                                schedules.map {
                                    current ->

                                    if (
                                        current.id ==
                                        updated.id
                                    ) {
                                        updated
                                    } else {
                                        current
                                    }
                                }
                        },

                        onDelete = {

                            if (
                                schedules.size > 1
                            ) {

                                schedules =
                                    schedules.filterNot {
                                        it.id ==
                                            schedule.id
                                    }
                            }
                        }
                    )
                }

                if (
                    schedules.size <
                    MAX_SCHEDULES
                ) {

                    OutlinedButton(
                        modifier =
                            Modifier.fillMaxWidth(),

                        onClick = {

                            val nextId =
                                (
                                    schedules
                                        .maxOfOrNull {
                                            it.id
                                        }
                                        ?: 0L
                                ) + 1L

                            schedules =
                                schedules +
                                    ScheduleConfig(
                                        id = nextId
                                    )
                        }
                    ) {

                        Text(
                            "+ Добавить расписание"
                        )
                    }

                } else {

                    Text(
                        text =
                            "Максимум: $MAX_SCHEDULES расписаний",

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }


                /*
                 * -------------------------------------------------
                 * ЕДИНИЦЫ ИНТЕРВАЛА
                 * -------------------------------------------------
                 */

                Row(
                    verticalAlignment =
                        androidx.compose.ui.Alignment.CenterVertically
                ) {

                    Switch(
                        checked = delayInMs,

                        onCheckedChange = {
                            delayInMs = it
                        }
                    )

                    Text(
                        modifier =
                            Modifier.padding(10.dp),

                        text =
                            if (delayInMs) {
                                "Интервал в миллисекундах"
                            } else {
                                "Интервал в секундах"
                            },

                        color =
                            Color(0xFF666666)
                    )
                }


                /*
                 * -------------------------------------------------
                 * СТАРТ / СТОП
                 * -------------------------------------------------
                 */

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Button(

                        modifier =
                            Modifier.weight(1f),

                        enabled =
                            canStart || running,

                        onClick = {

                            if (!running) {

                                val intent =
                                    Intent(
                                        context,
                                        SmsSendingService::class.java
                                    ).apply {

                                        action =
                                            SmsSendingService.ACTION_START

                                        putExtra(
                                            SmsSendingService.EXTRA_PHONE,
                                            phone
                                        )

                                        putExtra(
                                            SmsSendingService.EXTRA_MESSAGE,
                                            message
                                        )

                                        putExtra(
                                            SmsSendingService.EXTRA_SUMM,
                                            summ
                                        )

                                        putExtra(
                                            SmsSendingService.EXTRA_SCHEDULES_JSON,
                                            schedulesToJson(schedules)
                                        )

                                        putExtra(
                                            SmsSendingService.EXTRA_DELAY_MS,
                                            delayInMs
                                        )
                                    }

                                ContextCompat
                                    .startForegroundService(
                                        context,
                                        intent
                                    )

                            } else {

                                val intent =
                                    Intent(
                                        context,
                                        SmsSendingService::class.java
                                    ).apply {

                                        action =
                                            SmsSendingService.ACTION_STOP
                                    }

                                context.startService(intent)
                            }
                        },

                                colors =
                                ButtonDefaults.buttonColors(

                                containerColor =
                                    if (running) {
                                        Color.Red
                                    } else {
                                        Color(0xFF4CAF50)
                                    }
                                )
                    ) {


                        Text(
                            if (running) {
                                "Стоп"
                            } else {
                                "Старт"
                            }
                        )
                    }
                }

                /*
                 * -------------------------------------------------
                 * АВТООТВЕТ
                 * -------------------------------------------------
                 */

                Row(
                    verticalAlignment =
                        androidx.compose.ui.Alignment.CenterVertically
                ) {


                    Switch(
                        checked = autoReply,

                        onCheckedChange = { enabled ->

                            autoReply = enabled

                            if (enabled) {

                                scope.launch {

                                    // Ждём, пока Compose покажет поля автоответа.
                                    delay(100)

                                    scrollState.animateScrollTo(
                                        scrollState.maxValue
                                    )
                                }
                            }
                        }
                    )


                    Spacer(
                        Modifier.width(8.dp)
                    )


                    Text("Автоответ")
                }


                if (autoReply) {


                    SelectAllTextField(
                        value = keyword,

                        onValueChange = {

                            keyword =
                                it.filter(
                                    Char::isDigit
                                )
                        },

                        label =
                            "Номер отправителя",

                        modifier =
                            Modifier.fillMaxWidth(),

                        keyboardType =
                            KeyboardType.Phone
                    )


                    SelectAllTextField(
                        value = replyText,

                        onValueChange = {
                            replyText = it
                        },

                        label =
                            "Ответить текстом",

                        modifier =
                            Modifier.fillMaxWidth()
                    )


                    SelectAllTextField(
                        value = autoReplyLimit,

                        onValueChange = {
                            value ->

                            autoReplyLimit =
                                value
                                    .filter(Char::isDigit)
                                    .take(3)
                        },

                        label =
                            "Лимит автоответов",

                        modifier =
                            Modifier.fillMaxWidth(),

                        keyboardType =
                            KeyboardType.Number
                    )

                    Text(
                        text =
                            "Ответить на " +
                                "${autoReplyLimit.toIntOrNull() ?: 0} " + "SMS",

                        style =
                            MaterialTheme.typography.bodySmall,

                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Лёгкий индикатор отправки: зелёная рамка появляется только на 300 мс.
            if (smsBorderActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 5.dp,
                            color = Color(0xFF00E676),
                            shape = RoundedCornerShape(18.dp)
                        )
                )
            }
        }
    }
}
}
