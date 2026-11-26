package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.text.Spanned
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key as composeKey
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.core.text.toHtml
import com.google.android.gms.oss.licenses.R as OssLicensesR

internal data class MainUiState(
    val pairingServiceRunning: Boolean,
    val gameNotificationServiceRunning: Boolean,
    val bluetoothMonitorEnabled: Boolean,
    val privilegedPairingEnabled: Boolean,
    val showPairingServices: Boolean,
    val showCompanionAssociation: Boolean,
    val companionAssociationCount: Int,
    val connectionPriority: Int,
    val lifetimeConnections: Long,
    val lifetimeStats: String,
)

@StringRes
private fun connectionPriorityLabel(priority: Int) = when (priority) {
    BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER -> R.string.settings_connection_priority_low_power
    BluetoothGatt.CONNECTION_PRIORITY_BALANCED -> R.string.settings_connection_priority_balanced
    BluetoothGatt.CONNECTION_PRIORITY_DCK -> R.string.settings_connection_priority_dck
    BluetoothGatt.CONNECTION_PRIORITY_HIGH -> R.string.settings_connection_priority_high
    else -> R.string.settings_connection_priority_default
}

@StringRes
private fun connectionPrioritySummary(priority: Int) = when (priority) {
    BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER -> R.string.settings_connection_priority_low_power_summary
    BluetoothGatt.CONNECTION_PRIORITY_BALANCED -> R.string.settings_connection_priority_balanced_summary
    BluetoothGatt.CONNECTION_PRIORITY_DCK -> R.string.settings_connection_priority_dck_summary
    BluetoothGatt.CONNECTION_PRIORITY_HIGH -> R.string.settings_connection_priority_high_summary
    else -> R.string.settings_connection_priority_default_summary
}

internal fun appColorScheme(context: Context, darkTheme: Boolean) = if (Build.VERSION.SDK_INT >= 31) {
    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
} else if (darkTheme) darkColorScheme() else expressiveLightColorScheme()

@Composable
internal fun PoGoPlusPlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = appColorScheme(LocalContext.current, isSystemInDarkTheme()),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@Composable
internal fun MainScreen(
    state: MainUiState,
    snackbarHostState: SnackbarHostState,
    showPairingDisclosure: Boolean,
    onDismissPairingDisclosure: () -> Unit,
    onAcceptPairingDisclosure: () -> Unit,
    onPairingServiceChange: (Boolean) -> Unit,
    onGameNotificationServiceChange: (Boolean) -> Unit,
    onBluetoothMonitorChange: (Boolean) -> Unit,
    onManageNotifications: () -> Unit,
    onLaunchGame: () -> Unit,
    onOpenStats: () -> Unit,
    onCompanionAssociation: () -> Unit,
    onPrivilegedPairingChange: (Boolean) -> Unit,
    onConnectionPriorityChange: (Int) -> Unit,
    onOpenSource: () -> Unit,
    onDonate: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val bottomSafePadding = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    var selectingConnectionPriority by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomSafePadding),
            ) { data ->
                SwipeToDismissBox(
                    state = rememberSwipeToDismissBoxState(),
                    backgroundContent = {},
                    onDismiss = { data.dismiss() },
                ) {
                    Snackbar(data)
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        SettingsList(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp + bottomSafePadding),
        ) {
            preferenceGroup(key = "main") {
                if (state.showPairingServices) row(R.string.settings_service_pairing) {
                    PreferenceSwitchRow(
                        checked = state.pairingServiceRunning,
                        icon = R.drawable.ic_touch_app,
                        title = stringResource(R.string.settings_service_pairing),
                        summary = stringResource(R.string.settings_service_pairing_summary),
                        onCheckedChange = onPairingServiceChange,
                    )
                }
                row(R.string.game_notification_service_name) {
                    PreferenceSwitchRow(
                        checked = state.gameNotificationServiceRunning,
                        icon = R.drawable.ic_monitor_heart,
                        title = stringResource(R.string.game_notification_service_name),
                        summary = stringResource(R.string.game_notification_service_summary),
                        onCheckedChange = onGameNotificationServiceChange,
                    )
                }
                row(R.string.settings_permission_bluetooth) {
                    PreferenceSwitchRow(
                        checked = state.bluetoothMonitorEnabled,
                        icon = R.drawable.ic_bluetooth_searching,
                        title = stringResource(R.string.settings_permission_bluetooth),
                        summary = stringResource(R.string.settings_permission_bluetooth_summary),
                        onCheckedChange = onBluetoothMonitorChange,
                    )
                }
                row(R.string.settings_permission_notification) {
                    PreferenceRow(
                        icon = R.drawable.ic_notifications_active,
                        title = stringResource(R.string.settings_permission_notification),
                        summary = stringResource(R.string.settings_permission_notification_summary),
                        onClick = onManageNotifications,
                    )
                }
                row(R.string.settings_game) {
                    PreferenceRow(
                        icon = R.drawable.ic_sports_esports,
                        title = stringResource(R.string.settings_game),
                        summary = stringResource(R.string.settings_game_summary),
                        onClick = onLaunchGame,
                    )
                }
                row(R.string.settings_stats) {
                    PreferenceRow(
                        icon = R.drawable.ic_bar_chart,
                        title = pluralStringResource(R.plurals.settings_stats_connections,
                            state.lifetimeConnections.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            state.lifetimeConnections),
                        summary = state.lifetimeStats,
                        onClick = onOpenStats,
                    )
                }
            }

            preferenceGroup(title = R.string.settings_advanced) {
                if (state.showPairingServices && state.showCompanionAssociation) {
                    row(R.string.settings_companion_association) {
                        PreferenceRow(
                            icon = R.drawable.ic_bluetooth_connected,
                            title = stringResource(R.string.settings_companion_association),
                            summary = if (state.companionAssociationCount == 0) {
                                stringResource(R.string.settings_companion_association_summary)
                            } else stringResource(
                                R.string.settings_companion_association_summary_count,
                                state.companionAssociationCount,
                            ),
                            onClick = onCompanionAssociation,
                        )
                    }
                }
                row(R.string.settings_connection_priority) {
                    PreferenceRow(
                        icon = R.drawable.ic_speed,
                        title = stringResource(R.string.settings_connection_priority),
                        summary = stringResource(connectionPriorityLabel(state.connectionPriority)),
                        onClick = { selectingConnectionPriority = true },
                    )
                }
                if (state.showPairingServices) row(R.string.settings_service_pairing_privileged) {
                    PreferenceSwitchRow(
                        checked = state.privilegedPairingEnabled,
                        icon = R.drawable.ic_electric_bolt,
                        title = stringResource(R.string.settings_service_pairing_privileged),
                        summary = stringResource(R.string.settings_service_pairing_privileged_summary),
                        onCheckedChange = onPrivilegedPairingChange,
                    )
                }
            }

            preferenceGroup(title = R.string.settings_about) {
                row(R.string.settings_misc_source) {
                    PreferenceRow(
                        icon = R.drawable.ic_star,
                        title = stringResource(R.string.settings_misc_source),
                        summary = stringResource(R.string.settings_misc_source_summary),
                        onClick = onOpenSource,
                    )
                }
                row(R.string.settings_misc_donate) {
                    PreferenceRow(
                        icon = R.drawable.ic_volunteer_activism,
                        title = stringResource(R.string.settings_misc_donate),
                        summary = stringResource(R.string.settings_misc_donate_summary),
                        onClick = onDonate,
                    )
                }
                row(OssLicensesR.string.oss_license_title) {
                    PreferenceRow(
                        icon = R.drawable.ic_license,
                        title = stringResource(OssLicensesR.string.oss_license_title),
                        summary = stringResource(OssLicensesR.string.preferences_license_summary),
                        onClick = onOpenLicenses,
                    )
                }
            }
        }
    }

    if (showPairingDisclosure) PairingDisclosureDialog(
        onDismissRequest = onDismissPairingDisclosure,
        onAccept = onAcceptPairingDisclosure,
    )
    if (selectingConnectionPriority) {
        val priorities = SfidaConnectionPriority.priorities
        PreferenceSelectionSheet(
            title = stringResource(R.string.settings_connection_priority),
            entryCount = priorities.size,
            selectedIndex = priorities.indexOf(state.connectionPriority),
            entryLabel = { stringResource(connectionPriorityLabel(priorities[it])) },
            entrySummary = { stringResource(connectionPrioritySummary(priorities[it])) },
            description = stringResource(R.string.settings_connection_priority_message),
            onDismissRequest = { selectingConnectionPriority = false },
            onSelect = { onConnectionPriorityChange(priorities[it]) },
        )
    }
}

@Composable
internal fun Modifier.nonInteractiveVerticalScrollbar(state: ScrollIndicatorState?) = state?.let {
    nonInteractiveScrollbar(
        state = it,
        orientation = Orientation.Vertical,
    )
} ?: this

@Composable
private fun SettingsList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    LazyColumn(
        state = state,
        modifier = modifier
            .fillMaxSize()
            .nonInteractiveVerticalScrollbar(state.scrollIndicatorState),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PreferenceSelectionSheet(
    title: String,
    entryCount: Int,
    selectedIndex: Int,
    entryLabel: @Composable (Int) -> String,
    entrySummary: @Composable (Int) -> String? = { null },
    description: String? = null,
    onDismissRequest: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top) },
    ) {
        val state = rememberLazyListState()
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .nonInteractiveVerticalScrollbar(state.scrollIndicatorState),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            description?.let {
                item("description") {
                    Text(
                        text = it,
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(entryCount, key = { it }) { index ->
                CompositionLocalProvider(
                    LocalPreferenceRowPosition provides PreferenceRowPosition(index, entryCount),
                ) {
                    PreferenceRadioRow(
                        selected = index == selectedIndex,
                        title = entryLabel(index),
                        summary = entrySummary(index),
                    ) {
                        onSelect(index)
                        onDismissRequest()
                    }
                }
            }
        }
    }
}

private fun LazyListScope.preferenceGroup(
    key: Any? = null,
    @StringRes title: Int? = null,
    content: PreferenceGroupScope.() -> Unit,
) {
    item(key = key ?: title) {
        PreferenceGroup(
            title = title?.let { stringResource(it) },
            content = content,
        )
    }
}

@Composable
private fun PreferenceGroup(
    title: String? = null,
    horizontalPadding: Dp = 16.dp,
    content: PreferenceGroupScope.() -> Unit,
) {
    val items = ArrayList<@Composable () -> Unit>()
    PreferenceGroupScope(items).apply(content)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        title?.let {
            Text(
                text = it,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { heading() },
                fontWeight = FontWeight.SemiBold,
            )
        }
        for (item in items) item()
    }
}

private class PreferenceGroupScope(private val items: MutableList<@Composable () -> Unit>) {
    private var rowCount = 0

    fun row(key: Any? = null, content: @Composable () -> Unit) {
        val index = rowCount++
        items += {
            if (key == null) {
                CompositionLocalProvider(LocalPreferenceRowPosition provides PreferenceRowPosition(index, rowCount)) {
                    content()
                }
            } else composeKey(key) {
                CompositionLocalProvider(LocalPreferenceRowPosition provides PreferenceRowPosition(index, rowCount)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun PreferenceRow(
    title: String,
    @DrawableRes icon: Int,
    summary: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val position = LocalPreferenceRowPosition.current
    SegmentedListItem(
        onClick = onClick,
        shapes = preferenceRowShapes(position),
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = trailingContent,
        supportingContent = { Text(summary) },
        verticalAlignment = Alignment.CenterVertically,
        colors = preferenceRowColors(position),
    ) {
        Text(title)
    }
}

@Composable
private fun PreferenceRadioRow(
    selected: Boolean,
    title: String,
    summary: String?,
    onClick: () -> Unit,
) {
    val position = LocalPreferenceRowPosition.current
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = preferenceRowShapes(position),
        modifier = Modifier.fillMaxWidth(),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        supportingContent = summary?.let { { Text(it) } },
        verticalAlignment = Alignment.CenterVertically,
        colors = preferenceRowColors(position),
    ) {
        Text(title)
    }
}

@Composable
private fun preferenceRowShapes(position: PreferenceRowPosition?) = if (position == null) {
    ListItemDefaults.shapes()
} else ListItemDefaults.segmentedShapes(position.index, position.count)

@Composable
private fun preferenceRowColors(position: PreferenceRowPosition?) = if (position == null) {
    ListItemDefaults.colors()
} else ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

@Composable
private fun PreferenceSwitchRow(
    checked: Boolean,
    @DrawableRes icon: Int,
    title: String,
    summary: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    PreferenceRow(
        icon = icon,
        title = title,
        summary = summary,
        modifier = Modifier.semantics(mergeDescendants = true) {
            toggleableState = ToggleableState(checked)
            role = Role.Switch
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics { },
                thumbContent = {
                    Icon(
                        painter = painterResource(if (checked) R.drawable.ic_check else R.drawable.ic_close),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}

private class PreferenceRowPosition(val index: Int, val count: Int)

private val LocalPreferenceRowPosition = compositionLocalOf<PreferenceRowPosition?> { null }

@Composable
private fun PairingDisclosureDialog(
    onDismissRequest: () -> Unit,
    onAccept: () -> Unit,
) {
    val resources = LocalResources.current
    val message = buildAnnotatedString {
        append(resources.getText(R.string.bluetooth_pairing_service_disclosure).toAnnotatedString())
        if (Build.VERSION.SDK_INT >= 33) {
            append("\n\n")
            append(resources.getText(
                R.string.bluetooth_pairing_service_disclosure_restricted_settings).toAnnotatedString())
        }
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.bluetooth_pairing_service_disclosure_title)) },
        text = { Text(message) },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(resources.getFrameworkString("decline"))
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(resources.getFrameworkString("accept"))
            }
        },
    )
}

private fun CharSequence.toAnnotatedString() = if (this is Spanned) {
    AnnotatedString.fromHtml(toHtml(HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE))
} else AnnotatedString(toString())

@SuppressLint("DiscouragedApi")
private fun Resources.getFrameworkString(name: String) =
    getText(getIdentifier(name, "string", "android")).toString()
