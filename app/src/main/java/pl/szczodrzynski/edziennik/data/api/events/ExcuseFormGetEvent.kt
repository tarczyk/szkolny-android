package pl.szczodrzynski.edziennik.data.api.events

import pl.szczodrzynski.edziennik.ui.attendance.excuse.ExcuseFormInfo

data class ExcuseFormGetEvent(val profileId: Int, val form: ExcuseFormInfo)
