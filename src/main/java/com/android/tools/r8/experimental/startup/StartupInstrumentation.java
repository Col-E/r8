// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.objectweb.asm.Opcodes;

public class StartupInstrumentation {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final StartupOptions options;

  public StartupInstrumentation(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options().getStartupOptions();
  }

  public void instrumentClasses(ExecutorService executorService) throws ExecutionException {
    if (!appView.options().getStartupOptions().isStartupInstrumentationEnabled()) {
      return;
    }
    ThreadUtils.processItems(
        appView.appInfo().classes(), this::internalInstrumentClass, executorService);
  }

  public void instrumentClass(DexProgramClass clazz) {
    if (!appView.options().getStartupOptions().isStartupInstrumentationEnabled()) {
      return;
    }
    internalInstrumentClass(clazz);
  }

  private void internalInstrumentClass(DexProgramClass clazz) {
    ProgramMethod classInitializer = ensureClassInitializer(clazz);
    instrumentClassInitializer(classInitializer);
  }

  private ProgramMethod ensureClassInitializer(DexProgramClass clazz) {
    if (!clazz.hasClassInitializer()) {
      int maxLocals = 0;
      int maxStack = 0;
      ComputedApiLevel computedApiLevel =
          appView.apiLevelCompute().computeInitialMinApiLevel(appView.options());
      clazz.addDirectMethod(
          DexEncodedMethod.syntheticBuilder()
              .setAccessFlags(MethodAccessFlags.createForClassInitializer())
              .setApiLevelForCode(computedApiLevel)
              .setApiLevelForDefinition(computedApiLevel)
              .setClassFileVersion(CfVersion.V1_6)
              .setCode(
                  new CfCode(
                      clazz.getType(), maxStack, maxLocals, ImmutableList.of(new CfReturnVoid())))
              .setMethod(dexItemFactory.createClassInitializer(clazz.getType()))
              .build());
    }
    return clazz.getProgramClassInitializer();
  }

  private void instrumentClassInitializer(ProgramMethod classInitializer) {
    Code code = classInitializer.getDefinition().getCode();
    if (!code.isCfCode()) {
      // Should generally not happen.
      assert false;
      return;
    }

    CfCode cfCode = code.asCfCode();
    List<CfInstruction> instructions;
    if (options.hasStartupInstrumentationTag()) {
      instructions = new ArrayList<>(4 + cfCode.getInstructions().size());
      instructions.add(
          new CfConstString(dexItemFactory.createString(options.getStartupInstrumentationTag())));
      instructions.add(new CfConstString(classInitializer.getHolderType().getDescriptor()));
      instructions.add(
          new CfInvoke(Opcodes.INVOKESTATIC, dexItemFactory.androidUtilLogMembers.i, false));
      instructions.add(new CfStackInstruction(Opcode.Pop));
    } else {
      instructions = new ArrayList<>(3 + cfCode.getInstructions().size());
      instructions.add(new CfStaticFieldRead(dexItemFactory.javaLangSystemMembers.out));
      instructions.add(new CfConstString(classInitializer.getHolderType().getDescriptor()));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKEVIRTUAL,
              dexItemFactory.javaIoPrintStreamMembers.printlnWithString,
              false));
    }
    instructions.addAll(cfCode.getInstructions());
    classInitializer.setCode(
        new CfCode(
            cfCode.getOriginalHolder(),
            Math.max(cfCode.getMaxStack(), 2),
            cfCode.getMaxLocals(),
            instructions,
            cfCode.getTryCatchRanges(),
            cfCode.getLocalVariables(),
            cfCode.getDiagnosticPosition(),
            cfCode.getMetadata()),
        appView);
  }
}
