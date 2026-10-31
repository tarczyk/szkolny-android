package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.synergia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrusSynergiaExcuseParserTest {

    // Struktura jak na /eusprawiedliwienia, dane zmyślone.
    private fun page(rows: String) = """
        <html><body>jesteś zalogowany jako: <b>Jan Testowy (rodzic)</b>
        <form method="POST">
            <input type="hidden" name="requestkey" value="abc123">
            <input type="hidden" name="usprawiedliwienia_filtr" value="1">
        </form>
        <table class="decorated stretch center">
            <thead><tr>
                <td>Załącznik</td><td>Okres usprawiedliwienia</td>
                <td>Liczba usprawiedliwionych nieobecności</td><td>Treść</td>
                <td>Powiadomieni nauczyciele</td><td class="medium">Data wysłania</td><td>Status</td>
            </tr></thead>
            <tbody>$rows</tbody>
            <tfoot><tr><td colspan="7">&nbsp;</td></tr></tfoot>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun parsesRequestKey() {
        assertEquals("abc123", LibrusSynergiaExcuseParser.parseRequestKey(page("")))
    }

    @Test
    fun parsesRowWithStatusAuthor() {
        val html = page("""
            <tr class="line0">
                <td> </td>
                <td><b>2026-10-07, lekcja: 1</b></td>
                <td class="right"> 1 </td>
                <td> Proszę o usprawiedliwienie. </td>
                <td> Nowak Anna, Kowalski Jan </td>
                <td> 2026-10-07 13:24:21 </td>
                <td> usprawiedliwione (Nowak Anna) </td>
            </tr>
        """)
        val result = LibrusSynergiaExcuseParser.parseExcuses(html)

        assertEquals(1, result.size)
        with(result[0]) {
            assertEquals("2026-10-07, lekcja: 1", period)
            assertEquals(1, absenceCount)
            assertEquals("Proszę o usprawiedliwienie.", content)
            assertEquals("Nowak Anna, Kowalski Jan", notifiedTeachers)
            assertEquals("2026-10-07 13:24:21", sentDate)
            assertEquals("usprawiedliwione", status)
            assertEquals("Nowak Anna", statusComment)
            assertFalse(hasAttachment)
        }
    }

    @Test
    fun parsesStatusWithoutAuthorAndAttachment() {
        val html = page("""
            <tr><td><a href="#"><img src="x.png"></a></td><td><b>2026-10-01 - 2026-10-03</b></td>
            <td>12</td><td>Choroba</td><td>Nowak Anna</td><td>2026-10-04 08:00:00</td><td>wysłane</td></tr>
        """)
        val excuse = LibrusSynergiaExcuseParser.parseExcuses(html).single()

        assertEquals("wysłane", excuse.status)
        assertNull(excuse.statusComment)
        assertEquals(12, excuse.absenceCount)
        assertTrue(excuse.hasAttachment)
    }

    @Test
    fun skipsEmptyRowAndMissingTable() {
        assertTrue(LibrusSynergiaExcuseParser.parseExcuses(
            page("""<tr><td colspan="7">Brak danych</td></tr>""")
        ).isEmpty())
        assertTrue(LibrusSynergiaExcuseParser.parseExcuses("<html></html>").isEmpty())
    }

    private fun formPage(autoAccept: String) = """
        <html><body>jesteś zalogowany
        <div class="warning-box information medium">
            <div class="warning-content">
                <p>Usprawiedliwienie zostanie wysłane do wychowawcy: <b>Nowak Anna.</b></p>
            </div>
        </div>
        <input type="hidden" id="autoAkceptacja" value="$autoAccept">
        <form method="POST" id="formUsprawiedliwienie" enctype="multipart/form-data">
            <input type="hidden" name="requestkey" value="key42">
            <input type="hidden" name="dodajUsprawiedliwienieDo" value="1">
        </form>
        </body></html>
    """.trimIndent()

    @Test
    fun parsesFormInfo() {
        val form = LibrusSynergiaExcuseParser.parseFormInfo(formPage("0"))!!
        assertEquals("Nowak Anna", form.tutorName)
        assertEquals("key42", form.requestKey)
        assertFalse(form.autoAccept)
        assertTrue(LibrusSynergiaExcuseParser.parseFormInfo(formPage("1"))!!.autoAccept)
    }

    @Test
    fun returnsNullWithoutForm() {
        assertNull(LibrusSynergiaExcuseParser.parseFormInfo("<html><body>stop.png</body></html>"))
    }
}
