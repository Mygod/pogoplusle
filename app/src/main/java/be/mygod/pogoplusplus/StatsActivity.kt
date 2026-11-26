package be.mygod.pogoplusplus

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDate

class StatsActivity : ComponentActivity() {
    companion object {
        private const val KEY_EXPORT_DEVICE = "exportDevice"
    }

    private val snackbarHostState = SnackbarHostState()
    private val sessions by lazy {
        SessionDatabase.instance.sessions().observe().stateIn(lifecycleScope, SharingStarted.WhileSubscribed(), null)
    }
    /** Undo prompt of the deleted session, which a later deletion or clearing makes moot. */
    private val undoScope by lazy {
        CoroutineScope(lifecycleScope.coroutineContext + SupervisorJob(lifecycleScope.coroutineContext.job))
    }
    /** Clearing whose sessions stay hidden while it can be undone, and until the list catches up with it. */
    private var pendingClear by mutableStateOf<SfidaSessionManager.PendingClear?>(null)
    private var clearPrompt: Job? = null
    /** Whether a clearing awaits its prompt or its commit, before which another cannot start. */
    private var clearing by mutableStateOf(false)
    /** Address of the device whose sessions to export, or null for all, for when the file picker returns. */
    private var exportDevice: String? = null
    private val exportCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) lifecycleScope.launch { export(uri, exportDevice) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        exportDevice = savedInstanceState?.getString(KEY_EXPORT_DEVICE)
        setContent {
            PoGoPlusPlusTheme {
                val list by sessions.collectAsStateWithLifecycle()
                val ongoingStartTime by SfidaSessionManager.ongoingStartTime.collectAsStateWithLifecycle()
                // the session in progress lasts until now rather than until its latest event
                val now = remember(ongoingStartTime) {
                    if (ongoingStartTime == null) emptyFlow() else flow {
                        while (true) {
                            emit(System.currentTimeMillis())
                            delay(1000)
                        }
                    }
                }.collectAsStateWithLifecycle(System.currentTimeMillis())
                val pendingClear = pendingClear
                StatsScreen(
                    sessions = remember(list, pendingClear) {
                        if (pendingClear == null) list else list?.filterNot(pendingClear::removes)
                    },
                    ongoingStartTime = ongoingStartTime,
                    snackbarHostState = snackbarHostState,
                    onNavigateUp = ::finish,
                    onExport = { device ->
                        exportDevice = device
                        exportCsv.launch("pogoplusle-sessions-${LocalDate.now()}.csv")
                    },
                    now = { now.value },
                    canClear = !clearing,
                    onClear = ::clear,
                    onDelete = ::delete,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_EXPORT_DEVICE, exportDevice)
    }

    override fun onStart() {
        super.onStart()
        SfidaSessionManager.reconcile()
    }

    override fun onStop() {
        super.onStop()
        // leaving the screen confirms clearing, so that it cannot linger while sessions come and go
        clearPrompt?.cancel()
    }

    private fun showError(e: Exception) = lifecycleScope.launch {
        snackbarHostState.showSnackbar(e.localizedMessage ?: e.javaClass.name, duration = SnackbarDuration.Long)
    }

    /** Completes with whether the session is gone. */
    private fun delete(session: SfidaSession) = lifecycleScope.async {
        val error = try {
            SfidaSessionManager.delete(session).await()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e  // logged where it happened
        }
        // prompts that never time out would otherwise queue up
        undoScope.coroutineContext.cancelChildren()
        if (error != null) {
            showError(error)
            return@async false
        }
        // a clearing since would make its undo restore what it took off the totals
        if (clearing) return@async true
        undoScope.launch {
            if (snackbarHostState.showSnackbar(getString(R.string.stats_session_deleted),
                    getString(R.string.stats_undo), withDismissAction = true,
                    duration = SnackbarDuration.Indefinite) != SnackbarResult.ActionPerformed) return@launch
            try {
                SfidaSessionManager.restore(session).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e)
            }
        }
        true
    }

    /**
     * Hides what there is to clear of [device], or of all devices if null, and clears it unless undone before the
     * prompt is dismissed or the screen left. Another clearing waits until lifetime totals have followed this one.
     */
    private fun clear(device: String?) {
        if (clearPrompt?.isActive == true) return
        undoScope.coroutineContext.cancelChildren()
        val pending = SfidaSessionManager.prepareClear(device)
        pendingClear = pending
        clearing = true
        clearPrompt = lifecycleScope.launch {
            try {
                val undone = try {
                    snackbarHostState.showSnackbar(getString(R.string.stats_cleared), getString(R.string.stats_undo),
                        withDismissAction = true, duration = SnackbarDuration.Indefinite) ==
                        SnackbarResult.ActionPerformed
                } catch (e: CancellationException) {
                    commit(pending)
                    throw e
                }
                if (undone) pendingClear = null else commit(pending).join()
            } finally {
                clearing = false
            }
        }
    }

    /** Completes once lifetime totals have followed, while the list stays filtered until it catches up. */
    private fun commit(pending: SfidaSessionManager.PendingClear): Job {
        val result = SfidaSessionManager.clear(pending)
        return lifecycleScope.launch {
            try {
                result.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e)
                if (pendingClear === pending) pendingClear = null
                return@launch
            }
            lifecycleScope.launch {
                // the list would otherwise show them again until it catches up
                sessions.first { list -> list != null && list.none(pending::removes) }
                if (pendingClear === pending) pendingClear = null
            }
        }
    }

    private suspend fun export(uri: Uri, device: String?) {
        val pending = pendingClear
        val ongoingStartTime = SfidaSessionManager.ongoingStartTime.value
        val now = System.currentTimeMillis()
        val error = try {
            // leaving the screen must not cut the file short
            withContext(Dispatchers.IO + NonCancellable) {
                val sessions = SessionDatabase.instance.sessions().list().mapNotNull {
                    when {
                        device != null && it.deviceAddress != device || pending?.removes(it) == true -> null
                        // as on screen, the session in progress lasts until now rather than until its latest event
                        it.startTime == ongoingStartTime -> it.copy(endTime = maxOf(it.endTime, now))
                        else -> it
                    }
                }
                (contentResolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException(uri.toString()))
                    .bufferedWriter().use {
                        it.appendSessionsCsv(sessions)
                    }
            }
            return
        } catch (e: IOException) {
            e
        } catch (e: SecurityException) {
            // the document provider can refuse to write
            e
        }
        Timber.d(error)
        showError(error)
    }
}
