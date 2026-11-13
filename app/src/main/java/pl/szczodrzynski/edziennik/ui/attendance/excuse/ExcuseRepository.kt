package pl.szczodrzynski.edziennik.ui.attendance.excuse

import android.content.Context
import kotlinx.coroutines.delay
import pl.szczodrzynski.edziennik.utils.models.Date

/**
 * Źródło danych e-Usprawiedliwień.
 *
 * [LibrusExcuseRepository]: lista z Synergii (gotowe), formularz i wysyłka (do zrobienia).
 * [FakeExcuseRepository]: dane przykładowe, nic nie wysyła.
 * Endpointy Synergii:
 *  - getExcuses     -> POST /eusprawiedliwienia (requestkey, usprawiedliwienia_filtr=1, data_od,
 *                      data_do, status) + parsowanie <table class="decorated stretch center">:
 *                      7 kolumn: Załącznik | Okres (<b>) | Liczba | Treść | Nauczyciele | Data | Status,
 *                      status np. "usprawiedliwione (Nazwisko Imię)"
 *  - getFormInfo    -> GET  /eusprawiedliwienia/dodaj (wychowawca, requestkey, autoAkceptacja)
 *  - getLessons     -> POST /eusprawiedliwienia_rodzic_godziny, dataOd=YYYY-MM-DD
 *  - send           -> POST multipart na adres formularza (do potwierdzenia z cURL)
 */
interface ExcuseRepository {
    suspend fun getExcuses(from: Date, to: Date, status: ExcuseStatusFilter): List<Excuse>
    suspend fun getFormInfo(): ExcuseFormInfo
    suspend fun getLessons(date: Date): List<ExcuseLesson>
    suspend fun send(form: ExcuseFormInfo, request: ExcuseRequest)

    companion object {
        /** Ustaw na true, żeby testować UI bez logowania do Synergii. */
        var useFake = false

        fun get(context: Context, profileId: Int): ExcuseRepository =
            if (useFake) FakeExcuseRepository()
            else LibrusExcuseRepository(context.applicationContext, profileId)
    }
}

class FakeExcuseRepository : ExcuseRepository {

    private val excuses = mutableListOf(
        Excuse(
            period = "2026-10-07, lekcja: 1",
            absenceCount = 1,
            content = "Bardzo proszę o usprawiedliwienie nieobecności córki w dniu dzisiejszym.",
            notifiedTeachers = "Wychowawca",
            sentDate = "2026-10-07 13:24:21",
            status = "usprawiedliwione (Wychowawca)",
            statusComment = null,
            hasAttachment = false,
        ),
    )

    override suspend fun getExcuses(from: Date, to: Date, status: ExcuseStatusFilter): List<Excuse> {
        delay(400)
        return excuses.toList()
    }

    override suspend fun getFormInfo(): ExcuseFormInfo {
        delay(300)
        return ExcuseFormInfo(tutorName = "Wychowawca (przykład)", requestKey = "fake", autoAccept = true)
    }

    override suspend fun getLessons(date: Date): List<ExcuseLesson> {
        delay(300)
        return (1..6).map { ExcuseLesson("lekcja[]", "$it", "$it. Lekcja przykładowa") }
    }

    override suspend fun send(form: ExcuseFormInfo, request: ExcuseRequest) {
        delay(500)
        val period = when (request.mode) {
            ExcuseMode.DAYS -> "${request.dateFrom.stringY_m_d} - ${request.dateTo.stringY_m_d}"
            ExcuseMode.HOURS -> "${request.dateFrom.stringY_m_d}, lekcje: " +
                request.lessons.joinToString { it.fieldValue }
        }
        excuses.add(0, Excuse(
            period = period,
            absenceCount = request.lessons.size.takeIf { it > 0 } ?: 0,
            content = request.reason,
            notifiedTeachers = form.tutorName,
            sentDate = "(szkic, nie wysłano)",
            status = "wysłane",
            statusComment = null,
            hasAttachment = false,
        ))
    }
}
