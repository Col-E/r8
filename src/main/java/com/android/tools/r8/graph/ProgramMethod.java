// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.ir.optimize.info.OptimizationFeedback.getSimpleFeedback;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.kotlin.KotlinMethodLevelInfo;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/** Type representing a method definition in the programs compilation unit and its holder. */
public final class ProgramMethod extends DexClassAndMethod
    implements ProgramMember<DexEncodedMethod, DexMethod> {

  public ProgramMethod(DexProgramClass holder, DexEncodedMethod method) {
    super(holder, method);
  }

  public IRCode buildIR(AppView<?> appView) {
    DexEncodedMethod method = getDefinition();
    return method.hasCode() ? method.getCode().buildIR(this, appView, getOrigin()) : null;
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
      codeLens = appView.codeLens();
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
      IndexedItemCollection indexedItems, GraphLens graphLens, LensCodeRewriterUtils rewriter) {
    DexEncodedMethod definition = getDefinition();
    assert !definition.isObsolete();
    getReference().collectIndexedItems(indexedItems);
    if (definition.hasCode()) {
      Code code = definition.getCode();
      code.asDexWritableCode().collectIndexedItems(indexedItems, this, graphLens, rewriter);
    }
    definition.annotations().collectIndexedItems(indexedItems);
    definition.parameterAnnotationsList.collectIndexedItems(indexedItems);
  }

  public boolean canBeConvertedToAbstractMethod(AppView<AppInfoWithLiveness> appView) {
    return (appView.options().canUseAbstractMethodOnNonAbstractClass()
            || getHolder().isAbstract()
            || getHolder().isInterface())
        && !getAccessFlags().isNative()
        && !getAccessFlags().isPrivate()
        && !getAccessFlags().isStatic()
        && !getDefinition().isInstanceInitializer()
        && !appView.appInfo().isFailedResolutionTarget(getReference());
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
    getDefinition().setCode(ThrowNullCode.get(), appView);
    getSimpleFeedback().markProcessed(getDefinition(), ConstraintWithTarget.ALWAYS);
    getSimpleFeedback().unsetOptimizationInfoForThrowNullMethod(this);
  }

  public void registerCodeReferences(UseRegistry<?> registry) {
    Code code = getDefinition().getCode();
    if (code != null) {
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Registering definitions reachable from `%s`.", this);
      }
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
}
