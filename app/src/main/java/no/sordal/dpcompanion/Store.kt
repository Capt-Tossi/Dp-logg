package no.sordal.dpcompanion

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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

    fun importZip(input: InputStream): AppData {
        val staging = File(context.cacheDir, "dp-import-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var json: ByteArray? = null
            val photos = mutableMapOf<String, File>()
            var count = 0
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) { zip.closeEntry(); continue }
                    if (++count > 201) error("Too many files in backup")
                    val name = entry.name
                    if (name != "dp-data.json" && !Regex("photos/[0-9a-fA-F-]{36}\\.jpg").matches(name))
                        error("Unexpected file in backup")
                    if (name == "dp-data.json" && json != null || name != "dp-data.json" && photos.containsKey(name.removePrefix("photos/")))
                        error("Duplicate file in backup")
                    val max = if (name == "dp-data.json") 10_000_000L else 20_000_000L
                    val buffer = ByteArray(8192)
                    val output = if (name == "dp-data.json") ByteArrayOutputStream() else null
                    val photo = if (output == null) File(staging, name.removePrefix("photos/")) else null
                    (output ?: photo!!.outputStream()).use { dest ->
                        var written = 0L
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            written += n; total += n
                            if (written > max || total > 80_000_000L) error("Backup exceeds size limit")
                            dest.write(buffer, 0, n)
                        }
                    }
                    if (output != null) json = output.toByteArray()
                    else photos[photo!!.name] = photo
                    zip.closeEntry()
                }
            }
            val restored = decode(JSONObject((json ?: error("Missing backup data")).toString(Charsets.UTF_8)))
            if (restored.attachments.any { it.fileName !in photos }) error("Backup is missing a referenced photo")
            val target = File(context.filesDir, "photos").apply { mkdirs() }
            photos.forEach { (name, source) ->
                if (!source.renameTo(File(target, name))) error("Could not restore photo")
            }
            return restored
        } finally { staging.deleteRecursively() }
    }

    private fun encode(data: AppData) = JSONObject().apply {
        put("schema", 4); put("activeTourId", data.activeTourId); put("certificateNumber", data.certificateNumber)
        put("certificateExpiry", data.certificateExpiry); put("certificateIssue", data.certificateIssue)
        put("cpd6Completed", data.cpd6Completed); put("fullName", data.fullName); put("lastName", data.lastName)
        put("dateOfBirth", data.dateOfBirth)
        put("preferredRank", data.preferredRank); put("preferredCapacity", data.preferredCapacity)
        put("vessels", JSONArray().apply { data.vessels.forEach { v -> put(JSONObject().apply {
            put("id",v.id); put("name",v.name); put("imo",v.imo); put("type",v.type); put("dpClass",v.dpClass)
            put("dpSystem",v.dpSystem); put("grossTonnage",v.grossTonnage)
        }) } })
        put("tours", JSONArray().apply { data.tours.forEach { t -> put(JSONObject().apply {
            put("id",t.id); put("vessel",t.vessel); put("imo",t.imo); put("vesselType",t.vesselType)
            put("dpClass",t.dpClass); put("rank",t.rank); put("capacity",t.capacity)
            put("signedOn",t.signedOn); put("disembarked",t.disembarked); put("mode",t.mode)
            put("dutyHours",t.dutyHours); put("zoneId",t.zoneId); put("vesselId",t.vesselId)
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
            put("id",c.id); put("title",c.title); put("completedDate",c.completedDate); put("kind",c.kind)
        }) } })
        put("attachments", JSONArray().apply { data.attachments.forEach { a -> put(JSONObject().apply {
            put("id",a.id); put("ownerId",a.ownerId); put("category",a.category)
            put("fileName",a.fileName); put("createdAtMillis",a.createdAtMillis); put("rotationDegrees",a.rotationDegrees)
        }) } })
    }

    private fun decode(j: JSONObject): AppData {
        if (j.optInt("schema", 1) !in 1..4) error("Unsupported backup format")
        val vessels = j.optJSONArray("vessels").objects().map { v -> Vessel(
            id=v.optString("id"), name=v.optString("name"), imo=v.optString("imo"),
            type=v.optString("type","PSV"), dpClass=v.optString("dpClass","DP2"),
            dpSystem=v.optString("dpSystem"), grossTonnage=v.optString("grossTonnage")
        ) }
        val tours = j.optJSONArray("tours").objects().map { t -> Tour(
            id=t.optString("id"), vessel=t.optString("vessel"), imo=t.optString("imo"),
            vesselType=t.optString("vesselType","PSV"), dpClass=t.optString("dpClass","DP2"),
            rank=t.optString("rank","Master"), capacity=t.optString("capacity","Senior DPO / DP Master"),
            signedOn=t.optString("signedOn"), disembarked=t.optString("disembarked"),
            mode=t.optString("mode","Short operations"), dutyHours=t.optDouble("dutyHours",0.0),
            zoneId=t.optString("zoneId",java.time.ZoneId.systemDefault().id), vesselId=t.optString("vesselId")
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
        val cpd = j.optJSONArray("cpd").objects().map { c -> CpdEntry(c.optString("id"),c.optString("title"),c.optString("completedDate"),c.optString("kind","CPD")) }
        val attachments = j.optJSONArray("attachments").objects().map { a -> Attachment(
            a.optString("id"),a.optString("ownerId"),a.optString("category"),
            a.optString("fileName"),a.optLong("createdAtMillis"),a.optInt("rotationDegrees",0)
        ) }
        val activeTourId = if (j.isNull("activeTourId")) null else j.optString("activeTourId").ifBlank { null }
        return AppData(tours=tours,sessions=sessions,cpd=cpd,attachments=attachments,activeTourId=activeTourId,
            certificateNumber=j.optString("certificateNumber"),certificateExpiry=j.optString("certificateExpiry"),
            // v0.2 seeded this flag as true without user input; reset it on migration.
            cpd6Completed=if (j.optInt("schema",1) < 3) false else j.optBoolean("cpd6Completed",false),vessels=vessels,
            fullName=j.optString("fullName"),lastName=j.optString("lastName"),
            preferredRank=j.optString("preferredRank","Master"),
            preferredCapacity=j.optString("preferredCapacity","Senior DPO / DP Master"),
            certificateIssue=j.optString("certificateIssue"), dateOfBirth=j.optString("dateOfBirth"))
    }
}

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
