package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.synergia

import org.greenrobot.eventbus.EventBus
import pl.szczodrzynski.edziennik.data.api.ERROR_LIBRUS_SYNERGIA_OTHER
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.LibrusSynergia
import pl.szczodrzynski.edziennik.data.api.events.ExcuseFormGetEvent
import pl.szczodrzynski.edziennik.data.api.models.ApiError

/** GET /eusprawiedliwienia/dodaj: wychowawca, requestkey, autoAkceptacja. */
class LibrusSynergiaGetExcuseForm(
    override val data: DataLibrus,
    val onSuccess: () -> Unit,
) : LibrusSynergia(data, null) {
    companion object {
        const val TAG = "LibrusSynergiaGetExcuseForm"
    }

    init {
        synergiaGet(TAG, "eusprawiedliwienia/dodaj") { text ->
            val form = LibrusSynergiaExcuseParser.parseFormInfo(text)
            if (form == null) {
                data.error(ApiError(TAG, ERROR_LIBRUS_SYNERGIA_OTHER).withApiResponse(text))
                return@synergiaGet
            }
            EventBus.getDefault().post(ExcuseFormGetEvent(profileId, form))
            onSuccess()
        }
    }
}
