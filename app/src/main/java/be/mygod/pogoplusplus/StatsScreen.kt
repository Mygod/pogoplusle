package be.mygod.pogoplusplus

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Formatter
import java.util.TimeZone
import androidx.appcompat.R as AppCompatR

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun StatsScreen(
    sessions: List<SfidaSession>?,
    ongoingStartTime: Long?,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    /** Takes the address of the device to export, or null for all. */
    onExport: (String?) -> Unit,
    /** Current time, which an ongoing session lasts until. */
    now: () -> Long,
    canClear: Boolean,
    /** Takes the address of the device to clear, or null for all. */
    onClear: (String?) -> Unit,
    /** Completes with whether the session is gone. */
    onDelete: (SfidaSession) -> Deferred<Boolean>,
) {
    val bottomSafePadding = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    var selectedDevice by rememberSaveable { mutableStateOf<String?>(null) }
    val labels = remember(sessions) { sessions?.deviceLabels().orEmpty() }
    val device = selectedDevice?.takeIf { it in labels }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_stats)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(AppCompatR.string.abc_action_bar_up_description),
                        )
                    }
                },
                actions = {
                    StatsMenu(
                        canExport = !sessions.isNullOrEmpty(),
                        // the selected device is only known once history has loaded
                        canClear = canClear && sessions != null,
                        onExport = { onExport(device) },
                        onClear = { onClear(device) },
                    )
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(bottom = bottomSafePadding))
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        when {
            sessions == null -> { }
            sessions.isEmpty() -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.stats_empty), textAlign = TextAlign.Center)
            }
            else -> {
                val shown = remember(sessions, device) {
                    if (device == null) sessions else sessions.filter { it.deviceAddress == device }
                }
                val days = remember(shown) { shown.groupByDay() }
                val totals = remember(shown) { shown.totals() }
                val ongoing = remember(shown, ongoingStartTime) { shown.firstOrNull { it.startTime == ongoingStartTime } }
                val state = rememberLazyListState()
                LazyColumn(
                    state = state,
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .nonInteractiveVerticalScrollbar(state.scrollIndicatorState),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp + bottomSafePadding),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    if (labels.size > 1) item("devices") {
                        DeviceFilter(labels, device) { selectedDevice = it }
                    }
                    item("summary") { SummaryCard(totals, ongoing, now) }
                    for ((day, daySessions) in days) {
                        item("day:${day.toEpochDay()}") { DayHeader(day, daySessions.totals()) }
                        itemsIndexed(daySessions, key = { _, session -> session.startTime }) { index, session ->
                            SessionRow(
                                session = session,
                                deviceLabel = labels.getValue(session.deviceAddress),
                                ongoing = session.startTime == ongoingStartTime,
                                index = index,
                                count = daySessions.size,
                                onDelete = { onDelete(session) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsMenu(canExport: Boolean, canClear: Boolean, onExport: () -> Unit, onClear: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(AppCompatR.string.abc_action_menu_overflow_description),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stats_export)) },
                onClick = {
                    expanded = false
                    onExport()
                },
                enabled = canExport,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stats_clear)) },
                onClick = {
                    expanded = false
                    onClear()
                },
                enabled = canClear,
            )
        }
    }
}

@Composable
private fun DeviceFilter(labels: Map<String, String>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.stats_all_devices)) },
        )
        for ((address, label) in labels) FilterChip(
            selected = selected == address,
            onClick = { onSelect(address) },
            label = { Text(label) },
        )
    }
}

@Composable
private fun rememberFormats(): Pair<NumberFormat, NumberFormat> {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 } to
                NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    }
}

@Composable
private fun SummaryCard(recorded: SessionTotals, ongoing: SfidaSession?, now: () -> Long) {
    val (percent, decimal) = rememberFormats()
    // only this card follows the clock
    val totals = if (ongoing == null) recorded else recorded.copy(duration = recorded.duration - ongoing.duration +
            ongoing.copy(endTime = maxOf(ongoing.endTime, now())).duration)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = pluralStringResource(R.plurals.stats_session_count, totals.sessions, totals.sessions) +
                        " · " + DateUtils.formatElapsedTime(totals.duration / 1000),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(totals.stats, style = MaterialTheme.typography.titleMedium)
            totals.catchRate?.let { Text(stringResource(R.string.stats_catch_rate, percent.format(it))) }
            totals.itemsPerSpin?.let { Text(stringResource(R.string.stats_items_per_spin, decimal.format(it))) }
            totals.hourlyStats?.let { Text(stringResource(R.string.stats_per_hour, it)) }
            FailureCounts(totals.itemFull, totals.pokemonFull, totals.noBall, totals.spinFail)
        }
    }
}

private class FailureCount(@DrawableRes val icon: Int, @StringRes val label: Int, val count: Long)

/** Alerts the game raised during the session, with the icons of the matching notifications. */
@Composable
private fun FailureCounts(itemFull: Long, pokemonFull: Long, noBall: Long, spinFail: Long) {
    val counts = listOf(
        FailureCount(R.drawable.ic_shopping_bag, R.string.notification_channel_item_full, itemFull),
        FailureCount(R.drawable.ic_disc_full, R.string.notification_channel_pokemon_full, pokemonFull),
        FailureCount(R.drawable.ic_hide_source, R.string.notification_channel_no_ball, noBall),
        FailureCount(R.drawable.ic_error, R.string.notification_channel_spin_fail, spinFail),
    ).filter { it.count > 0 }
    if (counts.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (failure in counts) Row(
            modifier = Modifier.semantics(mergeDescendants = true) { },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(failure.icon),
                contentDescription = stringResource(failure.label),
                modifier = Modifier.size(16.dp),
            )
            Text("×${failure.count}")
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, totals: SessionTotals) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, top = 16.dp, end = 32.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) { heading() },
    ) {
        Text(
            // the date itself, as midnight in UTC formatted in UTC
            text = DateUtils.formatDateRange(context, Formatter(StringBuilder(), locale),
                day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY, "UTC").toString(),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = totals.stats,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SessionRow(
    session: SfidaSession,
    deviceLabel: String,
    ongoing: Boolean,
    index: Int,
    count: Int,
    onDelete: () -> Deferred<Boolean>,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val (percent, _) = rememberFormats()
    val shapes = ListItemDefaults.segmentedShapes(index, count)
    val zone = session.zone
    // times show as they were where the session was played, labeled when the clock differs from here
    val zoneLabel = Instant.ofEpochMilli(session.startTime).let { start ->
        if (zone.rules.getOffset(start) == ZoneId.systemDefault().rules.getOffset(start)) "" else {
            " " + TimeZone.getTimeZone(zone).getDisplayName(zone.rules.isDaylightSavings(start), TimeZone.SHORT, locale)
        }
    }
    fun formatTimes(start: Long, end: Long) = DateUtils.formatDateRange(context, Formatter(StringBuilder(), locale),
        start, end, DateUtils.FORMAT_SHOW_TIME, zone.id).toString() + zoneLabel
    val positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold
    // not saveable, so that a session restored by undo does not come back dismissed
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold) }
    val scope = rememberCoroutineScope()
    val deleteLabel = stringResource(R.string.stats_delete_session)
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val direction = state.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shapes.shape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else Alignment.CenterEnd,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        modifier = Modifier.padding(horizontal = 16.dp),
        // its next event would bring it back
        gesturesEnabled = !ongoing,
        onDismiss = { scope.launch { if (!onDelete().await()) state.reset() } },
    ) {
        SegmentedListItem(
            shapes = shapes,
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    if (!ongoing) customActions = listOf(CustomAccessibilityAction(deleteLabel) {
                        onDelete()
                        true
                    })
                },
            overlineContent = {
                Text(if (ongoing) {
                    formatTimes(session.startTime, session.startTime) + " · " + stringResource(R.string.stats_ongoing)
                } else formatTimes(session.startTime, session.startTime + session.duration) + " · " +
                        DateUtils.formatElapsedTime(session.duration / 1000))
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(deviceLabel)
                    FailureCounts(session.itemFull, session.pokemonFull, session.noBall, session.spinFail)
                }
            },
            trailingContent = session.catchRate?.let { { Text(percent.format(it)) } },
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Text(formatStats(session.caught, session.encounters, session.spins, session.items))
        }
    }
}
