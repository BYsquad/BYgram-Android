# Dev Server для BYgram

Включите движок плагинов, затем переключатель **Сервер разработки плагинов** в том же разделе. В Release, Standalone и Debug этот переключатель запускает сервер вручную до остановки процесса или выключения переключателя. Скопируйте токен из диалога, задайте его в `BYGRAM_DEV_TOKEN` и выполните `python Tools/plugin_dev.py attach` с подключённым ADB. Сервер слушает `127.0.0.1:42690` и требует токен в каждой команде. В Debug также доступен прежний `start` через `adb run-as`. В Release и Standalone остановите сервер переключателем в приложении.

```powershell
$env:BYGRAM_DEV_TOKEN = python -c "import secrets; print(secrets.token_hex(32))"
python Tools/plugin_dev.py start
python Tools/plugin_dev.py deploy docs/plugins/examples/hello
python Tools/plugin_dev.py list
```

Если `adb` отсутствует в PATH, укажите `--adb <путь-к-adb>` перед командой `start` или `stop`. Для нескольких подключённых устройств используйте `--serial <serial>`.

`deploy` принимает каталог проекта с `manifest.json` или готовый `.byplugin`. Для Elyx используйте [конвертер](exteragram-port.md). `watch` отслеживает каталог или готовый архив и разворачивает изменения после стабилизации файлов. После первого развёртывания он передаёт изменившиеся и удалённые файлы, пока хеш базы совпадает с установленным пакетом. `reload <plugin_id>` перезапускает установленный пакет без пересборки.

Dev Server работает с пакетами, впервые установленными через него. Он применяет новую версию или изменившийся пакет с прежней версией на месте. Пакет проходит обычную проверку размера, манифеста, подписи и списка отзывов. Установка через сервер подтверждает запуск с новым SHA-256. Разрешения аккаунтов для изменившегося пакета выдаются в менеджере заново. Развёрнутые через сервер плагины запускаются на текущую сессию приложения.

```powershell
python Tools/plugin_dev.py watch docs/plugins/examples/hello
python Tools/plugin_dev.py debug dev.bygram.hello on_load
python Tools/plugin_dev.py stop
```

Команда `debug` запускает выбранный метод активного dev-плагина без аргументов под интерактивным Pdb. Команды `where`, `p`, `next`, `step`, `continue` и `quit` доступны через ту же ADB-сессию. Выбирайте метод, который безопасно вызвать повторно. При остановке сервера сокет закрывается. Активные dev-плагины можно выключить отдельно командой `disable`.

## Совместимость с протоколом exteraGram

Сервер принимает последовательные UTF-8 JSON-объекты, в том числе без переводов строк между ними. Ответы завершаются переводом строки и повторяют `#`. Для BYgram добавьте поле `token` в каждый запрос. Клиент exteraGram без передачи токена потребует адаптации. Сервер принимает только локальные подключения.

- `write_plugin` принимает `plugin_id` и исходный Python в `content`. Для первого развёртывания Elyx отправьте `elyx_write` с исходным ID и архивом в Base64 в `content`. Команды изменения состояния принимают исходный ID или ID BYgram.
- `elyx_ping` проверяет соединение, `get_elyx_plugins` выдаёт список Elyx-пакетов Dev Server. `elyx_compare_folder` принимает `plugin_id` и словарь `files` или `hashes` с SHA-256. Ответ содержит `hashes`, `changed`, `removed` и `base_hash`.
- `elyx_changes` принимает записи `created`, `modified`, `deleted` и `moved`, включая удаление и перемещение каталогов. Передайте `base_hash` для обнаружения конкурирующей правки. Сравнение и изменения передаются в `data` как Base64 от zlib JSON. Сервер заново конвертирует пакет перед заменой.
- `start_debugger` с `platform=vscode` открывает loopback-порт debugpy для ADB forward. `platform=pycharm` подключается к IDE через ADB reverse. Хост запроса должен быть `127.0.0.1`. `stop_debugger` прекращает трассировку. Для смены порта debugpy и закрытия его listener перезапустите приложение.

## Получение сигнатур ART

```powershell
python Tools/plugin_dev.py art-targets org.telegram.ui.ActionBar.ActionBar setTitle > bygram-hooks.json
```

Команда показывает найденные перегрузки и точные номера сборок устройства. Для конструктора укажите `'<init>'`. Оставьте нужные сигнатуры, положите файл в корень Elyx или передайте его в `port_extera_plugin.py --hook-manifest`. Однофайловый исходник может объявить тот же словарь как `__bygram_hooks__`. При запуске BYgram проверяет диапазоны сборок и все объявленные цели до импорта Python, затем выполняет пробу ART на устройстве. Получение сигнатур само по себе не запускает пробу.

## Отладка из IDE

`install-debugger` загружает Python-файлы адаптера с PyPI, сверяет SHA-256 и через `adb run-as` записывает их в закрытый каталог Debug. Адаптер устанавливается один раз на устройство. Перезапустите BYgram после обновления адаптера или при смене IDE и снова запустите Dev Server. `debug-ide` вызывает выбранный метод без аргументов после подключения к IDE и снимает временный `adb reverse` после завершения. Порт IDE должен слушать на компьютере. После точки останова продолжите выполнение метода, чтобы Dev Server снова принимал команды развёртывания и перезагрузки.

### VS Code

```powershell
python Tools/plugin_dev.py install-debugger debugpy
```

Добавьте в `.vscode/launch.json` конфигурацию и запустите её. В `remoteRoot` укажите путь, который печатает `deploy` для текущей сборки плагина. `localRoot` должен указывать на каталог с теми же исходниками. Параметр `clientOS` в примере предназначен для Windows. Для Linux и macOS используйте `unix`.

```json
{
  "name": "BYgram plugin",
  "type": "debugpy",
  "request": "attach",
  "clientOS": "windows",
  "listen": {"host": "127.0.0.1", "port": 5678},
  "pathMappings": [
    {"localRoot": "${workspaceFolder}/docs/plugins/examples/hello", "remoteRoot": "/data/user/0/com.bygram.mobile/no_backup/plugins/…"}
  ]
}
```

```powershell
python Tools/plugin_dev.py debug-ide debugpy dev.bygram.hello on_load --port 5678
```

### PyCharm

Создайте конфигурацию **Python Debug Server** на порту 5678, добавьте соответствие локального каталога и пути из `deploy`, затем запустите её. Подставьте точную версию `pydevd-pycharm`, которую показывает PyCharm для своей сборки.

```powershell
python Tools/plugin_dev.py install-debugger pycharm --version 263.7307.206
python Tools/plugin_dev.py debug-ide pycharm dev.bygram.hello on_load --port 5678
```

[Документация VS Code](https://code.visualstudio.com/docs/python/debugging) · [Документация PyCharm](https://www.jetbrains.com/help/pycharm/remote-debugging-with-product.html)

Протокол принимает последовательные UTF-8 JSON-объекты. Общие поля `@`, `#` и `token` задают команду, ID запроса и токен. `write_plugin` принимает поле `package` с Base64 готового архива. `patch_plugin` принимает `plugin_id`, `base_hash`, словарь `changes` с Base64 изменённых файлов и список `removed`. Сервер собирает и проверяет новый пакет перед заменой установленного. `start_debugger` принимает `plugin_id` и `method` и переключает соединение на терминал Pdb после JSON-подтверждения. `start_ide_debugger` дополнительно принимает `adapter` и `port` и возвращает JSON после завершения метода.
