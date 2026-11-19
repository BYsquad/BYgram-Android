# Python Android wheels для Full

В этой папке находятся восемь wheels с нативными зависимостями Pillow и lxml для arm64 и x86_64. Python-расширения Pillow 11.0.0 и lxml 5.3.0 берутся из индекса Chaquopy для CPython 3.11. Gradle проверяет `SHA256SUMS` и передаёт папку в `pip --find-links`. Для 32-битных ABI используются пакеты Chaquopy.

Сборка выполняется на Linux x86_64 или WSL с Android NDK r27c, GNU Make, Clang из NDK, Autoconf, Automake и Libtool. Скрипт проверяет хеши исходников, задаёт выравнивание ELF и проверяет каждый загружаемый сегмент перед упаковкой.

```bash
bash Tools/build_python_native_wheels.sh /path/to/android-ndk-r27c ~/bygram-wheel-build
```

Результаты находятся в `~/bygram-wheel-build/output`. При обновлении wheels проверьте итоговый APK через `Tools/check_plugin_apk.py` и запустите диагностический сценарий на 16-КиБ Android. В каждом wheel включены уведомления о лицензии соответствующей библиотеки.

- [Android 16 KiB](https://developer.android.com/guide/practices/page-sizes)
- [Chaquopy 17.2](https://chaquo.com/chaquopy/doc/current/changelog.html)
- [Публичный форк со скриптом сборки](https://github.com/BYxarek/chaquopy/tree/bygram-python311-16k/server/pypi/bygram-16k)
- [Релиз wheels и SHA-256](https://github.com/BYxarek/chaquopy/releases/tag/bygram-python311-16k-v1)
