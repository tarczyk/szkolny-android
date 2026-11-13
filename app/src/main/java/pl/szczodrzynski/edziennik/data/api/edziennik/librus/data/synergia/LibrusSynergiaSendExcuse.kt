package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.synergia

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.greenrobot.eventbus.EventBus
import pl.szczodrzynski.edziennik.data.api.ERROR_LIBRUS_SYNERGIA_OTHER
import pl.szczodrzynski.edziennik.data.api.ERROR_REQUEST_FAILURE
import pl.szczodrzynski.edziennik.data.api.LIBRUS_SYNERGIA_URL
import pl.szczodrzynski.edziennik.data.api.LIBRUS_USER_AGENT
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.LibrusSynergia
import pl.szczodrzynski.edziennik.data.api.events.ExcuseSentEvent
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.ui.attendance.excuse.ExcuseMode
import pl.szczodrzynski.edziennik.ui.attendance.excuse.ExcuseRequest
import timber.log.Timber
import java.io.IOException

/**
 * Wysyła e-usprawiedliwienie tak jak formularz na /eusprawiedliwienia/dodaj
 * (zweryfikowane na prawdziwej wysyłce z przeglądarki 2026-11-13):
 * 1. GET  /eusprawiedliwienia/dodaj -> świeży requestkey
 * 2. POST /eusprawiedliwienia/dodaj, multipart/form-data -> 302 -> /eusprawiedliwienia
 *    z komunikatem "Usprawiedliwienie zostało wysłane."
 *
 * Tylko tryb "wybrane dni". Pola lekcji dla trybu "kilka godzin" nie są znane.
 * mhttp nie pozwala wymusić multipart bez pliku, dlatego POST idzie przez OkHttp
 * na tym samym kliencie (wspólny cookie jar z DZIENNIKSID).
 */
class LibrusSynergiaSendExcuse(
    override val data: DataLibrus,
    private val request: ExcuseRequest,
    val onSuccess: () -> Unit,
) : LibrusSynergia(data, null) {
    companion object {
        const val TAG = "LibrusSynergiaSendExcuse"
        private const val ENDPOINT = "eusprawiedliwienia/dodaj"
    }

    init {
        if (request.mode != ExcuseMode.DAYS) {
            data.error(ApiError(TAG, ERROR_LIBRUS_SYNERGIA_OTHER)
                .withThrowable(UnsupportedOperationException("Tryb ${request.mode} nie jest obsługiwany")))
        } else {
            synergiaGet(TAG, ENDPOINT) { page ->
                val form = LibrusSynergiaExcuseParser.parseFormInfo(page)
                if (form == null) {
                    data.error(ApiError(TAG, ERROR_LIBRUS_SYNERGIA_OTHER).withApiResponse(page))
                    return@synergiaGet
                }
                post(form.requestKey)
            }
        }
    }

    private fun post(requestKey: String) {
        // Kolejność i zestaw pól jak w przeglądarce. Pusty "attachment" też jest wysyłany.
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("requestkey", requestKey)
            .addFormDataPart("dodajUsprawiedliwienieDo", "1")
            .addFormDataPart("datyLubLekcje", request.mode.formValue)
            .addFormDataPart("dataOd", request.dateFrom.stringY_m_d)
            .addFormDataPart("dataDo", request.dateTo.stringY_m_d)
            .addFormDataPart("powodNieobecnosci", request.reason)
            .addFormDataPart("attachment", "",
                RequestBody.create(MediaType.parse("application/octet-stream"), ByteArray(0)))
            .apply {
                if (request.notifyTeachers)
                    addFormDataPart("powiadomInnych", "1")
            }
            .build()

        val httpRequest = Request.Builder()
            .url("$LIBRUS_SYNERGIA_URL/$ENDPOINT")
            .header("User-Agent", LIBRUS_USER_AGENT)
            .header("Origin", LIBRUS_SYNERGIA_URL)
            .header("Referer", "$LIBRUS_SYNERGIA_URL/$ENDPOINT")
            .post(body)
            .build()

        Timber.tag(TAG).d("Request: Librus/Synergia - POST $LIBRUS_SYNERGIA_URL/$ENDPOINT")

        // data.app.http podąża za przekierowaniem, więc dostajemy stronę listy.
        data.app.http.newCall(httpRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                data.error(ApiError(TAG, ERROR_REQUEST_FAILURE).withThrowable(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val text = try {
                    response.body()?.string().orEmpty()
                } catch (e: IOException) {
                    data.error(ApiError(TAG, ERROR_REQUEST_FAILURE).withThrowable(e))
                    return
                } finally {
                    response.close()
                }

                if (!LibrusSynergiaExcuseParser.isExcuseSent(text)) {
                    data.error(ApiError(TAG, ERROR_LIBRUS_SYNERGIA_OTHER).withApiResponse(text))
                    return
                }
                EventBus.getDefault().post(ExcuseSentEvent(profileId))
                onSuccess()
            }
        })
    }
}
