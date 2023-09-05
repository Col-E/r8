// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.RewrittenPrototypeDescriptionMethodOptimizationInfoFixer;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfoFixer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class RewrittenPrototypeDescription {

  private static final RewrittenPrototypeDescription NONE = new RewrittenPrototypeDescription();

  private final List<ExtraParameter> extraParameters;
  private final ArgumentInfoCollection argumentInfoCollection;
  private final RewrittenTypeInfo rewrittenReturnInfo;

  private RewrittenPrototypeDescription() {
    this.extraParameters = Collections.emptyList();
    this.rewrittenReturnInfo = null;
    this.argumentInfoCollection = ArgumentInfoCollection.empty();
  }

  private RewrittenPrototypeDescription(
      List<ExtraParameter> extraParameters,
      RewrittenTypeInfo rewrittenReturnInfo,
      ArgumentInfoCollection argumentsInfo) {
    assert argumentsInfo != null;
    this.extraParameters = extraParameters;
    this.rewrittenReturnInfo = rewrittenReturnInfo;
    this.argumentInfoCollection = argumentsInfo;
    assert !isEmpty();
  }

  public static RewrittenPrototypeDescription create(
      List<ExtraParameter> extraParameters,
      RewrittenTypeInfo rewrittenReturnInfo,
      ArgumentInfoCollection argumentsInfo) {
    return extraParameters.isEmpty() && rewrittenReturnInfo == null && argumentsInfo.isEmpty()
        ? none()
        : new RewrittenPrototypeDescription(extraParameters, rewrittenReturnInfo, argumentsInfo);
  }

  public static RewrittenPrototypeDescription createForRewrittenTypes(
      RewrittenTypeInfo returnInfo, ArgumentInfoCollection rewrittenArgumentsInfo) {
    return create(Collections.emptyList(), returnInfo, rewrittenArgumentsInfo);
  }

  public static RewrittenPrototypeDescription none() {
    return NONE;
  }

  public Consumer<DexEncodedMethod.Builder> createParameterAnnotationsRemover(
      DexEncodedMethod method) {
    return getArgumentInfoCollection().createParameterAnnotationsRemover(method);
  }

  public MethodOptimizationInfoFixer createMethodOptimizationInfoFixer() {
    return new RewrittenPrototypeDescriptionMethodOptimizationInfoFixer(this);
  }

  public RewrittenPrototypeDescription combine(RewrittenPrototypeDescription other) {
    if (isEmpty()) {
      return other;
    }
    if (other.isEmpty()) {
      return this;
    }
    // We currently don't have any passes that remove extra parameters inserted by previous passes.
    // If the input prototype changes have removed some of the extra parameters, we would need to
    // adapt the merging of prototype changes below.
    List<ExtraParameter> newExtraParameters =
        ImmutableList.<ExtraParameter>builder()
            .addAll(getExtraParameters())
            .addAll(other.getExtraParameters())
            .build();
    RewrittenTypeInfo newRewrittenTypeInfo =
        hasRewrittenReturnInfo()
            ? getRewrittenReturnInfo().combine(other)
            : other.getRewrittenReturnInfo();
    ArgumentInfoCollection newArgumentInfoCollection =
        getArgumentInfoCollection().combine(other.getArgumentInfoCollection());
    return new RewrittenPrototypeDescription(
        newExtraParameters, newRewrittenTypeInfo, newArgumentInfoCollection);
  }

  public boolean isEmpty() {
    return extraParameters.isEmpty()
        && rewrittenReturnInfo == null
        && argumentInfoCollection.isEmpty();
  }

  public boolean hasExtraParameters() {
    return !extraParameters.isEmpty();
  }

  public List<ExtraParameter> getExtraParameters() {
    return extraParameters;
  }

  public int numberOfExtraParameters() {
    return extraParameters.size();
  }

  public boolean hasBeenChangedToReturnVoid() {
    return rewrittenReturnInfo != null && rewrittenReturnInfo.hasBeenChangedToReturnVoid();
  }

  public ArgumentInfoCollection getArgumentInfoCollection() {
    return argumentInfoCollection;
  }

  public boolean hasRewrittenReturnInfo() {
    return rewrittenReturnInfo != null;
  }

  public boolean requiresRewritingAtCallSite() {
    return hasRewrittenReturnInfo()
        || numberOfExtraParameters() > 0
        || argumentInfoCollection.hasArgumentPermutation()
        || argumentInfoCollection.numberOfRemovedArguments() > 0;
  }

  public RewrittenTypeInfo getRewrittenReturnInfo() {
    return rewrittenReturnInfo;
  }

  /**
   * Returns the {@link ConstInstruction} that should be used to materialize the result of
   * invocations to the method represented by this {@link RewrittenPrototypeDescription}.
   *
   * <p>This method should only be used for methods that return a constant value and whose return
   * type has been changed to void.
   *
   * <p>Note that the current implementation always returns null at this point.
   */
  public Instruction getConstantReturn(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      Position position,
      TypeAndLocalInfoSupplier info) {
    assert rewrittenReturnInfo != null;
    assert rewrittenReturnInfo.hasSingleValue();
    Instruction instruction =
        rewrittenReturnInfo.getSingleValue().createMaterializingInstruction(appView, code, info);
    instruction.setPosition(position);
    return instruction;
  }

  public boolean verifyConstantReturnAccessibleInContext(
      AppView<AppInfoWithLiveness> appView, ProgramMethod method, GraphLens codeLens) {
    SingleValue rewrittenSingleValue =
        rewrittenReturnInfo
            .getSingleValue()
            .rewrittenWithLens(appView, appView.graphLens(), codeLens);
    assert rewrittenSingleValue.isMaterializableInContext(appView, method);
    return true;
  }

  public DexMethod rewriteMethod(ProgramMethod method, DexItemFactory dexItemFactory) {
    if (isEmpty()) {
      return method.getReference();
    }
    DexProto rewrittenProto = rewriteProto(method, dexItemFactory);
    return method.getReference().withProto(rewrittenProto, dexItemFactory);
  }

  public DexProto rewriteProto(ProgramMethod method, DexItemFactory dexItemFactory) {
    if (isEmpty()) {
      return method.getProto();
    }
    DexType newReturnType =
        rewrittenReturnInfo != null ? rewrittenReturnInfo.getNewType() : method.getReturnType();
    DexType[] newParameters = rewriteParameters(method, dexItemFactory);
    return dexItemFactory.createProto(newReturnType, newParameters);
  }

  @SuppressWarnings("ReferenceEquality")
  public DexType[] rewriteParameters(ProgramMethod method, DexItemFactory dexItemFactory) {
    DexType[] params = method.getParameters().values;
    if (isEmpty()) {
      return params;
    }
    DexType[] newParams =
        new DexType
            [params.length
                - argumentInfoCollection.numberOfRemovedNonReceiverArguments(method)
                + extraParameters.size()];
    int offset = method.getDefinition().getFirstNonReceiverArgumentIndex();
    int newParamIndex = 0;
    for (int oldParamIndex = 0; oldParamIndex < params.length; oldParamIndex++) {
      ArgumentInfo argInfo = argumentInfoCollection.getArgumentInfo(oldParamIndex + offset);
      if (argInfo.isNone()) {
        newParams[newParamIndex++] = params[oldParamIndex];
      } else if (argInfo.isRewrittenTypeInfo()) {
        RewrittenTypeInfo rewrittenTypeInfo = argInfo.asRewrittenTypeInfo();
        assert params[oldParamIndex] == rewrittenTypeInfo.getOldType();
        newParams[newParamIndex++] = rewrittenTypeInfo.getNewType();
      }
    }
    for (ExtraParameter extraParameter : extraParameters) {
      newParams[newParamIndex++] = extraParameter.getType(dexItemFactory);
    }
    return newParams;
  }

  @SuppressWarnings("ReferenceEquality")
  public RewrittenPrototypeDescription rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens graphLens, GraphLens codeLens) {
    ArgumentInfoCollection newArgumentInfoCollection =
        argumentInfoCollection.rewrittenWithLens(appView, graphLens, codeLens);
    RewrittenTypeInfo newRewrittenReturnInfo =
        hasRewrittenReturnInfo()
            ? rewrittenReturnInfo.rewrittenWithLens(appView, graphLens, codeLens)
            : null;
    if (newArgumentInfoCollection != argumentInfoCollection
        || newRewrittenReturnInfo != rewrittenReturnInfo) {
      return new RewrittenPrototypeDescription(
          extraParameters, newRewrittenReturnInfo, newArgumentInfoCollection);
    }
    return this;
  }

  public RewrittenPrototypeDescription withRewrittenReturnInfo(
      RewrittenTypeInfo newRewrittenReturnInfo) {
    if (Objects.equals(rewrittenReturnInfo, newRewrittenReturnInfo)) {
      return this;
    }
    return new RewrittenPrototypeDescription(
        extraParameters, newRewrittenReturnInfo, argumentInfoCollection);
  }

  public RewrittenPrototypeDescription withExtraParameters(ExtraParameter... parameters) {
    return withExtraParameters(Arrays.asList(parameters));
  }

  public RewrittenPrototypeDescription withExtraParameters(
      List<? extends ExtraParameter> parameters) {
    if (parameters.isEmpty()) {
      return this;
    }
    List<ExtraParameter> newExtraParameters =
        new ArrayList<>(extraParameters.size() + parameters.size());
    newExtraParameters.addAll(extraParameters);
    newExtraParameters.addAll(parameters);
    return new RewrittenPrototypeDescription(
        newExtraParameters, rewrittenReturnInfo, argumentInfoCollection);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    RewrittenPrototypeDescription other = (RewrittenPrototypeDescription) obj;
    return extraParameters.equals(other.extraParameters)
        && Objects.equals(rewrittenReturnInfo, other.rewrittenReturnInfo)
        && argumentInfoCollection.equals(other.argumentInfoCollection);
  }

  @Override
  public int hashCode() {
    return Objects.hash(extraParameters, rewrittenReturnInfo, argumentInfoCollection);
  }
}
