/*
 * Build-time adaptation of Mozilla Gecko Android glue.
 * Gecko target files remain MPL-2.0-covered; corresponding source and modification
 * instructions are listed in licenses/NOTICE.txt. ASM is BSD-3-Clause; see licenses/.
 */
package com.tuman.build;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import org.objectweb.asm.ClassVisitor;

public abstract class GeckoPermissionVisitorFactory implements AsmClassVisitorFactory<InstrumentationParameters.None> {
    @Override public boolean isInstrumentable(ClassData data) {
        String name = data.getClassName();
        return PermissionGuards.SHELL.equals(name) || PermissionGuards.NETWORK.equals(name) || PermissionGuards.MANAGER.equals(name);
    }
    @Override public ClassVisitor createClassVisitor(ClassContext context, ClassVisitor next) {
        return PermissionGuards.visitor(context.getCurrentClassData().getClassName(), next);
    }
}
