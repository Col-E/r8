// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AssumeRemover;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.Consumer;

public class TypeAnalysis {

  private enum Mode {
    UNSET,
    WIDENING,  // initial analysis, including fixed-point iteration for phis and updating with less
               // specific info, e.g., removing assume nodes.
    NARROWING, // updating with more specific info, e.g., passing the return value of the inlinee.
    NO_CHANGE  // utility to ensure types are up to date
  }

  private final boolean mayHaveImpreciseTypes;

  private boolean keepRedundantBlocksAfterAssumeRemoval = false;
  private Mode mode = Mode.UNSET;

  private final AppView<?> appView;
  private final AssumeRemover assumeRemover;
  private final IRCode code;

  private final WorkList<Value> worklist = WorkList.newIdentityWorkList();

  public TypeAnalysis(AppView<?> appView, IRCode code) {
    this(appView, code, false);
  }

  public TypeAnalysis(AppView<?> appView, IRCode code, boolean mayHaveImpreciseTypes) {
    this.appView = appView;
    this.assumeRemover = new AssumeRemover(appView, code);
    this.code = code;
    this.mayHaveImpreciseTypes = mayHaveImpreciseTypes;
  }

  // Allows disabling the removal of redundant blocks after assume removal.
  public TypeAnalysis setKeepRedundantBlocksAfterAssumeRemoval(
      boolean keepRedundantBlocksAfterAssumeRemoval) {
    this.keepRedundantBlocksAfterAssumeRemoval = keepRedundantBlocksAfterAssumeRemoval;
    return this;
  }

  private void analyze() {
    while (worklist.hasNext()) {
      analyzeValue(worklist.removeSeen());
    }
  }

  public void widening() {
    mode = Mode.WIDENING;
    assert verifyIsEmpty();
    code.topologicallySortedBlocks().forEach(this::analyzeBasicBlock);
    analyze();
  }

  public void widening(Iterable<Value> values) {
    analyzeValues(values, Mode.WIDENING);
  }

  public void narrowing() {
    mode = Mode.NARROWING;
    assert verifyIsEmpty();
    code.topologicallySortedBlocks().forEach(this::analyzeBasicBlock);
    analyze();
  }

  public void narrowing(Iterable<? extends Value> values) {
    analyzeValues(values, Mode.NARROWING);
  }

  public void narrowingWithAssumeRemoval(Iterable<? extends Value> values) {
    narrowingWithAssumeRemoval(values, ConsumerUtils.emptyConsumer());
  }

  public void narrowingWithAssumeRemoval(
      Iterable<? extends Value> values, Consumer<Assume> redundantAssumeConsumer) {
    narrowing(values);
    removeRedundantAssumeInstructions(redundantAssumeConsumer);
  }

  private void removeRedundantAssumeInstructions(Consumer<Assume> redundantAssumeConsumer) {
    Set<Value> affectedValuesFromAssumeRemoval = Sets.newIdentityHashSet();
    while (assumeRemover.removeRedundantAssumeInstructions(
        affectedValuesFromAssumeRemoval, redundantAssumeConsumer)) {
      widening(affectedValuesFromAssumeRemoval);
      Set<Value> affectedValuesFromPhiRemoval = Sets.newIdentityHashSet();
      code.removeAllDeadAndTrivialPhis(affectedValuesFromPhiRemoval);
      narrowing(affectedValuesFromPhiRemoval);
      affectedValuesFromAssumeRemoval.clear();
    }
    if (!keepRedundantBlocksAfterAssumeRemoval) {
      code.removeRedundantBlocks();
    }
  }

  public boolean verifyIsEmpty() {
    assert !assumeRemover.hasAffectedAssumeInstructions();
    assert worklist.isEmpty();
    return true;
  }

  public static void verifyValuesUpToDate(AppView<?> appView, IRCode code) {
    TypeAnalysis typeAnalysis = new TypeAnalysis(appView, code);
    typeAnalysis.mode = Mode.NO_CHANGE;
    code.topologicallySortedBlocks().forEach(typeAnalysis::analyzeBasicBlock);
    typeAnalysis.analyze();
  }

  public static boolean verifyValuesUpToDate(
      AppView<?> appView, IRCode code, Iterable<? extends Value> values) {
    new TypeAnalysis(appView, code).analyzeValues(values, Mode.NO_CHANGE);
    return true;
  }

  private void analyzeValues(Iterable<? extends Value> values, Mode mode) {
    this.mode = mode;
    assert worklist.isEmpty();
    values.forEach(this::enqueue);
    analyze();
  }

  private void enqueue(Value v) {
    worklist.addFirstIfNotSeen(v);
  }

  private void analyzeBasicBlock(BasicBlock block) {
    for (Instruction instruction : block.getInstructions()) {
      Value outValue = instruction.outValue();
      if (outValue == null) {
        continue;
      }
      if (instruction.isArgument()) {
        // The type for Argument, a quasi instruction is already set correctly during IR building.
        // Note that we don't need to enqueue the out value of arguments here because it's constant.
      } else if (instruction.hasInvariantOutType()) {
        TypeElement derived = instruction.evaluate(appView);
        updateTypeOfValue(outValue, derived);
      } else {
        enqueue(outValue);
      }
    }
    for (Phi phi : block.getPhis()) {
      enqueue(phi);
    }
  }

  private void analyzeValue(Value value) {
    TypeElement previous = value.getType();
    TypeElement derived =
        value.isPhi() ? value.asPhi().computePhiType(appView) : value.definition.evaluate(appView);
    assert mayHaveImpreciseTypes || derived.isPreciseType();
    assert !previous.isPreciseType() || derived.isPreciseType();
    updateTypeOfValue(value, derived);
  }

  private void updateTypeOfValue(Value value, TypeElement type) {
    assert mode != Mode.UNSET;

    if (value.isDefinedByInstructionSatisfying(Instruction::isAssume)) {
      assumeRemover.addAffectedAssumeInstruction(value.getDefinition().asAssume());
    }

    TypeElement current = value.getType();
    if (current.equals(type)) {
      return;
    }

    assert mode != Mode.NO_CHANGE
        : "Unexpected type change for value "
            + value
            + " defined by "
            + (value.isPhi() ? "phi" : value.getDefinition())
            + ": was "
            + type
            + ", but expected "
            + current
            + " (context: "
            + code.context()
            + ")";

    if (type.isBottom()) {
      return;
    }

    if (mode == Mode.WIDENING) {
      value.widening(appView, type);
    } else {
      assert mode == Mode.NARROWING;
      value.narrowing(appView, code.context(), type);
    }

    // propagate the type change to (instruction) users if any.
    for (Instruction instruction : value.uniqueUsers()) {
      Value outValue = instruction.outValue();
      if (outValue != null) {
        enqueue(outValue);
      }
    }
    // Propagate the type change to phi users if any.
    for (Phi phi : value.uniquePhiUsers()) {
      enqueue(phi);
    }
  }

  public static DexType getRefinedReceiverType(
      AppView<AppInfoWithLiveness> appView, InvokeMethodWithReceiver invoke) {
    return toRefinedReceiverType(
        invoke.getReceiver().getDynamicType(appView), invoke.getInvokedMethod(), appView);
  }

  @SuppressWarnings("ReferenceEquality")
  public static DexType toRefinedReceiverType(
      DynamicType dynamicReceiverType,
      DexMethod method,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    DexType staticReceiverType = method.getHolderType();
    TypeElement staticReceiverTypeElement = staticReceiverType.toTypeElement(appView);
    TypeElement dynamicReceiverUpperBoundType =
        dynamicReceiverType.getDynamicUpperBoundType(staticReceiverTypeElement);
    if (dynamicReceiverUpperBoundType.isClassType()) {
      ClassTypeElement dynamicReceiverUpperBoundClassType =
          dynamicReceiverUpperBoundType.asClassType();
      DexType refinedType = dynamicReceiverUpperBoundClassType.getClassType();
      if (refinedType == appView.dexItemFactory().objectType) {
        DexType singleKnownInterface =
            dynamicReceiverUpperBoundClassType.getInterfaces().getSingleKnownInterface();
        if (singleKnownInterface != null) {
          refinedType = singleKnownInterface;
        }
      }
      if (appView.appInfo().isSubtype(refinedType, staticReceiverType)) {
        return refinedType;
      }
    }
    return staticReceiverType;
  }
}
