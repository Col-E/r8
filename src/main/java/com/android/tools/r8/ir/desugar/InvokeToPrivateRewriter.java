// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.google.common.collect.ImmutableList;
import org.objectweb.asm.Opcodes;

/**
 * If an invoke-virtual targets a private method in the current class overriding will not apply (see
 * JVM 11 spec on method selection 5.4.6. In previous jvm specs this was not explicitly stated, but
 * derived from method resolution 5.4.3.3 and overriding 5.4.5).
 *
 * <p>An invoke-interface can in the same way target a private method.
 *
 * <p>For desugaring we use invoke-direct instead. We need to do this as the Android Runtime will
 * not allow invoke-virtual of a private method.
 */
public class InvokeToPrivateRewriter implements CfInstructionDesugaring {

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (!instruction.isInvokeVirtual() && !instruction.isInvokeInterface()) {
      return DesugarDescription.nothing();
    }
    CfInvoke invoke = instruction.asInvoke();
    DexMethod method = invoke.getMethod();
    DexEncodedMethod privateMethod = privateMethodInvokedOnSelf(invoke, context);
    if (privateMethod == null) {
      return DesugarDescription.nothing();
    }
    return desugarInstruction(invoke, method);
  }

  private DesugarDescription desugarInstruction(CfInvoke invoke, DexMethod method) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                ImmutableList.of(new CfInvoke(Opcodes.INVOKESPECIAL, method, invoke.isInterface())))
        .build();
  }

  @SuppressWarnings("ReferenceEquality")
  private DexEncodedMethod privateMethodInvokedOnSelf(CfInvoke invoke, ProgramMethod context) {
    DexMethod method = invoke.getMethod();
    if (method.getHolderType() != context.getHolderType()) {
      return null;
    }
    DexEncodedMethod directTarget = context.getHolder().lookupDirectMethod(method);
    if (directTarget != null && !directTarget.isStatic()) {
      assert method.holder == directTarget.getHolderType();
      return directTarget;
    }
    return null;
  }
}
