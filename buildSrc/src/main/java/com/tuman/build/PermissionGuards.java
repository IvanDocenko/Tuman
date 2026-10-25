/*
 * Build-time adaptation of Mozilla Gecko Android glue.
 * Gecko target files remain MPL-2.0-covered; corresponding source and modification
 * instructions are listed in licenses/NOTICE.txt. ASM is BSD-3-Clause; see licenses/.
 */
package com.tuman.build;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Gecko's stock Android glue assumes permissions that Tuman deliberately does not request.
 * Insert permission checks before those calls, retaining the original body when allowed.
 * Known method descriptors are checked so a Gecko upgrade cannot silently drop a guard.
 */
public final class PermissionGuards {
    private PermissionGuards() {}
    public static final String SHELL = "org.mozilla.gecko.GeckoAppShell";
    public static final String NETWORK = "org.mozilla.gecko.util.NetworkUtils";
    public static final String MANAGER = "org.mozilla.gecko.GeckoNetworkManager";
    private static final String CONNECTIVITY = "(Landroid/net/ConnectivityManager;)";
    private static final String UTILS = "org/mozilla/gecko/util/NetworkUtils$";

    public static ClassVisitor visitor(String className, ClassVisitor next) {
        Map<String, String> methods;
        if (SHELL.equals(className)) {
            methods = Map.of("setWakeLockState(Ljava/lang/String;I)V", "void",
                "isNetworkLinkUp()Z", "true", "isNetworkLinkKnown()Z", "false",
                "getNetworkLinkType()I", "zero");
        } else if (NETWORK.equals(className)) {
            methods = Map.of("isConnected" + CONNECTIVITY + "Z", "true",
                "getConnectionType" + CONNECTIVITY + "L" + UTILS + "ConnectionType;", "ConnectionType:OTHER",
                "getConnectionSubType" + CONNECTIVITY + "L" + UTILS + "ConnectionSubType;", "ConnectionSubType:UNKNOWN",
                "getNetworkStatus" + CONNECTIVITY + "L" + UTILS + "NetworkStatus;", "NetworkStatus:UNKNOWN");
        } else if (MANAGER.equals(className)) {
            methods = Map.of("registerNetworkCallback(Landroid/content/Context;)V", "void");
        } else {
            throw new IllegalArgumentException("Unexpected Gecko class: " + className);
        }
        return new ClassVisitor(Opcodes.ASM9, next) {
            private final Set<String> found = new HashSet<>();
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                       String signature, String[] exceptions) {
                MethodVisitor original = super.visitMethod(access, name, descriptor, signature, exceptions);
                String methodKey = name + descriptor;
                String fallback = methods.get(methodKey);
                if (fallback == null) return original;
                found.add(methodKey);
                String permission = name.equals("setWakeLockState") ?
                    "android.permission.WAKE_LOCK" : "android.permission.ACCESS_NETWORK_STATE";
                return new MethodVisitor(Opcodes.ASM9, original) {
                    @Override public void visitCode() {
                        super.visitCode();
                        Label contextPresent = new Label();
                        Label allowed = new Label();
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "org/mozilla/gecko/GeckoAppShell",
                            "getApplicationContext", "()Landroid/content/Context;", false);
                        super.visitInsn(Opcodes.DUP);
                        super.visitJumpInsn(Opcodes.IFNONNULL, contextPresent);
                        super.visitInsn(Opcodes.POP);
                        emitFallback(this.mv, fallback);
                        super.visitLabel(contextPresent);
                        super.visitLdcInsn(permission);
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Context",
                            "checkSelfPermission", "(Ljava/lang/String;)I", false);
                        super.visitJumpInsn(Opcodes.IFEQ, allowed);
                        emitFallback(this.mv, fallback);
                        super.visitLabel(allowed);
                    }
                };
            }
            @Override public void visitEnd() {
                if (!found.equals(methods.keySet())) {
                    throw new IllegalStateException("Gecko permission guard signatures changed in " + className + ": " + found);
                }
                super.visitEnd();
            }
        };
    }
    private static void emitFallback(MethodVisitor method, String value) {
        switch (value) {
            case "void": method.visitInsn(Opcodes.RETURN); break;
            case "true": method.visitInsn(Opcodes.ICONST_1); method.visitInsn(Opcodes.IRETURN); break;
            case "false":
            case "zero": method.visitInsn(Opcodes.ICONST_0); method.visitInsn(Opcodes.IRETURN); break;
            default:
                String[] parts = value.split(":");
                String owner = UTILS + parts[0];
                method.visitFieldInsn(Opcodes.GETSTATIC, owner, parts[1], "L" + owner + ";");
                method.visitInsn(Opcodes.ARETURN);
        }
    }
}
