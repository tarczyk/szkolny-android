package pl.szczodrzynski.edziennik.ui.attendance.excuse

import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.AttendanceExcuseSendDialogBinding
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog
import pl.szczodrzynski.edziennik.ui.base.dialog.SimpleDialog
import pl.szczodrzynski.edziennik.utils.models.Date
import timber.log.Timber

/**
 * Odpowiednik ekranu Synergii "e-Usprawiedliwienia - wyślij usprawiedliwienie".
 * Walidacja odwzorowuje eusprawiedliwienia_rodzic.js (wyslijUsprawiedliwienie).
 */
class ExcuseSendDialog(
    activity: AppCompatActivity,
    private val repository: ExcuseRepository = ExcuseRepository.instance,
    private val onSent: () -> Unit = {},
) : BindingDialog<AttendanceExcuseSendDialogBinding>(activity) {

    override fun inflate(layoutInflater: LayoutInflater) =
        AttendanceExcuseSendDialogBinding.inflate(layoutInflater)

    override fun getTitleRes() = R.string.excuse_send_title
    override fun getPositiveButtonText() = R.string.excuse_send
    override fun getNegativeButtonText() = R.string.cancel

    private var formInfo: ExcuseFormInfo? = null
    private var dateFrom = Date.getToday()
    private var dateTo = Date.getToday()
    private val lessonChecks = mutableListOf<Pair<CheckBox, ExcuseLesson>>()
    private var sending = false

    private val mode
        get() = if (b.modeHours.isChecked) ExcuseMode.HOURS else ExcuseMode.DAYS

    override suspend fun onShow() {
        setSendEnabled(false)
        b.tutorInfo.isVisible = false

        b.modeGroup.setOnCheckedChangeListener { _, _ -> onModeChanged() }
        b.dateFrom.setOnClickListener { pickDate(dateFrom) { onDateFromChanged(it) } }
        b.dateTo.setOnClickListener { pickDate(dateTo) { dateTo = it; updateDates() } }
        b.attachmentButton.setOnClickListener {
            Toast.makeText(activity, R.string.excuse_attachment_todo, Toast.LENGTH_SHORT).show()
        }
        updateDates()

        try {
            val info = repository.getFormInfo()
            formInfo = info
            b.tutorInfo.text = activity.getString(R.string.excuse_tutor_info, info.tutorName)
            b.tutorInfo.isVisible = true
            b.form.isVisible = true
            setSendEnabled(true)
        } catch (e: Exception) {
            Timber.e(e)
            b.tutorInfo.text = activity.getString(R.string.excuse_error)
            b.tutorInfo.isVisible = true
        } finally {
            b.progressBar.isVisible = false
        }
    }

    // --- Zachowanie formularza (jak w JS Synergii) ---

    /** zmienDatyLubGodziny() */
    private fun onModeChanged() {
        val hours = mode == ExcuseMode.HOURS
        b.dateToLayout.isVisible = !hours
        b.dateFromLayout.hint = activity.getString(
            if (hours) R.string.excuse_date else R.string.excuse_date_from
        )
        b.lessonsContainer.isVisible = hours
        if (hours) {
            dateTo = dateFrom
            loadLessons()
        }
        updateDates()
    }

    /** $('#dataOd').on('change') ustawia też dataDo; potem zmienZakresDat() */
    private fun onDateFromChanged(date: Date) {
        dateFrom = date
        dateTo = date
        updateDates()
        if (mode == ExcuseMode.HOURS)
            loadLessons()
    }

    /** zmienZakresDat(): POST /eusprawiedliwienia_rodzic_godziny */
    private fun loadLessons() {
        val container = b.lessonsContainer
        container.removeAllViews()
        lessonChecks.clear()
        launch {
            val lessons = try {
                repository.getLessons(dateFrom)
            } catch (e: Exception) {
                Timber.e(e)
                emptyList()
            }
            if (lessons.isEmpty()) {
                container.addView(TextView(activity).apply { setText(R.string.excuse_no_lessons) })
                return@launch
            }
            lessons.forEach { lesson ->
                val check = CheckBox(activity).apply { text = lesson.label }
                container.addView(check)
                lessonChecks += check to lesson
            }
        }
    }

    /** ustawPolePowiadomNauczycieli(): tylko dla dat od dziś w przód */
    private fun updateNotifyField() {
        val lastDay = if (mode == ExcuseMode.DAYS) dateTo else dateFrom
        val isFuture = lastDay >= Date.getToday()
        b.notifyTeachers.isEnabled = isFuture
        if (!isFuture)
            b.notifyTeachers.isChecked = false
        b.notifyTeachersHint.setText(
            if (isFuture) R.string.excuse_notify_teachers_hint_future
            else R.string.excuse_notify_teachers_hint_past
        )
    }

    private fun updateDates() {
        b.dateFrom.setText(dateFrom.stringY_m_d)
        b.dateTo.setText(dateTo.stringY_m_d)
        updateNotifyField()
    }

    private fun pickDate(current: Date, onPicked: (Date) -> Unit) {
        MaterialDatePicker.Builder.datePicker()
            .setSelection(current.inMillisUtc)
            .build()
            .apply { addOnPositiveButtonClickListener { onPicked(Date.fromMillisUtc(it)) } }
            .show(activity.supportFragmentManager, "ExcuseSendDialog")
    }

    // --- Wysyłka ---

    /** wyslijUsprawiedliwienie() */
    override suspend fun onPositiveClick(): Boolean {
        if (sending) return NO_DISMISS
        val info = formInfo ?: return NO_DISMISS

        if (mode == ExcuseMode.DAYS && dateFrom > dateTo) {
            showError(R.string.excuse_error_date_range)
            return NO_DISMISS
        }
        val selectedLessons = lessonChecks.filter { it.first.isChecked }.map { it.second }
        if (mode == ExcuseMode.HOURS && selectedLessons.isEmpty()) {
            showError(R.string.excuse_error_no_lesson)
            return NO_DISMISS
        }

        // wyslijFormularzPoPotwierdzeniu(): pytanie tylko gdy #autoAkceptacja == 1
        if (info.autoAccept && !confirm()) return NO_DISMISS

        val request = ExcuseRequest(
            mode = mode,
            dateFrom = dateFrom,
            dateTo = if (mode == ExcuseMode.HOURS) dateFrom else dateTo,
            lessons = selectedLessons,
            reason = b.reason.text?.toString().orEmpty(),
            notifyTeachers = b.notifyTeachers.isChecked,
        )

        sending = true
        setSendEnabled(false)
        return try {
            repository.send(info, request)
            Toast.makeText(activity, R.string.excuse_sent, Toast.LENGTH_SHORT).show()
            onSent()
            DISMISS
        } catch (e: Exception) {
            Timber.e(e)
            showError(R.string.excuse_error)
            setSendEnabled(true)
            NO_DISMISS
        } finally {
            sending = false
        }
    }

    private suspend fun confirm(): Boolean {
        val dialog = SimpleDialog<Unit>(activity) {
            title(R.string.excuse_confirm_title)
            message(R.string.excuse_confirm_text)
            positive(R.string.yes)
            negative(R.string.no)
        }.showModal()
        return dialog.getButton() == AlertDialog.BUTTON_POSITIVE
    }

    private fun showError(message: Int) {
        launch {
            SimpleDialog<Unit>(activity) {
                title(R.string.error_occured)
                message(message)
                positive(R.string.ok)
            }.show()
        }
    }

    private fun setSendEnabled(enabled: Boolean) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = enabled
    }
}
