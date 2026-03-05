package com.bikearcade.app.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Retro dark theme colors
private val BackgroundDark = Color(0xFF0D0D1A)
private val SurfaceDark = Color(0xFF1A1A2E)
private val CardDark = Color(0xFF16213E)
private val AccentBlue = Color(0xFF4FC3F7)
private val AccentPurple = Color(0xFFBB86FC)
private val NESRed = Color(0xFFE53935)
private val SNESPurple = Color(0xFF7E57C2)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF9E9E9E)
private val DividerColor = Color(0xFF2A2A3E)

/**
 * Full-screen game selection UI.
 *
 * Displays a grid of game cards discovered by [GameLibrary].
 * Supports search/filter by name and console type.
 *
 * @param gameLibrary The library that provides discovered ROM entries.
 * @param onGameSelected Callback when the user taps a game to launch it.
 */
@Composable
fun GameLauncherScreen(
    gameLibrary: GameLibrary,
    onGameSelected: (GameEntry) -> Unit,
    onExit: () -> Unit = {}
) {
    val games by gameLibrary.games.collectAsState()
    val isScanning by gameLibrary.isScanning.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var consoleFilter by remember { mutableStateOf<ConsoleType?>(null) }

    // Trigger initial scan
    LaunchedEffect(Unit) {
        gameLibrary.scan()
    }

    val filteredGames by remember(games, searchQuery, consoleFilter) {
        derivedStateOf {
            games.filter { game ->
                val matchesSearch = searchQuery.isBlank() ||
                        game.name.contains(searchQuery, ignoreCase = true)
                val matchesConsole = consoleFilter == null || game.consoleType == consoleFilter
                matchesSearch && matchesConsole
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            GameLauncherHeader(
                searchQuery = searchQuery,
                onSearchChanged = { searchQuery = it },
                consoleFilter = consoleFilter,
                onConsoleFilterChanged = { consoleFilter = it },
                isScanning = isScanning,
                onRefresh = {
                    // Re-scan is handled via LaunchedEffect or explicit call
                },
                onExit = onExit,
                gameLibrary = gameLibrary
            )

            // Content
            when {
                isScanning && games.isEmpty() -> {
                    LoadingContent()
                }
                games.isEmpty() -> {
                    EmptyLibraryContent()
                }
                filteredGames.isEmpty() -> {
                    NoResultsContent(searchQuery, consoleFilter)
                }
                else -> {
                    GameGrid(
                        games = filteredGames,
                        onGameSelected = { entry ->
                            gameLibrary.recordLaunch(entry)
                            onGameSelected(entry)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameLauncherHeader(
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    consoleFilter: ConsoleType?,
    onConsoleFilterChanged: (ConsoleType?) -> Unit,
    isScanning: Boolean,
    onRefresh: () -> Unit,
    onExit: () -> Unit,
    gameLibrary: GameLibrary
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "GAME LIBRARY",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    letterSpacing = 2.sp
                )
            }

            Row {
                // Refresh button
                IconButton(onClick = { onRefresh() }) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AccentBlue,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rescan ROMs",
                            tint = TextSecondary
                        )
                    }
                }

                // Exit button
                IconButton(onClick = onExit) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Exit app",
                        tint = NESRed
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search bar and filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChanged,
                placeholder = { Text("Search games...", color = TextSecondary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = searchQuery.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
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
                    cursorColor = AccentBlue
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Console filter chips
            FilterChip(
                selected = consoleFilter == null,
                onClick = { onConsoleFilterChanged(null) },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                    selectedLabelColor = AccentBlue,
                    containerColor = CardDark,
                    labelColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = consoleFilter == ConsoleType.NES,
                onClick = {
                    onConsoleFilterChanged(if (consoleFilter == ConsoleType.NES) null else ConsoleType.NES)
                },
                label = { Text("NES") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NESRed.copy(alpha = 0.2f),
                    selectedLabelColor = NESRed,
                    containerColor = CardDark,
                    labelColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = consoleFilter == ConsoleType.SNES,
                onClick = {
                    onConsoleFilterChanged(if (consoleFilter == ConsoleType.SNES) null else ConsoleType.SNES)
                },
                label = { Text("SNES") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SNESPurple.copy(alpha = 0.2f),
                    selectedLabelColor = SNESPurple,
                    containerColor = CardDark,
                    labelColor = TextSecondary
                )
            )
        }
    }
}

@Composable
private fun GameGrid(
    games: List<GameEntry>,
    onGameSelected: (GameEntry) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(games, key = { it.path }) { entry ->
            GameCard(entry = entry, onClick = { onGameSelected(entry) })
        }
    }
}

@Composable
private fun GameCard(
    entry: GameEntry,
    onClick: () -> Unit
) {
    val badgeColor = when (entry.consoleType) {
        ConsoleType.NES -> NESRed
        ConsoleType.SNES -> SNESPurple
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            badgeColor.copy(alpha = 0.15f),
            CardDark
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Console badge
                Box(
                    modifier = Modifier
                        .background(
                            color = badgeColor,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = entry.consoleType.badge,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Game name
                Text(
                    text = entry.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // File size and last played
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatFileSize(entry.fileSize),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    if (entry.lastPlayed > 0) {
                        Text(
                            text = formatLastPlayed(entry.lastPlayed),
                            color = AccentPurple.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentBlue)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scanning for ROMs...",
                color = TextSecondary,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun EmptyLibraryContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "No ROMs Found",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Place your ROM files in:",
                color = TextSecondary,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = GameLibrary.ROM_DIRECTORY,
                color = AccentBlue,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Supported formats: .nes, .smc, .sfc, .zip",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun NoResultsContent(
    searchQuery: String,
    consoleFilter: ConsoleType?
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No matching games",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (searchQuery.isNotBlank()) {
                Text(
                    text = "No results for \"$searchQuery\"",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
            if (consoleFilter != null) {
                Text(
                    text = "Filtered to ${consoleFilter.badge} only",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

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
