package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.synergia

import org.greenrobot.eventbus.EventBus
import pl.szczodrzynski.edziennik.data.api.POST
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.LibrusSynergia
import pl.szczodrzynski.edziennik.data.api.events.ExcuseListGetEvent
import pl.szczodrzynski.edziennik.ui.attendance.excuse.ExcuseStatusFilter
import pl.szczodrzynski.edziennik.utils.models.Date

/**
 * Pobiera listę e-usprawiedliwień, tak jak formularz filtra na /eusprawiedliwienia:
 * 1. GET  /eusprawiedliwienia  -> requestkey
 * 2. POST /eusprawiedliwienia  (requestkey, usprawiedliwienia_filtr=1, data_od, data_do, status)
 */
class LibrusSynergiaGetExcuses(
    override val data: DataLibrus,
    private val dateFrom: Date,
    private val dateTo: Date,
    private val status: ExcuseStatusFilter,
    val onSuccess: () -> Unit,
) : LibrusSynergia(data, null) {
    companion object {
        const val TAG = "LibrusSynergiaGetExcuses"
        private const val ENDPOINT = "eusprawiedliwienia"
    }

    init {
        synergiaGet(TAG, ENDPOINT) { page ->
            val requestKey = LibrusSynergiaExcuseParser.parseRequestKey(page)
            synergiaGet(TAG, ENDPOINT, method = POST, parameters = listOfNotNull(
                requestKey?.let { "requestkey" to it },
                "usprawiedliwienia_filtr" to 1,
                "data_od" to dateFrom.stringY_m_d,
                "data_do" to dateTo.stringY_m_d,
                "status" to status.value,
            ).toMap()) { text ->
                val excuses = LibrusSynergiaExcuseParser.parseExcuses(text)
                EventBus.getDefault().post(ExcuseListGetEvent(profileId, excuses))
                onSuccess()
            }
        }
    }
}
