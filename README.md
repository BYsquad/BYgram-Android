# BYgram-Android

BYgram — неофициальный клиент Telegram для Android. Он добавляет настройки приватности и оформления, серии переписки и плагины на Python. Текущая база клиента — Telegram 13.2.2. Пакет приложения — `com.bygram.mobile`, минимальная версия — Android 7.0 (API 24).

[Скачать BYgram](https://byxarek.site/TEST-PROJECT/BYgram/download.html) · [Документация](docs/readme.md) · [История изменений](changelog.md)

## Возможности

- **Приватность.** Режим призрака управляет отправкой прочтений, онлайна и набора. «Шпион» хранит на устройстве историю правок и сообщения, полученные до удаления. Чаты можно скрыть или защитить паролем.
- **Оформление.** Меняйте вид списка чатов, папок и профиля. Для отдельных чатов можно установить локальные видеообои.
- **Пламя и активность.** Пламя показывает серию дней, когда оба собеседника писали в личном чате. Доступ к обмену активностью задаётся в настройках. [Правила серии](docs/bygram-settings/chat-flame.md) · [Настройки BYgram](docs/bygram-settings/README.md)
- **Плагины.** Устанавливайте `.byplugin` из файла или каталога. Включение пакета требует отдельного согласия: код плагина выполняется с правами приложения. [SDK и примеры](docs/plugins/README.md)

## Сборка

Клонируйте репозиторий с подмодулями и соберите Debug с JDK 21. Команды ниже рассчитаны на PowerShell.

```powershell
git clone --recurse-submodules https://github.com/BYsquad/BYgram-Android.git
cd BYgram-Android
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :TMessagesProj_App:assembleAfatDebug
```

Для входа в Telegram укажите свои `TELEGRAM_API_ID` и `TELEGRAM_API_HASH` в `local.properties`. Версии Android SDK и NDK, настройка подписи и путь к APK описаны в [руководстве по сборке](docs/build.md).

## Структура репозитория

| Каталог | Назначение |
| --- | --- |
| [`TMessagesProj`](TMessagesProj/) | Android-клиент, кастомные настройки и движок плагинов |
| [`TMessagesProj_App`](TMessagesProj_App/) | Основная сборка APK |
| [`server/site`](server/site/) | Публичный сайт, документация SDK и интерфейс админки |
| [`server/activity-api`](server/activity-api/) | Activity API, каталог и платежи |
| [`Tools`](Tools/) | Утилиты сборки и проверки |

Исходная база — [Telegram for Android](https://github.com/DrKLO/Telegram). Лицензия проекта — [GPL-2.0](LICENSE).
