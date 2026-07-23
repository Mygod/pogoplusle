package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Build
import android.text.Spanned
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
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
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.scrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key as composeKey
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
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
)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun PoGoPlusPlusTheme(dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) darkColorScheme() else expressiveLightColorScheme()
    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    onCompanionAssociation: () -> Unit,
    onPrivilegedPairingChange: (Boolean) -> Unit,
    onOpenSource: () -> Unit,
    onDonate: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val bottomSafePadding = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
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
                        icon = R.drawable.ic_action_touch_app,
                        title = stringResource(R.string.settings_service_pairing),
                        summary = stringResource(R.string.settings_service_pairing_summary),
                        onCheckedChange = onPairingServiceChange,
                    )
                }
                row(R.string.game_notification_service_name) {
                    PreferenceSwitchRow(
                        checked = state.gameNotificationServiceRunning,
                        icon = R.drawable.ic_device_monitor_heart,
                        title = stringResource(R.string.game_notification_service_name),
                        summary = stringResource(R.string.game_notification_service_summary),
                        onCheckedChange = onGameNotificationServiceChange,
                    )
                }
                row(R.string.settings_permission_bluetooth) {
                    PreferenceSwitchRow(
                        checked = state.bluetoothMonitorEnabled,
                        icon = R.drawable.ic_device_bluetooth_connected,
                        title = stringResource(R.string.settings_permission_bluetooth),
                        summary = stringResource(R.string.settings_permission_bluetooth_summary),
                        onCheckedChange = onBluetoothMonitorChange,
                    )
                }
                row(R.string.settings_permission_notification) {
                    PreferenceRow(
                        icon = R.drawable.ic_social_notifications_active,
                        title = stringResource(R.string.settings_permission_notification),
                        summary = stringResource(R.string.settings_permission_notification_summary),
                        onClick = onManageNotifications,
                    )
                }
                row(R.string.settings_game) {
                    PreferenceRow(
                        icon = R.drawable.ic_av_games,
                        title = stringResource(R.string.settings_game),
                        summary = stringResource(R.string.settings_game_summary),
                        onClick = onLaunchGame,
                    )
                }
            }

            if (state.showPairingServices) preferenceGroup(title = R.string.settings_advanced) {
                if (state.showCompanionAssociation) row(R.string.settings_companion_association) {
                    PreferenceRow(
                        icon = R.drawable.ic_device_bluetooth_connected,
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
                row(R.string.settings_service_pairing_privileged) {
                    PreferenceSwitchRow(
                        checked = state.privilegedPairingEnabled,
                        icon = R.drawable.ic_home_electric_bolt,
                        title = stringResource(R.string.settings_service_pairing_privileged),
                        summary = stringResource(R.string.settings_service_pairing_privileged_summary),
                        onCheckedChange = onPrivilegedPairingChange,
                    )
                }
            }

            preferenceGroup(title = R.string.settings_about) {
                row(R.string.settings_misc_source) {
                    PreferenceRow(
                        icon = R.drawable.ic_toggle_star,
                        title = stringResource(R.string.settings_misc_source),
                        summary = stringResource(R.string.settings_misc_source_summary),
                        onClick = onOpenSource,
                    )
                }
                row(R.string.settings_misc_donate) {
                    PreferenceRow(
                        icon = R.drawable.ic_action_card_giftcard,
                        title = stringResource(R.string.settings_misc_donate),
                        summary = stringResource(R.string.settings_misc_donate_summary),
                        onClick = onDonate,
                    )
                }
                row(OssLicensesR.string.oss_license_title) {
                    PreferenceRow(
                        icon = R.drawable.ic_action_code,
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
}

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
            .scrollbar(
                state = state.scrollIndicatorState,
                orientation = Orientation.Vertical,
                isFadeEnabled = false,
            ),
        contentPadding = contentPadding,
        content = content,
    )
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        shapes = when {
            position == null -> ListItemDefaults.shapes()
            position.count == 1 -> ListItemDefaults.shapes(shape = MaterialTheme.shapes.large)
            else -> ListItemDefaults.segmentedShapes(position.index, position.count)
        },
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
        colors = if (position == null) {
            ListItemDefaults.colors()
        } else {
            ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        },
    ) {
        Text(title)
    }
}

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
