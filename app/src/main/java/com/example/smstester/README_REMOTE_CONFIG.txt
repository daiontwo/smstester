ЭТАП 1: получение настроек телефона из Firebase

1. Новый файл DeviceConfigSync.kt обязателен.
2. MainActivity.kt уже подключён к devices/{ANDROID_ID}/config.
3. При появлении/изменении конфигурации автоматически обновляются:
   - Номер
   - Реквизит
   - Сумма
   - Все расписания
   - Режим интервала секунды/миллисекунды
4. Если сервис уже запущен, существующий ACTION_UPDATE автоматически применит новые значения.
5. Внизу приложения показывается ID устройства и статус конфигурации.
6. Приложение создаёт/обновляет devices/{ANDROID_ID}/meta с deviceId, lastSeen и appVersion.

Firebase dependency:
implementation(platform("com.google.firebase:firebase-bom:<твоя версия BOM>"))
implementation("com.google.firebase:firebase-database")

Пример структуры находится в FIREBASE_CONFIG_EXAMPLE.json.
