package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.synergia

import org.jsoup.Jsoup
import pl.szczodrzynski.edziennik.ui.attendance.excuse.Excuse

/**
 * Parsuje stronę Synergii /eusprawiedliwienia (lista wysłanych e-usprawiedliwień).
 */
object LibrusSynergiaExcuseParser {

    private val STATUS_REGEX = """^(.*?)\s*\((.+)\)$""".toRegex()

    /** Ukryte pole formularza filtra, wymagane w POST. */
    fun parseRequestKey(html: String): String? =
        Jsoup.parse(html).selectFirst("input[name=requestkey]")?.attr("value")

    fun parseExcuses(html: String): List<Excuse> {
        val doc = Jsoup.parse(html)
        val table = doc.select("table.decorated").firstOrNull { table ->
            table.select("thead td").any { it.text().contains("Okres usprawiedliwienia") }
        } ?: return emptyList()

        return table.select("tbody > tr").mapNotNull { row ->
            val cells = row.children()
            // wiersz "brak danych" ma zwykle jedną komórkę z colspan
            if (cells.size < 7) return@mapNotNull null

            val statusText = cells[6].text().trim()
            val statusMatch = STATUS_REGEX.find(statusText)

            Excuse(
                period = cells[1].text().trim(),
                absenceCount = cells[2].text().trim().toIntOrNull() ?: 0,
                content = cells[3].text().trim(),
                notifiedTeachers = cells[4].text().trim(),
                sentDate = cells[5].text().trim(),
                status = statusMatch?.groupValues?.get(1) ?: statusText,
                statusComment = statusMatch?.groupValues?.get(2),
                hasAttachment = cells[0].children().isNotEmpty() || cells[0].text().isNotBlank(),
            )
        }
    }
}
