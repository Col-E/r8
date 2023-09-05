// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.invokespecial;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.google.common.collect.ImmutableList;
import org.objectweb.asm.Opcodes;

/** This class defines the desugaring of a single invoke-special instruction. */
public class InvokeSpecialToSelfDesugaring implements CfInstructionDesugaring {

  public static final String INVOKE_SPECIAL_BRIDGE_PREFIX = "$invoke$special$";

  private final DexItemFactory dexItemFactory;

  public InvokeSpecialToSelfDesugaring(AppView<?> appView) {
    this.dexItemFactory = appView.dexItemFactory();
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (!instruction.isInvokeSpecial()) {
      return DesugarDescription.nothing();
    }

    CfInvoke invoke = instruction.asInvoke();
    if (!invoke.isInvokeSpecial() || invoke.isInvokeConstructor(dexItemFactory)) {
      return DesugarDescription.nothing();
    }

    DexMethod invokedMethod = invoke.getMethod();
    if (invokedMethod.getHolderType() != context.getHolderType()) {
      return DesugarDescription.nothing();
    }

    ProgramMethod method = context.getHolder().lookupProgramMethod(invokedMethod);
    if (method == null
        || method.getAccessFlags().isPrivate()
        || method.getDefinition().isStatic()
        || (invoke.isInterface() && method.isDefaultMethod())) {
      return DesugarDescription.nothing();
    }

    if (method.getAccessFlags().isFinal()) {
      // This method is final thus we can use invoke-virtual.
      return desugarToInvokeVirtual(invoke);
    }

    // This is an invoke-special to a virtual method on invoke-special method holder.
    // The invoke should be rewritten with a bridge.
    return desugarWithBridge(invoke, method);
  }

  private DesugarDescription desugarToInvokeVirtual(CfInvoke invoke) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                ImmutableList.of(
                    new CfInvoke(Opcodes.INVOKEVIRTUAL, invoke.getMethod(), invoke.isInterface())))
        .build();
  }

  private DesugarDescription desugarWithBridge(CfInvoke invoke, ProgramMethod method) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                ImmutableList.of(
                    new CfInvoke(
                        Opcodes.INVOKESPECIAL,
                        ensureInvokeSpecialBridge(method, eventConsumer),
                        invoke.isInterface())))
        .build();
  }

  private DexMethod ensureInvokeSpecialBridge(
      ProgramMethod method, InvokeSpecialToSelfDesugaringEventConsumer eventConsumer) {
    DexMethod bridgeReference = getInvokeSpecialBridgeReference(method);
    DexProgramClass clazz = method.getHolder();
    synchronized (clazz.getMethodCollection()) {
      if (clazz.lookupProgramMethod(bridgeReference) == null) {
        // Create a new private method holding the code of the virtual method.
        ProgramMethod newDirectMethod =
            method.getDefinition().toPrivateSyntheticMethod(clazz, bridgeReference);

        // Create the new cf code object for the virtual method.
        CfCode virtualMethodCode =
            ForwardMethodBuilder.builder(dexItemFactory)
                .setDirectTarget(bridgeReference, clazz.isInterface())
                .setNonStaticSource(method.getReference())
                .build();

        // Add the newly created direct method to its holder.
        clazz.addDirectMethod(newDirectMethod.getDefinition());

        eventConsumer.acceptInvokeSpecialBridgeInfo(
            new InvokeSpecialBridgeInfo(newDirectMethod, method, virtualMethodCode));
      }
    }
    return bridgeReference;
  }

  private DexMethod getInvokeSpecialBridgeReference(DexClassAndMethod method) {
    return method
        .getReference()
        .withName(
            dexItemFactory.createString(INVOKE_SPECIAL_BRIDGE_PREFIX + method.getName().toString()),
            dexItemFactory);
  }
}
