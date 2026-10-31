package pl.szczodrzynski.edziennik.data.api.events

import pl.szczodrzynski.edziennik.ui.attendance.excuse.Excuse

data class ExcuseListGetEvent(val profileId: Int, val excuses: List<Excuse>)
