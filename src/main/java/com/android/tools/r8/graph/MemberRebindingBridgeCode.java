// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeSuper;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.ThrowingMethodConversionOptions;
import com.android.tools.r8.ir.conversion.SyntheticStraightLineSourceCode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;

public class MemberRebindingBridgeCode extends Code {

  private final DexMethod target;
  private final boolean isInterface;

  private MemberRebindingBridgeCode(DexMethod target, boolean isInterface) {
    this.target = target;
    this.isInterface = isInterface;
  }

  @Override
  public boolean isMemberRebindingBridgeCode() {
    return true;
  }

  public DexMethod getTarget() {
    return target;
  }

  public boolean getInterface() {
    return isInterface;
  }

  @Override
  public boolean passThroughDesugarAndIRConversion() {
    return true;
  }

  @Override
  public MemberRebindingBridgeCode asMemberRebindingBridgeCode() {
    return this;
  }

  @Override
  public IRCode buildIR(
      ProgramMethod method,
      AppView<?> appView,
      Origin origin,
      MutableMethodConversionOptions conversionOptions) {
    DexMethod originalMethod =
        appView.graphLens().getOriginalMethodSignature(method.getReference());
    MemberRebindingBridgeSourceCode source =
        new MemberRebindingBridgeSourceCode(originalMethod, this.target, isInterface);
    return IRBuilder.create(method, appView, source, origin).build(method, conversionOptions);
  }

  @Override
  public IRCode buildInliningIR(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      GraphLens codeLens,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      RewrittenPrototypeDescription protoChanges) {
    DexMethod originalMethod =
        appView.graphLens().getOriginalMethodSignature(method.getReference());
    MemberRebindingBridgeSourceCode source =
        new MemberRebindingBridgeSourceCode(
            originalMethod, this.target, isInterface, callerPosition);
    return IRBuilder.createForInlining(
            method, appView, codeLens, source, origin, valueNumberGenerator, protoChanges)
        .build(context, new ThrowingMethodConversionOptions(appView.options()));
  }

  @Override
  public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
    registry.registerInvokeSuper(this.target);
  }

  @Override
  public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
    registry.registerInvokeSuper(this.target);
  }

  @Override
  public String toString() {
    return "<member-rebinding-bridge-code>";
  }

  @Override
  public String toString(DexEncodedMethod method, RetracerForCodePrinting retracer) {
    StringBuilder builder = new StringBuilder();
    if (method != null) {
      builder.append(method.toSourceString()).append("\n");
    }
    return builder.append(this).toString();
  }

  @Override
  public int estimatedDexCodeSizeUpperBoundInBytes() {
    throw new Unreachable();
  }

  @Override
  public boolean isEmptyVoidMethod() {
    return false;
  }

  @Override
  protected int computeHashCode() {
    return System.identityHashCode(this);
  }

  @Override
  protected boolean computeEquals(Object other) {
    return this == other;
  }

  @Override
  public boolean isSharedCodeObject() {
    return true;
  }

  public static Builder builder() {
    return new Builder();
  }

  static class MemberRebindingBridgeSourceCode extends SyntheticStraightLineSourceCode {

    MemberRebindingBridgeSourceCode(DexMethod context, DexMethod method, boolean isInterface) {
      this(context, method, isInterface, null);
    }

    MemberRebindingBridgeSourceCode(
        DexMethod context, DexMethod method, boolean isInterface, Position callerPosition) {
      super(
          getInstructionBuilders(method, isInterface),
          SyntheticPosition.builder()
              .setLine(0)
              .setMethod(context)
              .setCallerPosition(callerPosition)
              .build());
    }

    private static List<Consumer<IRBuilder>> getInstructionBuilders(
        DexMethod method, boolean isInterface) {
      return ImmutableList.of(
          builder -> {
            InvokeSuper invokeSuper =
                InvokeSuper.builder()
                    .setMethod(method)
                    .setArguments(
                        ListUtils.newArrayList(
                            builder.getReceiverValue(), builder.getArgumentValues()))
                    .setInterface(isInterface)
                    .applyIf(
                        !method.getReturnType().isVoidType(),
                        b -> b.setFreshOutValue(builder.appView, builder))
                    .build();
            builder.add(invokeSuper);
            builder.addReturn(new Return(invokeSuper.outValue()));
          });
    }
  }

  public static class Builder {

    private DexMethod target;
    private boolean isInterface;

    public Builder setTarget(DexMethod target) {
      this.target = target;
      return this;
    }

    public Builder setInterface(boolean isInterface) {
      this.isInterface = isInterface;
      return this;
    }

    public MemberRebindingBridgeCode build() {
      assert target != null;
      return new MemberRebindingBridgeCode(target, isInterface);
    }
  }
}
