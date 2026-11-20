package com.brokenbored.neo.terminal

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Plain failure talking to Termux. The UI shows this message as-is. */
class TermuxError(message: String) : Exception(message)

/** What came back from one command. */
data class TermuxResult(
    /** Process exit code, null when Termux never ran it. */
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    /** True when Termux truncated the output (100 KB combined cap). */
    val stdoutTruncated: Boolean,
    val stderrTruncated: Boolean,
    /** Termux-internal error code: -1 (Activity.RESULT_OK) means none. */
    val err: Int,
    val errmsg: String?
) {
    val succeeded: Boolean get() = err == -1 && exitCode == 0

    /** One plain-words line for the chat, used when the run failed. */
    fun failureLine(): String = when {
        err != -1 -> errmsg?.ifBlank { null }
            ?: "Termux reported an internal error (code $err)."
        exitCode == null -> "Termux never ran the command."
        else -> "Command finished with exit code $exitCode."
    }
}

/** What the setup check found on this phone. */
data class TermuxCheck(
    /** Termux is installed at all. */
    val installed: Boolean,
    /** The RUN_COMMAND permission exists on the device — true for the
     * F-Droid/GitHub build, false for the Play Store build (which strips
     * the whole RUN_COMMAND API). */
    val runCommandApiPresent: Boolean,
    /** The user granted NEO the RUN_COMMAND permission. */
    val permissionGranted: Boolean,
    /** Termux version, when installed, for display. */
    val versionName: String?
)

/**
 * Runs shell commands in the phone's real Termux via the official
 * RUN_COMMAND intent, background mode, with stdout/stderr/exit code back
 * through a PendingIntent. No root, no Termux:API app, no fake shell.
 *
 * Two gates must both be passed before anything runs (both are the user's
 * own doing, in Settings and inside Termux): the RUN_COMMAND permission,
 * and allow-external-apps=true in ~/.termux/termux.properties. NEO cannot
 * flip either one itself.
 */
class TermuxRunner(private val context: Context) {

    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<TermuxResult>>()

    /** The setup check the Help screen shows. */
    fun check(): TermuxCheck {
        val pm = context.packageManager
        val installed = try {
            pm.getPackageInfo(TermuxConstants.PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        val versionName = try {
            pm.getPackageInfo(TermuxConstants.PACKAGE, 0)?.versionName
        } catch (_: Exception) {
            null
        }
        val apiPresent = try {
            pm.getPermissionInfo(TermuxConstants.PERMISSION_RUN_COMMAND, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            TermuxConstants.PERMISSION_RUN_COMMAND
        ) == PackageManager.PERMISSION_GRANTED
        return TermuxCheck(installed, apiPresent, granted, versionName)
    }

    /**
     * Runs [command] in Termux's bash and waits for the result.
     * Throws [TermuxError] with a plain message when the command cannot
     * even be handed to Termux. A command that runs and fails comes back
     * as a [TermuxResult], not an exception.
     */
    suspend fun run(command: String, timeoutMs: Long = 120_000): TermuxResult {
        val check = check()
        if (!check.installed) {
            throw TermuxError(
                "Termux is not installed. Install it from F-Droid " +
                    "(not the Play Store) — see Help."
            )
        }
        if (!check.runCommandApiPresent) {
            throw TermuxError(
                "This Termux build has no RUN_COMMAND support — that is " +
                    "the Play Store build. Install Termux from F-Droid or " +
                    "GitHub instead (see Help)."
            )
        }
        if (!check.permissionGranted) {
            throw TermuxError(
                "NEO does not have the Termux permission yet. Grant it " +
                    "in Settings → Apps → NEO → Permissions → " +
                    "Additional permissions → \"Run commands in Termux\"."
            )
        }

        val executionId = nextId.getAndIncrement()
        val deferred = CompletableDeferred<TermuxResult>()
        pending[executionId] = deferred

        val resultIntent = Intent(context, TermuxResultReceiver::class.java)
            .setAction(TermuxConstants.RESULT_ACTION)
            .putExtra(TermuxConstants.EXTRA_EXECUTION_ID, executionId)
        val flags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        val pendingIntent = PendingIntent.getBroadcast(
            context, executionId, resultIntent, flags
        )

        val intent = Intent(TermuxConstants.ACTION_RUN_COMMAND).apply {
            setClassName(TermuxConstants.PACKAGE, TermuxConstants.SERVICE_CLASS)
            putExtra(TermuxConstants.EXTRA_COMMAND_PATH, TermuxConstants.BASH_PATH)
            putExtra(
                TermuxConstants.EXTRA_ARGUMENTS,
                arrayOf("-c", command)
            )
            putExtra(TermuxConstants.EXTRA_BACKGROUND, true)
            putExtra(TermuxConstants.EXTRA_COMMAND_LABEL, "NEO")
            putExtra(TermuxConstants.EXTRA_PENDING_INTENT, pendingIntent)
        }
        try {
            context.startService(intent)
        } catch (e: SecurityException) {
            pending.remove(executionId)
            throw TermuxError(
                "Android blocked the command — the Termux permission " +
                    "was refused. Grant it in NEO's app permissions."
            )
        } catch (e: IllegalStateException) {
            pending.remove(executionId)
            throw TermuxError(
                "Android would not start Termux from the background. " +
                    "Keep NEO on screen while it runs a command."
            )
        }

        val result = try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            pending.remove(executionId)
        }
        return result ?: TermuxResult(
            exitCode = null,
            stdout = "",
            stderr = "",
            stdoutTruncated = false,
            stderrTruncated = false,
            err = -1,
            errmsg = "Timed out after ${timeoutMs / 1000}s — the command " +
                "may still be running in Termux."
        )
    }

    /** Quick probe for the Help screen: runs `echo` and checks it comes back. */
    suspend fun probe(): Result<String> {
        return try {
            val result = run("echo NEO-PROBE-OK", timeoutMs = 30_000)
            if (result.stdout.contains("NEO-PROBE-OK")) {
                Result.success("Termux answered — the connection works.")
            } else {
                Result.failure(
                    TermuxError(
                        "Termux did not answer as expected. " +
                            result.failureLine() +
                            " Check allow-external-apps=true in Termux (see Help)."
                    )
                )
            }
        } catch (e: TermuxError) {
            Result.failure(e)
        }
    }

    internal fun onResult(intent: Intent) {
        val executionId = intent.getIntExtra(
            TermuxConstants.EXTRA_EXECUTION_ID, -1
        )
        if (executionId == -1) return
        val deferred = pending[executionId] ?: return
        val bundle = intent.getBundleExtra(TermuxConstants.RESULT_BUNDLE)
        deferred.complete(parseResult(bundle))
    }

    companion object {
        /**
         * Parses a Termux result bundle. The real work is in
         * [parseResultMap], over plain values, so JVM unit tests can
         * drive it without an Android device.
         */
        fun parseResult(bundle: Bundle?): TermuxResult {
            if (bundle == null) {
                return TermuxResult(
                    exitCode = null, stdout = "", stderr = "",
                    stdoutTruncated = false, stderrTruncated = false,
                    err = -1,
                    errmsg = "Termux sent back an empty result."
                )
            }
            val values = mutableMapOf<String, Any?>()
            for (key in bundle.keySet()) {
                values[key] = bundle.get(key)
            }
            return parseResultMap(values)
        }

        /**
         * Parses a result from plain values. The wiki's example reads the
         * original lengths with getString, so both String and int forms
         * are accepted.
         */
        fun parseResultMap(values: Map<String, Any?>): TermuxResult {
            val stdout = values[TermuxConstants.RESULT_STDOUT] as? String ?: ""
            val stderr = values[TermuxConstants.RESULT_STDERR] as? String ?: ""
            val stdoutOriginal = readLength(
                values, TermuxConstants.RESULT_STDOUT_ORIGINAL_LENGTH
            )
            val stderrOriginal = readLength(
                values, TermuxConstants.RESULT_STDERR_ORIGINAL_LENGTH
            )
            return TermuxResult(
                exitCode = (values[TermuxConstants.RESULT_EXIT_CODE] as? Number)?.toInt(),
                stdout = stdout,
                stderr = stderr,
                stdoutTruncated = stdoutOriginal != null && stdoutOriginal > stdout.length,
                stderrTruncated = stderrOriginal != null && stderrOriginal > stderr.length,
                err = (values[TermuxConstants.RESULT_ERR] as? Number)?.toInt() ?: -1,
                errmsg = values[TermuxConstants.RESULT_ERRMSG] as? String
            )
        }

        private fun readLength(values: Map<String, Any?>, key: String): Int? {
            return when (val v = values[key]) {
                is String -> v.toIntOrNull()
                is Number -> v.toInt()
                else -> null
            }
        }

        /** The single runner instance the receiver reports into. Set by the
         * Application container at startup. */
        @Volatile
        var shared: TermuxRunner? = null
    }
}

/** Receives Termux RUN_COMMAND results and hands them to the runner. */
class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TermuxRunner.shared?.onResult(intent)
    }
}
