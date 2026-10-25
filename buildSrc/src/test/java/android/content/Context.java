package android.content;

/** JVM test double for Android's permission result, not shipped in the APK. */
public class Context {
    public static int permission;
    public int checkSelfPermission(String name) { return permission; }
}
