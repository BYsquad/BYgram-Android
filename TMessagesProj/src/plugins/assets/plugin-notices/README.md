# Plugin runtime dependencies

- Chaquopy 17.2.0: https://github.com/chaquo/chaquopy (MIT).
- Python 3.11: https://docs.python.org/3.11/license.html (PSF license and incorporated notices).
- BYxarek/hook: https://github.com/BYxarek/hook/tree/28137a6287e78ec55aaaa439c3c8e4cae55c0cf0
  (Open Software License 3.0; Xposed-derived Java files also carry Apache 2.0 notices).
- LSPlant at 6ac2280d8d88e14f0864c26ac97c1dc96713bde1:
  https://github.com/LSPosed/LSPlant (LGPL 3.0), statically linked into libaliuhook.so.
- Dobby 1.2: https://github.com/jmpews/Dobby (Apache 2.0).
- Android libc++: https://android.googlesource.com/platform/ndk (LLVM runtime license).

The hook AAR is copied unchanged from the local release build of the pinned clean
checkout. It is resolved only from the group-restricted repository in this directory.
SHA-256: 01fa427a1e6d460069cc32c772d47219677885528697e5dfd91cfc823eda4189.
Gradle verifies that hash before Full preBuild; Lite does not resolve these runtimes.
The upstream AAR contains a generated classes.dex alongside classes.jar; standard
AGP consumes the jar. Verify the final APK rather than assuming AAR root contents
are all packaged.

License compatibility and redistribution obligations require separate review
before a public release; this engineering note is not a legal determination.
