package pl.szczodrzynski.edziennik.ui.attendance.excuse

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.AttendanceExcuseItemBinding

class ExcuseAdapter : RecyclerView.Adapter<ExcuseAdapter.ViewHolder>() {

    var items: List<Excuse> = emptyList()

    class ViewHolder(val b: AttendanceExcuseItemBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        AttendanceExcuseItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.b
        b.period.text = item.period
        b.status.text = listOfNotNull(item.status, item.statusComment).joinToString("\n")
        b.content.text = item.content
        b.details.text = b.root.context.getString(
            R.string.excuse_list_details,
            item.notifiedTeachers,
            item.sentDate,
            item.absenceCount,
        )
    }
}
