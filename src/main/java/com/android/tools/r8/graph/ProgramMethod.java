// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.ir.optimize.info.OptimizationFeedback.getSimpleFeedback;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.kotlin.KotlinMethodLevelInfo;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;

/** Type representing a method definition in the programs compilation unit and its holder. */
public final class ProgramMethod extends DexClassAndMethod
    implements ProgramMember<DexEncodedMethod, DexMethod> {

  public ProgramMethod(DexProgramClass holder, DexEncodedMethod method) {
    super(holder, method);
  }

  public IRCode buildIR(AppView<?> appView) {
    return buildIR(appView, MethodConversionOptions.forLirPhase(appView));
  }

  public IRCode buildIR(AppView<?> appView, MutableMethodConversionOptions conversionOptions) {
    DexEncodedMethod method = getDefinition();
    return method.hasCode()
        ? method.getCode().buildIR(this, appView, getOrigin(), conversionOptions)
        : null;
  }

  public IRCode buildInliningIR(
      ProgramMethod context,
      AppView<?> appView,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      MethodProcessor methodProcessor) {
    Code code = getDefinition().getCode();
    GraphLens codeLens = appView.graphLens();
    RewrittenPrototypeDescription protoChanges = RewrittenPrototypeDescription.none();
    if (methodProcessor.shouldApplyCodeRewritings(this)) {
      codeLens = getDefinition().getCode().getCodeLens(appView);
      protoChanges = appView.graphLens().lookupPrototypeChangesForMethodDefinition(getReference());
    }
    return code.buildInliningIR(
        context,
        this,
        appView,
        codeLens,
        valueNumberGenerator,
        callerPosition,
        origin,
        protoChanges);
  }

  public void collectIndexedItems(
      AppView<?> appView, IndexedItemCollection indexedItems, LensCodeRewriterUtils rewriter) {
    DexEncodedMethod definition = getDefinition();
    assert !definition.isObsolete();
    getReference().collectIndexedItems(appView, indexedItems);
    if (definition.hasCode()) {
      Code code = definition.getCode();
      code.asDexWritableCode().collectIndexedItems(appView, indexedItems, this, rewriter);
    }
    definition.annotations().collectIndexedItems(appView, indexedItems);
    definition.parameterAnnotationsList.collectIndexedItems(appView, indexedItems);
  }

  public boolean canBeConvertedToAbstractMethod(AppView<AppInfoWithLiveness> appView) {
    return (appView.options().canUseAbstractMethodOnNonAbstractClass()
            || getHolder().isAbstract()
            || getHolder().isInterface())
        && !getAccessFlags().isNative()
        && !getAccessFlags().isPrivate()
        && !getAccessFlags().isStatic()
        && !getDefinition().isInstanceInitializer()
        && !appView.appInfo().isFailedMethodResolutionTarget(getReference());
  }

  public void convertToAbstractOrThrowNullMethod(AppView<AppInfoWithLiveness> appView) {
    if (!convertToAbstractMethodIfPossible(appView)) {
      convertToThrowNullMethod(appView);
    }
  }

  private boolean convertToAbstractMethodIfPossible(AppView<AppInfoWithLiveness> appView) {
    boolean canBeAbstract = canBeConvertedToAbstractMethod(appView);
    if (canBeAbstract) {
      MethodAccessFlags accessFlags = getAccessFlags();
      accessFlags.demoteFromFinal();
      accessFlags.demoteFromStrict();
      accessFlags.demoteFromSynchronized();
      accessFlags.promoteToAbstract();
      getDefinition().clearApiLevelForCode();
      getDefinition().unsetCode();
      getSimpleFeedback().unsetOptimizationInfoForAbstractMethod(this);
    }
    return canBeAbstract;
  }

  public void convertToThrowNullMethod(AppView<?> appView) {
    MethodAccessFlags accessFlags = getAccessFlags();
    accessFlags.demoteFromAbstract();
    getDefinition().setApiLevelForCode(appView.computedMinApiLevel());
    setCode(ThrowNullCode.get(), appView);
    getSimpleFeedback().markProcessed(getDefinition(), ConstraintWithTarget.ALWAYS);
    getSimpleFeedback().unsetOptimizationInfoForThrowNullMethod(appView, this);
  }

  public void registerCodeReferences(UseRegistry<?> registry) {
    Code code = getDefinition().getCode();
    if (code != null) {
      code.registerCodeReferences(this, registry);
    }
  }

  public <R> R registerCodeReferencesWithResult(UseRegistryWithResult<R, ?> registry) {
    registerCodeReferences(registry);
    return registry.getResult();
  }

  @Override
  public ProgramMethod getContext() {
    return this;
  }

  @Override
  public DexProgramClass getContextClass() {
    return getHolder();
  }

  @Override
  public boolean isProgramMember() {
    return true;
  }

  @Override
  public ProgramMethod asProgramMember() {
    return this;
  }

  @Override
  public boolean isProgramMethod() {
    return true;
  }

  @Override
  public ProgramMethod asMethod() {
    return this;
  }

  @Override
  public ProgramMethod asProgramMethod() {
    return this;
  }

  @Override
  public DexProgramClass getHolder() {
    DexClass holder = super.getHolder();
    assert holder.isProgramClass();
    return holder.asProgramClass();
  }

  @Override
  public KotlinMethodLevelInfo getKotlinInfo() {
    return getDefinition().getKotlinInfo();
  }

  public boolean getOrComputeReachabilitySensitive(AppView<?> appView) {
    return getHolder().getOrComputeReachabilitySensitive(appView);
  }

  public void setCode(Code newCode, AppView<?> appView) {
    // If the locals are not kept, we might still need information to satisfy -keepparameternames.
    // The information needs to be retrieved on the original code object before replacing it.
    Code code = getDefinition().getCode();
    Int2ReferenceMap<DebugLocalInfo> parameterInfo = getDefinition().getParameterInfo();
    if (code != null
        && code.isCfCode()
        && !getDefinition().hasParameterInfo()
        && !keepLocals(appView)) {
      parameterInfo = code.collectParameterInfo(getDefinition(), appView);
    }
    getDefinition().setCode(newCode, parameterInfo);
  }

  public boolean keepLocals(AppView<?> appView) {
    if (appView.testing().noLocalsTableOnInput) {
      return false;
    }
    return appView.options().debug || getOrComputeReachabilitySensitive(appView);
  }

  @SuppressWarnings("ReferenceEquality")
  public ProgramMethod rewrittenWithLens(
      GraphLens lens, GraphLens appliedLens, DexDefinitionSupplier definitions) {
    DexMethod newMethod = lens.getRenamedMethodSignature(getReference(), appliedLens);
    if (newMethod == getReference() && !getDefinition().isObsolete()) {
      assert verifyIsConsistentWithLookup(definitions);
      return this;
    }
    return asProgramMethodOrNull(definitions.definitionFor(newMethod));
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean verifyIsConsistentWithLookup(DexDefinitionSupplier definitions) {
    DexClassAndMethod lookupMethod = definitions.definitionFor(getReference());
    assert getDefinition() == lookupMethod.getDefinition();
    return true;
  }
}
