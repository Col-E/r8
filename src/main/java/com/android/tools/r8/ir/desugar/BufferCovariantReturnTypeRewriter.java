// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.ProgramMethod;
import com.google.common.collect.ImmutableList;

/**
 * BufferCovariantReturnTypeRewriter rewrites the return type of invoked methods matching
 * factory.bufferMembers.bufferCovariantMethods to return Buffer instead of the subtype.
 */
public class BufferCovariantReturnTypeRewriter implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final DexItemFactory factory;

  public BufferCovariantReturnTypeRewriter(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (!isInvokeCandidate(instruction)) {
      return DesugarDescription.nothing();
    }
    CfInvoke cfInvoke = instruction.asInvoke();
    DexMethod invokedMethod = cfInvoke.getMethod();
    DexMethod covariantMethod = matchingBufferCovariantMethod(invokedMethod);
    if (covariantMethod == null) {
      return DesugarDescription.nothing();
    }
    DexProto proto =
        factory.createProto(factory.bufferType, invokedMethod.getProto().parameters.values);
    CfInvoke newInvoke =
        new CfInvoke(
            cfInvoke.getOpcode(), invokedMethod.withProto(proto, factory), cfInvoke.isInterface());
    return desugarInstruction(invokedMethod, newInvoke);
  }

  private DesugarDescription desugarInstruction(DexMethod invokedMethod, CfInvoke newInvoke) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                ImmutableList.of(newInvoke, new CfCheckCast(invokedMethod.getReturnType())))
        .build();
  }

  @SuppressWarnings("ReferenceEquality")
  private DexMethod matchingBufferCovariantMethod(DexMethod invokedMethod) {
    if (invokedMethod.getArity() > 1
        || (invokedMethod.getArity() == 1 && !invokedMethod.getParameter(0).isIntType())
        || invokedMethod.getReturnType() == factory.bufferType
        || !factory.typeSpecificBuffers.contains(invokedMethod.holder)
        // The return type can differ from the holder with for example
        // holder: MappedByteBuffer, return type: ByteBuffer, covariant return type: Buffer.
        || !factory.typeSpecificBuffers.contains(invokedMethod.getReturnType())) {
      return null;
    }
    // This rewrites the methods only for the java library buffers, but it is not normally possible
    // to create user-defined buffers which suffer from the issue since all constructors in buffers
    // are package-private.
    for (DexMethod covariantMethod : factory.bufferMembers.bufferCovariantMethods) {
      if (covariantMethod.name == invokedMethod.name
          && covariantMethod.getParameters().equals(invokedMethod.getParameters())) {
        return covariantMethod;
      }
    }
    return null;
  }

  private boolean isInvokeCandidate(CfInstruction instruction) {
    return instruction.isInvoke()
        || instruction.isInvokeStatic()
        || instruction.isInvokeInterface();
  }
}
