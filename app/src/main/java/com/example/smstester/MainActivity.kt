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
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit


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
        label = {
            Text(label)
        },
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

        setContent {
            SmsTesterApp()
        }
    }
}


@Composable
fun SmsTesterApp() {

    val context = LocalContext.current
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
        val saved = SettingsDataStore.settingsFlow(context).first()

        phone = saved.phone
        message = saved.message
        sendLimitText = saved.sendLimitText
        summ = saved.summ
        darkTheme = saved.darkTheme
        scheduleMinute = saved.scheduleMinute
        scheduleSecond = saved.scheduleSecond
        smsDelayValue = saved.smsDelayValue
        delayInMs = saved.delayInMs
        autoReply = saved.autoReply
        keyword = saved.keyword
        replyText = saved.replyText

        settingsLoaded = true
    }

    // После загрузки автоматически сохраняем любое изменение полей.
    LaunchedEffect(
        phone,
        message,
        sendLimitText,
        summ,
        darkTheme,
        scheduleMinute,
        scheduleSecond,
        smsDelayValue,
        delayInMs,
        autoReply,
        keyword,
        replyText,
        settingsLoaded
    ) {
        if (settingsLoaded) {
            SettingsDataStore.save(
                context = context,
                settings = AppSettings(
                    phone = phone,
                    message = message,
                    sendLimitText = sendLimitText,
                    summ = summ,
                    darkTheme = darkTheme,
                    scheduleMinute = scheduleMinute,
                    scheduleSecond = scheduleSecond,
                    smsDelayValue = smsDelayValue,
                    delayInMs = delayInMs,
                    autoReply = autoReply,
                    keyword = keyword,
                    replyText = replyText
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

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS
            )
        )


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


    val canStart =
        canSend &&
                sendLimitText.isNotBlank() &&
                smsDelayValue.isNotBlank() &&
                scheduleMinute.isNotBlank() &&
                scheduleSecond.isNotBlank()


    var lastConfig by remember {
        mutableStateOf("")
    }


    val currentConfig =
        "$phone|" +
                "$message|" +
                "$summ|" +
                "$scheduleMinute|" +
                "$scheduleSecond|" +
                "$smsDelayValue|" +
                "$delayInMs|" +
                sendLimitText


    LaunchedEffect(currentConfig) {

        if (
            running &&
            lastConfig.isNotEmpty() &&
            currentConfig != lastConfig
        ) {

            sendJob?.cancel()

            sendJob = null
            running = false
        }

        lastConfig = currentConfig
    }


    LaunchedEffect(
        autoReply,
        keyword,
        replyText
    ) {

        SmsStore.autoReplyEnabled = autoReply
        SmsStore.autoReplyKeyword = keyword
        SmsStore.autoReplyText = replyText
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
                        70.dp
                    )
                    .fillMaxHeight()
                    .verticalScroll(
                        rememberScrollState()
                    )
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
                        }
                    ) {

                        Text("Отправить")
                    }
                }


                /*
                 * -------------------------------------------------
                 * ВРЕМЯ ЗАПУСКА
                 * -------------------------------------------------
                 */

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {


                    SelectAllTextField(
                        value = scheduleMinute,

                        onValueChange = {
                            scheduleMinute =
                                it.filter(Char::isDigit)
                        },

                        label = "Минута запуска",

                        modifier =
                            Modifier.weight(1f),

                        keyboardType =
                            KeyboardType.Phone
                    )


                    SelectAllTextField(
                        value = scheduleSecond,

                        onValueChange = {
                            scheduleSecond =
                                it.filter(Char::isDigit)
                        },

                        label = "Секунда запуска",

                        modifier =
                            Modifier.weight(1f),

                        keyboardType =
                            KeyboardType.Phone
                    )
                }


                /*
                 * -------------------------------------------------
                 * ИНТЕРВАЛ / КОЛИЧЕСТВО
                 * -------------------------------------------------
                 */

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {


                    SelectAllTextField(
                        value = smsDelayValue,

                        onValueChange = {

                            smsDelayValue =
                                it.filter(Char::isDigit)
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
                        value = sendLimitText,

                        onValueChange = {

                            sendLimitText =
                                it.filter(Char::isDigit)
                        },

                        label =
                            "Количество отправок",

                        modifier =
                            Modifier.weight(1f),

                        keyboardType =
                            KeyboardType.Number
                    )
                }


                /*
                 * -------------------------------------------------
                 * МИЛЛИСЕКУНДЫ
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
                                "миллисекунды"
                            } else {
                                "секунды"
                            },

                        color =
                            Color(0xFF666666)
                    )
                }


                /*
                 * -------------------------------------------------
                 * ИНФОРМАЦИЯ
                 * -------------------------------------------------
                 */

                Text(
                    text =
                        "Каждый час в $scheduleMinute минут $scheduleSecond секунд\n" +
                                "Отправок: $sendLimitText\n" +
                                "Интервал: $smsDelayValue " +
                                if (delayInMs) {
                                    "мс"
                                } else {
                                    "сек"
                                },

                    color =
                        Color(0xFF666666),

                    style =
                        MaterialTheme.typography.bodyMedium
                )


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

                                running = true


                                sendJob =
                                    scope.launch {


                                        val minute =
                                            scheduleMinute
                                                .toIntOrNull()
                                                ?: 59


                                        val second =
                                            scheduleSecond
                                                .toIntOrNull()
                                                ?: 50


                                        val limit =
                                            sendLimitText
                                                .toIntOrNull()
                                                ?: 5


                                        while (isActive) {


                                            val now =
                                                LocalDateTime.now()


                                            var next =
                                                now
                                                    .withMinute(minute)
                                                    .withSecond(second)
                                                    .withNano(0)


                                            /*
                                             * Если время прошло —
                                             * ждём следующий час.
                                             */

                                            if (!next.isAfter(now)) {

                                                next =
                                                    next.plusHours(1)
                                            }


                                            val waitMillis =
                                                ChronoUnit.MILLIS
                                                    .between(
                                                        now,
                                                        next
                                                    )


                                            delay(waitMillis)


                                            /*
                                             * Отправляем SMS.
                                             */

                                            val smsDelay =
                                                smsDelayValue
                                                    .toLongOrNull()
                                                    ?: 1L


                                            val delayMillis =
                                                if (delayInMs) {

                                                    smsDelay

                                                } else {

                                                    smsDelay * 1000
                                                }


                                            repeat(limit) {


                                                SmsSender.send(
                                                    phone,
                                                    "$message $summ"
                                                )


                                                delay(
                                                    delayMillis
                                                )
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


                HorizontalDivider()


                /*
                 * -------------------------------------------------
                 * ВХОДЯЩИЕ SMS
                 * -------------------------------------------------
                 */

                Text(
                    "Последнее входящее SMS"
                )


                if (incoming.from == "8464") {


                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    color =
                                        Color(0xFFDFF7DF),

                                    shape =
                                        RoundedCornerShape(
                                            12.dp
                                        )
                                )
                                .padding(16.dp)
                    ) {


                        Text(
                            text = "УСПЕШНО!",

                            color =
                                Color(0xFF2E7D32),

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }


                Text(
                    "От: ${
                        incoming.from.ifBlank {
                            "—"
                        }
                    }"
                )


                Text(
                    "Текст: ${
                        incoming.text.ifBlank {
                            "—"
                        }
                    }"
                )


                HorizontalDivider()


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

                        onCheckedChange = {
                            autoReply = it
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
                }
            }
        }
    }
}