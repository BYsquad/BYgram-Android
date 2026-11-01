# Причина падения raw hooks: Dobby 1.2

> 01.11.2026: [обновлённый fork, bygram.3 и физический unhook](hook-update-and-audit-2026-11-01.md).
> Ниже история расследования. Прежний retention до restart в текущем HookBridge удалён.

Дата: 31.10.2026. Проверка исходников `C:/project/Aliucord-hook`, HEAD
`28137a6287e78ec55aaaa439c3c8e4cae55c0cf0`, LSPlant
`6ac2280d8d88e14f0864c26ac97c1dc96713bde1`.

> Продолжение того же дня: этот дефект Dobby исправлен в локальном pinned
> AAR `1.1.4-api37.1-bygram.1`, но полная интеграция BYgram r3 по-прежнему
> не проходит на API 35/36/37. Новый ART/Python сбой не локализован.
> Gate исходников остаётся только x86_64 API 28; [результаты](development-report.md).

## Подтверждённая причина

AliuHook вызывает DobbyHook из Maven-зависимости `io.github.vvb2060.ndk:dobby:1.2`.
Её исходники: `https://github.com/vvb2060/dobby-android`, submodule
`https://github.com/LSPosed/Dobby.git`, commit
`edb2af1216313cf6c0d6771be2b279c1db573faf`.

В `source/MemoryAllocator/NearMemoryArena.cc`, `search_near_blank_page`,
проверка последнего диапазона инвертирована: `max_page_addr < last_region_end`.
Вычисленный `next_page(last_region_end, ...)` затем затирается результатом
Allocate с другим адресом — оставшимся `min_page_addr`.
В `source/UserMode/UnifiedInterface/platform-posix.cc`, `OSMemory::Allocate`,
ненулевой адрес включает `MAP_FIXED`. Это позволяет заменить уже существующее
отображение, включая зарезервированную ART память с правами PROT_NONE.

На API 36 x86_64 / 4 KiB при первом перехвате
`art::mirror::Class::SetStatus` адрес перехода Dobby оказался
`0x7e66f116e000`. Снимки `/proc/self/maps` показали:

```text
до:
7e66c0000000-7e6700000000 ---p [anon:dalvik-linear-alloc shadow map]
после:
7e66c0000000-7e66f116e000 ---p [anon:dalvik-linear-alloc shadow map]
7e66f116e000-7e66f116f000 rw-p
7e66f116f000-7e6700000000 ---p [anon:dalvik-linear-alloc shadow map]
```

Процесс затем падал при GC (exit 139). Символ, смещение и исходные машинные
инструкции проверены. Базовый Java/HashMap/GC probe без загрузки backend проходил.
Это объясняет наблюдавшееся повреждение памяти; прежняя гипотеза о неверной
сигнатуре Class::SetStatus не подтвердилась.

## Исправление для проверки

Патч: `third_party/patches/dobby-1.2-preserve-mappings.patch`.
Он исправляет выбор адреса после последнего диапазона и убирает MAP_FIXED.
Если ядро вернуло другой адрес вместо запрошенного, освобождается только новое
отображение и возвращается nullptr. Это также защищает от гонки между чтением
maps и mmap; MAP_FIXED_NOREPLACE не требуется на старых ядрах Android.
Все ненулевые вызовы Allocate в этой версии находятся в NearMemoryArena;
обычный MemoryArena выделяет с nullptr.

Патч применён только к отдельной копии исходников в `output/dobby-android-inspect`.
Исходный Aliucord-hook, pinned AAR, gate BYgram и доставленный APK r2 не изменены.
Для сборки старого Dobby с NDK 30 также перенесён include `<android/log.h>`
из тела функции в начало `external/logging/logging.c` — отдельно от runtime-патча.
Использованы x86_64, android-21, Release, static Dobby, shared libc++, CMake 4.1.2,
`CMAKE_POLICY_VERSION_MINIMUM=3.5`, `Plugin.Android.BionicLinkerRestriction=ON`.
Экспериментальный AliuHook связан с полученным архивом вместо prefab Dobby.

## Проверки

- API 36 / 4 KiB: 100 hook/unhook, проверка изменённых аргументов/результата,
  восстановление оригинала, HashMap stress и GC — PASS, exit 0.
  В первом прогоне включались диагностические снимки maps; резерв ART сохранился.
  Повторный прогон чистого AliuHook с исправленным Dobby также PASS, exit 0.
- API 37 / 16 KiB: те же 100 циклов с исходным AliuHook без диагностических
  изменений, заменён только Dobby — PASS, exit 0.
- API 35 / 16 KiB: исходный AliuHook + исправленный Dobby, 100 циклов — PASS,
  exit 0. Тест выделения памяти — PASS. Контрольный тест с исходным Maven
  архивом Dobby 1.2 упал на первой проверке отклонения занятого адреса,
  exit 134; это ожидаемое обнаружение старого дефекта.
- `Tools/DobbyAllocationProbe.cpp`, связанный с настоящим исправленным архивом:
  попытки занять RW и PROT_NONE отклоняются, каждый байт sentinel сохранён;
  nullptr allocation/free — PASS на API 28/36 (4096) и API 35/37 (16384), exit 0.
- API 28 / 4 KiB: чистый AliuHook + исправленный Dobby, 100 циклов — PASS,
  exit 0; ранее работавший сценарий сохранился.

Артефакты: `output/plugin-native-root-maps-before.txt`,
`output/plugin-native-root-maps-after.txt`, `output/plugin-native-root-stage2.log`,
`output/plugin-dobby-fixed-maps-before.txt`, `output/plugin-dobby-fixed-maps-after.txt`,
`output/plugin-dobby-fixed-api36-probe.log`, `output/plugin-dobby-fixed-clean-api37.log`,
`output/plugin-dobby-allocation-api37.log`.
Дополнительно: `output/plugin-dobby-fixed-clean-api35.log`,
`output/plugin-dobby-allocation-api35.log`,
`output/plugin-dobby-original-allocation-api35.log`.
Чистые повторные прогоны: `output/plugin-dobby-fixed-clean-api28.log`,
`output/plugin-dobby-fixed-clean-api36.log`, `output/plugin-dobby-allocation-api28.log`,
`output/plugin-dobby-allocation-api36.log`. Все тестовые AVD остановлены.

SHA-256 чистого экспериментального x86_64 `libaliuhook.so`:
`a3abe183487aa0dcae053a1ef9eeb213599fbbaf63e431c3eafa73b0c80447d0`.
SHA-256 исправленного `libdobby.a`:
`739bf0042e6bed8b9740a761592c26939d5c1befcde14ee086addaa8335541c6`.

Сборка отдельного regression (PowerShell из корня BYgram; assert включены):

```powershell
$dobby = "$PWD/output/dobby-android-inspect/dobby/src/main/dobby"
$clang = "$env:LOCALAPPDATA/Android/Sdk/ndk/30.0.16248370/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe"
& $clang --target=x86_64-linux-android21 -std=c++17 -UNDEBUG `
  "-I$dobby/source/UserMode" "-I$dobby/source" "-I$dobby/source/include" `
  "-I$dobby/external" "-I$dobby/external/logging" `
  Tools/DobbyAllocationProbe.cpp output/dobby-fixed-build/libdobby.a `
  -llog -o output/dobby-allocation-probe
```

После push/chmod 755 запускать с тем же LD_LIBRARY_PATH, что и Java probe.
Для отрицательного контроля заменить архив на исходный Maven libdobby.a;
ожидается assertion на первой занятой RW-странице и exit 134.

Для запуска Java probe APK не нужен:

```sh
CLASSPATH=/data/local/tmp/bygram-native-probe/classes.dex \
LD_LIBRARY_PATH=/data/local/tmp/bygram-native-probe \
app_process /system/bin PluginNativeProbe 100
```

В каталоге должны находиться classes.dex с PluginNativeProbe/XposedBridge,
исправленный libaliuhook.so и совместимый libc++_shared.so. DEX имеет права 444.
Считать PASS только при наличии итоговой строки и коде выхода 0.

Это подтверждённое исправление одного native-дефекта, ещё не новая поставка SDK.
ARM/ARM64, физические устройства и интеграция исправленной библиотеки с Python
и полным приложением не проверены. Ограничение raw hooks в BYgram сохраняется
до сборки закреплённой зависимости и интеграционной проверки.

## Воспроизводимая сборка исправленного AAR

Продолжение 31.10.2026: `Tools/build_plugin_backend.py` создаёт отдельный
native-only кандидат `1.1.4-api37.1-bygram.1`. Старый Maven-артефакт не
перезаписывается. Скрипт экспортирует committed bytes hook, LSPlant,
dex_builder, parallel_hashmap, xz и Dobby, применяет патч и собирает все четыре
ABI с NDK 30 / CMake 4.1.2. Из исходного AAR заменяются только четыре
`jni/*/libaliuhook.so`; Java API, libc++ и прочие entries сверяются побайтно.
Проверяются ELF64 16 KiB, сохраняются SHA-256 каждого native файла и AAR.
Это Release CMake configuration с прежними пустыми optimization flags
экспериментального probe, не измерение производительности Release BYgram.

```powershell
python Tools/build_plugin_backend.py --hook C:/project/Aliucord-hook `
  --dobby output/dobby-android-inspect/dobby/src/main/dobby `
  --sdk C:/Users/broek/AppData/Local/Android/Sdk `
  --prefab C:/project/Aliucord-hook/core/.cxx/RelWithDebInfo/54q654d6/prefab `
  --work output/plugin-backend-bygram1
```

`--work` должен быть новым каталогом; script не удаляет прежние результаты.
`--prefab` — уже сгенерированный hook Gradle prefab для libcxx-full
30.0.16248370, с подкаталогами четырёх ABI. Пути локального окружения
передаются аргументами. Нужны git objects указанных commits и установленный
Android SDK; сетевые загрузки и APK этой командой не создаются.

Результат сборки сам по себе не расширяет матрицу. Итоговая интеграционная
приёмка и поставка фиксируются в `development-report.md`.


## Дополнение: lifecycle Python/native, завершение SDK 31.10.2026

После исправления Dobby оставался отдельный дефект повторного физического unhook: полный Python fixture с GC воспроизводил SIGSEGV/порчу Gson metadata на современных ART. Контроль с постоянным native dispatcher на Member и удалением только plugin registrations прошёл API 35/36/37 x86_64 и POCO X7 Pro arm64 API 36. AAR bygram.1 не заменялся; экспериментальные варианты LSPlant/Dobby не включены в Maven dependency.

Production HookBridge сохраняет пустой native dispatcher до завершения процесса, переиспользует его и ограничивает общее число разных целей 1024. После 300 циклов и закрытия обоих scopes: 5 targets, 0 registrations, GC/HashMap stress и PID survival PASS. Полное снятие native-прослойки требует холодного restart. Это выбранная граница lifecycle, видимая в UI/SDK, а не заявление о физическом unhook.

Скрипт `Tools/check_plugin_backend_source.py SERIAL --work output/NEW_DIRECTORY` компилирует текущий переименованный HookBridge и fixed context factory, запускает собственный контролируемый плагин в установленном Debug BYgram и восстанавливает реестры/markers. Он разрешает только пустой registry на выделенной тестовой установке. JSON-отчёт фиксирует source/DEX/native SHA, build fingerprint и результат. Это source-проверка, отдельная от итогового Release APK.

Java bridge не вводит глобального отключения JIT. У pinned LSPlant остаются его ART init hooks и штатный Debug workaround Runtime::SetJavaDebuggable(kNonJavaDebuggable). Этот факт не скрывается утверждением о неизменности всех ART flags; Release runtime проверяется отдельно. Актуальная сводка: [завершение SDK](completion-2026-10-31.md).
