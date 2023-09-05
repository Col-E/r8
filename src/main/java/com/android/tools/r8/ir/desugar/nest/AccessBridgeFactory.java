// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.nest;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.synthetic.FieldAccessorBuilder;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;

public class AccessBridgeFactory {

  @SuppressWarnings("ReferenceEquality")
  static ProgramMethod createFieldAccessorBridge(
      DexMethod bridgeMethodReference, ProgramField field, boolean isGet) {
    assert bridgeMethodReference.getHolderType() == field.getHolderType();
    assert field.getAccessFlags().isPrivate();
    return new ProgramMethod(
        field.getHolder(),
        DexEncodedMethod.syntheticBuilder()
            .setAccessFlags(
                MethodAccessFlags.builder()
                    .setBridge()
                    .setPublic(field.getHolder().isInterface())
                    .setStatic()
                    .setSynthetic()
                    .build())
            .setCode(
                FieldAccessorBuilder.builder()
                    .applyIf(
                        isGet, FieldAccessorBuilder::setGetter, FieldAccessorBuilder::setSetter)
                    .setField(field)
                    .setSourceMethod(bridgeMethodReference)
                    .build())
            .setMethod(bridgeMethodReference)
            .setApiLevelForDefinition(field.getDefinition().getApiLevel())
            .setApiLevelForCode(field.getDefinition().getApiLevel())
            .build());
  }

  @SuppressWarnings("ReferenceEquality")
  static ProgramMethod createInitializerAccessorBridge(
      DexMethod bridgeMethodReference, ProgramMethod method, DexItemFactory dexItemFactory) {
    assert bridgeMethodReference.getHolderType() == method.getHolderType();
    assert method.getAccessFlags().isConstructor();
    assert method.getAccessFlags().isPrivate();
    assert !method.getHolder().isInterface();
    return new ProgramMethod(
        method.getHolder(),
        DexEncodedMethod.syntheticBuilder()
            // Not setting the 'bridge' flag as this fails verification.
            .setAccessFlags(MethodAccessFlags.builder().setConstructor().setSynthetic().build())
            .setCode(
                ForwardMethodBuilder.builder(dexItemFactory)
                    .setNonStaticSourceWithExtraUnusedParameter(bridgeMethodReference)
                    .setConstructorTarget(method.getReference())
                    .build())
            .setMethod(bridgeMethodReference)
            .setApiLevelForDefinition(method.getDefinition().getApiLevelForDefinition())
            .setApiLevelForCode(method.getDefinition().getApiLevelForCode())
            .build());
  }

  @SuppressWarnings("ReferenceEquality")
  static ProgramMethod createMethodAccessorBridge(
      DexMethod bridgeMethodReference, ProgramMethod method, DexItemFactory dexItemFactory) {
    assert bridgeMethodReference.getHolderType() == method.getHolderType();
    assert !method.getAccessFlags().isConstructor();
    assert method.getAccessFlags().isPrivate();
    boolean isInterface = method.getHolder().isInterface();
    return new ProgramMethod(
        method.getHolder(),
        DexEncodedMethod.syntheticBuilder()
            .setAccessFlags(
                MethodAccessFlags.builder()
                    .setBridge()
                    .setPublic(isInterface)
                    .setStatic()
                    .setSynthetic()
                    .build())
            .setCode(
                ForwardMethodBuilder.builder(dexItemFactory)
                    .setStaticSource(bridgeMethodReference)
                    .applyIf(
                        method.getAccessFlags().isStatic(),
                        builder -> builder.setStaticTarget(method.getReference(), isInterface),
                        builder -> builder.setDirectTarget(method.getReference(), isInterface))
                    .build())
            .setMethod(bridgeMethodReference)
            .setApiLevelForDefinition(method.getDefinition().getApiLevelForDefinition())
            .setApiLevelForCode(method.getDefinition().getApiLevelForDefinition())
            .build());
  }
}
