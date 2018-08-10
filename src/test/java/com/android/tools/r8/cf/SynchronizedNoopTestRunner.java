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
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfMonitor;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

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
    CodeInspector inspector = new CodeInspector(a.build());
    DexEncodedMethod method =
        inspector.clazz(CLASS).method("void", "noop", Collections.emptyList()).getMethod();
    CfCode cfCode = method.getCode().asCfCode();
    List<CfInstruction> insns = cfCode.getInstructions();
    boolean hasMonitor = insns.stream().anyMatch(insn -> insn instanceof CfMonitor);
    assertFalse(hasMonitor);
  }
}
