# Проверка изменений настроек BYgram

## Перед любой сборкой

```powershell
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

## Статусы печати и значки

1. Собрать Lite и Full без изменения версии; на аккаунтах A и B открыть личный чат в BYgram.
2. На A задать фразы для текста, записи голоса, видео и видеосообщения. В Full на уже привязанном устройстве публикация должна пройти без отдельного подтверждения через бота. В Lite подтвердить A через бота и повторно нажать «Опубликовать статусы».
3. Начать каждое действие на A; на B проверить кастомную фразу. В обычном Telegram должен остаться стандартный статус. После удаления фразы и публикации в BYgram снова показывается стандартный статус (с учётом кеша до 7 секунд).
4. Проверить, что новая привязка Lite без подтверждения Telegram ID не записывает настройки на сервер, а подтверждение другого ID не принимается. Отозвать устройство Full или лицензию и проверить, что его ключ больше не даёт право публикации без независимой привязки статусов.
5. Выдать в админ панели значки тестовому пользователю и тестовой группе/каналу по ID. Проверить изображения после имени в списке чатов, заголовке чата, профиле и имени автора сообщения в группе; нажатие должно показать соответствующий текст внизу экрана. Удалить назначения и проверить исчезновение после обновления кеша.
6. Для активности Full проверить обновление у наблюдателя каждые 7 секунд; Lite не публикует платную активность.

## Критерии готовности

- Нет crash/ANR при быстрых toggles и navigation.
- UI state после restart совпадает с consumer state.
- Global/account scope проверен сменой Telegram account.
- Privacy data не уходит до opt-in.
- Reset/clear удаляет ровно перечисленный scope.
- Все новые строки есть в английском каталоге [BYgram-translations](https://github.com/BYxarek/BYgram-translations) и в приложении; проверенные переводы импортированы из публичного каталога.
- Licensed activity test пройден на контролируемом test dataset.
