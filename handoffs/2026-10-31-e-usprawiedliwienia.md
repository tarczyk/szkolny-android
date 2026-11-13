# Handoff: e-Usprawiedliwienia (Librus Synergia)

Data: 2026-10-31, aktualizacja 2026-11-13 (wysyłka w trybie „wybrane dni”)
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
| Wysyłka, tryb „wybrane dni” | Gotowe w kodzie | Format z prawdziwej wysyłki w przeglądarce (HAR, 2026-11-13). **Z aplikacji jeszcze nie testowane** |
| Wysyłka, tryb „kilka godzin” | **Nie podłączona** | `send()` rzuca `ExcuseNotReadyException`, nic nie jest wysyłane |
| Załącznik | **Nie zrobione** | Przycisk pokazuje komunikat, wysyłany jest pusty `attachment` |

Testy jednostkowe parsera: 7/7 (`LibrusSynergiaExcuseParserTest`, syntetyczny HTML).

## Znany problem
Przy pierwszym otwarciu zakładki po świeżym zalogowaniu pojawił się raz błąd „Brak dostępu do Synergii” (kod 156, `ERROR_LIBRUS_SYNERGIA_ACCESS_DENIED`, Synergia zwraca stronę ze `stop.png`). Kolejna próba zadziałała. Przyczyna nieustalona. Jeśli wróci, zebrać odpowiedź przez Chucker (debug ma go włączonego: powiadomienie „recording HTTP activity”). Hipoteza do sprawdzenia: sesja z `AutoLoginToken` ląduje na `/uczen/...`, a moduł jest dla rodzica.

## Architektura
Zapytania idą tą samą drogą co reszta Synergii w aplikacji:

```
UI (ExcuseListFragment / ExcuseSendDialog)
  -> ExcuseRepository.get(context, profileId)  // LibrusExcuseRepository albo FakeExcuseRepository (useFake)
  -> EdziennikTask.excuseListGet / excuseFormGet / excuseSend  -> ApiService (kolejka zadań)
  -> Librus.getExcuses / getExcuseForm / sendExcuse  (login LIBRUS_SYNERGIA)
  -> LibrusSynergiaGetExcuses / LibrusSynergiaGetExcuseForm  (synergiaGet)
     LibrusSynergiaSendExcuse  (synergiaGet po requestkey, potem POST multipart przez data.app.http)
  -> EventBus: ExcuseListGetEvent / ExcuseFormGetEvent / ExcuseSentEvent
  -> LibrusExcuseRepository.await() wznawia coroutine (timeout 45 s, błąd krytyczny przerywa)
```

Decyzje:
- **`EdziennikInterface` nie jest rozszerzany.** `EdziennikTask` rzutuje na `Librus` (`as? Librus`). Dzięki temu nie trzeba dopisywać stubów w 7 implementacjach i fork mniej koliduje z upstreamem.
- **`ApiTaskFinishedEvent` jest ignorowany**, bo nie identyfikuje zadania (np. równoległa synchronizacja). Czekamy na event z wynikiem, błąd krytyczny albo timeout.
- **Modele (`Excuse`, `ExcuseFormInfo`…) są w `ui/attendance/excuse`**, a importuje je warstwa API. To świadomy skrót w forku i można je przenieść.
- **POST wysyłki idzie przez OkHttp, nie mhttp.** mhttp nie pozwala wymusić multipart bez pliku. `data.app.http` to ten sam klient, którego mhttp używa globalnie (`MHttp.customOkHttpClient`), więc ma wspólny cookie jar z `DZIENNIKSID` i podąża za 302.
- **Timeout przy wysyłce nie jest ponawiany.** POST mógł już dojść, więc dialog prosi o sprawdzenie listy przed ponowieniem.

## Pliki
- `app/src/main/java/.../ui/attendance/excuse/`: `ExcuseModels.kt`, `ExcuseRepository.kt` (interfejs + fake), `LibrusExcuseRepository.kt`, `ExcuseListFragment.kt`, `ExcuseAdapter.kt`, `ExcuseSendDialog.kt`
- `app/src/main/java/.../data/api/edziennik/librus/data/synergia/`: `LibrusSynergiaExcuseParser.kt`, `LibrusSynergiaGetExcuses.kt`, `LibrusSynergiaGetExcuseForm.kt`, `LibrusSynergiaSendExcuse.kt`
- `app/src/main/java/.../data/api/events/`: `ExcuseListGetEvent.kt`, `ExcuseFormGetEvent.kt`, `ExcuseSentEvent.kt`
- Zmienione: `EdziennikTask.kt`, `Librus.kt`, `AttendanceFragment.kt` (nowa zakładka), `res/values/strings.xml` (`excuse_*`)
- Layouty: `attendance_excuse_fragment.xml`, `attendance_excuse_item.xml`, `attendance_excuse_send_dialog.xml`
- Test: `app/src/test/.../LibrusSynergiaExcuseParserTest.kt`

## Synergia: co wiadomo (z zapisanych stron i `eusprawiedliwienia_rodzic.js`)

### Lista: `/eusprawiedliwienia` (zrobione)
- `POST /eusprawiedliwienia` z polami: `requestkey`, `usprawiedliwienia_filtr=1`, `data_od`, `data_do`, `status`. `requestkey` pobierany wcześniej z `GET /eusprawiedliwienia`.
- `status`: 0 wszystkie, 1 wysłane, 2 usprawiedliwione, 3 odrzucone.
- Tabela `table.decorated` z 7 kolumnami: Załącznik | Okres (`<b>`) | Liczba | Treść | Powiadomieni nauczyciele | Data wysłania | Status. Status ma postać „usprawiedliwione (Nazwisko Imię)”.

### Formularz: `/eusprawiedliwienia/dodaj` (odczyt zrobiony)
- `form#formUsprawiedliwienie`, `method=POST`, `enctype=multipart/form-data`, **bez `action`**, czyli POST na ten sam adres (potwierdzone HAR-em).
- Pola: `requestkey` (hidden), `dodajUsprawiedliwienieDo=1` (hidden), `datyLubLekcje` (`wgDat` | `wgGodzin`), `dataOd`, `dataDo` (YYYY-MM-DD), `powodNieobecnosci` (textarea), `attachment` (file, max 5 MB, .jpg/.bmp/.png/.pdf), `powiadomInnych=1` (checkbox).
- `input#autoAkceptacja` (poza formularzem): gdy `1`, JS pyta „Czy na pewno wysłać usprawiedliwienie?”.
- Wychowawca: `.warning-content p` zawierające „wychowawcy”, potem `<b>`.

### Logika JS (odwzorowana w `ExcuseSendDialog`)
- Zmiana `dataOd` ustawia `dataDo` na tę samą wartość.
- Tryb `wgGodzin`: `dataDo = dataOd`, pokazuje listę lekcji.
- Walidacja: brak dat, `dataOd > dataDo`, w trybie godzin brak zaznaczonej lekcji (`.usprawiedliwienie:checkbox`).
- `powiadomInnych` aktywne tylko gdy ostatni dzień >= dziś.

### Wysyłka: `POST /eusprawiedliwienia/dodaj` (zrobione dla „wybrane dni”)
Z prawdziwej wysyłki w Chrome (HAR, 2026-11-13, tryb `wgDat`, bez załącznika):
- `Content-Type: multipart/form-data`. Pola w kolejności: `requestkey`, `dodajUsprawiedliwienieDo=1`, `datyLubLekcje=wgDat`, `dataOd`, `dataDo`, `powodNieobecnosci`, `attachment` (pusty: `filename=""`, `application/octet-stream`), `powiadomInnych=1` (tylko gdy zaznaczone).
- Przeglądarka wysłała też `Origin` i `Referer` na `/eusprawiedliwienia/dodaj`. Aplikacja wysyła oba nagłówki, nie wiadomo, czy są wymagane.
- Sukces: `302`, `Location: /eusprawiedliwienia`, pusta treść. Strona listy ma `div.container.green p` z tekstem „Usprawiedliwienie zostało wysłane.” (`LibrusSynergiaExcuseParser.isExcuseSent`).
- **Nieznane:** wygląd odpowiedzi przy błędzie. Każda odpowiedź bez zielonego komunikatu jest traktowana jako `ERROR_LIBRUS_SYNERGIA_OTHER` z całym HTML w `apiResponse`.

### Lista lekcji (do zrobienia)
- `POST /eusprawiedliwienia_rodzic_godziny` z polem `dataOd`, zwraca fragment HTML wstawiany do `#blokWgGodzinContent`.
- **Nieznane:** nazwy i wartości checkboxów `.usprawiedliwienie`. Potrzebna odpowiedź z Network.

## Następne kroki
Zrobione 2026-11-13: wysyłka multipart, świeży `requestkey` przed POST, rozpoznanie sukcesu, odświeżenie listy po sukcesie (`onSent = ::load`).

1. **Pierwszy test wysyłki z aplikacji** przy następnej prawdziwej nieobecności. Sprawdzić w Chuckerze: 302 → `/eusprawiedliwienia`, zielony komunikat, wpis na liście. Nie wiadomo, czy da się wycofać wysłane usprawiedliwienie.
2. Przy pierwszym błędzie wysyłki zapisać odpowiedź (Chucker albo szczegóły błędu) i dodać rozpoznanie komunikatu błędu w parserze.
3. **Tryb „kilka godzin”:** zebrać od użytkownika Response z `eusprawiedliwienia_rodzic_godziny` (bezpieczne, nic nie wysyła). Potem:
   - `LibrusSynergiaExcuseParser.parseLessons(html)`: `ExcuseLesson(fieldName, fieldValue, label)`.
   - `LibrusSynergiaGetExcuseLessons` + event + `EdziennikTask.excuseLessonsGet`, analogicznie do listy.
   - Dodać pola lekcji do multipart w `LibrusSynergiaSendExcuse` i zdjąć blokadę trybu w `LibrusExcuseRepository.send()`. Format wysyłki w tym trybie też potwierdzić HAR-em.
4. Po sukcesie: synchronizacja frekwencji (teraz tylko odświeżenie listy).
5. Załącznik: wybór pliku wymaga `ActivityResult`, a z dialogu jest to niewygodne. Rozważyć przeniesienie formularza do fragmentu. W multipart podmienić pusty `attachment` na plik.

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
- Nie commitować zapisanych stron Synergii ani HAR/cURL: są tam dane ucznia, nauczycieli, `requestkey` i ciasteczka. HAR analizować lokalnie skryptem, który zasłania `cookie`, `requestkey` i treść, a nie wklejać go w całości.
- W Chuckerze zakładka Request zawiera `DZIENNIKSID` i hasło przy logowaniu.
