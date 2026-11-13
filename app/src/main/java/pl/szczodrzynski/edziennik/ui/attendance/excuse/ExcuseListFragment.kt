package pl.szczodrzynski.edziennik.ui.attendance.excuse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.AttendanceExcuseFragmentBinding
import pl.szczodrzynski.edziennik.ui.base.lazypager.LazyFragment
import pl.szczodrzynski.edziennik.utils.models.Date
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

/**
 * Odpowiednik ekranu Synergii "e-Usprawiedliwienia" (/eusprawiedliwienia).
 */
class ExcuseListFragment : LazyFragment(), CoroutineScope {

    private lateinit var activity: MainActivity
    private lateinit var b: AttendanceExcuseFragmentBinding

    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val repository by lazy { ExcuseRepository.get(activity, App.profileId) }
    private val adapter = ExcuseAdapter()

    // Domyślnie bieżący miesiąc, jak w Synergii
    private var dateFrom = Date.getToday().clone().setDay(1)
    private var dateTo = Date.getToday()
    private var status = ExcuseStatusFilter.ALL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as MainActivity?) ?: return null
        context ?: return null
        b = AttendanceExcuseFragmentBinding.inflate(inflater)
        return b.root
    }

    override fun onPageCreated(): Boolean {
        if (!isAdded) return false
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
        return true
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
            val result = runCatching { repository.getExcuses(dateFrom, dateTo, status) }
            if (!isAdded) return@launch
            val items = result.getOrElse { emptyList() }
            adapter.items = items
            adapter.notifyDataSetChanged()
            b.progressBar.isVisible = false
            b.list.isVisible = items.isNotEmpty()
            b.noData.isVisible = items.isEmpty()
            result.exceptionOrNull()?.let { e ->
                Timber.e(e)
                b.noData.text = getString(R.string.excuse_load_error, e.message ?: e.toString())
            } ?: b.noData.setText(R.string.excuse_no_data)
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
