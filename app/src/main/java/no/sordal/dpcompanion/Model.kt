package no.sordal.dpcompanion

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.net.URLEncoder
import java.util.UUID
import java.util.Locale

data class Tour(
    val id: String = UUID.randomUUID().toString(),
    val vessel: String = "", val imo: String = "", val vesselType: String = "PSV",
    val dpClass: String = "DP2", val rank: String = "Master", val capacity: String = "Senior DPO / DP Master",
    val signedOn: String = "", val disembarked: String = "",
    val mode: String = "Short operations", val dutyHours: Double = 0.0,
    val zoneId: String = ZoneId.systemDefault().id,
    val vesselId: String = "", val dpSystem: String = "", val grossTonnage: String = "",
    val scheme: String = "Offshore DP"
)

data class Vessel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "", val imo: String = "", val type: String = "PSV", val dpClass: String = "DP2",
    val dpSystem: String = "", val grossTonnage: String = ""
)

data class DpSession(
    val id: String = UUID.randomUUID().toString(), val tourId: String,
    val startMillis: Long, val endMillis: Long? = null,
    val activity: String = "", val location: String = "", val notes: String = "",
    val originalStartMillis: Long = startMillis, val originalEndMillis: Long? = endMillis,
    val corrections: List<Correction> = emptyList()
)

data class Correction(val changedAtMillis: Long, val previousStartMillis: Long, val previousEndMillis: Long?, val reason: String)
data class CpdEntry(val id: String = UUID.randomUUID().toString(), val title: String, val completedDate: String, val kind: String = "CPD")
data class Attachment(val id: String = UUID.randomUUID().toString(), val ownerId: String, val category: String, val fileName: String, val createdAtMillis: Long, val rotationDegrees: Int = 0)
data class AppData(
    val tours: List<Tour> = emptyList(), val sessions: List<DpSession> = emptyList(),
    val cpd: List<CpdEntry> = emptyList(), val attachments: List<Attachment> = emptyList(),
    val activeTourId: String? = null, val certificateNumber: String = "", val certificateExpiry: String = "",
    val cpd6Completed: Boolean = false,
    val vessels: List<Vessel> = emptyList(), val fullName: String = "", val lastName: String = "",
    val preferredRank: String = "Master", val preferredCapacity: String = "Senior DPO / DP Master",
    val certificateIssue: String = "", val dateOfBirth: String = "",
    val renewalReminderEnabled: Boolean = false
)

fun AppData.vesselName(tour: Tour): String = when {
    tour.vesselId.isBlank() -> tour.vessel.ifBlank { "Missing vessel info" } // Legacy tour from v0.1.
    vessels.none { it.id == tour.vesselId } -> "Missing vessel info"
    else -> tour.vessel.ifBlank { vessels.first { it.id == tour.vesselId }.name.ifBlank { "Missing vessel info" } }
}

fun AppData.newTour(vessel: Vessel): Tour = Tour(
    vesselId = vessel.id, vessel = vessel.name, imo = vessel.imo, vesselType = vessel.type,
    dpClass = vessel.dpClass, dpSystem = vessel.dpSystem, grossTonnage = vessel.grossTonnage,
    rank = preferredRank, capacity = preferredCapacity
)

fun AppData.reassignTourVessel(tourId: String, vesselId: String): AppData {
    val vessel = vessels.firstOrNull { it.id == vesselId } ?: return this
    if (tours.none { it.id == tourId }) return this
    return copy(tours = tours.map { tour -> if (tour.id != tourId) tour else tour.copy(
        vesselId = vessel.id, vessel = vessel.name, imo = vessel.imo, vesselType = vessel.type,
        dpClass = vessel.dpClass, dpSystem = vessel.dpSystem, grossTonnage = vessel.grossTonnage
    ) })
}

fun AppData.removeTour(tourId: String): AppData {
    if (tours.none { it.id == tourId }) return this
    val removedSessions = sessions.filter { it.tourId == tourId }.map { it.id }.toSet()
    val remainingTours = tours.filterNot { it.id == tourId }
    val nextActive = if (activeTourId != tourId) activeTourId
        else remainingTours.maxWithOrNull(compareBy<Tour> { it.signedOn }.thenBy { it.id })?.id
    return copy(tours = remainingTours, sessions = sessions.filterNot { it.id in removedSessions },
        attachments = attachments.filterNot { it.ownerId == tourId || it.ownerId in removedSessions },
        activeTourId = nextActive)
}

object SessionDisplay {
    private val dateTime = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun range(session: DpSession, tour: Tour): String {
        val zone = runCatching { ZoneId.of(tour.zoneId) }.getOrDefault(ZoneId.systemDefault())
        val start = Instant.ofEpochMilli(session.startMillis).atZone(zone)
        val end = session.endMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
            ?: return "${start.format(dateTime)} · Ongoing"
        return if (tour.mode != "Continuous DP" && start.toLocalDate() == end.toLocalDate())
            "${start.format(dateTime)}–${end.format(time)}"
        else "${start.format(dateTime)} – ${end.format(dateTime)}"
    }
}

object CertificateRenewal {
    fun openingDate(expiry: String): LocalDate? = runCatching { LocalDate.parse(expiry).minusMonths(6) }.getOrNull()
    fun status(expiry: String, today: LocalDate = LocalDate.now()): String {
        val date = runCatching { LocalDate.parse(expiry) }.getOrNull() ?: return "Set expiry date"
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days < 0 -> "Expired ${-days} ${if (days == -1L) "day" else "days"} ago"
            else -> "$days ${if (days == 1L) "day" else "days"} to expire"
        }
    }
    fun message(expiry: String, today: LocalDate = LocalDate.now()): String {
        val date = runCatching { LocalDate.parse(expiry) }.getOrNull() ?: return ""
        if (today.isAfter(date)) return "Certificate expired"
        return if (today.isBefore(date.minusMonths(6)))
            "You can apply for renewal from ${date.minusMonths(6).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))}"
        else "Renewal applications are open"
    }
}

object NiVerification {
    const val formUrl = "https://www.nialexisplatform.org/certification/dynamic-positioning/verify-dp-certificate/"
    fun url(certificateNumber: String, lastName: String): String {
        val number = certificateNumber.trim()
        val surname = lastName.trim()
        if (number.isEmpty() || surname.isEmpty()) return formUrl
        val cid = URLEncoder.encode("$number-$surname", "UTF-8")
        return "https://dp.nialexisplatform.org/VerifyCertificate/WebPage?code=1&cid=$cid"
    }
}

data class TourTotals(val loggedHours: Int, val dpDays: Double?, val daysOnBoard: Long?, val issue: String?, val provisional: Boolean = false)

object DpMath {
    // Keep source timestamps untouched. This is the workbook's inclusive-minute whole-hour convention.
    fun loggedHours(startMillis: Long, endMillis: Long): Int {
        if (endMillis <= startMillis) return 0
        val startMinute = Math.floorDiv(startMillis, 60_000L)
        val endMinute = Math.floorDiv(endMillis, 60_000L)
        return Math.floorDiv(endMinute - startMinute + 1L, 60L).coerceAtLeast(0).toInt()
    }

    fun totals(tour: Tour, sessions: List<DpSession>, today: LocalDate? = null): TourTotals {
        val completed = sessions.filter { it.tourId == tour.id && it.endMillis != null && it.endMillis > it.startMillis }
        val hours = completed.sumOf { loggedHours(it.startMillis, it.endMillis!!) }
        val signed = tour.signedOn.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val zone = runCatching { ZoneId.of(tour.zoneId) }.getOrElse { ZoneId.systemDefault() }
        val provisional = tour.disembarked.isBlank()
        val left = if (provisional) today ?: LocalDate.now(zone)
            else runCatching { LocalDate.parse(tour.disembarked) }.getOrNull()
        if (signed == null || left == null || left.isBefore(signed))
            return TourTotals(hours, null, null, "Set valid signed-on and disembark dates")
        val days = ChronoUnit.DAYS.between(signed, left) + 1L
        if (completed.any { s ->
                Instant.ofEpochMilli(s.startMillis).atZone(zone).toLocalDate().isBefore(signed) ||
                    Instant.ofEpochMilli(s.endMillis!! - 1).atZone(zone).toLocalDate().isAfter(left)
            }) return TourTotals(hours, null, days, "Session outside service period dates")
        val sorted = completed.sortedBy { it.startMillis }
        if (sorted.zipWithNext().any { (a, b) -> a.endMillis!! > b.startMillis })
            return TourTotals(hours, null, days, "Overlapping DP sessions need review")
        if (tour.mode == "Continuous DP") {
            return TourTotals(hours, (hours / 2.0).coerceAtMost(days.toDouble()), days, null, provisional)
        }
        // Sum elapsed time on each calendar date before applying the minimum 2 h rule.
        // Flooring each session first would lose partial hours across separate sessions.
        val millisByDay = mutableMapOf<LocalDate, Long>()
        for (session in completed) {
            var cursor = Instant.ofEpochMilli(session.startMillis).atZone(zone)
            val end = Instant.ofEpochMilli(session.endMillis!!).atZone(zone)
            while (cursor.isBefore(end)) {
                val midnight = cursor.toLocalDate().plusDays(1).atStartOfDay(zone)
                val partEnd = if (end.isBefore(midnight)) end else midnight
                val elapsed = partEnd.toInstant().toEpochMilli() - cursor.toInstant().toEpochMilli()
                millisByDay[cursor.toLocalDate()] = (millisByDay[cursor.toLocalDate()] ?: 0L) + elapsed
                cursor = partEnd
            }
        }
        val shortDays = millisByDay.count { (date, elapsed) -> !date.isBefore(signed) && !date.isAfter(left) && elapsed >= 2L * 60L * 60L * 1000L }
        return TourTotals(hours, shortDays.toDouble().coerceAtMost(days.toDouble()), days, null, provisional)
    }
}
