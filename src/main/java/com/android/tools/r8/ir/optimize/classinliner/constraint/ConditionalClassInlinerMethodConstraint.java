// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.constraint;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.classinliner.analysis.AnalysisContext;
import com.android.tools.r8.ir.optimize.classinliner.analysis.NonEmptyParameterUsage;
import com.android.tools.r8.ir.optimize.classinliner.analysis.NonEmptyParameterUsages;
import com.android.tools.r8.ir.optimize.classinliner.analysis.ParameterUsage;
import com.android.tools.r8.ir.optimize.classinliner.analysis.ParameterUsagePerContext;
import com.android.tools.r8.ir.optimize.classinliner.analysis.ParameterUsages;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class ConditionalClassInlinerMethodConstraint implements ClassInlinerMethodConstraint {

  private final ParameterUsages usages;

  public ConditionalClassInlinerMethodConstraint(ParameterUsages usages) {
    assert !usages.isTop();
    this.usages = usages;
  }

  @Override
  public ClassInlinerMethodConstraint fixupAfterRemovingThisParameter() {
    if (usages.isBottom()) {
      return this;
    }
    Int2ObjectMap<ParameterUsagePerContext> backing = new Int2ObjectOpenHashMap<>();
    usages
        .asNonEmpty()
        .forEach(
            (parameter, usagePerContext) -> {
              if (parameter > 0) {
                backing.put(parameter - 1, usagePerContext);
              }
            });
    return new ConditionalClassInlinerMethodConstraint(NonEmptyParameterUsages.create(backing));
  }

  @Override
  public ParameterUsage getParameterUsage(int parameter) {
    AnalysisContext defaultContext = AnalysisContext.getDefaultContext();
    return usages.get(parameter).get(defaultContext);
  }

  @Override
  public boolean isEligibleForNewInstanceClassInlining(ProgramMethod method, int parameter) {
    AnalysisContext defaultContext = AnalysisContext.getDefaultContext();
    ParameterUsage usage = usages.get(parameter).get(defaultContext);
    return !usage.isTop();
  }

  @Override
  public boolean isEligibleForStaticGetClassInlining(ProgramMethod method, int parameter) {
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
    if (!knownUsage.getFieldsReadFromParameter().isEmpty()) {
      // We don't know the value of the field.
      return false;
    }
    return true;
  }
}
