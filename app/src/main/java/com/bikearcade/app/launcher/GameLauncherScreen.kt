package com.bikearcade.app.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bikearcade.app.R
import com.bikearcade.emulator.Platform
import com.bikearcade.emulator.PlatformRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Retro dark theme colors
private val BackgroundDark = Color(0xFF0D0D1A)
private val SurfaceDark = Color(0xFF1A1A2E)
private val CardDark = Color(0xFF16213E)
private val CardBorder = Color(0xFF2A2A3E)
private val AccentBlue = Color(0xFF4FC3F7)
private val AccentPurple = Color(0xFFBB86FC)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF9E9E9E)
private val DividerColor = Color(0xFF2A2A3E)

/** Sorting options for the game list. */
enum class SortMode(val label: String) {
    RECENT("Recently Played"),
    NAME_AZ("Name A-Z"),
    NAME_ZA("Name Z-A"),
    SYSTEM("By System"),
}

/** View mode toggle. */
enum class ViewMode { GRID, LIST }

@Composable
fun GameLauncherScreen(
    gameLibrary: GameLibrary,
    onGameSelected: (GameEntry) -> Unit,
    onExit: () -> Unit = {}
) {
    val games by gameLibrary.games.collectAsState()
    val isScanning by gameLibrary.isScanning.collectAsState()
    val isImporting by UsbImporter.isImporting.collectAsState()
    val importStatus by UsbImporter.importStatus.collectAsState()
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var platformFilter by remember { mutableStateOf<Platform?>(null) }
    var sortMode by remember { mutableStateOf(SortMode.RECENT) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }
    var initialLoadDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val minDelay = launch { delay(1200) }
        gameLibrary.scan() // applies cached thumbnails + metadata inline
        // Fetch missing thumbnails and metadata while Pac-Man is still playing
        gameLibrary.fetchMissingThumbnails()
        gameLibrary.fetchMetadata()
        minDelay.join() // keep Pac-Man until at least 1200ms
        initialLoadDone = true
    }

    // Auto-detect USB drive insertion and import ROMs
    LaunchedEffect(Unit) {
        var usbWasPresent = UsbImporter.hasUsbDrive()
        while (isActive) {
            delay(3000)
            val usbPresent = UsbImporter.hasUsbDrive()
            if (usbPresent && !usbWasPresent && !isImporting) {
                // USB drive just inserted — auto-import
                scope.launch {
                    UsbImporter.importFromUsb()
                    gameLibrary.scan()
                    gameLibrary.fetchMissingThumbnails()
                }
            }
            usbWasPresent = usbPresent
        }
    }

    val availablePlatforms by remember(games) {
        derivedStateOf {
            val ids = games.map { it.platformId }.toSet()
            PlatformRegistry.ALL.filter { it.id in ids }
        }
    }

    val filteredGames by remember(games, searchQuery, platformFilter, sortMode) {
        derivedStateOf {
            val filtered = games.filter { game ->
                val matchesSearch = searchQuery.isBlank() ||
                        game.name.contains(searchQuery, ignoreCase = true)
                val matchesPlatform = platformFilter == null || game.platformId == platformFilter?.id
                matchesSearch && matchesPlatform
            }

            when (sortMode) {
                SortMode.RECENT -> filtered.sortedByDescending { it.lastPlayed }
                SortMode.NAME_AZ -> filtered.sortedBy { it.name.lowercase() }
                SortMode.NAME_ZA -> filtered.sortedByDescending { it.name.lowercase() }
                SortMode.SYSTEM -> filtered.sortedWith(
                    compareBy<GameEntry> { it.platformId }
                        .thenBy { it.name.lowercase() }
                )
            }
        }
    }

    val recentGames by remember(games, searchQuery, platformFilter) {
        derivedStateOf {
            if (searchQuery.isNotBlank() || platformFilter != null) emptyList()
            else games.filter { it.lastPlayed > 0 }
                .sortedByDescending { it.lastPlayed }
                .take(6)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .paint(
                painter = painterResource(R.drawable.gamewallpaper),
                contentScale = ContentScale.Crop,
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GameLauncherHeader(
                searchQuery = searchQuery,
                onSearchChanged = { searchQuery = it },
                platformFilter = platformFilter,
                availablePlatforms = availablePlatforms,
                onPlatformFilterChanged = { platformFilter = it },
                sortMode = sortMode,
                onSortModeChanged = { sortMode = it },
                viewMode = viewMode,
                onViewModeChanged = { viewMode = it },
                isScanning = isScanning,
                isImporting = isImporting,
                importStatus = importStatus,
                onRefresh = {
                    scope.launch {
                        gameLibrary.scan()
                        gameLibrary.fetchMissingThumbnails()
                    }
                },
                onImportUsb = {
                    scope.launch {
                        UsbImporter.importFromUsb()
                        gameLibrary.scan()
                        gameLibrary.fetchMissingThumbnails()
                    }
                },
                onExit = onExit,
            )

            when {
                !initialLoadDone -> LoadingContent()
                games.isEmpty() -> EmptyLibraryContent()
                filteredGames.isEmpty() -> NoResultsContent(searchQuery, platformFilter)
                viewMode == ViewMode.GRID -> GameGrid(
                    games = filteredGames,
                    recentGames = recentGames,
                    onGameSelected = { entry ->
                        gameLibrary.recordLaunch(entry)
                        onGameSelected(entry)
                    }
                )
                else -> GameList(
                    games = filteredGames,
                    recentGames = recentGames,
                    onGameSelected = { entry ->
                        gameLibrary.recordLaunch(entry)
                        onGameSelected(entry)
                    }
                )
            }
        }
    }
}

@Composable
private fun GameLauncherHeader(
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    platformFilter: Platform?,
    availablePlatforms: List<Platform>,
    onPlatformFilterChanged: (Platform?) -> Unit,
    sortMode: SortMode,
    onSortModeChanged: (SortMode) -> Unit,
    viewMode: ViewMode,
    onViewModeChanged: (ViewMode) -> Unit,
    isScanning: Boolean,
    isImporting: Boolean,
    importStatus: String,
    onRefresh: () -> Unit,
    onImportUsb: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark.copy(alpha = 0.9f))
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GAME LIBRARY",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AccentBlue,
                letterSpacing = 3.sp,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // View mode toggle
                IconButton(onClick = {
                    onViewModeChanged(if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
                }) {
                    Icon(
                        imageVector = if (viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle view",
                        tint = TextSecondary,
                    )
                }

                SortDropdown(currentSort = sortMode, onSortChanged = onSortModeChanged)

                IconButton(onClick = onImportUsb, enabled = !isImporting) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AccentPurple,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Usb, contentDescription = "Import from USB", tint = AccentPurple)
                    }
                }

                IconButton(onClick = onRefresh, enabled = !isScanning) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AccentBlue,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan ROMs", tint = TextSecondary)
                    }
                }

                IconButton(onClick = onExit) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                }
            }
        }

        if (importStatus.isNotBlank()) {
            Text(
                text = importStatus,
                color = if (isImporting) AccentPurple else TextSecondary,
                fontSize = 12.sp,

            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search + filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChanged,
                placeholder = { Text("Search games...", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                trailingIcon = {
                    AnimatedVisibility(visible = searchQuery.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { onSearchChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = DividerColor,
                    focusedContainerColor = CardDark,
                    unfocusedContainerColor = CardDark,
                    cursorColor = AccentBlue,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(16.dp))

            FilterChip(
                selected = platformFilter == null,
                onClick = { onPlatformFilterChanged(null) },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                    selectedLabelColor = AccentBlue,
                    containerColor = CardDark,
                    labelColor = TextSecondary,
                ),
            )

            for (platform in availablePlatforms) {
                Spacer(modifier = Modifier.width(8.dp))
                val chipColor = Color(platform.accentColorArgb)
                FilterChip(
                    selected = platformFilter?.id == platform.id,
                    onClick = {
                        onPlatformFilterChanged(if (platformFilter?.id == platform.id) null else platform)
                    },
                    label = { Text(platform.badge) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor.copy(alpha = 0.2f),
                        selectedLabelColor = chipColor,
                        containerColor = CardDark,
                        labelColor = TextSecondary,
                    ),
                )
            }
        }
    }
}

// ---- Grid View ----

@Composable
private fun GameGrid(
    games: List<GameEntry>,
    recentGames: List<GameEntry>,
    onGameSelected: (GameEntry) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 200.dp),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (recentGames.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("RECENTLY PLAYED", AccentPurple)
            }
            items(recentGames, key = { "recent_${it.path}" }) { entry ->
                GameCardGrid(entry = entry, onClick = { onGameSelected(entry) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("ALL GAMES", AccentBlue)
            }
        }

        items(games, key = { it.path }) { entry ->
            GameCardGrid(entry = entry, onClick = { onGameSelected(entry) })
        }
    }
}

@Composable
private fun GameCardGrid(entry: GameEntry, onClick: () -> Unit) {
    val platform = entry.platform
    val badgeColor = platform?.let { Color(it.accentColorArgb) } ?: AccentBlue
    val badge = platform?.badge ?: entry.platformId.uppercase()
    val platformIcon = platformDrawable(entry.platformId)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            // Thumbnail area — clean, no overlay badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color(0xFF0A0A18))
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (entry.artworkPath != null) {
                    val isNes = entry.platformId == "nes"
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(entry.artworkPath))
                            .crossfade(true)
                            .build(),
                        contentDescription = entry.name,
                        contentScale = ContentScale.Crop,
                        alignment = if (isNes) Alignment.TopCenter else Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (platformIcon != null) {
                    Image(
                        painter = painterResource(platformIcon),
                        contentDescription = badge,
                        modifier = Modifier.size(64.dp),
                        alpha = 0.4f,
                    )
                }
            }

            // Info area below thumbnail — fixed height for uniform cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(12.dp)
            ) {
                Text(
                    text = entry.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (platformIcon != null) {
                        Image(
                            painter = painterResource(platformIcon),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = badge,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val detail = entry.releaseDate ?: formatFileSize(entry.fileSize)
                    Text(
                        text = "  \u00B7  $detail",
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                    if (entry.genre != null) {
                        Text(
                            text = "  \u00B7  ${entry.genre.substringBefore(",")}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ---- List View ----

@Composable
private fun GameList(
    games: List<GameEntry>,
    recentGames: List<GameEntry>,
    onGameSelected: (GameEntry) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (recentGames.isNotEmpty()) {
            item { SectionHeader("RECENTLY PLAYED", AccentPurple) }
            items(recentGames, key = { "recent_${it.path}" }) { entry ->
                GameCardList(entry = entry, onClick = { onGameSelected(entry) })
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader("ALL GAMES", AccentBlue)
            }
        }

        items(games, key = { it.path }) { entry ->
            GameCardList(entry = entry, onClick = { onGameSelected(entry) })
        }
    }
}

@Composable
private fun GameCardList(entry: GameEntry, onClick: () -> Unit) {
    val platform = entry.platform
    val badgeColor = platform?.let { Color(it.accentColorArgb) } ?: AccentBlue
    val badge = platform?.badge ?: entry.platformId.uppercase()
    val platformIcon = platformDrawable(entry.platformId)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 60.dp)
                    .background(Color(0xFF0A0A18), RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (entry.artworkPath != null) {
                    val isNes = entry.platformId == "nes"
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(entry.artworkPath))
                            .crossfade(true)
                            .build(),
                        contentDescription = entry.name,
                        contentScale = ContentScale.Crop,
                        alignment = if (isNes) Alignment.TopCenter else Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (platformIcon != null) {
                    Image(
                        painter = painterResource(platformIcon),
                        contentDescription = badge,
                        modifier = Modifier.size(36.dp),
                        alpha = 0.4f,
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Game info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (platformIcon != null) {
                        Image(
                            painter = painterResource(platformIcon),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(text = badge, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val detail = entry.releaseDate ?: formatFileSize(entry.fileSize)
                    Text(
                        text = "  \u00B7  $detail",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    if (entry.genre != null) {
                        Text(
                            text = "  \u00B7  ${entry.genre.substringBefore(",")}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    if (entry.lastPlayed > 0) {
                        Text(
                            text = "  \u00B7  ${formatLastPlayed(entry.lastPlayed)}",
                            color = AccentPurple.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // Platform icon on the right
            if (platformIcon != null) {
                Image(
                    painter = painterResource(platformIcon),
                    contentDescription = badge,
                    modifier = Modifier.size(28.dp),
    
                )
            }
        }
    }
}

private fun platformDrawable(platformId: String): Int? = when (platformId) {
    "nes" -> R.drawable.ic_platform_nes
    "snes" -> R.drawable.ic_platform_snes
    "gba" -> R.drawable.ic_platform_gba
    "gb" -> R.drawable.ic_platform_gb
    "genesis" -> R.drawable.ic_platform_genesis
    else -> null
}

// ---- Shared Components ----

@Composable
private fun SectionHeader(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(bottom = 4.dp, top = 4.dp),
    )
}

@Composable
private fun SortDropdown(
    currentSort: SortMode,
    onSortChanged: (SortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.SortByAlpha, contentDescription = "Sort", tint = AccentBlue)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceDark),
        ) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = mode.label,
                            color = if (mode == currentSort) AccentBlue else TextPrimary,
                            fontWeight = if (mode == currentSort) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSortChanged(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ---- Empty/Loading States ----

@Composable
private fun LoadingContent() {
    // Use a monotonic counter incremented by withFrameNanos for reliable animation.
    // Previous approach (delay(16) + System.currentTimeMillis()) was starved by IO coroutines.
    var frameTimeMs by remember { mutableStateOf(0L) }
    val startTime = remember { System.currentTimeMillis() }
    LaunchedEffect(Unit) {
        while (isActive) {
            androidx.compose.animation.core.withInfiniteAnimationFrameMillis { ms ->
                frameTimeMs = ms
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(width = 240.dp, height = 48.dp)) {
                val t = frameTimeMs
                val now = t % 2500L
                val travel = now / 2500f
                val mouthRaw = (t % 400L) / 200f // 0→2 over 400ms
                val mouth = if (mouthRaw < 1f) mouthRaw else 2f - mouthRaw // triangle wave 0→1→0
                val mouthAngle = 5f + mouth * 40f // 5°–45°

                val pacRadius = 20.dp.toPx()
                val dotRadius = 4.dp.toPx()
                val y = size.height / 2
                val startX = pacRadius
                val endX = size.width - pacRadius
                val pacX = startX + (endX - startX) * travel

                // Dots that Pac-Man hasn't eaten yet
                val dotSpacing = 30.dp.toPx()
                var dx = startX + dotSpacing
                while (dx < endX) {
                    if (dx > pacX + pacRadius * 0.5f) {
                        drawCircle(
                            Color(0xFFFFB74D),
                            dotRadius,
                            center = androidx.compose.ui.geometry.Offset(dx, y),
                        )
                    }
                    dx += dotSpacing
                }

                // Pac-Man
                drawArc(
                    color = Color(0xFFFFEB3B),
                    startAngle = mouthAngle,
                    sweepAngle = 360f - 2 * mouthAngle,
                    useCenter = true,
                    topLeft = androidx.compose.ui.geometry.Offset(pacX - pacRadius, y - pacRadius),
                    size = androidx.compose.ui.geometry.Size(pacRadius * 2, pacRadius * 2),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Loading games...", color = TextSecondary, fontSize = 16.sp)
        }
    }
}

@Composable
private fun EmptyLibraryContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("No ROMs Found", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Place your ROM files in:", color = TextSecondary, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = GameLibrary.ROM_DIRECTORY,
                color = AccentBlue,
                fontSize = 14.sp,

                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Supported: ${PlatformRegistry.ALL.joinToString { it.badge }}",
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Or plug in a USB drive with ROMs and tap the USB icon",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NoResultsContent(searchQuery: String, platformFilter: Platform?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No matching games", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            if (searchQuery.isNotBlank()) {
                Text("No results for \"$searchQuery\"", color = TextSecondary, fontSize = 14.sp)
            }
            if (platformFilter != null) {
                Text("Filtered to ${platformFilter.badge} only", color = TextSecondary, fontSize = 14.sp)
            }
        }
    }
}

// ---- Helpers ----

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}

private fun formatLastPlayed(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffHours = (now - timestamp) / (1000 * 60 * 60)
    return when {
        diffHours < 1 -> "Just now"
        diffHours < 24 -> "${diffHours}h ago"
        diffHours < 48 -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
