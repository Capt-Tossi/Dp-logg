package no.sordal.dpcompanion

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LetterDetails(val company: String, val companyAddress: String, val dateOfBirth: String, val grossTonnage: String)

object ReportExport {
    private val date = DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)
    private fun pretty(raw: String) = runCatching { LocalDate.parse(raw).format(date) }.getOrDefault(raw.ifBlank { "—" })
    private fun stamp(millis: Long, zoneId: String): String {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
        return Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", java.util.Locale.ENGLISH))
    }

    fun lines(data: AppData, tours: List<Tour>, layout: ReportLayout, details: LetterDetails): List<String> {
        val result = mutableListOf<String>()
        val letter = layout == ReportLayout.NEW_SCHEME || layout == ReportLayout.IMCA
        if (letter) {
            result += "DRAFT — FOR COMPANY REVIEW AND SIGNATURE"
            result += "DP SEA TIME CONFIRMATION LETTER"
            result += "${details.company}  |  ${pretty(LocalDate.now().toString())}"
            result += details.companyAddress
            result += "To: DP Department, The Nautical Institute"
            result += "Applicant: ${data.fullName}  |  Date of birth: ${pretty(details.dateOfBirth)}"
            result += "Certificate number: ${data.certificateNumber.ifBlank { "—" }}"
            result += "This draft lists DP time recorded by the applicant. The company must check it against its records and the signed logbook before confirming it."
            result += ""
        } else {
            result += if (layout == ReportLayout.SUMMARY) "SEA SERVICE SUMMARY" else "DP SESSION REPORT"
            result += "Name: ${data.fullName.ifBlank { "—" }}  |  Generated: ${pretty(LocalDate.now().toString())}"
            result += ""
        }
        tours.sortedBy { it.signedOn }.forEach { tour ->
            val sessions = data.sessions.filter { it.tourId == tour.id }.sortedBy { it.startMillis }
            val totals = DpMath.totals(tour, data.sessions)
            result += "Vessel: ${tour.vessel}  |  IMO: ${tour.imo}  |  ${tour.dpClass}  |  GT: ${details.grossTonnage.ifBlank { "—" }}"
            result += "Signed on: ${pretty(tour.signedOn)}  |  Disembarked: ${pretty(tour.disembarked)}"
            result += "Rank: ${tour.rank}  |  DP capacity: ${tour.capacity}"
            result += "DP mode: ${if (tour.mode == "Continuous DP") "Continuous" else "Normal"}"
            if (tour.mode == "Continuous DP" && tour.dutyHours > 0) result += "Watch period: ${tour.dutyHours} hours (reference only)"
            result += "Logged DP hours: ${totals.loggedHours}  |  DP days: ${totals.dpDays?.let { "%.2f".format(java.util.Locale.US, it) } ?: "—"}  |  Days on board: ${totals.daysOnBoard ?: "—"}"
            if (layout == ReportLayout.NEW_SCHEME) {
                result += "Active dates on DP (minimum 2 hours on each date):"
                val days = ReportReview.activeDates(tour, sessions)
                result += if (days.isEmpty()) "—" else days.joinToString(", ") { pretty(it.toString()) }
            }
            if (layout == ReportLayout.IMCA) result += "IMCA/DPVOA hours basis: logged hours / 2, capped at days on board."
            if (layout == ReportLayout.DETAILED) {
                result += "DP sessions:"
                sessions.forEach { s -> result += "${stamp(s.startMillis, tour.zoneId)} – ${s.endMillis?.let { stamp(it, tour.zoneId) } ?: "ACTIVE"}  |  ${s.endMillis?.let { DpMath.loggedHours(s.startMillis, it) } ?: 0} h  |  ${s.activity.ifBlank { "Activity not set" }}" }
            }
            val findings = ReportReview.findings(tour, data.sessions)
            if (findings.isNotEmpty()) result += "Review required: ${findings.joinToString("; ") { it.description }}"
            result += ""
        }
        if (letter) {
            result += "After checking the above against company records and the signed logbook, the undersigned may confirm the verified DP sea time. Only active DP time is claimed."
            result += "Company representative: ______________________________"
            result += "Position: ____________________  Date: ____________________"
            result += "Signature and company stamp: __________________________"
            result += "Applicant-generated draft. The employer must verify all details before signing."
        } else result += "Personal working record. Check against the signed NI/IMCA logbook."
        return result
    }

    fun writeCsv(data: AppData, tours: List<Tour>, output: OutputStream) {
        fun row(vararg values: String): String = values.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } + "\r\n"
        output.writer(Charsets.UTF_8).use { writer ->
            writer.write(row("Vessel", "IMO", "Signed on", "Disembarked", "Rank", "DP capacity", "Mode", "Start", "Stop", "Logged hours", "Activity", "Location", "Notes", "Status"))
            tours.sortedBy { it.signedOn }.forEach { t ->
                data.sessions.filter { it.tourId == t.id }.sortedBy { it.startMillis }.forEach { s ->
                    writer.write(row(t.vessel, t.imo, t.signedOn, t.disembarked, t.rank, t.capacity, t.mode,
                        stamp(s.startMillis,t.zoneId), s.endMillis?.let { stamp(it,t.zoneId) } ?: "",
                        (s.endMillis?.let { DpMath.loggedHours(s.startMillis,it) } ?: 0).toString(), s.activity,s.location,s.notes,
                        ReportReview.sessionStatus(s,t,data.sessions).name))
                }
            }
        }
    }

    fun writePdf(lines: List<String>, output: OutputStream) {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 10f }
        var page: PdfDocument.Page? = null
        var y = 0f
        var pageNumber = 0
        fun nextPage() {
            page?.let { document.finishPage(it) }
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber).create())
            y = 46f
        }
        try {
            nextPage()
            lines.forEach { line ->
                if (line.isEmpty()) { y += 12f; return@forEach }
                var remaining = line
                while (remaining.isNotEmpty()) {
                    if (y > 797f) nextPage()
                    val count = paint.breakText(remaining, true, 505f, null).coerceAtLeast(1)
                    val breakAt = if (count < remaining.length) remaining.lastIndexOf(' ', count).takeIf { it > 0 } ?: count else count
                    page!!.canvas.drawText(remaining.take(breakAt), 45f, y, paint)
                    y += 15f
                    remaining = remaining.drop(breakAt).trimStart()
                }
            }
            page?.let { document.finishPage(it) }
            document.writeTo(output)
        } finally { document.close() }
    }
}
