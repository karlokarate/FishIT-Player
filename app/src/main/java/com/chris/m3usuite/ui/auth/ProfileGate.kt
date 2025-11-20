package com.chris.m3usuite.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chris.m3usuite.data.obx.ObxProfile
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.repo.ProfileObxRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.auth.CreateProfileSheet
import com.chris.m3usuite.ui.debug.safePainter
import com.chris.m3usuite.ui.focus.TvRow
import com.chris.m3usuite.ui.focus.focusGroup
import com.chris.m3usuite.ui.theme.DesignTokens
import com.chris.m3usuite.ui.util.rememberAvatarModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Bringt fokussierte Elemente automatisch in den sichtbaren Bereich des nächsten Scrollers.
 * Funktioniert in LazyColumn/LazyRow sowie vertical/horizontalScroll.
 */
// Bring-into-view now centralized via FocusKit.focusBringIntoViewOnFocus()

@Composable
fun ProfileGate(onEnter: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        com.chris.m3usuite.metrics.RouteTag
            .set("gate")
        com.chris.m3usuite.core.debug.GlobalDebug
            .logTree("gate:root")
    }

    val store = remember { SettingsStore(ctx) }
    val profileRepo = remember { ProfileObxRepository(ctx) }
    val scope = rememberCoroutineScope()

    var adult by remember { mutableStateOf<ObxProfile?>(null) }
    var kids by remember { mutableStateOf<List<ObxProfile>>(emptyList()) }

    // PIN UI state (persist across rotation)
    var showPin by rememberSaveable { mutableStateOf(false) }
    var setPin by rememberSaveable { mutableStateOf(false) }
    var pin by rememberSaveable { mutableStateOf("") }
    var pin2 by rememberSaveable { mutableStateOf("") }
    var pinError by rememberSaveable { mutableStateOf<String?>(null) }

    val pinSet by store.adultPinSet.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { profileRepo.all() }
        adult = list.firstOrNull { it.type == "adult" }
        kids = list.filter { it.type != "adult" } // show Kid + Guest

        // Skip only if user opted in to remember last profile
        val remember = store.rememberLastProfile.first()
        val cur = store.currentProfileId.first()
        if (remember && cur > 0) onEnter()
    }

    fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val dig = md.digest(s.toByteArray())
        return dig.joinToString("") { b -> "%02x".format(b) }
    }

    @Composable
    fun PinDialog(onDismiss: () -> Unit) {
        var stage by rememberSaveable(setPin) { mutableStateOf(if (setPin) 1 else 2) }
        val requiredDigits = 4

        val keypadRows =
            remember {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("CLR", "0", "DEL"),
                )
            }

        val focusers = remember { mutableStateMapOf<String, FocusRequester>() }
        val confirmRequester = remember { FocusRequester() }
        val cancelRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            keypadRows.flatten().forEach { key -> focusers.getOrPut(key) { FocusRequester() } }
        }
        LaunchedEffect(stage) {
            pinError = null
            focusers["5"]?.requestFocus()
        }
        LaunchedEffect(pinError) {
            if (pinError != null) {
                focusers["5"]?.requestFocus()
            }
        }

        val activePinValue = if (setPin && stage == 2) pin2 else pin

        fun updateActivePin(value: String) {
            if (setPin && stage == 2) {
                pin2 = value
            } else {
                pin = value
            }
        }

        fun confirmAction() {
            if (setPin) {
                if (stage == 1) {
                    if (pin.length < requiredDigits) {
                        pinError = "Mindestens 4 Ziffern"
                        return
                    }
                    stage = 2
                    pin2 = ""
                    pinError = null
                    return
                }
                if (pin2.length < requiredDigits) {
                    pinError = "Mindestens 4 Ziffern"
                    return
                }
                if (pin != pin2) {
                    pinError = "PINs stimmen nicht"
                    pin2 = ""
                    return
                }
                val hash = sha256(pin)
                scope.launch {
                    store.setAdultPinHash(hash)
                    store.setAdultPinSet(true)
                    val existing =
                        withContext(Dispatchers.IO) {
                            profileRepo.all().firstOrNull { it.type == "adult" }
                        }
                    if (existing != null) {
                        store.setCurrentProfileId(existing.id)
                    } else {
                        val now = System.currentTimeMillis()
                        val profile =
                            ObxProfile(
                                name = "Erwachsener",
                                type = "adult",
                                avatarPath = null,
                                createdAt = now,
                                updatedAt = now,
                            )
                        val newId = withContext(Dispatchers.IO) { profileRepo.insert(profile) }
                        store.setCurrentProfileId(newId)
                    }
                    pin = ""
                    pin2 = ""
                    showPin = false
                    stage = 1
                    onEnter()
                }
            } else {
                if (pin.length < requiredDigits) {
                    pinError = "Mindestens 4 Ziffern"
                    return
                }
                scope.launch {
                    val ok = sha256(pin) == store.adultPinHash.first()
                    if (ok) {
                        val existing =
                            withContext(Dispatchers.IO) {
                                profileRepo.all().firstOrNull { it.type == "adult" }
                            }
                        if (existing != null) {
                            store.setCurrentProfileId(existing.id)
                        } else {
                            val now = System.currentTimeMillis()
                            val profile =
                                ObxProfile(
                                    name = "Erwachsener",
                                    type = "adult",
                                    avatarPath = null,
                                    createdAt = now,
                                    updatedAt = now,
                                )
                            val newId = withContext(Dispatchers.IO) { profileRepo.insert(profile) }
                            store.setCurrentProfileId(newId)
                        }
                        pin = ""
                        pin2 = ""
                        showPin = false
                        stage = if (setPin) 1 else 2
                        onEnter()
                    } else {
                        pinError = "Falscher PIN"
                        pin = ""
                    }
                }
            }
        }

        fun handleKey(key: String) {
            when (key) {
                "CLR" -> {
                    updateActivePin("")
                    pinError = null
                    return
                }
                "DEL" -> {
                    updateActivePin(activePinValue.dropLast(1))
                    pinError = null
                    return
                }
                else ->
                    if (key.length == 1 && key[0].isDigit()) {
                        if (activePinValue.length < 8) {
                            val updated = activePinValue + key
                            updateActivePin(updated)
                            pinError = null
                            val shouldAutoConfirm =
                                when {
                                    setPin && stage == 1 -> updated.length >= requiredDigits
                                    setPin && stage == 2 -> updated.length >= requiredDigits
                                    !setPin -> updated.length >= requiredDigits
                                    else -> false
                                }
                            if (shouldAutoConfirm) {
                                confirmAction()
                                return
                            }
                        }
                    }
            }
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = null,
            text = {
                val pinScroll = rememberScrollState()
                Column(
                    modifier = Modifier.verticalScroll(pinScroll),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        tonalElevation = 2.dp,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    ) {
                        val display =
                            if (activePinValue.isEmpty()) {
                                "– – – –"
                            } else {
                                activePinValue.map { '•' }.joinToString(" ")
                            }
                        Text(
                            display,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.headlineSmall,
                            letterSpacing = 2.sp,
                        )
                    }
                    if (pinError != null) {
                        Text(
                            pinError!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                    // Keypad als eigener Fokus-Container
                    Column(modifier = Modifier.focusGroup()) {
                        keypadRows.forEachIndexed { rowIndex, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEachIndexed { colIndex, key ->
                                    val requester = focusers.getOrPut(key) { FocusRequester() }
                                    val label =
                                        when (key) {
                                            "CLR" -> "CLR"
                                            "DEL" -> "⌫"
                                            else -> key
                                        }
                                    val neighbor: (Int, Int) -> FocusRequester? = { rOffset, cOffset ->
                                        val r = rowIndex + rOffset
                                        val c = colIndex + cOffset
                                        if (r in keypadRows.indices) {
                                            val targetKey = keypadRows[r].getOrNull(c)
                                            if (targetKey != null) focusers[targetKey] else null
                                        } else {
                                            null
                                        }
                                    }
                                    Surface(
                                        modifier =
                                            Modifier
                                                .size(68.dp)
                                                .focusRequester(requester)
                                                .then(
                                                    com.chris.m3usuite.ui.focus
                                                        .run { Modifier.focusBringIntoViewOnFocus() },
                                                ) // zentral: beim Fokussieren sichtbar machen
                                                .then(
                                                    com.chris.m3usuite.ui.focus.run {
                                                        Modifier.tvFocusFrame(
                                                            focusedScale = 1.08f,
                                                            pressedScale = 1.04f,
                                                            shape = RoundedCornerShape(16.dp),
                                                            focusBorderWidth = 2.dp,
                                                        )
                                                    },
                                                ).focusProperties {
                                                    up = neighbor(-1, 0) ?: requester
                                                    down = neighbor(1, 0) ?: confirmRequester
                                                    left = neighbor(0, -1) ?: requester
                                                    right = neighbor(0, 1) ?: requester
                                                }.then(
                                                    com.chris.m3usuite.ui.focus.run {
                                                        Modifier.tvClickable(
                                                            role = androidx.compose.ui.semantics.Role.Button,
                                                            scaleFocused = 1f,
                                                            scalePressed = 1f,
                                                            focusBorderWidth = 0.dp,
                                                            brightenContent = false,
                                                            debugTag = "gate:key:$key",
                                                            onClick = { handleKey(key) },
                                                        )
                                                    },
                                                ),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(label, style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val label = if (setPin && stage == 1) "Weiter" else "OK"
                com.chris.m3usuite.ui.focus.FocusKit.TvTextButton(
                    modifier =
                        Modifier
                            .focusRequester(confirmRequester)
                            .focusable()
                            .then(
                                com.chris.m3usuite.ui.focus
                                    .run { Modifier.focusBringIntoViewOnFocus() },
                            ) // zentral
                            .focusProperties {
                                up = focusers["0"] ?: confirmRequester
                                left = cancelRequester
                                right = cancelRequester
                            },
                    onClick = { confirmAction() },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = DesignTokens.KidAccent,
                        ),
                ) { Text(label) }
            },
            dismissButton = {
                com.chris.m3usuite.ui.focus.FocusKit.TvTextButton(
                    modifier =
                        Modifier
                            .focusRequester(cancelRequester)
                            .focusable()
                            .then(
                                com.chris.m3usuite.ui.focus
                                    .run { Modifier.focusBringIntoViewOnFocus() },
                            ) // zentral
                            .focusProperties {
                                up = focusers["DEL"] ?: cancelRequester
                                right = confirmRequester
                                left = confirmRequester
                            },
                    onClick = {
                        pin = ""
                        pin2 = ""
                        pinError = null
                        stage = if (setPin) 1 else 2
                        onDismiss()
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = DesignTokens.KidAccent,
                        ),
                ) { Text("Abbrechen") }
            },
        )
    }

    var showCreate by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        val Accent = DesignTokens.KidAccent

        // Hintergrund
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background,
                        1f to MaterialTheme.colorScheme.surface,
                    ),
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Accent.copy(alpha = 0.24f),
                                Color.Transparent,
                            ),
                        radius = with(LocalDensity.current) { 640.dp.toPx() },
                    ),
                ),
        )
        com.chris.m3usuite.ui.fx.FishBackground(
            modifier = Modifier.align(Alignment.Center).size(540.dp),
            alpha = 0.06f,
        )

        // *** EIN gemeinsamer Scroller für die gesamte Seite ***
        val listState =
            com.chris.m3usuite.ui.state
                .rememberRouteListState("profiles:gate")

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .focusGroup(),
            state = listState,
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1) Erwachsenen‑Kachel
            item {
                val adultFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { adultFocus.requestFocus() }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val adultShape = RoundedCornerShape(28.dp)
                    Surface(
                        modifier =
                            Modifier
                                .size(164.dp)
                                .focusRequester(adultFocus)
                                .then(
                                    com.chris.m3usuite.ui.focus
                                        .run { Modifier.focusBringIntoViewOnFocus() },
                                ).then(
                                    com.chris.m3usuite.ui.focus.run {
                                        Modifier.tvFocusFrame(
                                            focusedScale = 1.12f,
                                            pressedScale = 1.08f,
                                            shape = adultShape,
                                            focusBorderWidth = 2.5.dp,
                                        )
                                    },
                                ).then(
                                    com.chris.m3usuite.ui.focus.run {
                                        Modifier.tvClickable(
                                            role = androidx.compose.ui.semantics.Role.Button,
                                            scaleFocused = 1f,
                                            scalePressed = 1f,
                                            focusBorderWidth = 0.dp,
                                            brightenContent = false,
                                            debugTag = "gate:adult",
                                            onClick = {
                                                if (!pinSet) {
                                                    setPin = true
                                                    pin = ""
                                                    pin2 = ""
                                                    pinError = null
                                                } else {
                                                    setPin = false
                                                    pin = ""
                                                    pin2 = ""
                                                    pinError = null
                                                }
                                                showPin = true
                                            },
                                        )
                                    },
                                ),
                        shape = adultShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Image(
                                painter = safePainter(com.chris.m3usuite.R.drawable.fisch_header),
                                contentDescription = "Erwachsenen-PIN",
                                modifier = Modifier.fillMaxSize(0.7f),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }

            // 2) „+“-Kachel
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    val addShape = RoundedCornerShape(28.dp)
                    Surface(
                        modifier =
                            Modifier
                                .size(164.dp)
                                .then(
                                    com.chris.m3usuite.ui.focus
                                        .run { Modifier.focusBringIntoViewOnFocus() },
                                ).then(
                                    com.chris.m3usuite.ui.focus.run {
                                        Modifier.tvFocusFrame(
                                            focusedScale = 1.12f,
                                            pressedScale = 1.08f,
                                            shape = addShape,
                                            focusBorderWidth = 2.5.dp,
                                        )
                                    },
                                ).then(
                                    com.chris.m3usuite.ui.focus.run {
                                        Modifier.tvClickable(
                                            role = androidx.compose.ui.semantics.Role.Button,
                                            scaleFocused = 1f,
                                            scalePressed = 1f,
                                            focusBorderWidth = 0.dp,
                                            brightenContent = false,
                                            debugTag = "gate:add",
                                            onClick = { showCreate = true },
                                        )
                                    },
                                ),
                        shape = addShape,
                        color = Color(0xFF1F5130),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "+",
                                style = MaterialTheme.typography.displayLarge,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            // 3) Kinder‑Profile
            if (kids.isNotEmpty()) {
                item {
                    val kidsState =
                        com.chris.m3usuite.ui.state
                            .rememberRouteListState("profiles:gate:kids")
                    TvRow(
                        items = kids,
                        key = { it.id },
                        listState = kidsState,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) { idx, k, itemModifier ->
                        var used by remember(k.id) { mutableStateOf<Int?>(null) }
                        var limit by remember(k.id) { mutableStateOf<Int?>(null) }

                        LaunchedEffect(k.id) {
                            withContext(Dispatchers.IO) {
                                val dayKey =
                                    java.text
                                        .SimpleDateFormat(
                                            "yyyyMMdd",
                                            java.util.Locale.getDefault(),
                                        ).format(
                                            java.util.Calendar
                                                .getInstance()
                                                .time,
                                        )
                                val b =
                                    ObxStore
                                        .get(ctx)
                                        .boxFor(com.chris.m3usuite.data.obx.ObxScreenTimeEntry::class.java)
                                val q =
                                    b
                                        .query(
                                            com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.kidProfileId
                                                .equal(k.id)
                                                .and(
                                                    com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.dayYyyymmdd.equal(
                                                        dayKey,
                                                    ),
                                                ),
                                        ).build()
                                val entry = q.findFirst()
                                withContext(Dispatchers.Main) {
                                    used = entry?.usedMinutes ?: 0
                                    limit = entry?.limitMinutes ?: 0
                                }
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                                itemModifier
                                    .widthIn(min = 148.dp)
                                    .then(
                                        com.chris.m3usuite.ui.focus.run {
                                            Modifier.tvFocusFrame(
                                                focusedScale = 1.10f,
                                                pressedScale = 1.06f,
                                                shape = RoundedCornerShape(22.dp),
                                                focusBorderWidth = 2.5.dp,
                                            )
                                        },
                                    ).then(
                                        com.chris.m3usuite.ui.focus.run {
                                            Modifier.tvClickable(
                                                role = androidx.compose.ui.semantics.Role.Button,
                                                scaleFocused = 1f,
                                                scalePressed = 1f,
                                                focusBorderWidth = 0.dp,
                                                brightenContent = false,
                                                debugTag = "gate:kid:${k.id}",
                                                onClick = {
                                                    scope.launch {
                                                        store.setCurrentProfileId(k.id)
                                                        onEnter()
                                                    }
                                                },
                                            )
                                        },
                                    ),
                        ) {
                            val shadowRadius = 24.dp
                            val avatarShape = CircleShape
                            val model = rememberAvatarModel(k.avatarPath)

                            Surface(
                                modifier =
                                    Modifier
                                        .size(132.dp)
                                        .clip(avatarShape)
                                        .border(3.dp, SolidColor(Color.Black), avatarShape)
                                        .graphicsLayer { shadowElevation = shadowRadius.toPx() },
                                shape = avatarShape,
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                if (model != null) {
                                    com.chris.m3usuite.ui.util.AppAsyncImage(
                                        url = model,
                                        contentDescription = k.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Box(
                                        Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = safePainter(android.R.drawable.ic_menu_report_image),
                                            contentDescription = k.name,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = k.name,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (used != null && limit != null) {
                                val u = used ?: 0
                                val l = limit ?: 0
                                val label =
                                    if (l > 0) {
                                        val rem = (l - u).coerceAtLeast(0)
                                        "Heute: $u min • Rest: $rem min"
                                    } else {
                                        "Heute: $u min"
                                    }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = 2.dp,
                                    modifier =
                                        Modifier
                                            .padding(top = 6.dp)
                                            .border(1.dp, SolidColor(Color.Black), RoundedCornerShape(16.dp))
                                            .graphicsLayer { shadowElevation = 18.dp.toPx() },
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPin) PinDialog(onDismiss = { showPin = false })

    if (showCreate) {
        CreateProfileSheet(
            onDismiss = { showCreate = false },
            onCreate = { name, kid ->
                scope.launch(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val p =
                        ObxProfile(
                            name = name,
                            type = if (kid) "kid" else "adult",
                            avatarPath = null,
                            createdAt = now,
                            updatedAt = now,
                        )
                    profileRepo.insert(p)
                    val list = profileRepo.all()
                    withContext(Dispatchers.Main) {
                        adult = list.firstOrNull { it.type == "adult" }
                        kids = list.filter { it.type != "adult" }
                        showCreate = false
                    }
                }
            },
        )
    }
}
