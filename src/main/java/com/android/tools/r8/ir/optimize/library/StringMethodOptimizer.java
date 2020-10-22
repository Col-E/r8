// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import java.util.Set;

public class StringMethodOptimizer implements LibraryMethodModelCollection {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;

  StringMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  @Override
  public DexType getType() {
    return dexItemFactory.stringType;
  }

  @Override
  public void optimize(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues) {
    if (singleTarget.getReference() == dexItemFactory.stringMembers.equals) {
      optimizeEquals(code, instructionIterator, invoke);
    }
  }

  private void optimizeEquals(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    if (appView.appInfo().hasLiveness()) {
      ProgramMethod context = code.context();
      Value first = invoke.arguments().get(0).getAliasedValue();
      Value second = invoke.arguments().get(1).getAliasedValue();
      if (isPrunedClassNameComparison(first, second, context)
          || isPrunedClassNameComparison(second, first, context)) {
        instructionIterator.replaceCurrentInstructionWithConstInt(code, 0);
      }
    }
  }

  /**
   * Returns true if {@param classNameValue} is defined by calling {@link Class#getName()} and
   * {@param constStringValue} is a constant string that is identical to the name of a class that
   * has been pruned by the {@link com.android.tools.r8.shaking.Enqueuer}.
   */
  private boolean isPrunedClassNameComparison(
      Value classNameValue, Value constStringValue, ProgramMethod context) {
    if (classNameValue.isPhi() || constStringValue.isPhi()) {
      return false;
    }

    Instruction classNameDefinition = classNameValue.definition;
    if (!classNameDefinition.isInvokeVirtual()) {
      return false;
    }

    DexClassAndMethod singleTarget =
        classNameDefinition.asInvokeVirtual().lookupSingleTarget(appView, context);
    if (singleTarget == null
        || singleTarget.getReference() != dexItemFactory.classMethods.getName) {
      return false;
    }

    if (!constStringValue.definition.isDexItemBasedConstString()) {
      return false;
    }

    DexItemBasedConstString constString = constStringValue.definition.asDexItemBasedConstString();
    DexReference reference = constString.getItem();
    return reference.isDexType()
        && appView.appInfo().withLiveness().wasPruned(reference.asDexType())
        && !constString.getNameComputationInfo().needsToComputeName();
  }
}
