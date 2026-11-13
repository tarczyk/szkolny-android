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
import pl.szczodrzynski.edziennik.data.api.events.ExcuseFormGetEvent
import pl.szczodrzynski.edziennik.data.api.events.ExcuseListGetEvent
import pl.szczodrzynski.edziennik.data.api.events.ExcuseSentEvent
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.utils.models.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ExcuseApiException(val apiError: ApiError?, message: String) : Exception(message)

/** Funkcja nie jest jeszcze podłączona do Synergii (czekamy na dane z Network). */
class ExcuseNotReadyException : UnsupportedOperationException()

private const val TIMEOUT_MS = 45_000L

/**
 * Lista, dane formularza i wysyłka w trybie "wybrane dni" z Synergii.
 * [getLessons] i wysyłka w trybie "kilka godzin" nie są jeszcze podłączone
 * i celowo rzucają [ExcuseNotReadyException], żeby nic nie udawało działania.
 */
class LibrusExcuseRepository(
    private val context: Context,
    private val profileId: Int,
) : ExcuseRepository {

    override suspend fun getExcuses(from: Date, to: Date, status: ExcuseStatusFilter): List<Excuse> =
        await(EdziennikTask.excuseListGet(profileId, from, to, status)) { event ->
            (event as? ExcuseListGetEvent)?.takeIf { it.profileId == profileId }?.excuses
        }

    override suspend fun getFormInfo(): ExcuseFormInfo =
        await(EdziennikTask.excuseFormGet(profileId)) { event ->
            (event as? ExcuseFormGetEvent)?.takeIf { it.profileId == profileId }?.form
        }

    /**
     * Uruchamia zadanie i czeka na jego wynik z EventBus.
     * ApiTaskFinishedEvent nie mówi, które zadanie się skończyło (np. trwająca
     * synchronizacja), więc czekamy tylko na wynik, błąd krytyczny albo timeout.
     */
    private suspend fun <T : Any> await(
        task: EdziennikTask,
        timeoutMessage: String = "Synergia nie odpowiedziała w ciągu ${TIMEOUT_MS / 1000} s",
        extract: (Any) -> T?,
    ): T = try {
        withTimeout(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val subscriber = object {
                    fun finish(block: () -> Unit) {
                        EventBus.getDefault().unregister(this)
                        if (cont.isActive) block()
                    }

                    fun onResult(event: Any) {
                        val result = extract(event) ?: return
                        finish { cont.resume(result) }
                    }

                    @Subscribe(threadMode = ThreadMode.MAIN)
                    fun onExcuseListGetEvent(event: ExcuseListGetEvent) = onResult(event)

                    @Subscribe(threadMode = ThreadMode.MAIN)
                    fun onExcuseFormGetEvent(event: ExcuseFormGetEvent) = onResult(event)

                    @Subscribe(threadMode = ThreadMode.MAIN)
                    fun onExcuseSentEvent(event: ExcuseSentEvent) = onResult(event)

                    @Subscribe(threadMode = ThreadMode.MAIN)
                    fun onApiTaskErrorEvent(event: ApiTaskErrorEvent) {
                        val error = event.error
                        if (error.profileId != profileId || !error.isCritical) return
                        finish {
                            cont.resumeWithException(ExcuseApiException(error, error.getStringReason(context)))
                        }
                    }
                }
                EventBus.getDefault().register(subscriber)
                cont.invokeOnCancellation { EventBus.getDefault().unregister(subscriber) }
                task.enqueue(context)
            }
        }
    } catch (e: TimeoutCancellationException) {
        throw ExcuseApiException(null, timeoutMessage)
    }

    override suspend fun getLessons(date: Date): List<ExcuseLesson> = throw ExcuseNotReadyException()

    /**
     * Tylko tryb "wybrane dni" (zweryfikowany). Pola lekcji trybu "kilka godzin" nie są znane.
     * requestkey z [form] nie jest używany: zadanie pobiera świeży tuż przed POST.
     */
    override suspend fun send(form: ExcuseFormInfo, request: ExcuseRequest) {
        if (request.mode != ExcuseMode.DAYS) throw ExcuseNotReadyException()
        await(
            EdziennikTask.excuseSend(profileId, request),
            // Po timeoucie POST mógł już dojść do Synergii, więc ponowienie może zdublować wysyłkę.
            timeoutMessage = "Brak odpowiedzi z Synergii. Usprawiedliwienie mogło zostać wysłane, sprawdź listę przed ponowieniem.",
        ) { event ->
            (event as? ExcuseSentEvent)?.takeIf { it.profileId == profileId }
        }
    }
}
