package no.sordal.dpcompanion

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Store(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "dp-data.json"))

    fun load(): AppData {
        val bytes = try { file.openRead().use { it.readBytes() } }
            catch (e: FileNotFoundException) { return AppData() }
        return decode(JSONObject(bytes.toString(Charsets.UTF_8)))
    }

    fun save(data: AppData) {
        val stream = file.startWrite()
        try {
            stream.write(encode(data).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    fun exportZip(data: AppData, output: java.io.OutputStream) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("dp-data.json"))
            zip.write(encode(data).toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            data.attachments.map { it.fileName }.distinct().forEach { name ->
                if (name.contains('/') || name.contains('\\')) return@forEach
                val photo = File(File(context.filesDir, "photos"), name)
                if (photo.isFile) {
                    zip.putNextEntry(ZipEntry("photos/$name"))
                    photo.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun encode(data: AppData) = JSONObject().apply {
        put("schema", 1); put("activeTourId", data.activeTourId); put("certificateNumber", data.certificateNumber)
        put("certificateExpiry", data.certificateExpiry); put("cpd6Completed", data.cpd6Completed)
        put("tours", JSONArray().apply { data.tours.forEach { t -> put(JSONObject().apply {
            put("id",t.id); put("vessel",t.vessel); put("imo",t.imo); put("vesselType",t.vesselType)
            put("dpClass",t.dpClass); put("rank",t.rank); put("capacity",t.capacity)
            put("signedOn",t.signedOn); put("disembarked",t.disembarked); put("mode",t.mode)
            put("dutyHours",t.dutyHours); put("zoneId",t.zoneId)
        }) } })
        put("sessions", JSONArray().apply { data.sessions.forEach { s -> put(JSONObject().apply {
            put("id",s.id); put("tourId",s.tourId); put("startMillis",s.startMillis); put("endMillis",s.endMillis)
            put("activity",s.activity); put("location",s.location); put("notes",s.notes)
            put("originalStartMillis",s.originalStartMillis); put("originalEndMillis",s.originalEndMillis)
            put("corrections", JSONArray().apply { s.corrections.forEach { c -> put(JSONObject().apply {
                put("changedAtMillis",c.changedAtMillis); put("previousStartMillis",c.previousStartMillis)
                put("previousEndMillis",c.previousEndMillis); put("reason",c.reason)
            }) } })
        }) } })
        put("cpd", JSONArray().apply { data.cpd.forEach { c -> put(JSONObject().apply {
            put("id",c.id); put("title",c.title); put("completedDate",c.completedDate)
        }) } })
        put("attachments", JSONArray().apply { data.attachments.forEach { a -> put(JSONObject().apply {
            put("id",a.id); put("ownerId",a.ownerId); put("category",a.category)
            put("fileName",a.fileName); put("createdAtMillis",a.createdAtMillis)
        }) } })
    }

    private fun decode(j: JSONObject): AppData {
        if (j.optInt("schema", 1) != 1) error("Unsupported backup format")
        val tours = j.optJSONArray("tours").objects().map { t -> Tour(
            id=t.optString("id"), vessel=t.optString("vessel"), imo=t.optString("imo"),
            vesselType=t.optString("vesselType","PSV"), dpClass=t.optString("dpClass","DP2"),
            rank=t.optString("rank","Master"), capacity=t.optString("capacity","Senior DPO / DP Master"),
            signedOn=t.optString("signedOn"), disembarked=t.optString("disembarked"),
            mode=t.optString("mode","Short operations"), dutyHours=t.optDouble("dutyHours",0.0),
            zoneId=t.optString("zoneId",java.time.ZoneId.systemDefault().id)
        ) }
        val sessions = j.optJSONArray("sessions").objects().map { s -> DpSession(
            id=s.optString("id"), tourId=s.optString("tourId"), startMillis=s.optLong("startMillis"),
            endMillis=s.longOrNull("endMillis"), activity=s.optString("activity"),
            location=s.optString("location"), notes=s.optString("notes"),
            originalStartMillis=s.optLong("originalStartMillis",s.optLong("startMillis")),
            originalEndMillis=s.longOrNull("originalEndMillis"),
            corrections=s.optJSONArray("corrections").objects().map { c -> Correction(
                c.optLong("changedAtMillis"),c.optLong("previousStartMillis"),
                c.longOrNull("previousEndMillis"),c.optString("reason")
            ) }
        ) }
        val cpd = j.optJSONArray("cpd").objects().map { c -> CpdEntry(c.optString("id"),c.optString("title"),c.optString("completedDate")) }
        val attachments = j.optJSONArray("attachments").objects().map { a -> Attachment(
            a.optString("id"),a.optString("ownerId"),a.optString("category"),
            a.optString("fileName"),a.optLong("createdAtMillis")
        ) }
        val activeTourId = if (j.isNull("activeTourId")) null else j.optString("activeTourId").ifBlank { null }
        return AppData(tours,sessions,cpd,attachments,activeTourId,
            j.optString("certificateNumber"),j.optString("certificateExpiry"),j.optBoolean("cpd6Completed",true))
    }
}

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
