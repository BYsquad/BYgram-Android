# Android: обновление форков Dobby и AliuHook — 01.11.2026

По запросу владельца обновлены исходные форки библиотек:

- Dobby: https://github.com/BYxarek/Dobby, локально `C:/project/LSPosed-Dobby`.
  База — последний проверенный upstream LSPosed/Dobby
  `c1da0315d7a2069bde2ab432e45d4ab6df91237a`; исправленный коммит
  `c53ab0bc8014092e160f9a9269d6057abf6aaa7f`.
- AliuHook: https://github.com/BYxarek/hook, локально `C:/project/Aliucord-hook`.
  Опубликован в `main` коммит `65de5469117f4557cbcb92cfb36970f2a6254256`.
  Новый pinned подмодуль Dobby вместо Maven `dobby:1.2`, статическая линковка,
  адаптация DobbyHook к function-pointer API, версия `1.1.4-api37.2`.
  LSPlant остаётся на `06bd3412d639d6b0d76fe6c7388066fe44e8963d`.

Dobby `c53ab0b` опубликован также в основной ветке `master` форка.
`:core:publishToMavenLocal` — PASS; POM `1.1.4-api37.2` не содержит старой
транзитивной зависимости Dobby 1.2. Публичный Maven release не выполнялся.

Сохранён запрет MAP_FIXED поверх существующих mappings, включая ART PROT_NONE.
Если mmap возвращает другой адрес, новое отображение освобождается и выделение
отклоняется. Старый патч NearMemoryArena не накладывается: этот аллокатор в
upstream заменён. Изменение касается системной границы native/ART, а не страниц
Telegram или пользовательских настроек BYgram.

## Проверки

- `:core:assembleRelease` — PASS, четыре ABI: arm64-v8a, armeabi-v7a, x86, x86_64.
- AAR: статический Dobby, нет libdobby.so; ELF64 LOAD alignment 16 KiB — PASS.
- Allocation regression, связанный с новым libdobby.a: сохранность RW и PROT_NONE,
  sentinel bytes и обычное allocation/free — PASS на API 35/36/37 x86_64.
- `tests/PluginNativeProbe.java` из нового AliuHook: 100 циклов hook/unhook,
  изменённые аргументы/результат, восстановление оригинала, HashMap stress и GC —
  PASS, exit 0 на API 35/36/37 x86_64. API 36 — 4 KiB, API 35/37 — 16 KiB.
- Логи: `C:/project/Aliucord-hook/build/dobby-latest-probe/emulator-555{4,6,8}.log`.
- AAR: `C:/project/Aliucord-hook/core/build/outputs/aar/core-release.aar`, SHA-256
  `90309de2c7210f2b4c0b33d0b932baef7f61a8ed53a27060fdd29684c8cdee02`.
- Локальная сборка: Gradle 8.13 / AGP 8.11 / JDK 21 / NDK 30.0.16248370 /
  CMake 4.1.2. Wrapper 8.14.3 требовал недоступную загрузку, использован уже
  установленный совместимый Gradle без изменения wrapper.

ARM runtime, instrumentation и полная интеграция BYgram в этом задании не
проверялись. Подключённая в BYgram версия `1.1.4-api37.1-bygram.3` не заменена;
её собственные Java/ART-flags патчи остаются отдельной частью интеграции.
APK не создавался. CI AliuHook собирает AAR без автоматического создания APK.
