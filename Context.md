# Context

## Build and install on the phone (fork `pl.szczodrzynski.edziennik.m`)

- **Clean git tree required.** Release builds with uncommitted changes are
  blocked at runtime (`BuildManager.gitIsDirty` → `InvalidBuildReason.UNSTAGED_CHANGES`,
  "korzystanie z aplikacji zostało zablokowane"). This includes untracked files.
  Commit everything first, then build. The version name must not end in `.dirty`.
- **JDK 17 for Gradle.** `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
  (kapt fails on the JDK 21 bundled with Android Studio). `ANDROID_HOME=~/Library/Android/sdk`.
- **Build:** `./gradlew :app:assembleUnofficialRelease`
  → `app/build/outputs/apk/unofficial/release/app-unofficial-release-unsigned.apk`
  (applicationId `pl.szczodrzynski.edziennik.m`).
- **Do not install the debug APK.** It has applicationId `pl.szczodrzynski.edziennik`
  and a debug signature, so it does not update `.m`.
- **Sign** with `~/.android/szkolny-m.jks` (the key the installed `.m` app uses; the
  password is entered by the user in their own terminal, never in chat):
  `zipalign -f -p 4 <unsigned> <aligned>`, then
  `apksigner sign --ks ~/.android/szkolny-m.jks --out <signed> <aligned>`
  (build-tools 36.1.0; `apksigner` needs `JAVA_HOME` set).
- **Install:** `adb install -r <signed>` (keeps data and logged-in accounts).
  Platform tools: `~/Library/Android/sdk/platform-tools/adb`.
- **HEAD must be pushed to the public fork before installing.** At first launch the
  app calls `https://api.github.com/repos/<remote>/git/commits/<hash>` anonymously
  (`BuildManager.validateRepo`); a 404 shows "kompilacja zawiera nieopublikowane
  zmiany ... repozytorium prywatne lub nie zawiera najnowszych zmian" and blocks the app.
  Remote used: `tarczyk/szkolny-android` (fork must stay PUBLIC).
  Push first: `git push fork feature/e-usprawiedliwienia-master`.
- **A failed validation is cached per remote+hash** (`invalid<remote><hash>` md5 in
  app config), so pushing afterwards does not help the already installed build.
  Rebuild from a new commit (new hash) or clear the app data. Therefore the order is:
  commit everything → push → build → align → sign → `adb install -r`.
  Any later commit (even docs) changes the hash, so push it and rebuild before installing.

## e-Usprawiedliwienia (Librus Synergia)

- The tab is shown only for Librus **parent** accounts (`profile.isParent`).
  Student accounts get Synergia `stop.png` → error 156 (`ERROR_LIBRUS_SYNERGIA_ACCESS_DENIED`).
- Layouts must use `NavView.TextView.*` styles. Material 3 attrs such as
  `?textAppearanceBodyMedium` are not defined in the `NavView` theme and render as tiny text.
