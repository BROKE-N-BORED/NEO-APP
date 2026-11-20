package com.brokenbored.neo.terminal

/**
 * Termux RUN_COMMAND constants.
 *
 * Every literal here was read from Termux's own source on 2026-11-18
 * (termux-app master, termux-shared TermuxConstants.java) — not copied from
 * memory or from the wiki's truncated rendering. Two of them do NOT follow
 * the naive pattern, which is why they are pinned here with the source note:
 * EXTRA_COMMAND_LABEL is "...RUN_COMMAND_COMMAND_LABEL" (not "..._LABEL"),
 * and the result bundle key is just "result" (not a com.termux.* string).
 *
 * If Termux ever changes these, the fix is to re-read that file, not to
 * guess. The JitPack termux-shared library carries the same constants.
 */
object TermuxConstants {
    const val PACKAGE = "com.termux"
    const val SERVICE_CLASS = "com.termux.app.RunCommandService"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    const val EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
    const val EXTRA_COMMAND_HELP = "com.termux.RUN_COMMAND_COMMAND_HELP"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    const val EXTRA_RESULT_DIRECTORY = "com.termux.RUN_COMMAND_RESULT_DIRECTORY"

    /** Key of the result Bundle inside the PendingIntent's result intent. */
    const val RESULT_BUNDLE = "result"
    const val RESULT_STDOUT = "stdout"
    const val RESULT_STDOUT_ORIGINAL_LENGTH = "stdout_original_length"
    const val RESULT_STDERR = "stderr"
    const val RESULT_STDERR_ORIGINAL_LENGTH = "stderr_original_length"
    const val RESULT_EXIT_CODE = "exitCode"
    const val RESULT_ERR = "err"
    const val RESULT_ERRMSG = "errmsg"

    /** Absolute path of Termux's bash — the executable for ad-hoc commands
     * (args: ["-c", "<command>"]). */
    const val BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"

    /** Action of the broadcast NEO sends itself for command results. */
    const val RESULT_ACTION = "com.brokenbored.neo.TERMUX_RESULT"
    const val EXTRA_EXECUTION_ID = "execution_id"
}
