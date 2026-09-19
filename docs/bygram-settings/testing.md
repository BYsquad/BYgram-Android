# Проверка изменений настроек BYgram

## Перед любой сборкой

```powershell
crowdin download translations --export-only-approved --skip-untranslated-strings
python Tools/check_bygram_settings_fixes.py
```

Не меняйте `APP_VERSION_*` без отдельного запроса. `APP_PACKAGE` должен оставаться `com.bygram.mobile`.

## Быстрые статические проверки

```powershell
$checkDir = Join-Path $env:TEMP 'bygram-text-check'
New-Item -ItemType Directory -Force -Path $checkDir | Out-Null
javac -d $checkDir TMessagesProj/src/main/java/org/telegram/messenger/BygramTextUtils.java Tools/BygramTextCheck.java
java -cp $checkDir BygramTextCheck
python Tools/check_bygram_md3_geometry.py
python Tools/check_bygram_settings_fixes.py
php -l server/activity-api/index.php
php -l server/activity-api/admin-api.php
$env:BYGRAM_TEST_LOCAL='1'
php -d zend.assertions=1 -d assert.exception=1 server/activity-api/self-test.php
```

Сборочные команды и пути APK: [инструкция по сборке](../build.md).

## Smoke test на авторизованном устройстве

1. Убедиться, что тестируется APK из текущего дерева: package/versionCode/versionName.
2. Открыть каждый дочерний экран раздела.
3. Для каждого switch: изменить, уйти/вернуться, force-stop/relaunch, проверить persistence и consumer.
4. Проверить 3 launcher icons, вернуть исходную.
5. Проверить avatar slider на 0, промежуточном значении и 28, включая forum avatar.
6. Раскрыть MD3/Liquid Glass; проверить каждую строку, счётчик и реальный component.
7. Экспортировать JSON, проверить allowlist и отсутствие history/viewers/tokens.
8. Reset/clear подтверждать только на чистом тестовом аккаунте; проверить restart.
9. Восстановить исходные пользовательские значения.

Для UI observation используйте `android layout`; screenshot — только если layout не отражает экран. `Tools/check_bygram_preferences.py` использует текущую карту «Кастомизация / Другое / Ghost / Spy» и `android layout`, а не legacy `uiautomator dump`. UI-сценарии требуют разблокированного авторизованного устройства.

## Activity integration

Нужны минимум два лицензированных test accounts и один невыбранный/нелицензированный. Проверяются challenge identity, viewers ACL, publish/get, TTL, background clear, token refresh, revoke и смена account. Подробный сценарий: [activity-sharing.md](activity-sharing.md#интеграционный-тест).

## Критерии готовности

- Нет crash/ANR при быстрых toggles и navigation.
- UI state после restart совпадает с consumer state.
- Global/account scope проверен сменой Telegram account.
- Privacy data не уходит до opt-in.
- Reset/clear удаляет ровно перечисленный scope.
- Все новые строки есть в `values` и `values-ru`, затем получены из Crowdin для остальных locales.
- Licensed activity test пройден на контролируемом test dataset.
