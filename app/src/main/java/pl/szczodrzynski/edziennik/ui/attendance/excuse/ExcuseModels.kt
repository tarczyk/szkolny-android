package pl.szczodrzynski.edziennik.ui.attendance.excuse

import pl.szczodrzynski.edziennik.utils.models.Date

/**
 * Wiersz tabeli z ekranu Synergia "e-Usprawiedliwienia" (/eusprawiedliwienia).
 */
data class Excuse(
    val period: String,             // "Okres usprawiedliwienia", np. "2026-10-07, lekcja: 1"
    val absenceCount: Int,          // "Liczba usprawiedliwionych nieobecności"
    val content: String,            // "Treść"
    val notifiedTeachers: String,   // "Powiadomieni nauczyciele"
    val sentDate: String,           // "Data wysłania"
    val status: String,             // "Status", np. "usprawiedliwione"
    val statusComment: String?,     // druga linia pod statusem (komentarz wychowawcy)
    val hasAttachment: Boolean,     // "Załącznik"
)

/**
 * Filtr "Status" z ekranu listy: <select name="status"> w /eusprawiedliwienia.
 * Formularz filtra: POST /eusprawiedliwienia z polami
 * requestkey, usprawiedliwienia_filtr=1, data_od, data_do, status.
 */
enum class ExcuseStatusFilter(val label: String, val value: Int) {
    ALL("wszystkie", 0),
    SENT("wysłane", 1),
    EXCUSED("usprawiedliwione", 2),
    REJECTED("odrzucone", 3),
}

/** Dane formularza z /eusprawiedliwienia/dodaj, potrzebne przed wysłaniem. */
data class ExcuseFormInfo(
    val tutorName: String,      // "Usprawiedliwienie zostanie wysłane do wychowawcy: ..."
    val requestKey: String,     // ukryte pole "requestkey" (token CSRF)
    val autoAccept: Boolean,    // ukryte pole #autoAkceptacja == 1 -> pytanie "Czy na pewno...?"
)

/** Lekcja z odpowiedzi POST /eusprawiedliwienia_rodzic_godziny (tryb "kilka godzin"). */
data class ExcuseLesson(
    val fieldName: String,      // name checkboxa .usprawiedliwienie (TODO: ustalić z odpowiedzi)
    val fieldValue: String,
    val label: String,          // np. "1. Matematyka"
)

enum class ExcuseMode(val formValue: String) {
    DAYS("wgDat"),              // "wybrane dni"
    HOURS("wgGodzin"),          // "kilka godzin w wybranym dniu"
}

data class ExcuseRequest(
    val mode: ExcuseMode,
    val dateFrom: Date,
    val dateTo: Date,           // w trybie HOURS == dateFrom (tak robi skrypt Librusa)
    val lessons: List<ExcuseLesson>,
    val reason: String,         // "powodNieobecnosci"
    val notifyTeachers: Boolean,// "powiadomInnych" — tylko dla przyszłych dat
    // TODO: załącznik "attachment" (max 5 MB, .jpg/.bmp/.png/.pdf)
)
