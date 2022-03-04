// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.constraint;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.proto.ArgumentInfo;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleConstValue;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.ir.optimize.classinliner.analysis.AnalysisContext;
import com.android.tools.r8.ir.optimize.classinliner.analysis.NonEmptyParameterUsage;
import com.android.tools.r8.ir.optimize.classinliner.analysis.NonEmptyParameterUsages;
import com.android.tools.r8.ir.optimize.classinliner.analysis.ParameterUsage;
import com.android.tools.r8.ir.optimize.classinliner.analysis.ParameterUsagePerContext;
import com.android.tools.r8.ir.optimize.classinliner.analysis.ParameterUsages;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class ConditionalClassInlinerMethodConstraint implements ClassInlinerMethodConstraint {

  private final ParameterUsages usages;

  public ConditionalClassInlinerMethodConstraint(ParameterUsages usages) {
    assert !usages.isTop();
    this.usages = usages;
  }

  @Override
  public ClassInlinerMethodConstraint fixupAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView, ArgumentInfoCollection changes) {
    if (usages.isBottom()) {
      return this;
    }
    Int2ObjectMap<ParameterUsagePerContext> backing = new Int2ObjectOpenHashMap<>();
    usages
        .asNonEmpty()
        .forEach(
            (argumentIndex, usagePerContext) -> {
              ArgumentInfo argumentInfo = changes.getArgumentInfo(argumentIndex);
              if (argumentInfo.isRemovedArgumentInfo()) {
                // When removing a parameter from a method, we no longer need information about the
                // usages of that parameter for class inlining.
                return;
              }
              if (argumentInfo.isRewrittenTypeInfo()
                  && argumentInfo.asRewrittenTypeInfo().getNewType().isIntType()) {
                // This is due to enum unboxing. After enum unboxing, we no longer need information
                // about the usages of this parameter for class inlining.
                return;
              }
              backing.put(changes.getNewArgumentIndex(argumentIndex), usagePerContext);
            });
    return new ConditionalClassInlinerMethodConstraint(NonEmptyParameterUsages.create(backing));
  }

  @Override
  public ParameterUsage getParameterUsage(int parameter) {
    AnalysisContext defaultContext = AnalysisContext.getDefaultContext();
    return usages.get(parameter).get(defaultContext);
  }

  @Override
  public boolean isEligibleForNewInstanceClassInlining(
      AppView<AppInfoWithLiveness> appView,
      DexProgramClass candidateClass,
      ProgramMethod method,
      int parameter) {
    AnalysisContext defaultContext = AnalysisContext.getDefaultContext();
    ParameterUsage usage = usages.get(parameter).get(defaultContext);
    if (usage.isBottom()) {
      return true;
    }
    if (usage.isTop()) {
      return false;
    }
    NonEmptyParameterUsage knownUsage = usage.asNonEmpty();
    if (hasUnsafeCast(appView, candidateClass, knownUsage)) {
      return false;
    }
    return true;
  }

  @Override
  public boolean isEligibleForStaticGetClassInlining(
      AppView<AppInfoWithLiveness> appView,
      DexProgramClass candidateClass,
      int parameter,
      ObjectState objectState,
      ProgramMethod context) {
    AnalysisContext defaultContext = AnalysisContext.getDefaultContext();
    ParameterUsage usage = usages.get(parameter).get(defaultContext);
    if (usage.isBottom()) {
      return true;
    }
    if (usage.isTop()) {
      return false;
    }

    NonEmptyParameterUsage knownUsage = usage.asNonEmpty();
    if (knownUsage.isParameterMutated()) {
      // The static instance could be accessed from elsewhere. Therefore, we cannot allow
      // side-effects to be removed and therefore cannot class inline method calls that modifies the
      // instance.
      return false;
    }
    if (knownUsage.isParameterUsedAsLock()) {
      // We will not be able to remove the monitor instruction afterwards.
      return false;
    }
    if (hasUnsafeCast(appView, candidateClass, knownUsage)) {
      return false;
    }
    for (DexField fieldReadFromParameter : knownUsage.getFieldsReadFromParameter()) {
      DexClass holder = appView.definitionFor(fieldReadFromParameter.getHolderType());
      DexEncodedField definition = fieldReadFromParameter.lookupOnClass(holder);
      if (definition == null) {
        return false;
      }
      AbstractValue abstractValue = objectState.getAbstractFieldValue(definition);
      if (!abstractValue.isSingleConstValue()) {
        return false;
      }
      SingleConstValue singleConstValue = abstractValue.asSingleConstValue();
      if (!singleConstValue.isMaterializableInContext(appView, context)) {
        return false;
      }
    }
    return true;
  }

  private boolean hasUnsafeCast(
      AppView<AppInfoWithLiveness> appView,
      DexProgramClass candidateClass,
      NonEmptyParameterUsage knownUsage) {
    for (DexType castType : knownUsage.getCastsWithParameter()) {
      if (!castType.isClassType()) {
        return true;
      }
      DexClass castClass = appView.definitionFor(castType);
      if (castClass == null || !appView.appInfo().isSubtype(candidateClass, castClass)) {
        return true;
      }
    }
    return false;
  }
}
