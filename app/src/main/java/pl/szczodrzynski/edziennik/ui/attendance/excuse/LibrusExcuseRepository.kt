package pl.szczodrzynski.edziennik.ui.attendance.excuse

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskErrorEvent
import pl.szczodrzynski.edziennik.data.api.events.ExcuseListGetEvent
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.utils.models.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ExcuseApiException(val apiError: ApiError?, message: String) : Exception(message)

/** Wysyłka nie jest jeszcze podłączona (czekamy na cURL z Synergii). */
class ExcuseSendNotReadyException : UnsupportedOperationException()

/**
 * Prawdziwy odczyt listy z Synergii. Formularz i wysyłka nadal są atrapą,
 * a [send] celowo rzuca wyjątek, żeby nic nie udawało wysłania.
 */
class LibrusExcuseRepository(
    private val context: Context,
    private val profileId: Int,
    private val fake: FakeExcuseRepository = FakeExcuseRepository(),
) : ExcuseRepository {

    override suspend fun getExcuses(from: Date, to: Date, status: ExcuseStatusFilter): List<Excuse> =
        try {
            requestExcuses(from, to, status)
        } catch (e: TimeoutCancellationException) {
            throw ExcuseApiException(null, "Synergia nie odpowiedziała w ciągu 45 s")
        }

    private suspend fun requestExcuses(from: Date, to: Date, status: ExcuseStatusFilter): List<Excuse> =
        withTimeout(45_000) {
            suspendCancellableCoroutine { cont ->
                // ApiTaskFinishedEvent nie mówi, które zadanie się skończyło (np. trwająca
                // synchronizacja), więc czekamy tylko na wynik, błąd krytyczny albo timeout.
                val subscriber = object {
                    fun finish(block: () -> Unit) {
                        EventBus.getDefault().unregister(this)
                        if (cont.isActive) block()
                    }

                    @Subscribe(threadMode = ThreadMode.MAIN)
                    fun onExcuseListGetEvent(event: ExcuseListGetEvent) {
                        if (event.profileId != profileId) return
                        finish { cont.resume(event.excuses) }
                    }

                    @Subscribe(threadMode = ThreadMode.MAIN)
                    fun onApiTaskErrorEvent(event: ApiTaskErrorEvent) {
                        if (event.error.profileId != profileId) return
                        if (event.error.isCritical)
                            finish { cont.resumeWithException(event.error.toException()) }
                    }

                    private fun ApiError?.toException() = ExcuseApiException(
                        this, this?.getStringReason(context) ?: "Brak odpowiedzi z Synergii"
                    )
                }
                EventBus.getDefault().register(subscriber)
                cont.invokeOnCancellation { EventBus.getDefault().unregister(subscriber) }
                EdziennikTask.excuseListGet(profileId, from, to, status).enqueue(context)
            }
        }

    override suspend fun getFormInfo() = fake.getFormInfo()

    override suspend fun getLessons(date: Date) = fake.getLessons(date)

    override suspend fun send(form: ExcuseFormInfo, request: ExcuseRequest) {
        throw ExcuseSendNotReadyException()
    }
}
