# NEO ProGuard rules. The release build does not minify yet, so this file
# is a placeholder for when it does. Keep the Room entities and the
# Termux receiver reachable.
-keep class com.brokenbored.neo.data.** { *; }
-keep class com.brokenbored.neo.terminal.TermuxResultReceiver { *; }
