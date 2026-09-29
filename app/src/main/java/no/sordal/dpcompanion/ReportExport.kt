package no.sordal.dpcompanion

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
        val letter = layout == ReportLayout.NEW_SCHEME || layout == ReportLayout.OLD_SCHEME || layout == ReportLayout.IMCA
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
            result += "Name: ${data.fullName.ifBlank { "—" }}  |  DOB: ${pretty(data.dateOfBirth)}  |  Generated: ${pretty(LocalDate.now().toString())}"
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
            if (layout == ReportLayout.IMCA) result += "IMCA hours reference: logged hours / 2, capped at days on board. Personal draft, not an NI form."
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

    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    // Small OOXML writer: every field remains editable in Word and needs no network or Office SDK.
    fun writeDocx(data: AppData, tours: List<Tour>, layout: ReportLayout, details: LetterDetails, output: OutputStream) {
        val body = StringBuilder()
        fun paragraph(value: String, bold: Boolean = false) {
            body.append("<w:p><w:r><w:rPr>")
            if (bold) body.append("<w:b/>")
            body.append("</w:rPr><w:t xml:space=\"preserve\">").append(xml(value))
                .append("</w:t></w:r></w:p>")
        }
        fun table(headers: List<String>, rows: List<List<String>>) {
            body.append("<w:tbl><w:tblPr><w:tblBorders><w:bottom w:val=\"single\" w:sz=\"4\"/><w:insideH w:val=\"single\" w:sz=\"4\"/></w:tblBorders></w:tblPr>")
            for (row in listOf(headers) + rows) {
                body.append("<w:tr>")
                row.forEach { cell -> body.append("<w:tc><w:p><w:r><w:t xml:space=\"preserve\">")
                    .append(xml(cell)).append("</w:t></w:r></w:p></w:tc>") }
                body.append("</w:tr>")
            }
            body.append("</w:tbl>")
        }
        val ni = layout == ReportLayout.NEW_SCHEME || layout == ReportLayout.OLD_SCHEME
        if (ni) {
            paragraph("DRAFT — COMPANY VERIFICATION REQUIRED", true)
            paragraph(details.company.ifBlank { "[Company headed paper]" })
            if (details.companyAddress.isNotBlank()) paragraph(details.companyAddress)
            paragraph(pretty(LocalDate.now().toString()))
            paragraph("DP Department, The Nautical Institute, 202 Lambeth Road, London SE1 7LQ, United Kingdom")
            paragraph("Application for Revalidation of a DP Certificate — Offshore ${if (layout == ReportLayout.NEW_SCHEME) "New" else "Old"} Scheme", true)
            paragraph("We hereby certify that ${data.fullName} (DOB: ${pretty(details.dateOfBirth)}) is employed by ${details.company} as a ${tours.firstOrNull()?.rank.orEmpty()} / ${tours.firstOrNull()?.capacity.orEmpty()} on board our vessels.")
            paragraph("The company must check the DP sea time below against vessel deck logs, DP logs and its own records before signing. Only active DP time is claimed for revalidation.")
            if (layout == ReportLayout.NEW_SCHEME) paragraph("For each listed active date on DP, the applicant performed DP duties for a minimum of two hours. The dates are broken down by individual service period.")
            else paragraph("The days on DP below are listed by individual service period. For revalidation after 1 January 2015, confirm at least two hours on DP per claimed day.")
            val headers = listOf("Vessel name", "GRT", "IMO No.", "DP class", "From", "To", "Days on DP", "Rank")
            val rows = tours.sortedBy { it.signedOn }.map { t ->
                val total = DpMath.totals(t, data.sessions)
                listOf(t.vessel, details.grossTonnage, t.imo, t.dpClass, pretty(t.signedOn), pretty(t.disembarked), total.dpDays?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.2f".format(java.util.Locale.US, it) } ?: "REVIEW", t.rank)
            }
            table(headers, rows)
            if (layout == ReportLayout.NEW_SCHEME) tours.sortedBy { it.signedOn }.forEach { t ->
                paragraph("Active dates on DP — ${t.vessel}, ${pretty(t.signedOn)} to ${pretty(t.disembarked)}:", true)
                paragraph(ReportReview.activeDates(t, data.sessions).joinToString(", ") { pretty(it.toString()) }.ifBlank { "None recorded" })
                paragraph("Passive dates on DP: [Company to confirm, if applicable]")
            }
            paragraph("This letter is provided in support of the applicant's DP certificate revalidation.")
            paragraph("Yours faithfully")
            paragraph("[Signatory's name and job title]  [Direct contact details]")
            paragraph("[Signature and company stamp]  [Date]")
        } else {
            lines(data, tours, layout, details).forEach { paragraph(it, it == "SEA SERVICE SUMMARY" || it == "DP SESSION REPORT") }
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1000" w:right="850" w:bottom="1000" w:left="850"/></w:sectPr></w:body></w:document>"""
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, contents: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(contents.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            entry("word/document.xml", document)
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
