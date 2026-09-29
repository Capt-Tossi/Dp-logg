package no.sordal.dpcompanion

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

private val activities = listOf("Cargo transfer", "Offshore loading (on DP)", "Position mooring / TAM (on DP)", "Anchor handling (on DP)", "Standby on DP", "ROV support", "Diving support", "Survey", "Drilling support", "DP set-up", "DP trials / FMEA", "DP training", "Other")
private val ranks = listOf("Master", "Chief Officer", "Second Officer", "Third Officer", "Other")
private val capacities = listOf("Senior DPO", "DPO", "Trainee DPO", "Senior DPO / DP Master", "Other")
private val vesselTypes = listOf("PSV", "AHTS / AHV", "Shuttle tanker / buoy loading", "Diving support vessel", "ROV support vessel", "Construction vessel", "Cable-laying vessel", "Survey vessel", "Drillship", "Dredger", "Other")
private val dpSystems = listOf("Kongsberg K-Pos", "Kongsberg cPos", "Kongsberg SDP-11", "ICON DP (Rolls-Royce / Thrustmaster)", "Marine Technologies Bridge Mate", "Brunvoll BruCon DP", "Wärtsilä NACOS DP Platinum", "GE SeaStream DP", "ABB Marine Pilot Control", "Navis DP", "Other")
private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)
private val stampFormat = DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm", java.util.Locale.ENGLISH)

@Composable
private fun SeaBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val base = Path().apply {
            moveTo(0f, size.height * .77f)
            cubicTo(size.width * .3f, size.height * .72f, size.width * .67f, size.height * .84f, size.width, size.height * .76f)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(base, Color(0xFF85BAC6).copy(alpha = .12f))
        val lower = Path().apply {
            moveTo(0f, size.height * .88f)
            cubicTo(size.width * .28f, size.height * .83f, size.width * .7f, size.height * .94f, size.width, size.height * .86f)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(lower, Color(0xFF417C9D).copy(alpha = .10f))
    }
}

@Composable
private fun CompanionApp(data: AppData, save: (AppData) -> Unit, store: Store) {
    val context = LocalContext.current
    LaunchedEffect(data.certificateExpiry, data.renewalReminderEnabled) { RenewalReminder.sync(context, data) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save(data.copy(renewalReminderEnabled = true))
    }
    var page by remember { mutableStateOf(0) }
    var pageHistory by remember { mutableStateOf(emptyList<Int>()) }
    var meSection by remember { mutableIntStateOf(0) }
    var editId by remember { mutableStateOf<String?>(null) }
    var showAllSessions by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            delay(3500L)
            message = ""
        }
    }
    var reportChoice by remember { mutableStateOf(ReportLayout.SUMMARY) }
    var reportTours by remember { mutableStateOf(emptyList<Tour>()) }
    var reportDetails by remember { mutableStateOf(LetterDetails("", "", "", "")) }
    var deleteSessionId by remember { mutableStateOf<String?>(null) }
    var deleteAttachmentId by remember { mutableStateOf<String?>(null) }
    var viewedPhoto by remember { mutableStateOf<Attachment?>(null) }
    var pendingOwner by rememberSaveable { mutableStateOf("") }
    var pendingCategory by rememberSaveable { mutableStateOf("") }
    var pendingFile by rememberSaveable { mutableStateOf("") }
    var pendingReplaceId by rememberSaveable { mutableStateOf("") }
    fun navigateTo(next: Int) {
        if (next != page) { pageHistory = pageHistory + page; page = next }
        editId = null; meSection = 0; showAllSessions = false
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (pendingFile.isNotBlank()) {
            val previous = data.attachments.firstOrNull { it.id == pendingReplaceId }
            val captured = File(File(context.filesDir, "photos"), pendingFile)
            if (success && captured.length() > 0L && (pendingReplaceId.isBlank() || previous != null)) {
                val updated = if (previous == null) data.attachments + Attachment(ownerId = pendingOwner, category = pendingCategory, fileName = pendingFile, createdAtMillis = System.currentTimeMillis())
                    else data.attachments.map { if (it.id == previous.id) it.copy(fileName = pendingFile, createdAtMillis = System.currentTimeMillis(), rotationDegrees = 0) else it }
                runCatching { save(data.copy(attachments = updated)) }
                    .onSuccess { if (previous != null) File(File(context.filesDir, "photos"), previous.fileName).delete() }
                    .onFailure { File(File(context.filesDir, "photos"), pendingFile).delete(); message = "Photo could not be saved: ${it.message}" }
            } else captured.delete()
        }
        pendingOwner = ""; pendingCategory = ""; pendingFile = ""; pendingReplaceId = ""
    }
    fun takePhoto(owner: String, category: String, replaceId: String = "") {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val name = "${UUID.randomUUID()}.jpg"
        val file = File(dir, name).apply { createNewFile() }
        pendingOwner = owner; pendingCategory = category; pendingFile = name; pendingReplaceId = replaceId
        runCatching { camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file)) }
            .onFailure { file.delete(); pendingOwner = ""; pendingCategory = ""; pendingFile = ""; pendingReplaceId = ""; message = "Camera unavailable: ${it.message}" }
    }
    fun replacePhoto(a: Attachment) = takePhoto(a.ownerId, a.category, a.id)
    fun viewPhoto(name: String) { viewedPhoto = data.attachments.firstOrNull { it.fileName == name } }
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
    val pdfExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { ReportExport.writePdf(ReportExport.lines(data, reportTours, reportChoice, reportDetails), it) } ?: error("Cannot open file")
        }.onSuccess { message = "PDF saved" }.onFailure { message = "PDF export failed: ${it.message}" }
    }
    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { ReportExport.writeCsv(data, reportTours, it) } ?: error("Cannot open file")
        }.onSuccess { message = "CSV saved" }.onFailure { message = "CSV export failed: ${it.message}" }
    }
    val wordExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { ReportExport.writeDocx(data, reportTours, reportChoice, reportDetails, it) } ?: error("Cannot open file")
        }.onSuccess { message = "Word draft saved" }.onFailure { message = "Word export failed: ${it.message}" }
    }
    BackHandler(enabled = editId == null && (viewedPhoto != null || deleteSessionId != null || deleteAttachmentId != null || meSection != 0 || showAllSessions || page != 0 || pageHistory.isNotEmpty())) {
        when {
            viewedPhoto != null -> viewedPhoto = null
            deleteAttachmentId != null -> deleteAttachmentId = null
            deleteSessionId != null -> deleteSessionId = null
            meSection != 0 && page == 3 -> meSection = 0
            showAllSessions && page == 0 -> showAllSessions = false
            pageHistory.isNotEmpty() -> {
                page = pageHistory.last(); pageHistory = pageHistory.dropLast(1)
                meSection = 0; showAllSessions = false
            }
            page != 0 -> { page = 0; meSection = 0; showAllSessions = false }
        }
    }
    if (deleteSessionId != null) AlertDialog(onDismissRequest = { deleteSessionId = null },
        title = { Text("Delete this DP session?") },
        text = { Text("This removes the selected session and its logged hours. The change will be included in future backups and reports.") },
        confirmButton = { TextButton(onClick = {
            val id = deleteSessionId ?: return@TextButton
            val removedPhotos = data.attachments.filter { it.ownerId == id }
            save(data.copy(sessions = data.sessions.filterNot { it.id == id }, attachments = data.attachments.filterNot { it.ownerId == id }))
            removedPhotos.forEach { File(File(context.filesDir, "photos"), it.fileName).delete() }
            if (editId == id) editId = null
            deleteSessionId = null
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteSessionId = null }) { Text("Cancel") } })
    if (deleteAttachmentId != null) AlertDialog(onDismissRequest = { deleteAttachmentId = null },
        title = { Text("Delete this photo?") },
        text = { Text("This removes the photo from the app and future backups.") },
        confirmButton = { TextButton(onClick = {
            val id = deleteAttachmentId
            val a = data.attachments.firstOrNull { it.id == id }
            if (a != null) {
                save(data.copy(attachments = data.attachments.filterNot { it.id == id }))
                File(File(context.filesDir, "photos"), a.fileName).delete()
            }
            deleteAttachmentId = null
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteAttachmentId = null }) { Text("Cancel") } })
    viewedPhoto?.let { a -> Dialog(onDismissRequest = { viewedPhoto = null }) {
        Surface(shape = MaterialTheme.shapes.medium) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(a.category, style = MaterialTheme.typography.titleMedium)
            PhotoImage(a, Modifier.fillMaxWidth().heightIn(max = 480.dp), ContentScale.Fit)
            TextButton(onClick = { viewedPhoto = null }) { Text("Close") }
        } }
    } }
    Scaffold(
        topBar = {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF032349), Color(0xFF064A8E), Color(0xFF0784BF))))
                    .statusBarsPadding().height(72.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("DP Companion", color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
        },
        bottomBar = { if (editId == null) NavigationBar {
            val icons = listOf(R.drawable.nav_dp, R.drawable.nav_sea_service, R.drawable.nav_vessels, R.drawable.nav_me)
            listOf("DP", "Sea Service", "My Vessels", "DPO").forEachIndexed { i, title ->
                NavigationBarItem(selected = page == i, onClick = { navigateTo(i) },
                    icon = { Image(painterResource(icons[i]), contentDescription = null, modifier = Modifier.size(30.dp)) },
                    label = { Text(if (i == 0) "DP Logg" else title, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1) })
            }
        } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
          SeaBackground()
          Column(Modifier.fillMaxSize()) {
            if (message.isNotBlank()) Text(message, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary)
            when (page) {
                0 -> if (editId != null) {
                    val session = data.sessions.firstOrNull { it.id == editId }
                    if (session != null) SessionEditor(session, data.tours.firstOrNull { it.id == session.tourId }, data.attachments.filter { it.ownerId == session.id },
                        onSave = { changed, reason ->
                            val corrected = if (changed.startMillis != session.startMillis || changed.endMillis != session.endMillis)
                                changed.copy(corrections = session.corrections + Correction(System.currentTimeMillis(), session.startMillis, session.endMillis, reason)) else changed
                            save(data.copy(sessions = data.sessions.map { if (it.id == session.id) corrected else it })); editId = null
                        }, onPhoto = { takePhoto(session.id, it) }, onClose = { editId = null }, onOpenPhoto = ::viewPhoto,
                        onReplacePhoto = ::replacePhoto, onDeletePhoto = { deleteAttachmentId = it.id }, onDelete = { deleteSessionId = session.id })
                    else editId = null
                } else HomeScreen(data, showAll = showAllSessions, onShowAll = { showAllSessions = it }, onStart = { tour ->
                    if (data.sessions.none { it.endMillis == null })
                        save(data.copy(sessions = data.sessions + DpSession(tourId = tour.id, startMillis = System.currentTimeMillis())))
                }, onStop = { active ->
                    val stoppedAt = System.currentTimeMillis()
                    save(data.copy(sessions = data.sessions.map { if (it.id == active.id) it.copy(endMillis = stoppedAt, originalEndMillis = stoppedAt) else it }))
                    editId = active.id
                }, onEdit = { editId = it }, onAddManual = { tour ->
                    val now = System.currentTimeMillis()
                    val session = DpSession(tourId = tour.id, startMillis = now - 3_600_000, endMillis = now)
                    save(data.copy(sessions = data.sessions + session)); editId = session.id
                }, onTours = { navigateTo(1) }, onDelete = { deleteSessionId = it })
                1 -> ToursScreen(data, save, onPhoto = { owner -> takePhoto(owner, "Service checklist") }, onOpenPhoto = ::viewPhoto,
                    onReplacePhoto = ::replacePhoto, onDeletePhoto = { deleteAttachmentId = it.id }, onVessels = { navigateTo(2) })
                2 -> VesselsScreen(data, save, onPhoto = { owner -> takePhoto(owner, "Vessel photo") }, onOpenPhoto = ::viewPhoto,
                    onReplacePhoto = ::replacePhoto, onDeletePhoto = { deleteAttachmentId = it.id })
                3 -> when (meSection) {
                    1 -> CertificateScreen(data, save, onReminderToggle = {
                        if (data.renewalReminderEnabled) save(data.copy(renewalReminderEnabled = false))
                        else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else save(data.copy(renewalReminderEnabled = true))
                    }, onPhoto = {
                        takePhoto("certificate", "DP certificate", data.attachments.filter { it.ownerId == "certificate" && it.category == "DP certificate" }.maxByOrNull { it.createdAtMillis }?.id ?: "")
                    }, onOpenPhoto = ::viewPhoto, onDeletePhoto = { deleteAttachmentId = it.id }, onClose = { meSection = 0 })
                    2 -> Column(Modifier.fillMaxSize()) {
                        TextButton(onClick = { meSection = 0 }) { Text("Back to DPO") }
                        Box(Modifier.weight(1f)) { CpdScreen(data, save, onPhoto = { owner -> takePhoto(owner, "CPD completion") }, onOpenPhoto = ::viewPhoto,
                            onReplacePhoto = ::replacePhoto, onDeletePhoto = { deleteAttachmentId = it.id }) }
                    }
                    3 -> ExportScreen(data, onClose = { meSection = 0 }, onPdf = { tours, layout, details ->
                        reportTours = tours; reportChoice = layout; reportDetails = details
                        pdfExport.launch("dp-companion-${layout.name.lowercase()}.pdf")
                    }, onWord = { tours, layout, details ->
                        reportTours = tours; reportChoice = layout; reportDetails = details
                        wordExport.launch("dp-companion-${layout.name.lowercase()}.docx")
                    }, onCsv = { tours -> reportTours = tours; csvExport.launch("dp-companion-sessions.csv") })
                    4 -> AboutScreen { meSection = 0 }
                    else -> MeScreen(data, save, onCertificate = { meSection = 1 }, onCpd = { meSection = 2 }, onReport = { meSection = 3 }, onAbout = { meSection = 4 }, onExport = { export.launch("dp-companion-backup.zip") }, onImport = { import.launch(arrayOf("application/zip", "application/octet-stream")) })
                }
            }
          }
        }
    }
}

@Composable
private fun HomeScreen(data: AppData, showAll: Boolean, onShowAll: (Boolean) -> Unit,
                       onStart: (Tour) -> Unit, onStop: (DpSession) -> Unit, onEdit: (String) -> Unit,
                       onAddManual: (Tour) -> Unit, onTours: () -> Unit, onDelete: (String) -> Unit) {
    val tour = data.tours.firstOrNull { it.id == data.activeTourId }
    val active = data.sessions.firstOrNull { it.endMillis == null }
    var selectedDelete by remember(tour?.id) { mutableStateOf<String?>(null) }
    var transitioning by remember(tour?.id) { mutableStateOf("") }
    var pendingStop by remember(tour?.id) { mutableStateOf<DpSession?>(null) }
    val context = LocalContext.current
    val motionDisabled = remember(context) { runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depressed = pressed || transitioning.isNotEmpty()
    val scale by animateFloatAsState(targetValue = if (depressed) .97f else 1f,
        animationSpec = tween(durationMillis = if (motionDisabled) 0 else 100), label = "DP control press")
    LaunchedEffect(transitioning) {
        if (transitioning.isNotEmpty()) {
            delay(if (motionDisabled) 0L else 170L)
            if (transitioning == "stop") pendingStop?.let { onEdit(it.id) }
            pendingStop = null
            transitioning = ""
        }
    }
    val sessions = tour?.let { t -> data.sessions.filter { it.tourId == t.id }.sortedByDescending { it.startMillis } } ?: emptyList()
    if (showAll && tour != null) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TextButton(onClick = { onShowAll(false) }) { Text("Back to recent sessions") } }
            item { Text("All sessions (${sessions.size})", style = MaterialTheme.typography.titleLarge) }
            items(sessions, key = { it.id }) { s -> SessionCard(s, tour, data, selectedDelete,
                onSelectDelete = { selectedDelete = it }, onEdit = onEdit,
                onDelete = { onDelete(it); selectedDelete = null }) }
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (tour == null) {
            Text("Set up a service period to begin", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onTours) { Text("Add service period") }
            return@Column
        }
        Text(data.vesselName(tour), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("IMO ${tour.imo.ifBlank { "—" }} · ${if (tour.mode == "Continuous DP") "Continuous" else "Normal"}")
        val totals = DpMath.totals(tour, data.sessions)
        Text("${totals.loggedHours} logged DP hours  ·  ${totals.dpDays?.formatDays() ?: if (totals.loggedHours == 0) "0" else "—"} DP days", style = MaterialTheme.typography.titleMedium)
        if (totals.provisional) Text("Provisional total until disembark date is entered", style = MaterialTheme.typography.bodySmall)
        if (totals.issue != null) Text(totals.issue, color = MaterialTheme.colorScheme.error)
        val displayedActive = active ?: pendingStop
        val showingStop = displayedActive != null && transitioning != "start"
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterStart) {
            if (showingStop) Column {
                Text("Recording since ${formatStamp(displayedActive!!.startMillis, tour.zoneId)}",
                    style = MaterialTheme.typography.titleMedium)
                if (displayedActive.tourId != tour.id)
                    Text("Active session belongs to another service period.", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
            }
        }
        val controlShape = RoundedCornerShape(48.dp)
        val gradient = if (showingStop) {
            if (depressed) listOf(Color(0xFFBB333B), Color(0xFFA5192C), Color(0xFF75111F))
            else listOf(Color(0xFFF05A5D), Color(0xFFCD2336), Color(0xFF941323))
        } else {
            if (depressed) listOf(Color(0xFF15994D), Color(0xFF087D43), Color(0xFF055A36))
            else listOf(Color(0xFF2BE46F), Color(0xFF08B251), Color(0xFF087545))
        }
        Button(
            onClick = {
                if (transitioning.isEmpty()) {
                    if (showingStop && active != null && active.tourId == tour.id) {
                        pendingStop = active
                        transitioning = "stop"
                        onStop(active) // Persist the stop timestamp before the visual transition.
                    } else if (!showingStop && active == null) {
                        transitioning = "start"
                        onStart(tour) // Persist the start timestamp before the visual transition.
                    }
                }
            },
            enabled = transitioning.isEmpty() && (!showingStop || displayedActive?.tourId == tour.id),
            interactionSource = interaction,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White,
                disabledContainerColor = Color.Transparent, disabledContentColor = Color.White),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.fillMaxWidth().height(88.dp)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationY = if (depressed) 2.dp.toPx() else 0f
                }
                .shadow(if (depressed) 2.dp else 8.dp, controlShape)
                .clip(controlShape)
                .background(Brush.verticalGradient(gradient), controlShape)
                .border(1.dp, if (depressed) Color.White.copy(alpha = .18f) else Color.White.copy(alpha = .45f), controlShape)
        ) {
            Icon(painterResource(if (showingStop) R.drawable.ic_dp_stop else R.drawable.ic_dp_start),
                contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text(if (showingStop) "STOP DP" else "START DP",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        if (!showingStop) OutlinedButton(onClick = { onAddManual(tour) },
            modifier = Modifier.fillMaxWidth()) { Text("Record past DP session") }
        HorizontalDivider()
        Text("Recent sessions", style = MaterialTheme.typography.titleLarge)
        sessions.take(8).forEach { s -> SessionCard(s, tour, data, selectedDelete,
            onSelectDelete = { selectedDelete = it }, onEdit = onEdit,
            onDelete = { onDelete(it); selectedDelete = null }) }
        if (sessions.size > 8) TextButton(onClick = { onShowAll(true) }, modifier = Modifier.fillMaxWidth()) { Text("View all sessions (${sessions.size})") }
    }
}

@Composable
private fun SessionCard(s: DpSession, tour: Tour, data: AppData, selectedDelete: String?, onSelectDelete: (String) -> Unit,
                        onEdit: (String) -> Unit, onDelete: (String) -> Unit) {
    val status = ReportReview.sessionStatus(s, tour, data.sessions)
    val tint = when (status) {
        RecordStatus.OVERLAP -> Color(0xFFFFD9D7)
        RecordStatus.INCOMPLETE -> Color(0xFFFFE3C2)
        RecordStatus.OK -> Color(0xFFDDF3DF)
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth().pointerInput(s.id) {
        coroutineScope { while (true) {
            awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false) }
            var longPressed = false
            val timer = launch { delay(900L); longPressed = true; onSelectDelete(s.id) }
            val up = awaitPointerEventScope { waitForUpOrCancellation() }
            timer.cancel()
            if (up != null) { if (longPressed) up.consume() else if (s.endMillis != null) onEdit(s.id) }
        } }
    }, colors = CardDefaults.outlinedCardColors(containerColor = tint)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${if (status == RecordStatus.OK) "✓" else "!"}  ${SessionDisplay.range(s, tour)}",
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (selectedDelete == s.id) Button(onClick = { onDelete(s.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF892026), contentColor = Color.White)) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
            Text(if (s.endMillis == null) "Recording in progress · ${s.activity.ifBlank { "Activity not set" }}"
                else "${DpMath.loggedHours(s.startMillis, s.endMillis)} logged hours · ${s.activity.ifBlank { "Activity not set" }}")
            val photoCount = data.attachments.count { it.ownerId == s.id }
            if (photoCount > 0) Text("📎 $photoCount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            ReportReview.sessionIssues(s, tour, data.sessions)
                .filterNot { (it == "Activity not set" && s.activity.isBlank()) || (it == "Session still running" && s.endMillis == null) }
                .forEach { Text(it) }
        }
    }
}

@Composable
private fun SessionEditor(session: DpSession, tour: Tour?, photos: List<Attachment>, onSave: (DpSession, String) -> Unit, onPhoto: (String) -> Unit, onClose: () -> Unit, onOpenPhoto: (String) -> Unit,
                          onReplacePhoto: (Attachment) -> Unit, onDeletePhoto: (Attachment) -> Unit, onDelete: () -> Unit) {
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
    var confirmDiscard by remember(session.id) { mutableStateOf(false) }
    val dirty = start != session.startMillis || end != (session.endMillis ?: end) ||
        activity != session.activity || location != session.location || notes != session.notes || reason.isNotBlank()
    fun requestClose() { if (dirty) confirmDiscard = true else onClose() }
    BackHandler { requestClose() }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard unsaved changes?") },
        text = { Text("Your changes to this DP session have not been saved.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } })
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { requestClose() }) { Text("Back") }
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
        }
        HorizontalDivider()
        Text("Documentation", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onPhoto("DP checklist") }, modifier = Modifier.weight(1f)) { Text("Photo DP checklist", textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            OutlinedButton(onClick = { onPhoto("Signed logbook page") }, modifier = Modifier.weight(1f)) { Text("Photo logbook page", textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        photos.forEach { a -> AttachmentItem(a, onOpenPhoto, onReplacePhoto, onDeletePhoto) }
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
private fun WatchHoursField(tour: Tour, onSave: (Double) -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var draft by remember(tour.id) { mutableStateOf(if (tour.dutyHours > 0) tour.dutyHours.toString() else "") }
    var error by remember(tour.id) { mutableStateOf("") }
    var hadFocus by remember(tour.id) { mutableStateOf(false) }
    fun commit() {
        val number = draft.replace(',', '.').toDoubleOrNull()
        if (number == null || !number.isFinite() || number <= 0.0 || number > 24.0) {
            error = "Enter watch hours greater than 0 and no more than 24"
        } else {
            error = ""
            onSave(number)
            draft = number.toString()
            hadFocus = false
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    OutlinedTextField(draft, { draft = it; error = "" }, label = { Text("Watch period (hours)") },
        isError = error.isNotBlank(), supportingText = { if (error.isNotBlank()) Text(error) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
            if (hadFocus && !state.isFocused) { hadFocus = false; commit() }
            hadFocus = state.isFocused
        })
}

@Composable
private fun OtherChoice(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var custom by remember(label) { mutableStateOf(value == "Other" || (value.isNotBlank() && value !in options)) }
    LaunchedEffect(value) {
        if (value in options) custom = value == "Other"
        else if (value.isNotBlank()) custom = true
    }
    ChoiceField(label, if (custom) "Other" else value, options) { custom = it == "Other"; onChange(it) }
    if (custom) OutlinedTextField(if (value == "Other") "" else value, onChange,
        label = { Text("Specify $label") }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PhotoImage(attachment: Attachment, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val bitmap = remember(attachment.fileName, attachment.rotationDegrees) {
        runCatching {
            val path = File(File(context.filesDir, "photos"), attachment.fileName).absolutePath
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > if (contentScale == ContentScale.Fit) 2048 else 512) sample *= 2
            val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Photo cannot be opened")
            val orientation = ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply { when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }; postRotate(attachment.rotationDegrees.toFloat()) }
            android.graphics.Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).asImageBitmap()
        }.getOrNull()
    }
    val shape = RoundedCornerShape(12.dp)
    if (bitmap != null) Image(bitmap, contentDescription = attachment.category, contentScale = contentScale,
        modifier = modifier.clip(shape).border(1.dp, Color(0xFF9EADB7), shape))
    else Text("Photo unavailable", modifier = modifier)
}

@Composable
private fun AttachmentItem(a: Attachment, onOpenPhoto: (String) -> Unit, onReplacePhoto: (Attachment) -> Unit, onDeletePhoto: (Attachment) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoImage(a, Modifier.size(72.dp))
            Column(Modifier.weight(1f)) {
                Text(a.category, style = MaterialTheme.typography.titleSmall)
                Text(formatStamp(a.createdAtMillis, ZoneId.systemDefault().id), style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { onOpenPhoto(a.fileName) }) { Text("View") }
            TextButton(onClick = { onReplacePhoto(a) }) { Text("Replace") }
            TextButton(onClick = { onDeletePhoto(a) }) { Text("Delete") }
        }
    }
}

@Composable
private fun VesselThumbnail(data: AppData, vesselId: String, size: Int = 90) {
    val attachment = data.attachments.filter { it.ownerId == vesselId && it.category == "Vessel photo" }.maxByOrNull { it.createdAtMillis }
    if (attachment != null) PhotoImage(attachment, Modifier.size(size.dp))
}

@Composable
private fun VesselsScreen(data: AppData, save: (AppData) -> Unit, onPhoto: (String) -> Unit, onOpenPhoto: (String) -> Unit,
                          onReplacePhoto: (Attachment) -> Unit, onDeletePhoto: (Attachment) -> Unit) {
    var selectedId by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
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
        Text("Save vessel details once, then select a vessel when creating a service period.")
        Button(onClick = { val v = Vessel(); save(data.copy(vessels = data.vessels + v)); selectedId = v.id; editing = true }) { Text("Add vessel") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column { data.vessels.forEach { v -> FilterChip(selected = v.id == selectedId,
                onClick = { selectedId = v.id; editing = false }, label = { Text(v.name.ifBlank { "New vessel" }) }) } }
            if (vessel != null) VesselThumbnail(data, vessel.id, 140)
        }
        if (vessel != null) {
            fun change(v: Vessel) { save(data.copy(vessels = data.vessels.map { if (it.id == vessel.id) v else it })) }
            if (!editing) {
                Text(vessel.name.ifBlank { "Unnamed vessel" }, style = MaterialTheme.typography.titleLarge)
                Text("IMO ${vessel.imo.ifBlank { "—" }} · ${vessel.type} · ${vessel.dpClass}")
                if (vessel.dpSystem.isNotBlank()) Text("DP system: ${vessel.dpSystem}")
                if (vessel.grossTonnage.isNotBlank()) Text("Gross tonnage: ${vessel.grossTonnage}")
                Button(onClick = { editing = true }) { Text("Update") }
            } else {
                OutlinedTextField(vessel.name, { change(vessel.copy(name = it)) }, label = { Text("Vessel name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(vessel.imo, { change(vessel.copy(imo = it)) }, label = { Text("IMO number") }, modifier = Modifier.fillMaxWidth())
                OtherChoice("vessel type", vessel.type, vesselTypes) { change(vessel.copy(type = it)) }
                ChoiceField("DP class", vessel.dpClass, listOf("DP1", "DP2", "DP3", "Other")) { change(vessel.copy(dpClass = it)) }
                OtherChoice("DP system", vessel.dpSystem, dpSystems) { change(vessel.copy(dpSystem = it)) }
                OutlinedTextField(vessel.grossTonnage, { change(vessel.copy(grossTonnage = it)) }, label = { Text("Gross tonnage (GT)") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { onPhoto(vessel.id) }) { Text("Photo vessel") }
                Button(onClick = { editing = false }, enabled = vessel.name.isNotBlank()) { Text("Done") }
            }
            data.attachments.filter { it.ownerId == vessel.id && it.category == "Vessel photo" }
                .forEach { a -> AttachmentItem(a, onOpenPhoto, onReplacePhoto, onDeletePhoto) }
            if (editing) TextButton(onClick = { confirmDelete = true }) { Text("Remove from My Vessels") }
        }
    }
}

@Composable
private fun ToursScreen(data: AppData, save: (AppData) -> Unit, onPhoto: (String) -> Unit, onOpenPhoto: (String) -> Unit,
                        onReplacePhoto: (Attachment) -> Unit, onDeletePhoto: (Attachment) -> Unit, onVessels: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val tour = data.tours.firstOrNull { it.id == data.activeTourId }
    var chosenId by remember { mutableStateOf("") }
    var vesselMenu by remember { mutableStateOf(false) }
    var editingAttachmentId by remember { mutableStateOf<String?>(null) }
    var attachmentLabel by remember { mutableStateOf("") }
    var replacingVessel by remember { mutableStateOf(false) }
    var replacementVesselId by remember { mutableStateOf("") }
    var confirmDeleteTour by remember { mutableStateOf(false) }
    val chosen = data.vessels.firstOrNull { it.id == chosenId }
    if (replacingVessel && tour != null) AlertDialog(
        onDismissRequest = { replacingVessel = false },
        title = { Text("Update vessel for this service period") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose a vessel to replace this period's saved vessel details. Sessions and documents stay with the period.")
            Column(Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState())) {
                data.vessels.forEach { v -> Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = replacementVesselId == v.id, onClick = { replacementVesselId = v.id })
                    TextButton(onClick = { replacementVesselId = v.id }) { Text(v.name.ifBlank { "Unnamed vessel" }) }
                } }
            }
            data.vessels.firstOrNull { it.id == replacementVesselId }?.let { v ->
                Text("Will save: ${v.name.ifBlank { "Unnamed vessel" }} · IMO ${v.imo.ifBlank { "—" }} · ${v.type} · ${v.dpClass}\nDP system: ${v.dpSystem.ifBlank { "Not recorded" }} · GT ${v.grossTonnage.ifBlank { "—" }}")
            }
        } },
        confirmButton = { TextButton(onClick = {
            save(data.reassignTourVessel(tour.id, replacementVesselId)); replacingVessel = false
        }, enabled = data.vessels.any { it.id == replacementVesselId }) { Text("Update period") } },
        dismissButton = { TextButton(onClick = { replacingVessel = false }) { Text("Cancel") } }
    )
    if (confirmDeleteTour && tour != null) {
        val sessionIds = data.sessions.filter { it.tourId == tour.id }.map { it.id }.toSet()
        val removedPhotos = data.attachments.filter { it.ownerId == tour.id || it.ownerId in sessionIds }
        AlertDialog(onDismissRequest = { confirmDeleteTour = false },
            title = { Text("Delete this service period?") },
            text = { Text("This permanently removes this period, its ${sessionIds.size} DP session(s), and ${removedPhotos.size} attached photo(s) from the app and future backups. The vessel in My Vessels and other periods stay.") },
            confirmButton = { TextButton(onClick = {
                save(data.removeTour(tour.id))
                removedPhotos.forEach { File(File(context.filesDir, "photos"), it.fileName).delete() }
                confirmDeleteTour = false
            }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete period") } },
            dismissButton = { TextButton(onClick = { confirmDeleteTour = false }) { Text("Cancel") } })
    }
    if (editingAttachmentId != null) AlertDialog(
        onDismissRequest = { editingAttachmentId = null },
        title = { Text("Edit photo label") },
        text = { OutlinedTextField(attachmentLabel, { attachmentLabel = it }, label = { Text("Document type") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            val id = editingAttachmentId
            val label = attachmentLabel.trim()
            if (id != null && label.isNotBlank()) save(data.copy(attachments = data.attachments.map { if (it.id == id) it.copy(category = label) else it }))
            editingAttachmentId = null
        }, enabled = attachmentLabel.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = { editingAttachmentId = null }) { Text("Cancel") } }
    )
    Column(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { focus.clearFocus(); keyboard?.hide() }) }
        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sea Service", style = MaterialTheme.typography.headlineSmall)
        if (data.vessels.isEmpty()) {
            Text("Add a vessel before creating a service period.")
            Button(onClick = onVessels) { Text("Open My Vessels") }
        } else {
            Box {
                OutlinedButton(onClick = { vesselMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Vessel for new service period: ${chosen?.name ?: "Choose"}") }
                DropdownMenu(expanded = vesselMenu, onDismissRequest = { vesselMenu = false }) {
                    data.vessels.forEach { v -> DropdownMenuItem(text = { Text("${v.name.ifBlank { "Unnamed vessel" }} · ${v.imo.ifBlank { "No IMO" }}") }, onClick = { chosenId = v.id; vesselMenu = false }) }
                }
            }
            Button(onClick = { chosen?.let { v -> val t = data.newTour(v); save(data.copy(tours = data.tours + t, activeTourId = t.id)) } }, enabled = chosen != null && chosen.name.isNotBlank()) { Text("Add service period") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
          data.tours.sortedWith(compareByDescending<Tour> { it.signedOn }.thenByDescending { it.id }).forEach { t ->
              FilterChip(selected = t.id == data.activeTourId, onClick = { save(data.copy(activeTourId = t.id)) },
                  label = { Column { Text(data.vesselName(t)); Text("${t.signedOn.ifBlank { "Date missing" }} – ${t.disembarked.ifBlank { "Ongoing" }}", style = MaterialTheme.typography.labelSmall) } })
          }
        }
        if (tour != null) {
            fun change(t: Tour) { save(data.copy(tours = data.tours.map { if (it.id == tour.id) t else it })) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(data.vesselName(tour), style = MaterialTheme.typography.titleLarge)
                    Text("IMO ${tour.imo.ifBlank { "—" }} · ${tour.vesselType} · ${tour.dpClass}")
                    Text("DP system: ${tour.dpSystem.ifBlank { "Not recorded" }}")
                    val totals = DpMath.totals(tour, data.sessions)
                    Text("${totals.loggedHours} logged DP hours · ${totals.dpDays?.formatDays() ?: if (totals.loggedHours == 0) "0" else "—"} DP days")
                    if (totals.provisional) Text("Provisional until disembarked date is entered", style = MaterialTheme.typography.bodySmall)
                    if (totals.issue != null) Text(totals.issue, color = MaterialTheme.colorScheme.error)
                }
                if (data.vessels.any { it.id == tour.vesselId }) VesselThumbnail(data, tour.vesselId)
            }
            Text("Vessel details are saved as a snapshot for this service period.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { replacementVesselId = tour.vesselId; replacingVessel = true },
                enabled = data.vessels.isNotEmpty()) { Text("Update vessel for this period") }
            OtherChoice("Shipboard rank", tour.rank, ranks) { value ->
                save(data.copy(tours = data.tours.map { if (it.id == tour.id) tour.copy(rank = value) else it }, preferredRank = value))
            }
            OtherChoice("DP capacity", tour.capacity, capacities) { value ->
                save(data.copy(tours = data.tours.map { if (it.id == tour.id) tour.copy(capacity = value) else it }, preferredCapacity = value))
            }
            OutlinedButton(onClick = { pickDate(context, tour.signedOn) { change(tour.copy(signedOn = it)) } }) { Text("Signed on: ${tour.signedOn.ifBlank { "Choose date" }}") }
            OutlinedButton(onClick = { pickDate(context, tour.disembarked) { change(tour.copy(disembarked = it)) } }) { Text("Disembarked: ${tour.disembarked.ifBlank { "Choose date" }}") }
            ChoiceField("DP scheme", tour.scheme, listOf("Offshore DP", "Shuttle tanker (restricted)")) { change(tour.copy(scheme = it)) }
            if (tour.scheme == "Shuttle tanker (restricted)") Text("Offshore loading operations must be recorded and verified separately. DP hours and day estimates here do not establish Shuttle Tanker scheme eligibility.", style = MaterialTheme.typography.bodySmall)
            Text("Operating mode for this service period", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tour.mode == "Short operations", onClick = { change(tour.copy(mode = "Short operations")) }, label = { Text("Normal") })
                FilterChip(selected = tour.mode == "Continuous DP", onClick = { change(tour.copy(mode = "Continuous DP")) }, label = { Text("Continuous") })
            }
            if (tour.mode == "Continuous DP") WatchHoursField(tour) { value -> change(tour.copy(dutyHours = value)) }
            OutlinedButton(onClick = { onPhoto(tour.id) }) { Text("Photo service checklist") }
            data.attachments.filter { it.ownerId == tour.id }.forEachIndexed { index, a ->
                Text("Photo ${index + 1}", style = MaterialTheme.typography.labelMedium)
                AttachmentItem(a, onOpenPhoto, onReplacePhoto, onDeletePhoto)
                TextButton(onClick = { editingAttachmentId = a.id; attachmentLabel = a.category }) { Text("Edit label") }
            }
            TextButton(onClick = { confirmDeleteTour = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete service period") }
        }
    }
}

@Composable
private fun CpdScreen(data: AppData, save: (AppData) -> Unit, onPhoto: (String) -> Unit, onOpenPhoto: (String) -> Unit,
                      onReplacePhoto: (Attachment) -> Unit, onDeletePhoto: (Attachment) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("CPD") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val thisYear = LocalDate.now().year
    val count = data.cpd.count { it.completedDate.startsWith(thisYear.toString()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("CPD / Training", style = MaterialTheme.typography.headlineSmall)
        Text("$count / 2 CPD or training entries recorded for $thisYear")
        if (data.cpd6Completed) Text("Previous CPD 6 completion recorded")
        ChoiceField("Previous CPD 6 status", if (data.cpd6Completed) "Completed" else "Not recorded", listOf("Not recorded", "Completed")) {
            save(data.copy(cpd6Completed = it == "Completed"))
        }
        ChoiceField("Type", kind, listOf("CPD", "Training course")) { kind = it }
        OutlinedTextField(title, { title = it }, label = { Text("Activity / course") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { pickDate(context, date) { date = it } }) { Text("Completed: $date") }
        Button(onClick = { if (title.isNotBlank()) { save(data.copy(cpd = data.cpd + CpdEntry(title = title.trim(), completedDate = date, kind = kind))); title = "" } }) { Text("Add entry") }
        data.cpd.sortedByDescending { it.completedDate }.forEach { c ->
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text(c.title, fontWeight = FontWeight.SemiBold); Text("${c.kind} · ${c.completedDate}")
                TextButton(onClick = { onPhoto(c.id) }) { Text("Photo completion") }
                data.attachments.filter { it.ownerId == c.id }.forEach { a -> AttachmentItem(a, onOpenPhoto, onReplacePhoto, onDeletePhoto) }
            } }
        }
    }
}

@Composable
private fun MeScreen(data: AppData, save: (AppData) -> Unit, onCertificate: () -> Unit, onCpd: () -> Unit, onReport: () -> Unit, onAbout: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    val context = LocalContext.current
    var confirmRestore by remember { mutableStateOf(false) }
    if (confirmRestore) AlertDialog(onDismissRequest = { confirmRestore = false },
        title = { Text("Restore backup?") },
        text = { Text("This replaces the records currently on this device. Export them first if you want to keep them.") },
        confirmButton = { TextButton(onClick = { confirmRestore = false; onImport() }) { Text("Choose backup") } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } })
    val year = LocalDate.now().year
    val entries = data.cpd.count { it.completedDate.startsWith(year.toString()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("DPO personal details", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(data.fullName, { save(data.copy(fullName = it)) }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(data.lastName, { save(data.copy(lastName = it)) }, label = { Text("Last name on certificate") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { pickDate(context, data.dateOfBirth) { save(data.copy(dateOfBirth = it)) } }) { Text("Date of birth: ${data.dateOfBirth.ifBlank { "Choose date" }}") }
        OtherChoice("Shipboard rank", data.preferredRank, ranks) { save(data.copy(preferredRank = it)) }
        OtherChoice("DP capacity", data.preferredCapacity, capacities) { save(data.copy(preferredCapacity = it)) }
        Text("These choices are used for each new service period. You can change them there.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        OutlinedCard(onClick = onCertificate, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Certificate", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Text(data.certificateNumber.ifBlank { "Add number" },
                    modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (data.certificateExpiry.isNotBlank()) Text("Expires ${displayDate(data.certificateExpiry)}", style = MaterialTheme.typography.bodyMedium)
            Text(CertificateRenewal.status(data.certificateExpiry), style = MaterialTheme.typography.titleSmall)
            CertificateRenewal.message(data.certificateExpiry).takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = if (it == "Renewal applications are open") Color(0xFF176B49) else MaterialTheme.colorScheme.primary)
            }
        } }
        OutlinedCard(onClick = onCpd, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("CPD / Training", style = MaterialTheme.typography.titleLarge)
            Text("$entries / 2 entries recorded for $year")
        } }
        HorizontalDivider()
        Text("Sea Service overview", style = MaterialTheme.typography.titleLarge)
        data.tours.sortedByDescending { it.signedOn }.forEach { t ->
            val totals = DpMath.totals(t, data.sessions)
            Text(data.vesselName(t), style = MaterialTheme.typography.titleMedium)
            Text("${t.signedOn.ifBlank { "Date missing" }} – ${t.disembarked.ifBlank { "Ongoing" }} · ${totals.loggedHours} h · ${totals.dpDays?.formatDays() ?: if (totals.loggedHours == 0) "0" else "—"} DP days")
            if (totals.provisional) Text("Provisional", style = MaterialTheme.typography.bodySmall)
            if (totals.issue != null) Text(totals.issue, color = MaterialTheme.colorScheme.error)
        }
        HorizontalDivider()
        Button(onClick = onReport, modifier = Modifier.fillMaxWidth()) { Text("Export reports / confirmation letter") }
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export backup with photos") }
        OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) { Text("Restore backup") }
        Text("Restoring replaces all records in this app. Export the current data first.", style = MaterialTheme.typography.bodySmall)
        Text("Personal working record. Copy verified totals into your signed NI/IMCA logbook.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onAbout) { Text("About DP Companion") }
    }
}

@Composable
private fun AboutScreen(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onClose) { Text("Back to DPO") }
        Text("About DP Companion", style = MaterialTheme.typography.headlineSmall)
        Text("Version 0.6.1", style = MaterialTheme.typography.titleMedium)
        Text("Developed with Torstein Sørdal, Master and Senior DPO, to make it easier to record DP sessions during work at sea and prepare accurate sea service summaries.")
        Text("Record start and stop times, correct entries later, manage vessels and service periods, save photos of supporting documents, track CPD/training and certificate validity, and export drafts for company verification.")
        Text("Your records and photos stay on this device unless you choose to export or share them. Export a backup regularly. The app works offline; opening NI certificate verification requires a connection.")
        Text("This is a personal working record. Confirm DP time against the vessel's records and the signed NI/IMCA logbook. A company confirmation letter is a draft until an authorised company representative verifies and signs it.")
        HorizontalDivider()
        Text("Changelog", style = MaterialTheme.typography.titleLarge)
        Text("Version 0.6.1", style = MaterialTheme.typography.titleMedium)
        Text("Gradient START and STOP controls with fixed position and pressed animation; blue gradient header and adaptive icon; one-line DP Logg label; clearer certificate card and sea background.")
        Text("Version 0.5.3", style = MaterialTheme.typography.titleMedium)
        Text("Edit or delete Sea Service periods; clearer certificate renewal text; system Back navigation; session time ranges; centered DP / Logg label; refreshed START DP control.")
        Text("Version 0.5.2", style = MaterialTheme.typography.titleMedium)
        Text("Expanded the launcher icon and removed its white surrounding ring.")
        Text("Version 0.5.1", style = MaterialTheme.typography.titleMedium)
        Text("Added certificate renewal window and optional offline reminder, improved Sea Service vessel details and export drafts, photo presentation, session deletion, and Continuous watch-hour entry.")
    }
}

@Composable
private fun ExportScreen(data: AppData, onClose: () -> Unit,
                         onPdf: (List<Tour>, ReportLayout, LetterDetails) -> Unit,
                         onWord: (List<Tour>, ReportLayout, LetterDetails) -> Unit,
                         onCsv: (List<Tour>) -> Unit) {
    val context = LocalContext.current
    val choices = ReportSelection.vessels(data)
    var selectedId by remember { mutableStateOf("") }
    var layout by remember { mutableStateOf(ReportLayout.SUMMARY) }
    var company by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf(data.dateOfBirth) }
    var grt by remember { mutableStateOf("") }
    val selected = choices.firstOrNull { it.vesselId == selectedId }
    val vesselLabels = choices.mapIndexed { index, choice ->
        "${choice.tours.first().vessel} · IMO ${choice.tours.first().imo.ifBlank { "—" }} · ${index + 1}"
    }
    val tours = selected?.tours.orEmpty()
    val letter = layout == ReportLayout.NEW_SCHEME || layout == ReportLayout.OLD_SCHEME || layout == ReportLayout.IMCA
    val issues = if (letter) ReportSelection.confirmationIssues(data, tours, layout, data.fullName, company, dob, grt) else emptyList()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onClose) { Text("Back to DPO") }
        Text("Export", style = MaterialTheme.typography.headlineSmall)
        Text("Choose a vessel. All its service periods are included in the selected report.")
        ChoiceField("Vessel", selected?.let { vesselLabels.getOrNull(choices.indexOf(it)) }.orEmpty(), vesselLabels) { name ->
            val choice = choices.getOrNull(vesselLabels.indexOf(name))
            selectedId = choice?.vesselId ?: ""
            grt = choice?.tours?.firstOrNull()?.grossTonnage.orEmpty()
        }
        if (selected != null) {
            Text("${tours.size} service period(s) · IMO ${tours.first().imo}", style = MaterialTheme.typography.titleMedium)
            ChoiceField("Export type", if (letter) "Confirmation letter" else layout.title,
                listOf(ReportLayout.SUMMARY.title, ReportLayout.DETAILED.title, "Confirmation letter")) { label ->
                layout = when (label) {
                    "Confirmation letter" -> ReportLayout.NEW_SCHEME
                    ReportLayout.DETAILED.title -> ReportLayout.DETAILED
                    else -> ReportLayout.SUMMARY
                }
            }
            if (letter) {
                ChoiceField("Letter layout", when (layout) { ReportLayout.NEW_SCHEME -> "NI New Scheme"; ReportLayout.OLD_SCHEME -> "NI Old Scheme / Revalidation"; else -> "IMCA logbook draft" },
                    listOf("NI New Scheme", "NI Old Scheme / Revalidation", "IMCA logbook draft")) { label ->
                    layout = when (label) { "NI New Scheme" -> ReportLayout.NEW_SCHEME; "NI Old Scheme / Revalidation" -> ReportLayout.OLD_SCHEME; else -> ReportLayout.IMCA }
                }
                Text("Draft for employer verification. The company must check the figures and sign the letter.")
                if (layout == ReportLayout.IMCA) Text("IMCA hours draft is not an NI confirmation-letter template. Select the matching NI scheme for an NI application.", color = Color(0xFFAF5200))
                OutlinedTextField(company, { company = it }, label = { Text("Company name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it }, label = { Text("Company address") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { pickDate(context, dob) { dob = it } }) { Text("Date of birth: ${dob.ifBlank { "Choose date" }}") }
                OutlinedTextField(grt, { grt = it }, label = { Text("Gross tonnage fallback (GT)") }, modifier = Modifier.fillMaxWidth())
                Text("Each service period uses its saved vessel gross tonnage. This field only fills older records that have none.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Review before export", style = MaterialTheme.typography.titleLarge)
            tours.forEach { tour ->
                val totals = DpMath.totals(tour, data.sessions)
                Text("${tour.vessel} · ${tour.signedOn.ifBlank { "Date missing" }} to ${tour.disembarked.ifBlank { "ongoing" }}")
                Text("${totals.loggedHours} DP hours · ${totals.dpDays?.formatDays() ?: if (totals.loggedHours == 0) "0" else "—"} DP days · ${data.sessions.count { it.tourId == tour.id }} sessions")
                ReportReview.findings(tour, data.sessions).forEach { finding ->
                    Text("! ${finding.description}", color = Color(0xFFAF5200))
                }
            }
            if (issues.isNotEmpty()) issues.forEach { Text("! $it", color = MaterialTheme.colorScheme.error) }
            Button(onClick = { onWord(tours, layout, LetterDetails(company,address,dob,grt)) }, enabled = !letter || issues.isEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Save editable Word (.docx)") }
            if (!letter) OutlinedButton(onClick = { onPdf(tours, layout, LetterDetails(company,address,dob,grt)) }, modifier = Modifier.fillMaxWidth()) { Text("Save PDF") }
            if (!letter) OutlinedButton(onClick = { onCsv(tours) }, modifier = Modifier.fillMaxWidth()) { Text("Save session CSV") }
        } else Text("Choose a vessel to preview its service periods.")
    }
}

@Composable
private fun CertificateScreen(data: AppData, save: (AppData) -> Unit, onPhoto: () -> Unit, onOpenPhoto: (String) -> Unit,
                              onDeletePhoto: (Attachment) -> Unit, onClose: () -> Unit, onReminderToggle: () -> Unit) {
    val context = LocalContext.current
    val photo = data.attachments.filter { it.ownerId == "certificate" && it.category == "DP certificate" }.maxByOrNull { it.createdAtMillis }
    var notice by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onClose) { Text("Back to DPO") }
        Text("Certificate", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { photo?.let { onOpenPhoto(it.fileName) } }, enabled = photo != null) { Text("View certificate") }
            OutlinedButton(onClick = onPhoto) { Text(if (photo == null) "Add photo" else "Update photo") }
        }
        if (photo != null) TextButton(onClick = { onDeletePhoto(photo) }) { Text("Delete certificate photo") }
        OutlinedTextField(data.certificateNumber, { save(data.copy(certificateNumber = it)) }, label = { Text("DP certificate number") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { pickDate(context, data.certificateIssue) { save(data.copy(certificateIssue = it)) } }) { Text("Issue date: ${data.certificateIssue.ifBlank { "Choose date" }}") }
        OutlinedButton(onClick = { pickDate(context, data.certificateExpiry) { save(data.copy(certificateExpiry = it)) } }) { Text("Certificate expiry: ${data.certificateExpiry.ifBlank { "Choose date" }}") }
        Text("Expiry date: ${data.certificateExpiry.takeIf { it.isNotBlank() }?.let(::displayDate) ?: "Choose date"}")
        Text(CertificateRenewal.status(data.certificateExpiry), style = MaterialTheme.typography.titleMedium)
        CertificateRenewal.message(data.certificateExpiry).takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleMedium,
                color = if (it == "Renewal applications are open") Color(0xFF176B49) else MaterialTheme.colorScheme.primary)
        }
        if (data.certificateExpiry.isNotBlank()) Text("NI online revalidation can be started six calendar months before expiry. Other requirements still apply.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onReminderToggle, enabled = data.certificateExpiry.isNotBlank()) {
            Text(if (data.renewalReminderEnabled) "Renewal reminder: On" else "Renewal reminder: Off")
        }
        Text("Optional local reminder on the opening date. The app works offline.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(NiVerification.url(data.certificateNumber, data.lastName))))
            }.onFailure { notice = "Could not open NI verification page" }
        }, modifier = Modifier.fillMaxWidth()) { Text("Check validity with NI") }
        Text(if (data.certificateNumber.isBlank() || data.lastName.isBlank())
            "Add certificate number here and last name under DPO for direct verification. The official form opens until both are set."
            else "Opens the NI certificate result using your saved number and last name.", style = MaterialTheme.typography.bodySmall)
        if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.error)
    }
}

private fun Double.formatDays() = if (this % 1.0 == 0.0) toInt().toString() else "%.2f".format(java.util.Locale.US, this)
private fun displayDate(iso: String): String = runCatching { LocalDate.parse(iso).format(dateFormat) }.getOrDefault(iso)
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
