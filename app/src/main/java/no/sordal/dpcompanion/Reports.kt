package no.sordal.dpcompanion

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class RecordStatus { OVERLAP, INCOMPLETE, OK }

data class ReviewFinding(val sessionId: String?, val description: String)

object ReportReview {
    fun sessionIssues(session: DpSession, tour: Tour, sessions: List<DpSession>): List<String> {
        val issues = mutableListOf<String>()
        val end = session.endMillis
        if (end != null && end > session.startMillis && sessions.any { other ->
                other.id != session.id && other.tourId == session.tourId && other.endMillis != null &&
                    other.endMillis > other.startMillis && session.startMillis < other.endMillis && other.startMillis < end
            }) issues += "Overlapping session — review times"
        if (end == null) issues += "Session still running"
        else if (end <= session.startMillis) issues += "Stop must be after start"
        if (session.activity.isBlank()) issues += "Activity not set"
        if (end != null && end > session.startMillis) {
            val zone = runCatching { ZoneId.of(tour.zoneId) }.getOrDefault(ZoneId.systemDefault())
            val first = runCatching { LocalDate.parse(tour.signedOn) }.getOrNull()
            val last = runCatching { LocalDate.parse(tour.disembarked) }.getOrNull()
            val from = Instant.ofEpochMilli(session.startMillis).atZone(zone).toLocalDate()
            val to = Instant.ofEpochMilli(end - 1).atZone(zone).toLocalDate()
            if (first == null) issues += "Signed-on date missing"
            else if (from.isBefore(first)) issues += "Outside service period: before signed on $first"
            if (last != null && to.isAfter(last)) issues += "Outside service period: after disembarked $last"
        }
        return issues
    }

    fun sessionStatus(session: DpSession, tour: Tour, sessions: List<DpSession>): RecordStatus {
        val issues = sessionIssues(session, tour, sessions)
        return when {
            issues.any { it.startsWith("Overlapping") } -> RecordStatus.OVERLAP
            issues.isNotEmpty() -> RecordStatus.INCOMPLETE
            else -> RecordStatus.OK
        }
    }

    fun findings(tour: Tour, sessions: List<DpSession>): List<ReviewFinding> {
        val own = sessions.filter { it.tourId == tour.id }
        val findings = own.flatMap { s -> sessionIssues(s, tour, own).map { ReviewFinding(s.id, it) } }.toMutableList()
        if (tour.signedOn.isBlank()) findings += ReviewFinding(null, "Signed-on date missing")
        if (tour.disembarked.isBlank()) findings += ReviewFinding(null, "Disembarked date missing; totals are provisional")
        val totals = DpMath.totals(tour, own)
        if (totals.issue != null && findings.none { it.description == totals.issue }) findings += ReviewFinding(null, totals.issue)
        return findings
    }

    fun activeDates(tour: Tour, sessions: List<DpSession>): List<LocalDate> {
        val zone = runCatching { ZoneId.of(tour.zoneId) }.getOrDefault(ZoneId.systemDefault())
        val millisByDate = mutableMapOf<LocalDate, Long>()
        for (s in sessions.filter { it.tourId == tour.id && it.endMillis != null && it.endMillis > it.startMillis }) {
            var cursor = Instant.ofEpochMilli(s.startMillis).atZone(zone)
            val end = Instant.ofEpochMilli(s.endMillis!!).atZone(zone)
            while (cursor.isBefore(end)) {
                val boundary = cursor.toLocalDate().plusDays(1).atStartOfDay(zone)
                val next = if (end.isBefore(boundary)) end else boundary
                val date = cursor.toLocalDate()
                millisByDate[date] = (millisByDate[date] ?: 0) + next.toInstant().toEpochMilli() - cursor.toInstant().toEpochMilli()
                cursor = next
            }
        }
        return millisByDate.filterValues { it >= 2L * 60 * 60 * 1000 }.keys.sorted()
    }
}

enum class ReportLayout(val title: String) {
    SUMMARY("Service period summary"),
    DETAILED("DP session report"),
    NEW_SCHEME("Confirmation letter — NI New Scheme"),
    OLD_SCHEME("Confirmation letter — NI Old Scheme / Revalidation"),
    IMCA("Confirmation letter — IMCA logbook")
}

data class ReportChoice(val vesselId: String, val tours: List<Tour>)

object ReportSelection {
    fun vessels(data: AppData): List<ReportChoice> = data.tours.groupBy { t ->
        t.vesselId.ifBlank { "legacy:${t.imo}:${t.vessel}" }
    }.map { (id, tours) -> ReportChoice(id, tours.sortedBy { it.signedOn }) }

    fun confirmationIssues(data: AppData, tours: List<Tour>, layout: ReportLayout, fullName: String, company: String, dob: String, grt: String): List<String> {
        val issues = mutableListOf<String>()
        if (fullName.isBlank()) issues += "Full name is required"
        if (company.isBlank()) issues += "Company name is required"
        if (runCatching { LocalDate.parse(dob) }.isFailure) issues += "Date of birth is required"
        if (grt.isBlank()) issues += "Gross tonnage is required"
        if (tours.isEmpty()) issues += "Select a vessel"
        tours.forEach { t ->
            if (t.imo.isBlank()) issues += "IMO number missing for ${t.vessel}"
            if (ReportReview.findings(t, data.sessions).isNotEmpty()) issues += "Review ${t.vessel} (${t.signedOn}) before drafting a letter"
            if (layout == ReportLayout.NEW_SCHEME && t.mode == "Continuous DP") issues += "New Scheme date layout needs individual DP days; choose IMCA layout for continuous-hour records"
            if (DpMath.totals(t, data.sessions).dpDays == null) issues += "DP days cannot be calculated for ${t.vessel}"
        }
        return issues.distinct()
    }
}
