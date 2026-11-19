# Plugin runtime dependencies

- Chaquopy 17.2.0: https://github.com/chaquo/chaquopy (MIT).
- Python 3.11: https://docs.python.org/3.11/license.html (PSF license and incorporated notices).
- MVEL 2.5.2.Final: https://github.com/mvel/mvel (Apache 2.0), Full edition only.
- Beautiful Soup 4.15.0: https://pypi.org/project/beautifulsoup4/4.15.0/ (MIT).
- Requests 2.34.2: https://pypi.org/project/requests/2.34.2/ (Apache 2.0).
- Pillow 11.0.0 and lxml 5.3.0: Chaquopy's Android CPython 3.11 wheels.
  Full uses rebuilt libxml2, libxslt, FreeType and libjpeg-turbo wheels on 64-bit ABI.
  Rebuild instructions, source checksums and the wheel lock are in [python-android-wheels](python-android-wheels/README.md).
- BYxarek/hook: https://github.com/BYxarek/hook/tree/43b0d8d01b580b8d4caa4aae7ed528ed80a35078
  (Open Software License 3.0; Xposed-derived Java files also carry Apache 2.0 notices).
- LSPlant at 06bd3412d639d6b0d76fe6c7388066fe44e8963d:
  https://github.com/LSPosed/LSPlant (LGPL 3.0), statically linked into libaliuhook.so.
- Dobby 1.2: https://github.com/LSPosed/Dobby/tree/edb2af1216313cf6c0d6771be2b279c1db573faf
  (Apache 2.0), with `patches/dobby-1.2-preserve-mappings.patch` and the NDK 30 logging include fix.
- Android libc++: https://android.googlesource.com/platform/ndk (LLVM runtime license).

The hook AAR `1.1.4-api37.1-bygram.3` uses the pinned fork's backup JNI ID fix,
patched static Dobby and an LSPlant access-flags restoration patch. XposedBridge
also synchronizes callback publication and defers native unhook until active
calls finish. The public Java API is unchanged. Four native libraries and
classes.jar replace the corresponding baseline entries; all other entries remain
byte-identical. The builder verifies these properties.
It is resolved only from the group-restricted repository in this directory.
SHA-256: 78827e37214b11b5ec4aa3e2e4afaabd28a9f1503c47c18294f2060b6c9d33f3.
Per-ABI hashes and source commits are in its `provenance.json`. Rebuild tooling and source locations: `../Tools/build_plugin_backend.py` and the pinned provenance. Upstream `1.1.4-api37.1` is retained for
reproduction of the original defect; it is no longer the Full dependency.
Gradle verifies that hash before Full preBuild; Lite does not resolve these runtimes.
The upstream AAR contains a generated classes.dex alongside classes.jar; standard
AGP consumes the jar. Verify the final APK rather than assuming AAR root contents
are all packaged.

License compatibility and redistribution obligations require separate review
before a public release; this engineering note is not a legal determination.
