// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.twr;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.desugar.backports.BackportedMethods;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.objectweb.asm.Opcodes;

public class TwrCloseResourceInstructionDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final DexProto twrCloseResourceProto;

  public TwrCloseResourceInstructionDesugaring(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.twrCloseResourceProto =
        dexItemFactory.createProto(
            dexItemFactory.voidType, dexItemFactory.throwableType, dexItemFactory.objectType);
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      DexItemFactory dexItemFactory) {
    if (!instruction.isInvokeStatic()) {
      return null;
    }

    CfInvoke invoke = instruction.asInvoke();
    DexMethod invokedMethod = invoke.getMethod();
    if (!isTwrCloseResourceMethod(invokedMethod)) {
      return null;
    }

    // Synthesize a new method.
    ProgramMethod closeMethod = createSyntheticCloseResourceMethod(methodProcessingContext);
    eventConsumer.acceptTwrCloseResourceMethod(closeMethod, context);

    // Rewrite the invoke to the new synthetic.
    return ImmutableList.of(new CfInvoke(Opcodes.INVOKESTATIC, closeMethod.getReference(), false));
  }

  private ProgramMethod createSyntheticCloseResourceMethod(
      MethodProcessingContext methodProcessingContext) {
    return appView
        .getSyntheticItems()
        .createMethod(
            SyntheticKind.TWR_CLOSE_RESOURCE,
            methodProcessingContext.createUniqueContext(),
            appView,
            methodBuilder ->
                methodBuilder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setProto(twrCloseResourceProto)
                    .setCode(
                        m ->
                            BackportedMethods.CloseResourceMethod_closeResourceImpl(
                                appView.options(), m)));
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    return instruction.isInvokeStatic()
        && isTwrCloseResourceMethod(instruction.asInvoke().getMethod());
  }

  private boolean isTwrCloseResourceMethod(DexMethod method) {
    return method.name == dexItemFactory.twrCloseResourceMethodName
        && method.proto == dexItemFactory.twrCloseResourceMethodProto;
  }
}
