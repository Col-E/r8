// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.twr;

import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.desugar.backports.BackportedMethods;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;

public class TwrInstructionDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final DexProto twrCloseResourceProto;
  private final DexMethod addSuppressed;
  private final DexMethod getSuppressed;

  public TwrInstructionDesugaring(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.twrCloseResourceProto =
        dexItemFactory.createProto(
            dexItemFactory.voidType, dexItemFactory.throwableType, dexItemFactory.objectType);
    this.addSuppressed = dexItemFactory.throwableMethods.addSuppressed;
    this.getSuppressed = dexItemFactory.throwableMethods.getSuppressed;
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      CfInstructionDesugaringCollection desugaringCollection,
      DexItemFactory dexItemFactory) {
    if (!instruction.isInvoke()) {
      return null;
    }
    if (isTwrCloseResourceInvoke(instruction)) {
      return rewriteTwrCloseResourceInvoke(eventConsumer, context, methodProcessingContext);
    }
    if (isTwrSuppressedInvoke(instruction, addSuppressed)) {
      return rewriteTwrAddSuppressedInvoke();
    }
    if (isTwrSuppressedInvoke(instruction, getSuppressed)) {
      return rewriteTwrGetSuppressedInvoke();
    }
    return null;
  }

  private Collection<CfInstruction> rewriteTwrAddSuppressedInvoke() {
    // Remove Throwable::addSuppressed(Throwable) call.
    return ImmutableList.of(new CfStackInstruction(Opcode.Pop), new CfStackInstruction(Opcode.Pop));
  }

  private Collection<CfInstruction> rewriteTwrGetSuppressedInvoke() {
    // Replace call to Throwable::getSuppressed() with new Throwable[0].
    return ImmutableList.of(
        new CfStackInstruction(Opcode.Pop),
        new CfConstNumber(0, ValueType.INT),
        new CfNewArray(dexItemFactory.createArrayType(1, dexItemFactory.throwableType)));
  }

  @NotNull
  private ImmutableList<CfInstruction> rewriteTwrCloseResourceInvoke(
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
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
                    // Will be traced by the enqueuer.
                    .disableAndroidApiLevelCheck()
                    .setCode(
                        m ->
                            BackportedMethods.CloseResourceMethod_closeResourceImpl(
                                appView.options(), m)));
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    if (!instruction.isInvoke()) {
      return false;
    }
    return isTwrCloseResourceInvoke(instruction)
        || isTwrSuppressedInvoke(instruction, addSuppressed)
        || isTwrSuppressedInvoke(instruction, getSuppressed);
  }

  private boolean isTwrSuppressedInvoke(CfInstruction instruction, DexMethod suppressed) {
    return instruction.isInvoke()
        && matchesMethodOfThrowable(instruction.asInvoke().getMethod(), suppressed);
  }

  private boolean matchesMethodOfThrowable(DexMethod invoked, DexMethod expected) {
    return invoked.name == expected.name
        && invoked.proto == expected.proto
        && isSubtypeOfThrowable(invoked.holder);
  }

  private boolean isSubtypeOfThrowable(DexType type) {
    while (type != null && type != dexItemFactory.objectType) {
      if (type == dexItemFactory.throwableType) {
        return true;
      }
      DexClass dexClass = appView.definitionFor(type);
      if (dexClass == null) {
        throw new CompilationError(
            "Class or interface "
                + type.toSourceString()
                + " required for desugaring of try-with-resources is not found.");
      }
      type = dexClass.superType;
    }
    return false;
  }

  private boolean isTwrCloseResourceInvoke(CfInstruction instruction) {
    return instruction.isInvokeStatic()
        && isTwrCloseResourceMethod(instruction.asInvoke().getMethod());
  }

  private boolean isTwrCloseResourceMethod(DexMethod method) {
    return method.name == dexItemFactory.twrCloseResourceMethodName
        && method.proto == dexItemFactory.twrCloseResourceMethodProto;
  }
}
