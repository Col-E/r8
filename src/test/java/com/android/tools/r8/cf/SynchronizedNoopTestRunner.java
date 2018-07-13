/*
 * Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
 * for details. All rights reserved. Use of this source code is governed by a
 * BSD-style license that can be found in the LICENSE file.
 */

package com.android.tools.r8.cf;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.JarCode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

public class SynchronizedNoopTestRunner {
  private byte[] data;
  static final Class CLASS = SynchronizedNoopTest.class;

  @Test
  public void testSynchronizedNoop() throws Exception {
    AndroidAppConsumers a = new AndroidAppConsumers();
    R8.run(
        R8Command.builder()
            .addClassProgramData(ToolHelper.getClassAsBytes(CLASS), Origin.unknown())
            .addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()))
            .setProgramConsumer(a.wrapClassFileConsumer(null))
            .build());
    DexInspector inspector = new DexInspector(a.build());
    DexEncodedMethod method =
        inspector.clazz(CLASS).method("void", "noop", Collections.emptyList()).getMethod();
    ArrayList<AbstractInsnNode> insns = new ArrayList<>();
    JarCode jarCode = method.getCode().asJarCode();
    MethodNode node = jarCode.getNode();
    assert node != null;
    InsnList asmInsns = node.instructions;
    for (int i = 0; i < asmInsns.size(); i++) {
      insns.add(asmInsns.get(i));
    }
    boolean hasMonitor =
        insns
            .stream()
            .anyMatch(
                insn ->
                    insn.getOpcode() == Opcodes.MONITORENTER
                        || insn.getOpcode() == Opcodes.MONITOREXIT);
    assertFalse(hasMonitor);
  }
}
