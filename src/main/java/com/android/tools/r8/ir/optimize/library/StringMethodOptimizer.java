// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import java.util.Set;

public class StringMethodOptimizer extends StatelessLibraryMethodModelCollection {

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
  @SuppressWarnings("ReferenceEquality")
  public void optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues,
      Set<BasicBlock> blocksToRemove) {
    DexMethod singleTargetReference = singleTarget.getReference();
    if (singleTargetReference == dexItemFactory.stringMembers.equals) {
      optimizeEquals(code, instructionIterator, invoke.asInvokeMethodWithReceiver());
    } else if (singleTargetReference == dexItemFactory.stringMembers.valueOf) {
      optimizeValueOf(code, instructionIterator, invoke.asInvokeStatic(), affectedValues);
    }
  }

  private void optimizeEquals(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethodWithReceiver invoke) {
    if (appView.appInfo().hasLiveness()) {
      ProgramMethod context = code.context();
      Value first = invoke.getReceiver().getAliasedValue();
      Value second = invoke.getArgument(1).getAliasedValue();
      if (isPrunedClassNameComparison(first, second, context)
          || isPrunedClassNameComparison(second, first, context)) {
        instructionIterator.replaceCurrentInstructionWithConstInt(code, 0);
      }
    }
  }

  private void optimizeValueOf(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeStatic invoke,
      Set<Value> affectedValues) {
    Value object = invoke.getFirstArgument();
    TypeElement type = object.getType();

    // Optimize String.valueOf(null) into "null".
    if (type.isDefinitelyNull()) {
      instructionIterator.replaceCurrentInstructionWithConstString(appView, code, "null");
      if (invoke.hasOutValue()) {
        affectedValues.addAll(invoke.outValue().affectedValues());
      }
      return;
    }

    // Optimize String.valueOf(nonNullString) into nonNullString.
    if (type.isDefinitelyNotNull() && type.isStringType(dexItemFactory)) {
      if (invoke.hasOutValue()) {
        affectedValues.addAll(invoke.outValue().affectedValues());
        invoke.outValue().replaceUsers(object);
      }
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  /**
   * Returns true if {@param classNameValue} is defined by calling {@link Class#getName()} and
   * {@param constStringValue} is a constant string that is identical to the name of a class that
   * has been pruned by the {@link com.android.tools.r8.shaking.Enqueuer}.
   */
  @SuppressWarnings("ReferenceEquality")
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
