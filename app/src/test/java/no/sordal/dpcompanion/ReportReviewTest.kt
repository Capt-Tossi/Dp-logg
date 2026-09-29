package no.sordal.dpcompanion

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReportReviewTest {
    private fun at(day: Int, hour: Int) = LocalDate.of(2026,9,day).atTime(hour,0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
    private val tour = Tour(id="t",vessel="Example",imo="1234567",signedOn="2026-09-28",disembarked="2026-09-30",zoneId="UTC")

    @Test fun overlappingSessionsAreRedEvenWhenActivityIsMissing() {
        val a = DpSession(id="a",tourId="t",startMillis=at(28,8),endMillis=at(28,12),activity="")
        val b = DpSession(id="b",tourId="t",startMillis=at(28,11),endMillis=at(28,14),activity="Cargo transfer")
        assertEquals(RecordStatus.OVERLAP,ReportReview.sessionStatus(a,tour,listOf(a,b)))
        assertEquals(RecordStatus.OVERLAP,ReportReview.sessionStatus(b,tour,listOf(a,b)))
    }

    @Test fun incompleteAndValidAreDistinct() {
        val a = DpSession(tourId="t",startMillis=at(28,8),endMillis=at(28,11),activity="")
        val b = DpSession(tourId="t",startMillis=at(28,12),endMillis=at(28,15),activity="Cargo transfer")
        assertEquals(RecordStatus.INCOMPLETE,ReportReview.sessionStatus(a,tour,listOf(a,b)))
        assertEquals(RecordStatus.OK,ReportReview.sessionStatus(b,tour,listOf(a,b)))
        assertTrue(ReportReview.findings(tour,listOf(a,b)).any { it.description == "Activity not set" })
    }

    @Test fun confirmationIsBlockedUntilDatesAndHoursAreReviewable() {
        val a = DpSession(tourId="t",startMillis=at(28,8),endMillis=at(28,11),activity="Cargo transfer")
        val clean = AppData(tours=listOf(tour),sessions=listOf(a),fullName="Example Officer")
        assertTrue(ReportSelection.confirmationIssues(clean,listOf(tour),ReportLayout.NEW_SCHEME,"Example Officer","Shipping Co","1990-01-01","2000").isEmpty())
        val incomplete = clean.copy(sessions=listOf(a.copy(activity="")))
        assertTrue(ReportSelection.confirmationIssues(incomplete,listOf(tour),ReportLayout.NEW_SCHEME,"Example Officer","Shipping Co","1990-01-01","2000").isNotEmpty())
    }

    @Test fun newSchemeIncludesIndividualDatesAndRejectsContinuousMode() {
        val a = DpSession(tourId="t",startMillis=at(28,8),endMillis=at(28,11),activity="Cargo transfer")
        assertEquals(listOf(LocalDate.of(2026,9,28)),ReportReview.activeDates(tour,listOf(a)))
        val continuous = tour.copy(mode="Continuous DP",dutyHours=2.0)
        assertTrue(ReportSelection.confirmationIssues(AppData(sessions=listOf(a)),listOf(continuous),ReportLayout.NEW_SCHEME,"A B","Co","1990-01-01","1000")
            .any { it.contains("IMCA draft") })
    }

    @Test fun deletedCatalogVesselStillAppearsForExport() {
        val saved = tour.copy(vesselId="gone")
        val result = ReportSelection.vessels(AppData(tours=listOf(saved)))
        assertEquals(1,result.size)
        assertEquals(saved,result.first().tours.first())
    }

    @Test fun orangeCardExplainsOutsideDatesAndMissingActivity() {
        val s = DpSession(tourId="t",startMillis=at(27,8),endMillis=at(27,12),activity="")
        val issues = ReportReview.sessionIssues(s,tour,listOf(s))
        assertEquals(RecordStatus.INCOMPLETE,ReportReview.sessionStatus(s,tour,listOf(s)))
        assertTrue(issues.contains("Activity not set"))
        assertTrue(issues.any { it.contains("before signed on 2026-09-28") })
    }

    @Test fun exportedWordIncludesProfileBirthDateAndNiColumns() {
        val session = DpSession(tourId="t",startMillis=at(28,8),endMillis=at(28,11),activity="Cargo transfer")
        val data = AppData(tours=listOf(tour),sessions=listOf(session),fullName="Example Officer",dateOfBirth="1993-11-15")
        val bytes = java.io.ByteArrayOutputStream()
        ReportExport.writeDocx(data,listOf(tour),ReportLayout.NEW_SCHEME,LetterDetails("Example Co","","1993-11-15","2000"),bytes)
        val zip = java.util.zip.ZipInputStream(bytes.toByteArray().inputStream())
        var document = ""
        zip.use { stream -> while (true) {
            val entry = stream.nextEntry ?: break
            if (entry.name == "word/document.xml") document = stream.readBytes().toString(Charsets.UTF_8)
        } }
        assertTrue(document.contains("Example Officer"))
        assertTrue(document.contains("15 Nov 1993"))
        assertTrue(document.contains("Supporting active date breakdown"))
        assertTrue(document.contains("28 Sep 2026"))
        assertTrue(document.contains("GRT"))
        assertTrue(document.contains("200B Lambeth Road"))
        assertTrue(document.contains("Total claimed DP sea time: 1 days"))
    }

    @Test fun twoVesselsKeepDistinctGrossTonnageInDraft() {
        val a = tour.copy(id="a",vessel="One",grossTonnage="3997",vesselId="v1")
        val b = tour.copy(id="b",vessel="Two",imo="7654321",grossTonnage="5012",vesselId="v2")
        val sa = DpSession(tourId="a",startMillis=at(28,8),endMillis=at(28,11),activity="Cargo transfer")
        val sb = sa.copy(id="sb",tourId="b")
        val data = AppData(tours=listOf(a,b),sessions=listOf(sa,sb),fullName="Example Officer")
        val bytes = java.io.ByteArrayOutputStream()
        ReportExport.writeDocx(data,listOf(a,b),ReportLayout.NEW_SCHEME,LetterDetails("Example Co","","1993-11-15",""),bytes)
        var xml = ""
        java.util.zip.ZipInputStream(bytes.toByteArray().inputStream()).use { stream ->
            while (true) { val entry = stream.nextEntry ?: break
                if (entry.name == "word/document.xml") xml = stream.readBytes().toString(Charsets.UTF_8) }
        }
        assertTrue(xml.contains("3997"))
        assertTrue(xml.contains("5012"))
        assertTrue(xml.contains("Total claimed DP sea time: 2 days"))
    }

    @Test fun multipleServicePeriodsOfOneVesselKeepDatesAndTotals() {
        val first = tour.copy(id="first",vesselId="v",grossTonnage="3997")
        val second = tour.copy(id="second",vesselId="v",signedOn="2026-09-01",disembarked="2026-09-07",grossTonnage="3997")
        val a = DpSession(tourId="first",startMillis=at(28,8),endMillis=at(28,11),activity="Cargo transfer")
        val b = DpSession(tourId="second",startMillis=at(1,8),endMillis=at(1,11),activity="Cargo transfer")
        val data = AppData(tours=listOf(first,second),sessions=listOf(a,b),fullName="Example Officer")
        assertEquals(2,ReportSelection.vessels(data).single().tours.size)
        assertTrue(ReportSelection.confirmationIssues(data,listOf(first,second),ReportLayout.NEW_SCHEME,"Example Officer","Example Co","1993-11-15","").isEmpty())
        val out = java.io.ByteArrayOutputStream()
        ReportExport.writeDocx(data,listOf(first,second),ReportLayout.NEW_SCHEME,LetterDetails("Example Co","","1993-11-15",""),out)
        var xml = ""
        java.util.zip.ZipInputStream(out.toByteArray().inputStream()).use { z -> while (true) {
            val entry = z.nextEntry ?: break
            if (entry.name == "word/document.xml") xml = z.readBytes().toString(Charsets.UTF_8)
        } }
        assertTrue(xml.contains("01 Sep 2026"))
        assertTrue(xml.contains("28 Sep 2026"))
        assertTrue(xml.contains("Total claimed DP sea time: 2 days"))
    }
}
