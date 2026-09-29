package no.sordal.dpcompanion

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        setContent {
            val initial = remember { runCatching { store.load() } }
            if (initial.isFailure) {
                MaterialTheme { Text("Stored DP records could not be read. No data has been overwritten. Keep the app installed and recover from a backup.", modifier = Modifier.padding(24.dp)) }
                return@setContent
            }
            var data by remember { mutableStateOf(initial.getOrThrow()) }
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF123C5C))) {
                CompanionApp(data, { next -> store.save(next); data = next }, store)
            }
        }
    }
}

private val activities = listOf("Cargo transfer", "Anchor handling (on DP)", "Standby on DP", "ROV support", "Diving support", "Survey", "Drilling support", "DP set-up", "DP trials / FMEA", "DP training", "Other")
private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")
private val stampFormat = DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm")

@Composable
private fun CompanionApp(data: AppData, save: (AppData) -> Unit, store: Store) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(0) }
    var editId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var pendingOwner by rememberSaveable { mutableStateOf("") }
    var pendingCategory by rememberSaveable { mutableStateOf("") }
    var pendingFile by rememberSaveable { mutableStateOf("") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (pendingFile.isNotBlank()) {
            if (success) save(data.copy(attachments = data.attachments + Attachment(ownerId = pendingOwner, category = pendingCategory, fileName = pendingFile, createdAtMillis = System.currentTimeMillis())))
            else File(File(context.filesDir, "photos"), pendingFile).delete()
        }
        pendingOwner = ""; pendingCategory = ""; pendingFile = ""
    }
    fun takePhoto(owner: String, category: String) {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val name = "${UUID.randomUUID()}.jpg"
        val file = File(dir, name).apply { createNewFile() }
        pendingOwner = owner; pendingCategory = category; pendingFile = name
        runCatching { camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file)) }
            .onFailure { file.delete(); pendingOwner = ""; pendingCategory = ""; pendingFile = ""; message = "Camera unavailable: ${it.message}" }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { store.exportZip(data, it) } ?: error("Cannot open file") }
                .onSuccess { message = "Backup saved" }.onFailure { message = "Backup failed: ${it.message}" }
        }
    }
    Scaffold(
        topBar = { Surface(color = MaterialTheme.colorScheme.primary) { Text("DP Companion", modifier = Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge) } },
        bottomBar = { NavigationBar {
            listOf("DP", "Tours", "CPD", "Overview").forEachIndexed { i, title ->
                NavigationBarItem(selected = page == i, onClick = { page = i; editId = null }, icon = { Text(listOf("●", "▤", "✓", "◷")[i]) }, label = { Text(title) })
            }
        } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (message.isNotBlank()) Text(message, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary)
            when (page) {
                0 -> if (editId != null) {
                    val session = data.sessions.firstOrNull { it.id == editId }
                    if (session != null) SessionEditor(session, data.tours.firstOrNull { it.id == session.tourId }, data.attachments.filter { it.ownerId == session.id },
                        onSave = { changed, reason ->
                            val corrected = if (changed.startMillis != session.startMillis || changed.endMillis != session.endMillis)
                                changed.copy(corrections = session.corrections + Correction(System.currentTimeMillis(), session.startMillis, session.endMillis, reason)) else changed
                            save(data.copy(sessions = data.sessions.map { if (it.id == session.id) corrected else it })); editId = null
                        }, onPhoto = { takePhoto(session.id, it) }, onClose = { editId = null }, onOpenPhoto = { openPhoto(context, it) })
                    else editId = null
                } else HomeScreen(data, onStart = { tour ->
                    save(data.copy(sessions = data.sessions + DpSession(tourId = tour.id, startMillis = System.currentTimeMillis())))
                }, onStop = { active ->
                    val stoppedAt = System.currentTimeMillis()
                    save(data.copy(sessions = data.sessions.map { if (it.id == active.id) it.copy(endMillis = stoppedAt, originalEndMillis = stoppedAt) else it }))
                    editId = active.id
                }, onEdit = { editId = it }, onAddManual = { tour ->
                    val now = System.currentTimeMillis()
                    val session = DpSession(tourId = tour.id, startMillis = now - 3_600_000, endMillis = now)
                    save(data.copy(sessions = data.sessions + session)); editId = session.id
                }, onTours = { page = 1 })
                1 -> ToursScreen(data, save, onPhoto = { owner -> takePhoto(owner, "DP checklist") }, onOpenPhoto = { openPhoto(context, it) })
                2 -> CpdScreen(data, save, onPhoto = { owner -> takePhoto(owner, "CPD completion") }, onOpenPhoto = { openPhoto(context, it) })
                3 -> OverviewScreen(data, save, onExport = { export.launch("dp-companion-backup.zip") })
            }
        }
    }
}

@Composable
private fun HomeScreen(data: AppData, onStart: (Tour) -> Unit, onStop: (DpSession) -> Unit, onEdit: (String) -> Unit, onAddManual: (Tour) -> Unit, onTours: () -> Unit) {
    val tour = data.tours.firstOrNull { it.id == data.activeTourId }
    val active = data.sessions.firstOrNull { it.endMillis == null }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (tour == null) {
            Text("Set up a tour to begin", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onTours) { Text("Create tour") }
            return@Column
        }
        Text(tour.vessel.ifBlank { "Unnamed vessel" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("IMO ${tour.imo.ifBlank { "—" }} · ${tour.mode}")
        val totals = DpMath.totals(tour, data.sessions)
        Text("${totals.loggedHours} logged DP hours  ·  ${totals.dpDays?.formatDays() ?: "—"} DP days", style = MaterialTheme.typography.titleMedium)
        if (totals.provisional) Text("Provisional total until disembark date is entered", style = MaterialTheme.typography.bodySmall)
        if (totals.issue != null) Text(totals.issue, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(30.dp))
        if (active == null) {
            Button(onClick = { onStart(tour) }, modifier = Modifier.fillMaxWidth().height(88.dp)) { Text("START DP", style = MaterialTheme.typography.headlineSmall) }
            OutlinedButton(onClick = { onAddManual(tour) }, modifier = Modifier.fillMaxWidth()) { Text("Add a session later") }
        } else {
            Text("Recording since ${formatStamp(active.startMillis, tour.zoneId)}", style = MaterialTheme.typography.titleMedium)
            if (active.tourId != tour.id) Text("Active session belongs to another tour. Select it before stopping.", color = MaterialTheme.colorScheme.error)
            Button(onClick = { onStop(active) }, enabled = active.tourId == tour.id, modifier = Modifier.fillMaxWidth().height(88.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("STOP DP", style = MaterialTheme.typography.headlineSmall)
            }
        }
        HorizontalDivider()
        Text("Recent sessions", style = MaterialTheme.typography.titleLarge)
        data.sessions.filter { it.tourId == tour.id && it.endMillis != null }.sortedByDescending { it.startMillis }.take(8).forEach { s ->
            OutlinedCard(onClick = { onEdit(s.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(formatStamp(s.startMillis, tour.zoneId), fontWeight = FontWeight.SemiBold)
                    Text("${DpMath.loggedHours(s.startMillis, s.endMillis!!)} logged hours · ${s.activity.ifBlank { "Activity not set" }}")
                }
            }
        }
    }
}

@Composable
private fun SessionEditor(session: DpSession, tour: Tour?, photos: List<Attachment>, onSave: (DpSession, String) -> Unit, onPhoto: (String) -> Unit, onClose: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val context = LocalContext.current
    val zone = tour?.zoneId ?: ZoneId.systemDefault().id
    var start by remember(session.id) { mutableLongStateOf(session.startMillis) }
    var end by remember(session.id) { mutableLongStateOf(session.endMillis ?: System.currentTimeMillis()) }
    var activity by remember(session.id) { mutableStateOf(session.activity) }
    var location by remember(session.id) { mutableStateOf(session.location) }
    var notes by remember(session.id) { mutableStateOf(session.notes) }
    var reason by remember(session.id) { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Review session", style = MaterialTheme.typography.headlineSmall)
        OutlinedButton(onClick = { pickDateTime(context, start, zone) { start = it } }, modifier = Modifier.fillMaxWidth()) { Text("Start: ${formatStamp(start, zone)}") }
        OutlinedButton(onClick = { pickDateTime(context, end, zone) { end = it } }, modifier = Modifier.fillMaxWidth()) { Text("Stop: ${formatStamp(end, zone)}") }
        Text("${DpMath.loggedHours(start, end)} logged hours", style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Activity: ${activity.ifBlank { "Choose" }}") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                activities.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { activity = option; expanded = false }) }
            }
        }
        OutlinedTextField(location, { location = it }, label = { Text("Location / field") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
        if (start != session.startMillis || end != session.endMillis) OutlinedTextField(reason, { reason = it }, label = { Text("Reason for time correction") }, modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            if (end <= start) error = "Stop must be after start"
            else onSave(session.copy(startMillis = start, endMillis = end, activity = activity, location = location, notes = notes), reason)
        }, modifier = Modifier.fillMaxWidth()) { Text("Save session") }
        TextButton(onClick = onClose) { Text("Back") }
        HorizontalDivider()
        Text("Evidence", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onPhoto("DP checklist") }) { Text("Photograph DP checklist") }
        OutlinedButton(onClick = { onPhoto("Signed logbook page") }) { Text("Photograph logbook page") }
        photos.forEach { a -> TextButton(onClick = { onOpenPhoto(a.fileName) }) { Text("${a.category} · ${a.fileName.take(8)}") } }
        if (session.corrections.isNotEmpty()) {
            HorizontalDivider(); Text("Time corrections", style = MaterialTheme.typography.titleMedium)
            session.corrections.forEach { c -> Text("${formatStamp(c.changedAtMillis, zone)} · Previous: ${formatStamp(c.previousStartMillis, zone)}–${c.previousEndMillis?.let { formatStamp(it, zone) } ?: "active"} · ${c.reason.ifBlank { "No reason entered" }}") }
        }
    }
}

@Composable
private fun ToursScreen(data: AppData, save: (AppData) -> Unit, onPhoto: (String) -> Unit, onOpenPhoto: (String) -> Unit) {
    val context = LocalContext.current
    val tour = data.tours.firstOrNull { it.id == data.activeTourId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tours", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { val t = Tour(); save(data.copy(tours = data.tours + t, activeTourId = t.id)) }) { Text("New tour") }
        data.tours.forEach { t -> FilterChip(selected = t.id == data.activeTourId, onClick = { save(data.copy(activeTourId = t.id)) }, label = { Text(t.vessel.ifBlank { "New tour" }) }) }
        if (tour != null) {
            fun change(t: Tour) { save(data.copy(tours = data.tours.map { if (it.id == tour.id) t else it })) }
            OutlinedTextField(tour.vessel, { change(tour.copy(vessel = it)) }, label = { Text("Vessel") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tour.imo, { change(tour.copy(imo = it)) }, label = { Text("IMO number") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tour.vesselType, { change(tour.copy(vesselType = it)) }, label = { Text("Vessel type (e.g. PSV, AHTS / AHV)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tour.dpClass, { change(tour.copy(dpClass = it)) }, label = { Text("DP class") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tour.rank, { change(tour.copy(rank = it)) }, label = { Text("Shipboard rank") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tour.capacity, { change(tour.copy(capacity = it)) }, label = { Text("DP capacity") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { pickDate(context, tour.signedOn) { change(tour.copy(signedOn = it)) } }) { Text("Signed on: ${tour.signedOn.ifBlank { "Choose date" }}") }
            OutlinedButton(onClick = { pickDate(context, tour.disembarked) { change(tour.copy(disembarked = it)) } }) { Text("Disembarked: ${tour.disembarked.ifBlank { "Choose date" }}") }
            Text("Operating mode for this tour", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tour.mode == "Short operations", onClick = { change(tour.copy(mode = "Short operations")) }, label = { Text("Short") })
                FilterChip(selected = tour.mode == "Continuous DP", onClick = { change(tour.copy(mode = "Continuous DP")) }, label = { Text("Continuous") })
            }
            if (tour.mode == "Continuous DP") OutlinedTextField(if (tour.dutyHours == 0.0) "" else tour.dutyHours.toString(), { change(tour.copy(dutyHours = it.toDoubleOrNull() ?: 0.0)) }, label = { Text("Duty period (hours)") }, modifier = Modifier.fillMaxWidth())
            val totals = DpMath.totals(tour, data.sessions)
            Text("${totals.loggedHours} logged hours · ${totals.dpDays?.formatDays() ?: "—"} DP days")
            if (totals.provisional) Text("Provisional until disembark date is entered", style = MaterialTheme.typography.bodySmall)
            if (totals.issue != null) Text(totals.issue, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { onPhoto(tour.id) }) { Text("Photograph tour checklist") }
            data.attachments.filter { it.ownerId == tour.id }.forEach { a -> TextButton(onClick = { onOpenPhoto(a.fileName) }) { Text(a.category) } }
        }
    }
}

@Composable
private fun CpdScreen(data: AppData, save: (AppData) -> Unit, onPhoto: (String) -> Unit, onOpenPhoto: (String) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val thisYear = LocalDate.now().year
    val count = data.cpd.count { it.completedDate.startsWith(thisYear.toString()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("CPD", style = MaterialTheme.typography.headlineSmall)
        Text("CPD 6 completed · $count / 2 activities recorded for $thisYear")
        OutlinedTextField(title, { title = it }, label = { Text("Activity / course") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { pickDate(context, date) { date = it } }) { Text("Completed: $date") }
        Button(onClick = { if (title.isNotBlank()) { save(data.copy(cpd = data.cpd + CpdEntry(title = title.trim(), completedDate = date))); title = "" } }) { Text("Add CPD") }
        data.cpd.sortedByDescending { it.completedDate }.forEach { c ->
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text(c.title, fontWeight = FontWeight.SemiBold); Text(c.completedDate)
                TextButton(onClick = { onPhoto(c.id) }) { Text("Photograph completion") }
                data.attachments.filter { it.ownerId == c.id }.forEach { a -> TextButton(onClick = { onOpenPhoto(a.fileName) }) { Text("View ${a.category}") } }
            } }
        }
    }
}

@Composable
private fun OverviewScreen(data: AppData, save: (AppData) -> Unit, onExport: () -> Unit) {
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(data.certificateExpiry) }.getOrNull()
    val remaining = date?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Overview", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(data.certificateNumber, { save(data.copy(certificateNumber = it)) }, label = { Text("DP certificate number") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { pickDate(context, data.certificateExpiry) { save(data.copy(certificateExpiry = it)) } }) { Text("Certificate expiry: ${data.certificateExpiry.ifBlank { "Choose date" }}") }
        Text(if (remaining == null) "Enter certificate expiry date" else if (remaining < 0) "Expired ${-remaining} days ago" else "$remaining days to expire", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        data.tours.forEach { t ->
            val totals = DpMath.totals(t, data.sessions)
            Text(t.vessel.ifBlank { "Unnamed vessel" }, style = MaterialTheme.typography.titleMedium)
            Text("${totals.loggedHours} logged hours · ${totals.dpDays?.formatDays() ?: "—"} DP days")
            if (totals.provisional) Text("Provisional", style = MaterialTheme.typography.bodySmall)
            if (totals.issue != null) Text(totals.issue, color = MaterialTheme.colorScheme.error)
        }
        HorizontalDivider()
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export backup with photos") }
        Text("Personal working record. Copy verified totals into your signed NI/IMCA logbook.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun Double.formatDays() = if (this % 1.0 == 0.0) toInt().toString() else "%.2f".format(java.util.Locale.US, this)
private fun formatStamp(millis: Long, zone: String): String = runCatching { Instant.ofEpochMilli(millis).atZone(ZoneId.of(zone)).format(stampFormat) }.getOrElse { Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(stampFormat) }
private fun pickDate(context: Context, current: String, onPicked: (String) -> Unit) {
    val d = runCatching { LocalDate.parse(current) }.getOrElse { LocalDate.now() }
    DatePickerDialog(context, { _, y, m, day -> onPicked(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}
private fun pickDateTime(context: Context, millis: Long, zone: String, onPicked: (Long) -> Unit) {
    val z = runCatching { ZoneId.of(zone) }.getOrElse { ZoneId.systemDefault() }
    val current = Instant.ofEpochMilli(millis).atZone(z)
    DatePickerDialog(context, { _, y, m, day ->
        TimePickerDialog(context, { _, h, minute -> onPicked(LocalDate.of(y,m+1,day).atTime(h,minute).atZone(z).toInstant().toEpochMilli()) }, current.hour, current.minute, true).show()
    }, current.year, current.monthValue - 1, current.dayOfMonth).show()
}
private fun openPhoto(context: Context, name: String) {
    if (name.contains('/') || name.contains('\\')) return
    val file = File(File(context.filesDir, "photos"), name)
    if (!file.isFile) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}
