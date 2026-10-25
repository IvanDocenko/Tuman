package com.tuman.build;

import org.junit.Test;
import org.objectweb.asm.*;
import java.lang.reflect.InvocationTargetException;
import static org.junit.Assert.*;

public class PermissionGuardsTest {
    private Class<?> target() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = PermissionGuards.visitor(PermissionGuards.SHELL, writer);
        visitor.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "org/mozilla/gecko/GeckoAppShell", null, "java/lang/Object", null);
        MethodVisitor context = visitor.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getApplicationContext", "()Landroid/content/Context;", null, null);
        context.visitCode();
        context.visitTypeInsn(Opcodes.NEW, "android/content/Context");
        context.visitInsn(Opcodes.DUP);
        context.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Context", "<init>", "()V", false);
        context.visitInsn(Opcodes.ARETURN); context.visitMaxs(0, 0); context.visitEnd();
        constant(visitor, "isNetworkLinkUp", "()Z", 0);
        constant(visitor, "isNetworkLinkKnown", "()Z", 1);
        constant(visitor, "getNetworkLinkType", "()I", 99);
        MethodVisitor lock = visitor.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "setWakeLockState", "(Ljava/lang/String;I)V", null, null);
        lock.visitCode();
        lock.visitTypeInsn(Opcodes.NEW, "java/lang/SecurityException");
        lock.visitInsn(Opcodes.DUP);
        lock.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/SecurityException", "<init>", "()V", false);
        lock.visitInsn(Opcodes.ATHROW); lock.visitMaxs(0, 0); lock.visitEnd();
        visitor.visitEnd();
        byte[] bytes = writer.toByteArray();
        return new ClassLoader(getClass().getClassLoader()) {
            Class<?> loadTarget() { return defineClass("org.mozilla.gecko.GeckoAppShell", bytes, 0, bytes.length); }
        }.loadTarget();
    }
    private void constant(ClassVisitor visitor, String name, String descriptor, int value) {
        MethodVisitor method = visitor.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, descriptor, null, null);
        method.visitCode(); method.visitLdcInsn(value); method.visitInsn(Opcodes.IRETURN); method.visitMaxs(0, 0); method.visitEnd();
    }
    @Test public void missingPermissionNeverCallsRestrictedBody() throws Exception {
        android.content.Context.permission = -1;
        Class<?> type = target();
        assertEquals(true, type.getMethod("isNetworkLinkUp").invoke(null));
        assertEquals(false, type.getMethod("isNetworkLinkKnown").invoke(null));
        assertEquals(0, type.getMethod("getNetworkLinkType").invoke(null));
        type.getMethod("setWakeLockState", String.class, int.class).invoke(null, "cpu", 1);
    }
    @Test public void grantedPermissionPreservesOriginalBehavior() throws Exception {
        android.content.Context.permission = 0;
        Class<?> type = target();
        assertEquals(false, type.getMethod("isNetworkLinkUp").invoke(null));
        assertEquals(true, type.getMethod("isNetworkLinkKnown").invoke(null));
        assertEquals(99, type.getMethod("getNetworkLinkType").invoke(null));
        try {
            type.getMethod("setWakeLockState", String.class, int.class).invoke(null, "cpu", 1);
            fail("Original restricted body should execute when permission is granted");
        } catch (InvocationTargetException exception) {
            assertTrue(exception.getCause() instanceof SecurityException);
        }
    }
    @Test public void changedGeckoSignaturesFailTheBuild() {
        ClassVisitor visitor = PermissionGuards.visitor(PermissionGuards.SHELL, new ClassWriter(0));
        assertThrows(IllegalStateException.class, visitor::visitEnd);
    }
}
