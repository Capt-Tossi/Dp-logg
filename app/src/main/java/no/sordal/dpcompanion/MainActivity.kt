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
private val ranks = listOf("Master", "Chief Officer", "Second Officer", "Third Officer", "Other")
private val capacities = listOf("Senior DPO / DP Master", "Senior DPO", "DPO", "Trainee DPO", "Other")
private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")
private val stampFormat = DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm")

@Composable
private fun CompanionApp(data: AppData, save: (AppData) -> Unit, store: Store) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(0) }
    var meSection by remember { mutableIntStateOf(0) }
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
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val restored = context.contentResolver.openInputStream(uri)?.use { store.importZip(it) } ?: error("Cannot open backup")
            save(restored)
        }.onSuccess { message = "Backup restored" }.onFailure { message = "Restore failed: ${it.message}" }
    }
    Scaffold(
        topBar = { Surface(color = MaterialTheme.colorScheme.primary) { Text("DP Companion", modifier = Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge) } },
        bottomBar = { NavigationBar {
            listOf("DP", "Tours", "My Vessels", "Me").forEachIndexed { i, title ->
                NavigationBarItem(selected = page == i, onClick = { page = i; editId = null; meSection = 0 }, icon = { Text(listOf("●", "▤", "⚓", "◷")[i]) }, label = { Text(title) })
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
                1 -> ToursScreen(data, save, onPhoto = { owner -> takePhoto(owner, "DP checklist") }, onOpenPhoto = { openPhoto(context, it) }, onVessels = { page = 2 })
                2 -> VesselsScreen(data, save, onPhoto = { owner -> takePhoto(owner, "Vessel photo") }, onOpenPhoto = { openPhoto(context, it) })
                3 -> when (meSection) {
                    1 -> CertificateScreen(data, save, onPhoto = { takePhoto("certificate", "DP certificate") }, onOpenPhoto = { openPhoto(context, it) }, onClose = { meSection = 0 })
                    2 -> Column(Modifier.fillMaxSize()) {
                        TextButton(onClick = { meSection = 0 }) { Text("Back to Me") }
                        Box(Modifier.weight(1f)) { CpdScreen(data, save, onPhoto = { owner -> takePhoto(owner, "CPD completion") }, onOpenPhoto = { openPhoto(context, it) }) }
                    }
                    else -> MeScreen(data, save, onCertificate = { meSection = 1 }, onCpd = { meSection = 2 }, onExport = { export.launch("dp-companion-backup.zip") }, onImport = { import.launch(arrayOf("application/zip", "application/octet-stream")) })
                }
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
        Text(data.vesselName(tour), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
private fun ChoiceField(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${value.ifBlank { "Choose" }}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}

@Composable
private fun VesselsScreen(data: AppData, save: (AppData) -> Unit, onPhoto: (String) -> Unit, onOpenPhoto: (String) -> Unit) {
    var selectedId by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val vessel = data.vessels.firstOrNull { it.id == selectedId }
    if (confirmDelete && vessel != null) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("Remove vessel?") },
        text = { Text("Existing tours and DP sessions will be kept. Those tours will show Missing vessel info.") },
        confirmButton = { TextButton(onClick = {
            save(data.copy(vessels = data.vessels.filterNot { it.id == vessel.id }))
            selectedId = ""; confirmDelete = false
        }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("My Vessels", style = MaterialTheme.typography.headlineSmall)
        Text("Save vessel details once, then select a vessel when creating a tour.")
        Button(onClick = { val v = Vessel(); save(data.copy(vessels = data.vessels + v)); selectedId = v.id }) { Text("Add vessel") }
        data.vessels.forEach { v -> FilterChip(selected = v.id == selectedId, onClick = { selectedId = v.id }, label = { Text(v.name.ifBlank { "New vessel" }) }) }
        if (vessel != null) {
            fun change(v: Vessel) { save(data.copy(vessels = data.vessels.map { if (it.id == vessel.id) v else it })) }
            OutlinedTextField(vessel.name, { change(vessel.copy(name = it)) }, label = { Text("Vessel name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(vessel.imo, { change(vessel.copy(imo = it)) }, label = { Text("IMO number") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(vessel.type, { change(vessel.copy(type = it)) }, label = { Text("Vessel type (PSV, AHTS / AHV, etc.)") }, modifier = Modifier.fillMaxWidth())
            ChoiceField("DP class", vessel.dpClass, listOf("DP1", "DP2", "DP3", "Other")) { change(vessel.copy(dpClass = it)) }
            OutlinedButton(onClick = { onPhoto(vessel.id) }) { Text("Photograph vessel") }
            data.attachments.filter { it.ownerId == vessel.id && it.category == "Vessel photo" }.lastOrNull()?.let { a ->
                TextButton(onClick = { onOpenPhoto(a.fileName) }) { Text("View vessel photo") }
            }
            TextButton(onClick = { confirmDelete = true }) { Text("Remove from My Vessels") }
        }
    }
}

@Composable
private fun ToursScreen(data: AppData, save: (AppData) -> Unit, onPhoto: (String) -> Unit, onOpenPhoto: (String) -> Unit, onVessels: () -> Unit) {
    val context = LocalContext.current
    val tour = data.tours.firstOrNull { it.id == data.activeTourId }
    var chosenId by remember { mutableStateOf("") }
    var vesselMenu by remember { mutableStateOf(false) }
    val chosen = data.vessels.firstOrNull { it.id == chosenId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tours", style = MaterialTheme.typography.headlineSmall)
        if (data.vessels.isEmpty()) {
            Text("Add a vessel before creating a new tour.")
            Button(onClick = onVessels) { Text("Open My Vessels") }
        } else {
            Box {
                OutlinedButton(onClick = { vesselMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Vessel for new tour: ${chosen?.name ?: "Choose"}") }
                DropdownMenu(expanded = vesselMenu, onDismissRequest = { vesselMenu = false }) {
                    data.vessels.forEach { v -> DropdownMenuItem(text = { Text("${v.name.ifBlank { "Unnamed vessel" }} · ${v.imo.ifBlank { "No IMO" }}") }, onClick = { chosenId = v.id; vesselMenu = false }) }
                }
            }
            Button(onClick = { chosen?.let { v -> val t = data.newTour(v); save(data.copy(tours = data.tours + t, activeTourId = t.id)) } }, enabled = chosen != null && chosen.name.isNotBlank()) { Text("Start new tour") }
        }
        data.tours.forEach { t -> FilterChip(selected = t.id == data.activeTourId, onClick = { save(data.copy(activeTourId = t.id)) }, label = { Text(data.vesselName(t)) }) }
        if (tour != null) {
            fun change(t: Tour) { save(data.copy(tours = data.tours.map { if (it.id == tour.id) t else it })) }
            Text(data.vesselName(tour), style = MaterialTheme.typography.titleLarge)
            Text("IMO ${tour.imo.ifBlank { "—" }} · ${tour.vesselType} · ${tour.dpClass}")
            Text("Vessel details are saved as a snapshot for this tour.", style = MaterialTheme.typography.bodySmall)
            ChoiceField("Shipboard rank", tour.rank, ranks) { value ->
                save(data.copy(tours = data.tours.map { if (it.id == tour.id) tour.copy(rank = value) else it }, preferredRank = value))
            }
            ChoiceField("DP capacity", tour.capacity, capacities) { value ->
                save(data.copy(tours = data.tours.map { if (it.id == tour.id) tour.copy(capacity = value) else it }, preferredCapacity = value))
            }
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
    var kind by remember { mutableStateOf("CPD") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val thisYear = LocalDate.now().year
    val count = data.cpd.count { it.completedDate.startsWith(thisYear.toString()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("CPD / Training", style = MaterialTheme.typography.headlineSmall)
        Text("CPD 6 completed · $count / 2 entries recorded for $thisYear")
        ChoiceField("Type", kind, listOf("CPD", "Training course")) { kind = it }
        OutlinedTextField(title, { title = it }, label = { Text("Activity / course") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { pickDate(context, date) { date = it } }) { Text("Completed: $date") }
        Button(onClick = { if (title.isNotBlank()) { save(data.copy(cpd = data.cpd + CpdEntry(title = title.trim(), completedDate = date, kind = kind))); title = "" } }) { Text("Add entry") }
        data.cpd.sortedByDescending { it.completedDate }.forEach { c ->
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text(c.title, fontWeight = FontWeight.SemiBold); Text("${c.kind} · ${c.completedDate}")
                TextButton(onClick = { onPhoto(c.id) }) { Text("Photograph completion") }
                data.attachments.filter { it.ownerId == c.id }.forEach { a -> TextButton(onClick = { onOpenPhoto(a.fileName) }) { Text("View ${a.category}") } }
            } }
        }
    }
}

@Composable
private fun MeScreen(data: AppData, save: (AppData) -> Unit, onCertificate: () -> Unit, onCpd: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    var confirmRestore by remember { mutableStateOf(false) }
    if (confirmRestore) AlertDialog(onDismissRequest = { confirmRestore = false },
        title = { Text("Restore backup?") },
        text = { Text("This replaces the records currently on this device. Export them first if you want to keep them.") },
        confirmButton = { TextButton(onClick = { confirmRestore = false; onImport() }) { Text("Choose backup") } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } })
    val expiry = runCatching { LocalDate.parse(data.certificateExpiry) }.getOrNull()
    val remaining = expiry?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
    val year = LocalDate.now().year
    val entries = data.cpd.count { it.completedDate.startsWith(year.toString()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Me", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(data.fullName, { save(data.copy(fullName = it)) }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(data.lastName, { save(data.copy(lastName = it)) }, label = { Text("Last name on certificate") }, modifier = Modifier.fillMaxWidth())
        ChoiceField("Shipboard rank", data.preferredRank, ranks) { save(data.copy(preferredRank = it)) }
        ChoiceField("DP capacity", data.preferredCapacity, capacities) { save(data.copy(preferredCapacity = it)) }
        Text("These choices are used for each new tour. You can change them on a tour.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        OutlinedCard(onClick = onCertificate, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("Certificate", style = MaterialTheme.typography.titleLarge)
            Text(data.certificateNumber.ifBlank { "Add certificate details" })
            Text(if (remaining == null) "Set expiry date" else if (remaining < 0) "Expired ${-remaining} days ago" else "$remaining days to expire")
        } }
        OutlinedCard(onClick = onCpd, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("CPD / Training", style = MaterialTheme.typography.titleLarge)
            Text("$entries / 2 entries recorded for $year")
        } }
        HorizontalDivider()
        Text("Tour overview", style = MaterialTheme.typography.titleLarge)
        data.tours.forEach { t ->
            val totals = DpMath.totals(t, data.sessions)
            Text(data.vesselName(t), style = MaterialTheme.typography.titleMedium)
            Text("${totals.loggedHours} logged hours · ${totals.dpDays?.formatDays() ?: "—"} DP days")
            if (totals.provisional) Text("Provisional", style = MaterialTheme.typography.bodySmall)
            if (totals.issue != null) Text(totals.issue, color = MaterialTheme.colorScheme.error)
        }
        HorizontalDivider()
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export backup with photos") }
        OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) { Text("Restore backup") }
        Text("Restoring replaces all records in this app. Export the current data first.", style = MaterialTheme.typography.bodySmall)
        Text("Personal working record. Copy verified totals into your signed NI/IMCA logbook.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CertificateScreen(data: AppData, save: (AppData) -> Unit, onPhoto: () -> Unit, onOpenPhoto: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(data.certificateExpiry) }.getOrNull()
    val remaining = date?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
    val photo = data.attachments.filter { it.ownerId == "certificate" && it.category == "DP certificate" }.maxByOrNull { it.createdAtMillis }
    var notice by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onClose) { Text("Back to Me") }
        Text("Certificate", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { photo?.let { onOpenPhoto(it.fileName) } }, enabled = photo != null) { Text("View certificate") }
            OutlinedButton(onClick = onPhoto) { Text(if (photo == null) "Add photo" else "Update photo") }
        }
        OutlinedTextField(data.certificateNumber, { save(data.copy(certificateNumber = it)) }, label = { Text("DP certificate number") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { pickDate(context, data.certificateIssue) { save(data.copy(certificateIssue = it)) } }) { Text("Issue date: ${data.certificateIssue.ifBlank { "Choose date" }}") }
        OutlinedButton(onClick = { pickDate(context, data.certificateExpiry) { save(data.copy(certificateExpiry = it)) } }) { Text("Certificate expiry: ${data.certificateExpiry.ifBlank { "Choose date" }}") }
        Text(if (remaining == null) "Enter certificate expiry date" else if (remaining < 0) "Expired ${-remaining} days ago" else "$remaining days to expire", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(NiVerification.url(data.certificateNumber, data.lastName))))
            }.onFailure { notice = "Could not open NI verification page" }
        }, modifier = Modifier.fillMaxWidth()) { Text("Check validity with NI") }
        Text(if (data.certificateNumber.isBlank() || data.lastName.isBlank())
            "Add certificate number here and last name under Me for direct verification. The official form opens until both are set."
            else "Opens the NI certificate result using your saved number and last name.", style = MaterialTheme.typography.bodySmall)
        if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.error)
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
