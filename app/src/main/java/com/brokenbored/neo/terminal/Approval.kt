package com.brokenbored.neo.terminal

/**
 * The approval rules for terminal commands. Three outcomes:
 *
 * - ALLOW: run it.
 * - ASK: show the approval card (Allow once / Always allow / Deny).
 * - REFUSE: never run it, and say why in plain words.
 *
 * Dangerous commands always ask, even under "always allow" — and refused
 * commands never run at all. "Always allow" matches the exact command
 * string only, so a lookalike with one word changed still asks.
 */
object Approval {

    enum class Decision { ALLOW, ASK, REFUSE }

    /** A refused command: the reason shown to the user. Null = not refused. */
    fun refusedReason(command: String): String? {
        val c = command.trim()
        // Fork bomb.
        if (c.contains(":(){")) {
            return "That is a fork bomb — it would crash the phone. Refused."
        }
        // Wiping the root filesystem or the whole home directory. The target
        // must be exactly / , /*, ~, $HOME or /data — a sub-path like
        // /tmp/junk is dangerous (asks), not refused.
        // ([$] is a character class for a literal dollar: a raw Kotlin
        // string cannot hold \$ the way the regex needs it.)
        if (Regex("""\brm\s+[^|;&]*-[a-z]*r[a-z]*\b[^|;&]*\s(/|/\*|~|[$]HOME|/data)(\s|$|[|;&])""").containsMatchIn(c)) {
            return "That would wipe the whole system or home directory. Refused."
        }
        // Formatting a device.
        if (Regex("""\bmkfs(\.\w+)?\b""").containsMatchIn(c)) {
            return "Formatting a filesystem is never run from chat. Refused."
        }
        // Raw writes to a block device.
        if (Regex("""\bdd\b[^|;&]*\bof=/dev/""").containsMatchIn(c)) {
            return "Writing straight to a device would destroy data. Refused."
        }
        return null
    }

    /** A dangerous command: the reason shown on the approval card.
     * Null = not dangerous. Dangerous commands always ask. */
    fun dangerousReason(command: String): String? {
        val c = command.trim()
        // Recursive/forced delete of anything.
        if (Regex("""\brm\s+[^|;&]*-[a-z]*r[a-z]*""").containsMatchIn(c)) {
            return "Deletes files without asking."
        }
        if (Regex("""\b(sudo|su)\b""").containsMatchIn(c)) {
            return "Runs as another user or as root."
        }
        if (Regex("""\b(shutdown|reboot|poweroff|halt)\b""").containsMatchIn(c)) {
            return "Shuts down or reboots the phone."
        }
        // Piping a download straight into a shell.
        if (Regex("""\b(curl|wget)\b[^|]*\|\s*(sh|bash)\b""").containsMatchIn(c)) {
            return "Runs a script downloaded from the internet."
        }
        // Installing software — always a deliberate choice.
        if (Regex("""\b(pkg|apt|apt-get|pip|pip3|npm)\s+install\b""").containsMatchIn(c)) {
            return "Installs software."
        }
        if (Regex("""\b(chmod|chown)\s+(-R\s+)?777\b""").containsMatchIn(c)) {
            return "Opens permissions wide."
        }
        if (Regex("""\b(killall|pkill|kill\s+-9)\b""").containsMatchIn(c)) {
            return "Kills running processes."
        }
        if (Regex("""\bollama\s+rm\b""").containsMatchIn(c)) {
            return "Deletes an Ollama model."
        }
        if (Regex("""\b(termux-setup-storage|termux-change-repo)\b""").containsMatchIn(c)) {
            return "Changes Termux setup."
        }
        return null
    }

    fun decide(
        command: String,
        alwaysAllow: Set<String>,
        defaultAllowSafe: Boolean
    ): Decision {
        if (refusedReason(command) != null) return Decision.REFUSE
        if (dangerousReason(command) != null) return Decision.ASK
        if (alwaysAllow.contains(command.trim())) return Decision.ALLOW
        if (defaultAllowSafe) return Decision.ALLOW
        return Decision.ASK
    }

    private val FENCE = Regex(
        """```(?:sh|bash|shell|terminal|console)?\s*\n(.*?)```""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * Commands the model proposed: fenced ```sh / ```bash / ```shell /
     * ```terminal / ```console blocks in its reply. Each block is one
     * command (it may span lines — bash -c runs the whole block).
     * Plain ``` blocks are not treated as commands.
     */
    fun extractCommands(markdown: String): List<String> =
        FENCE.findAll(markdown)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
}
