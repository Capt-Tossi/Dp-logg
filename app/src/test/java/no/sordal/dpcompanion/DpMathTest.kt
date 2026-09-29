package no.sordal.dpcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DpMathTest {
    private val zone = ZoneId.of("UTC")
    private fun time(day: Int, hour: Int, minute: Int = 0): Long = LocalDate.of(2026, 9, day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun tour(mode: String, left: String = "2026-10-26", duty: Double = 2.0) = Tour(
        id = "t", signedOn = "2026-09-29", disembarked = left, mode = mode, dutyHours = duty, zoneId = "UTC"
    )

    @Test fun wholeHourConvention() {
        assertEquals(24, DpMath.loggedHours(time(29,0), time(29,23,59)))
        assertEquals(23, DpMath.loggedHours(time(29,0), time(29,23,58)))
        assertEquals(20, DpMath.loggedHours(time(29,0), time(29,20)))
        assertEquals(0, DpMath.loggedHours(time(29,0), time(29,0)))
    }

    @Test fun continuousTwentyHoursOverTwoIsTenDays() {
        val s = DpSession(tourId="t", startMillis=time(29,0), endMillis=time(29,20))
        assertEquals(10.0, DpMath.totals(tour("Continuous DP"), listOf(s)).dpDays!!, 0.0001)
    }

    @Test fun continuousCannotExceedDaysOnBoard() {
        val s = DpSession(tourId="t", startMillis=time(29,0), endMillis=time(29,23,59))
        val many = (0 until 13).map { i -> s.copy(id=i.toString(),startMillis=s.startMillis + i*86_400_000L,endMillis=s.endMillis!! + i*86_400_000L) }
        val total = DpMath.totals(tour("Continuous DP"), many)
        assertEquals(312, total.loggedHours)
        assertEquals(28.0, total.dpDays!!, 0.0001)
    }

    @Test fun normalOperationsRequireAtLeastTwoHoursOnEachDate() {
        val two = DpSession(tourId="t", startMillis=time(29,0), endMillis=time(29,2))
        val three = DpSession(tourId="t", startMillis=time(30,0), endMillis=time(30,3))
        assertEquals(1.0, DpMath.totals(tour("Short operations"), listOf(two)).dpDays!!, 0.0001)
        assertEquals(2.0, DpMath.totals(tour("Short operations"), listOf(two,three)).dpDays!!, 0.0001)
    }

    @Test fun severalSessionsOnOneDateCannotEarnTwoDays() {
        val a=DpSession(tourId="t",startMillis=time(29,1),endMillis=time(29,4))
        val b=DpSession(tourId="t",startMillis=time(29,5),endMillis=time(29,8))
        assertEquals(1.0,DpMath.totals(tour("Short operations"),listOf(a,b)).dpDays!!,0.0001)
    }

    @Test fun shortOperationsAddPartialHoursBeforeCheckingTheDay() {
        val a=DpSession(tourId="t",startMillis=time(29,1),endMillis=time(29,2,30))
        val b=DpSession(tourId="t",startMillis=time(29,3),endMillis=time(29,4))
        assertEquals(1.0,DpMath.totals(tour("Short operations"),listOf(a,b)).dpDays!!,0.0001)
    }

    @Test fun sessionEndingAtMidnightAfterDisembarkIsWithinTour() {
        val s=DpSession(tourId="t",startMillis=time(29,21),endMillis=time(30,0))
        assertEquals(1.0,DpMath.totals(tour("Short operations",left="2026-09-29"),listOf(s)).dpDays!!,0.0001)
    }

    @Test fun midnightCrossingIsAllocatedToBothDates() {
        val a=DpSession(tourId="t",startMillis=time(29,23),endMillis=time(30,1))
        val b=DpSession(tourId="t",startMillis=time(30,1),endMillis=time(30,3))
        assertEquals(1.0,DpMath.totals(tour("Short operations"),listOf(a,b)).dpDays!!,0.0001)
    }

    @Test fun watchPeriodDoesNotChangeImcaHoursBasis() {
        val s=DpSession(tourId="t",startMillis=time(29,0),endMillis=time(29,20))
        assertEquals(10.0,DpMath.totals(tour("Continuous DP",duty=6.0),listOf(s)).dpDays!!,0.0001)
    }

    @Test fun overlappingSessionsAreFlaggedInsteadOfDoubleCounted() {
        val a=DpSession(tourId="t",startMillis=time(29,1),endMillis=time(29,4))
        val b=DpSession(tourId="t",startMillis=time(29,3),endMillis=time(29,6))
        val result=DpMath.totals(tour("Short operations"),listOf(a,b))
        assertNull(result.dpDays)
        assertEquals("Overlapping DP sessions need review",result.issue)
    }

    @Test fun timeOutsideTourIsFlagged() {
        val s=DpSession(tourId="t",startMillis=time(28,1),endMillis=time(28,4))
        assertEquals("Session outside service period dates",DpMath.totals(tour("Short operations"),listOf(s)).issue)
    }

    @Test fun openTourShowsProvisionalDaysThroughToday() {
        val s=DpSession(tourId="t",startMillis=time(29,0),endMillis=time(29,20))
        val result=DpMath.totals(tour("Continuous DP",left=""),listOf(s),LocalDate.of(2026,9,30))
        assertEquals(2.0,result.dpDays!!,0.0001)
        assertEquals(true,result.provisional)
    }

    @Test fun openTourRejectsSessionAfterToday() {
        val s=DpSession(tourId="t",startMillis=time(30,0),endMillis=time(30,20))
        val result=DpMath.totals(tour("Continuous DP",left=""),listOf(s),LocalDate.of(2026,9,29))
        assertNull(result.dpDays)
        assertEquals("Session outside service period dates",result.issue)
    }

    @Test fun removingVesselKeepsHistoricalDpRecords() {
        val vessel=Vessel(id="v1",name="Example Ship",imo="1234567",type="AHTS",dpClass="DP2",dpSystem="Kongsberg K-Pos",grossTonnage="3997")
        val initial=AppData(vessels=listOf(vessel),preferredRank="Master",preferredCapacity="Senior DPO")
        val t=initial.newTour(vessel).copy(id="t",signedOn="2026-09-29",disembarked="2026-10-26")
        val s=DpSession(tourId=t.id,startMillis=time(29,0),endMillis=time(29,3))
        val after=initial.copy(vessels=emptyList(),tours=listOf(t),sessions=listOf(s))
        assertEquals("Missing vessel info",after.vesselName(t))
        assertEquals(1,after.sessions.size)
        assertEquals(1.0,DpMath.totals(t,after.sessions).dpDays!!,0.0001)
        assertEquals("1234567",t.imo)
        assertEquals("Master",t.rank)
        assertEquals("Senior DPO",t.capacity)
        assertEquals("Kongsberg K-Pos",t.dpSystem)
        assertEquals("3997",t.grossTonnage)
    }

    @Test fun legacyTourStillDisplaysItsSavedVessel() {
        val old=Tour(vessel="Original Vessel",imo="1234567")
        assertEquals("Original Vessel",AppData().vesselName(old))
    }

    @Test fun niLinkEncodesSurnameAndKeepsCertificateNumber() {
        assertEquals(
            "https://dp.nialexisplatform.org/VerifyCertificate/WebPage?code=1&cid=12345-S%C3%B8lv",
            NiVerification.url(" 12345 "," Sølv ")
        )
    }

    @Test fun niLinkFallsBackToOfficialFormIfDetailsAreMissing() {
        assertEquals(NiVerification.formUrl,NiVerification.url("12345",""))
    }

    @Test fun renewalWindowUsesCalendarMonthsAndKeepsExpiredDistinct() {
        assertEquals(LocalDate.of(2030,12,28),CertificateRenewal.openingDate("2031-06-28"))
        assertEquals("182 days to expire",CertificateRenewal.status("2031-06-28",LocalDate.of(2030,12,28)))
        assertEquals("You can apply for renewal from 28 Dec 2030",CertificateRenewal.message("2031-06-28",LocalDate.of(2030,12,27)))
        assertEquals("Renewal applications are open",CertificateRenewal.message("2031-06-28",LocalDate.of(2030,12,28)))
        assertEquals("Expired 1 day ago",CertificateRenewal.status("2031-06-28",LocalDate.of(2031,6,29)))
        assertEquals("Certificate expired",CertificateRenewal.message("2031-06-28",LocalDate.of(2031,6,29)))
    }

    @Test fun reassigningTourUpdatesSnapshotButKeepsItsSessionsAndDocuments() {
        val first=Vessel(id="v1",name="Old name",imo="1234567",dpSystem="Old DP")
        val second=Vessel(id="v2",name="New vessel",imo="7654321",type="AHTS",dpClass="DP3",dpSystem="K-Pos",grossTonnage="4500")
        val t=AppData().newTour(first).copy(id="t",rank="Master",signedOn="2026-09-29")
        val session=DpSession(id="s",tourId="t",startMillis=time(29,10),endMillis=time(29,15))
        val attachment=Attachment(id="a",ownerId="s",category="DP checklist",fileName="photo.jpg",createdAtMillis=0)
        val before=AppData(vessels=listOf(first,second),tours=listOf(t),sessions=listOf(session),attachments=listOf(attachment))
        val after=before.reassignTourVessel("t","v2")
        assertEquals("New vessel",after.tours.single().vessel)
        assertEquals("7654321",after.tours.single().imo)
        assertEquals("K-Pos",after.tours.single().dpSystem)
        assertEquals("4500",after.tours.single().grossTonnage)
        assertEquals("Master",after.tours.single().rank)
        assertEquals(before.sessions,after.sessions)
        assertEquals(before.attachments,after.attachments)
        assertEquals("Old name",before.tours.single().vessel)
    }

    @Test fun deletingServicePeriodRemovesOnlyItsSessionsAndPhotosAndSelectsAnotherPeriod() {
        val t1=Tour(id="t1",signedOn="2026-09-01")
        val t2=Tour(id="t2",signedOn="2026-09-10")
        val s1=DpSession(id="s1",tourId="t1",startMillis=time(29,1))
        val s2=DpSession(id="s2",tourId="t2",startMillis=time(29,2))
        val photos=listOf(
            Attachment(id="a1",ownerId="t1",category="Checklist",fileName="one.jpg",createdAtMillis=0),
            Attachment(id="a2",ownerId="s1",category="Logbook",fileName="two.jpg",createdAtMillis=0),
            Attachment(id="a3",ownerId="s2",category="Logbook",fileName="three.jpg",createdAtMillis=0))
        val data=AppData(vessels=listOf(Vessel(id="v")),tours=listOf(t1,t2),sessions=listOf(s1,s2),attachments=photos,activeTourId="t1")
        val after=data.removeTour("t1")
        assertEquals(listOf(t2),after.tours)
        assertEquals(listOf(s2),after.sessions)
        assertEquals(listOf(photos[2]),after.attachments)
        assertEquals("t2",after.activeTourId)
        assertEquals(data.vessels,after.vessels)
    }

    @Test fun sessionCardRangeShowsEndTimeAndCrossMidnightDate() {
        val normal=Tour(mode="Short operations",zoneId="UTC")
        val continuous=normal.copy(mode="Continuous DP")
        val session=DpSession(tourId="t",startMillis=time(29,10,10),endMillis=time(29,15,10))
        assertEquals("29 Sep 2026 10:10–15:10",SessionDisplay.range(session,normal))
        assertEquals("29 Sep 2026 10:10 – 29 Sep 2026 15:10",SessionDisplay.range(session,continuous))
        assertEquals("29 Sep 2026 23:00 – 30 Sep 2026 01:00",SessionDisplay.range(session.copy(startMillis=time(29,23),endMillis=time(30,1)),normal))
        assertEquals("29 Sep 2026 10:10 · Ongoing",SessionDisplay.range(session.copy(endMillis=null),normal))
    }
}
