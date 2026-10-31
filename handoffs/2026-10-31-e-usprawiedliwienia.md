# Handoff: e-Usprawiedliwienia (Librus Synergia)

Data: 2026-10-31
Branch: `feature/e-usprawiedliwienia` (bazuje na `develop`)

## Cel
Fork Szkolny.eu dla jednej szkoły (Librus Synergia, konto rodzica). Funkcja odwzorowuje moduł Synergii „e-Usprawiedliwienia”: lista wysłanych usprawiedliwień i formularz „Wyślij usprawiedliwienie”.

## Stan
| Element | Stan | Zweryfikowane |
| --- | --- | --- |
| Build `develop` (naprawa) | Gotowe | `assembleUnofficialDebug` przechodzi |
| Zakładka „e-Usprawiedliwienia” we Frekwencji (tylko Librus) | Gotowe | Na telefonie |
| Lista z Synergii (filtr od/do/status) | Gotowe | Na telefonie, prawdziwe konto: dane się zgadzają |
| Dane formularza (wychowawca, `requestkey`, `autoAkceptacja`) | Gotowe | Na telefonie: właściwy wychowawca |
| Lista lekcji (tryb „kilka godzin”) | **Nie podłączone** | Pokazuje komunikat „nie jest jeszcze podłączony” |
| Wysyłka | **Nie podłączona** | `send()` rzuca `ExcuseNotReadyException`, nic nie jest wysyłane |
| Załącznik | **Nie zrobione** | Przycisk pokazuje komunikat |

Testy jednostkowe parsera: 6/6 (`LibrusSynergiaExcuseParserTest`, syntetyczny HTML).

## Znany problem
Przy pierwszym otwarciu zakładki po świeżym zalogowaniu pojawił się raz błąd „Brak dostępu do Synergii” (kod 156, `ERROR_LIBRUS_SYNERGIA_ACCESS_DENIED`, Synergia zwraca stronę ze `stop.png`). Kolejna próba zadziałała. Przyczyna nieustalona. Jeśli wróci, zebrać odpowiedź przez Chucker (debug ma go włączonego: powiadomienie „recording HTTP activity”). Hipoteza do sprawdzenia: sesja z `AutoLoginToken` ląduje na `/uczen/...`, a moduł jest dla rodzica.

## Architektura
Zapytania idą tą samą drogą co reszta Synergii w aplikacji:

```
UI (ExcuseListFragment / ExcuseSendDialog)
  -> ExcuseRepository.get(context, profileId)  // LibrusExcuseRepository albo FakeExcuseRepository (useFake)
  -> EdziennikTask.excuseListGet / excuseFormGet  -> ApiService (kolejka zadań)
  -> Librus.getExcuses / getExcuseForm  (login LIBRUS_SYNERGIA)
  -> LibrusSynergiaGetExcuses / LibrusSynergiaGetExcuseForm  (synergiaGet)
  -> EventBus: ExcuseListGetEvent / ExcuseFormGetEvent
  -> LibrusExcuseRepository.await() wznawia coroutine (timeout 45 s, błąd krytyczny przerywa)
```

Decyzje:
- **`EdziennikInterface` nie jest rozszerzany.** `EdziennikTask` rzutuje na `Librus` (`as? Librus`). Dzięki temu nie trzeba dopisywać stubów w 7 implementacjach i fork mniej koliduje z upstreamem.
- **`ApiTaskFinishedEvent` jest ignorowany**, bo nie identyfikuje zadania (np. równoległa synchronizacja). Czekamy na event z wynikiem, błąd krytyczny albo timeout.
- **Modele (`Excuse`, `ExcuseFormInfo`…) są w `ui/attendance/excuse`**, a importuje je warstwa API. To świadomy skrót w forku i można je przenieść.

## Pliki
- `app/src/main/java/.../ui/attendance/excuse/`: `ExcuseModels.kt`, `ExcuseRepository.kt` (interfejs + fake), `LibrusExcuseRepository.kt`, `ExcuseListFragment.kt`, `ExcuseAdapter.kt`, `ExcuseSendDialog.kt`
- `app/src/main/java/.../data/api/edziennik/librus/data/synergia/`: `LibrusSynergiaExcuseParser.kt`, `LibrusSynergiaGetExcuses.kt`, `LibrusSynergiaGetExcuseForm.kt`
- `app/src/main/java/.../data/api/events/`: `ExcuseListGetEvent.kt`, `ExcuseFormGetEvent.kt`
- Zmienione: `EdziennikTask.kt`, `Librus.kt`, `AttendanceFragment.kt` (nowa zakładka), `res/values/strings.xml` (`excuse_*`)
- Layouty: `attendance_excuse_fragment.xml`, `attendance_excuse_item.xml`, `attendance_excuse_send_dialog.xml`
- Test: `app/src/test/.../LibrusSynergiaExcuseParserTest.kt`

## Synergia: co wiadomo (z zapisanych stron i `eusprawiedliwienia_rodzic.js`)

### Lista: `/eusprawiedliwienia` (zrobione)
- `POST /eusprawiedliwienia` z polami: `requestkey`, `usprawiedliwienia_filtr=1`, `data_od`, `data_do`, `status`. `requestkey` pobierany wcześniej z `GET /eusprawiedliwienia`.
- `status`: 0 wszystkie, 1 wysłane, 2 usprawiedliwione, 3 odrzucone.
- Tabela `table.decorated` z 7 kolumnami: Załącznik | Okres (`<b>`) | Liczba | Treść | Powiadomieni nauczyciele | Data wysłania | Status. Status ma postać „usprawiedliwione (Nazwisko Imię)”.

### Formularz: `/eusprawiedliwienia/dodaj` (odczyt zrobiony)
- `form#formUsprawiedliwienie`, `method=POST`, `enctype=multipart/form-data`, **bez `action`**, czyli POST na ten sam adres (do potwierdzenia cURL-em).
- Pola: `requestkey` (hidden), `dodajUsprawiedliwienieDo=1` (hidden), `datyLubLekcje` (`wgDat` | `wgGodzin`), `dataOd`, `dataDo` (YYYY-MM-DD), `powodNieobecnosci` (textarea), `attachment` (file, max 5 MB, .jpg/.bmp/.png/.pdf), `powiadomInnych=1` (checkbox).
- `input#autoAkceptacja` (poza formularzem): gdy `1`, JS pyta „Czy na pewno wysłać usprawiedliwienie?”.
- Wychowawca: `.warning-content p` zawierające „wychowawcy”, potem `<b>`.

### Logika JS (odwzorowana w `ExcuseSendDialog`)
- Zmiana `dataOd` ustawia `dataDo` na tę samą wartość.
- Tryb `wgGodzin`: `dataDo = dataOd`, pokazuje listę lekcji.
- Walidacja: brak dat, `dataOd > dataDo`, w trybie godzin brak zaznaczonej lekcji (`.usprawiedliwienie:checkbox`).
- `powiadomInnych` aktywne tylko gdy ostatni dzień >= dziś.

### Lista lekcji (do zrobienia)
- `POST /eusprawiedliwienia_rodzic_godziny` z polem `dataOd`, zwraca fragment HTML wstawiany do `#blokWgGodzinContent`.
- **Nieznane:** nazwy i wartości checkboxów `.usprawiedliwienie`. Potrzebna odpowiedź z Network.

## Następne kroki: wysyłka
1. **Zebrać od użytkownika (przeglądarka, DevTools → Network, Preserve log):**
   - Response z `eusprawiedliwienia_rodzic_godziny` (bezpieczne, nic nie wysyła).
   - Przy prawdziwej nieobecności: POST wysyłki przez „Copy as cURL” (z `DZIENNIKSID` zamienionym na `XXX`) oraz strona lub komunikat po wysyłce (sukces i ewentualny błąd).
2. `LibrusExcuseParser.parseLessons(html)`: `ExcuseLesson(fieldName, fieldValue, label)`.
3. `LibrusSynergiaGetExcuseLessons` + event + `EdziennikTask.excuseLessonsGet`, analogicznie do listy.
4. Wysyłka multipart. `synergiaGet` wysyła form-urlencoded. Sprawdzić, czy Synergia przyjmie urlencoded bez załącznika. Jeśli nie, dodać wariant multipart (mhttp lub OkHttp z `data.app.httpLazy` i ciasteczkiem `DZIENNIKSID`).
5. Świeży `requestkey`: pobrać `GET /eusprawiedliwienia/dodaj` tuż przed POST, bo token może być jednorazowy.
6. Rozpoznanie sukcesu i błędu z odpowiedzi (wymaga punktu 1).
7. Po sukcesie: odświeżenie listy (już jest `onSent = ::load`) i synchronizacja frekwencji.
8. **Pierwszy test na jednej nieobecności, po uprzedzeniu wychowawcy.** Nie wiadomo, czy da się wycofać wysłane usprawiedliwienie.
9. Załącznik: wybór pliku wymaga `ActivityResult`, a z dialogu jest to niewygodne. Rozważyć przeniesienie formularza do fragmentu.

## Build
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME=~/Library/Android/sdk
./gradlew :app:assembleUnofficialDebug        # APK: app/build/outputs/apk/unofficial/debug/
./gradlew :app:testUnofficialDebugUnitTest --tests '*LibrusSynergiaExcuseParserTest'
```
- Upstream `develop` się nie budował. Naprawy są w commicie `5f7c3a7a`: biblioteki z JitPack (404) przeniesione do `vendor/`, importy po merge'u `develop-v4`, brakujący styl.
- Wydania upstream (v4.15.1) idą z `develop-v4`, nie z `develop`.

## Logowanie (uwaga dla testów)
- Aplikacja wymaga **Konta Librus (e-mail)** z podpiętym kontem Synergii. Login Synergii (`…u`) nie działa w polu e-mail (błąd „Nieprawidłowy login lub hasło”).
- Tryb „login Synergii” jest w upstreamie wykomentowany (`LoginInfo.kt`) i nie dałby sesji Synergii: `LIBRUS_SYNERGIA` wymaga `LIBRUS_API`, a `LIBRUS_API` jest wyłączone w tym trybie.
- Wersja debug ma `applicationId` z `.debug` i instaluje się obok oficjalnej.

## Bezpieczeństwo
- Nie commitować zapisanych stron Synergii ani HAR/cURL: są tam dane ucznia, nauczycieli, `requestkey` i ciasteczka.
- W Chuckerze zakładka Request zawiera `DZIENNIKSID` i hasło przy logowaniu.
