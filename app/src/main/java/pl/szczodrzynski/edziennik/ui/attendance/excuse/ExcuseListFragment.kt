package pl.szczodrzynski.edziennik.ui.attendance.excuse

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.databinding.AttendanceExcuseFragmentBinding
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment
import pl.szczodrzynski.edziennik.utils.models.Date
import timber.log.Timber

/**
 * Odpowiednik ekranu Synergii "e-Usprawiedliwienia" (/eusprawiedliwienia).
 */
class ExcuseListFragment : BaseFragment<AttendanceExcuseFragmentBinding, MainActivity>(
    inflater = AttendanceExcuseFragmentBinding::inflate,
) {

    private val repository = ExcuseRepository.instance
    private val adapter = ExcuseAdapter()

    // Domyślnie bieżący miesiąc, jak w Synergii
    private var dateFrom = Date.getToday().clone().setDay(1)
    private var dateTo = Date.getToday()
    private var status = ExcuseStatusFilter.ALL

    override fun getScrollingView() = b.list

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        b.list.layoutManager = LinearLayoutManager(activity)
        b.list.adapter = adapter

        val statuses = ExcuseStatusFilter.values()
        b.status.setAdapter(ArrayAdapter(activity, android.R.layout.simple_list_item_1, statuses.map { it.label }))
        b.status.setText(status.label, false)
        b.status.setOnItemClickListener { _, _, position, _ -> status = statuses[position] }

        b.dateFrom.setOnClickListener { pickDate(dateFrom) { dateFrom = it; updateDates() } }
        b.dateTo.setOnClickListener { pickDate(dateTo) { dateTo = it; updateDates() } }
        b.filterButton.setOnClickListener { load() }
        b.sendButton.setOnClickListener {
            ExcuseSendDialog(activity, repository, onSent = ::load).show()
        }

        updateDates()
        load()
    }

    private fun updateDates() {
        b.dateFrom.setText(dateFrom.stringY_m_d)
        b.dateTo.setText(dateTo.stringY_m_d)
    }

    private fun load() {
        b.progressBar.isVisible = true
        b.noData.isVisible = false
        b.list.isVisible = false
        launch {
            val items = try {
                repository.getExcuses(dateFrom, dateTo, status)
            } catch (e: Exception) {
                Timber.e(e)
                emptyList()
            }
            if (!isAdded) return@launch
            adapter.items = items
            adapter.notifyDataSetChanged()
            b.progressBar.isVisible = false
            b.list.isVisible = items.isNotEmpty()
            b.noData.isVisible = items.isEmpty()
        }
    }

    private fun pickDate(current: Date, onPicked: (Date) -> Unit) {
        MaterialDatePicker.Builder.datePicker()
            .setSelection(current.inMillisUtc)
            .build()
            .apply { addOnPositiveButtonClickListener { onPicked(Date.fromMillisUtc(it)) } }
            .show(activity.supportFragmentManager, "ExcuseListFragment")
    }
}
