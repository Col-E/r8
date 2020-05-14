// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.Assume.DynamicTypeAssumption;
import com.android.tools.r8.ir.code.Assume.NonNullAssumption;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.CodeOptimization;
import com.android.tools.r8.ir.conversion.PostOptimization;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.ConcreteCallSiteOptimizationInfo;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class CallSiteOptimizationInfoPropagator implements PostOptimization {

  // TODO(b/139246447): should we revisit new targets over and over again?
  //   Maybe piggy-back on MethodProcessor's wave/batch processing?
  // For now, before revisiting methods with more precise argument info, we switch the mode.
  // Then, revisiting a target at a certain level will not improve call site information of
  // callees in lower levels.
  private enum Mode {
    COLLECT, // Set until the end of the 1st round of IR processing. CallSiteOptimizationInfo will
             // be updated in this mode only.
    REVISIT  // Set once the all methods are processed. IRBuilder will add other instructions that
             // reflect collected CallSiteOptimizationInfo.
  }

  private final AppView<AppInfoWithLiveness> appView;
  private ProgramMethodSet revisitedMethods = null;
  private Mode mode = Mode.COLLECT;

  public CallSiteOptimizationInfoPropagator(AppView<AppInfoWithLiveness> appView) {
    assert appView.enableWholeProgramOptimizations();
    this.appView = appView;
    if (Log.isLoggingEnabledFor(CallSiteOptimizationInfoPropagator.class)) {
      revisitedMethods = ProgramMethodSet.create();
    }
  }

  public void logResults() {
    assert Log.ENABLED;
    if (revisitedMethods != null) {
      Log.info(getClass(), "# of methods to revisit: %s", revisitedMethods.size());
      for (ProgramMethod m : revisitedMethods) {
        Log.info(
            getClass(),
            "%s: %s",
            m.toSourceString(),
            m.getDefinition().getCallSiteOptimizationInfo().toString());
      }
    }
  }

  public void collectCallSiteOptimizationInfo(IRCode code, Timing timing) {
    // TODO(b/139246447): we could collect call site optimization during REVISIT mode as well,
    //   but that may require a separate copy of CallSiteOptimizationInfo.
    if (mode != Mode.COLLECT) {
      return;
    }
    ProgramMethod context = code.context();
    for (Instruction instruction : code.instructions()) {
      if (instruction.isInvokeMethod()) {
        collectCallSiteOptimizationInfoForInvokeMethod(
            instruction.asInvokeMethod(), context, timing);
      } else if (instruction.isInvokeCustom()) {
        collectCallSiteOptimizationInfoForInvokeCustom(instruction.asInvokeCustom());
      }
    }
  }

  private void collectCallSiteOptimizationInfoForInvokeMethod(
      InvokeMethod invoke, ProgramMethod context, Timing timing) {
    DexMethod invokedMethod = invoke.getInvokedMethod();
    SingleResolutionResult resolutionResult =
        appView
            .appInfo()
            .resolveMethod(invokedMethod, invoke.isInvokeInterface())
            .asSingleResolution();
    if (resolutionResult == null) {
      return;
    }
    // For virtual and interface calls, proceed on valid results only (since it's enforced).
    if (invoke.isInvokeMethodWithDynamicDispatch() && !resolutionResult.isVirtualTarget()) {
      return;
    }
    ProgramMethod resolutionTarget =
        resolutionResult.asSingleResolution().getResolutionPair().asProgramMethod();
    if (resolutionTarget == null || isMaybeClasspathOrLibraryMethodOverride(resolutionTarget)) {
      return;
    }
    propagateArgumentsToDispatchTargets(invoke, context, timing);
  }

  private void collectCallSiteOptimizationInfoForInvokeCustom(InvokeCustom invoke) {
    // If the bootstrap method is program declared it will be called. The call is with runtime
    // provided arguments so ensure that the call-site info is TOP.
    DexMethodHandle bootstrapMethod = invoke.getCallSite().bootstrapMethod;
    SingleResolutionResult resolution =
        appView
            .appInfo()
            .resolveMethod(bootstrapMethod.asMethod(), bootstrapMethod.isInterface)
            .asSingleResolution();
    if (resolution != null && resolution.getResolvedHolder().isProgramClass()) {
      resolution
          .getResolvedMethod()
          .joinCallSiteOptimizationInfo(CallSiteOptimizationInfo.TOP, appView);
    }
  }

  private boolean isMaybeClasspathOrLibraryMethodOverride(ProgramMethod target) {
    // If the method overrides a library method, it is unsure how the method would be invoked by
    // that library.
    return target.getDefinition().isLibraryMethodOverride().isPossiblyTrue();
  }

  // Propagate information about the arguments to all possible dispatch targets of the invoke.
  private void propagateArgumentsToDispatchTargets(
      InvokeMethod invoke, ProgramMethod context, Timing timing) {

    timing.begin("Lookup possible dispatch targets");
    ProgramMethodSet targets = invoke.lookupProgramDispatchTargets(appView, context);
    timing.end();

    assert invoke.isInvokeMethodWithDynamicDispatch()
        // For other invocation types, the size of targets should be at most one.
        || targets == null
        || targets.size() <= 1;

    if (targets == null || targets.isEmpty()) {
      return;
    }

    timing.begin("Record arguments");
    for (ProgramMethod target : targets) {
      propagateArgumentsToDispatchTarget(invoke, target);
    }
    timing.end();
  }

  private void propagateArgumentsToDispatchTarget(InvokeMethod invoke, ProgramMethod target) {
    if (!appView.appInfo().mayPropagateArgumentsTo(target)) {
      return;
    }
    if (target.getDefinition().getCallSiteOptimizationInfo().isTop()) {
      return;
    }
    target
        .getDefinition()
        .joinCallSiteOptimizationInfo(
            computeCallSiteOptimizationInfoFromArguments(target, invoke.arguments()), appView);
  }

  private CallSiteOptimizationInfo computeCallSiteOptimizationInfoFromArguments(
      ProgramMethod target, List<Value> inValues) {
    // No method body or no argument at all.
    if (target.getDefinition().shouldNotHaveCode() || inValues.size() == 0) {
      return CallSiteOptimizationInfo.TOP;
    }
    // If pinned, that method could be invoked via reflection.
    if (appView.appInfo().isPinned(target.getReference())) {
      return CallSiteOptimizationInfo.TOP;
    }
    // If the method overrides a library method, it is unsure how the method would be invoked by
    // that library.
    if (target.getDefinition().isLibraryMethodOverride().isTrue()) {
      // But, should not be reachable, since we already bail out.
      assert false
          : "Trying to compute call site optimization info for " + target.toSourceString();
      return CallSiteOptimizationInfo.TOP;
    }
    // If the program already has illegal accesses, method resolution results will reflect that too.
    // We should avoid recording arguments in that case. E.g., b/139823850: static methods can be a
    // result of virtual call targets, if that's the only method that matches name and signature.
    int argumentOffset = target.getDefinition().isStatic() ? 0 : 1;
    if (inValues.size() != argumentOffset + target.getReference().getArity()) {
      return CallSiteOptimizationInfo.BOTTOM;
    }
    return ConcreteCallSiteOptimizationInfo.fromArguments(appView, target, inValues);
  }

  // If collected call site optimization info has something useful, e.g., non-null argument,
  // insert corresponding assume instructions for arguments.
  public void applyCallSiteOptimizationInfo(
      IRCode code, CallSiteOptimizationInfo callSiteOptimizationInfo) {
    if (mode != Mode.REVISIT) {
      return;
    }
    // TODO(b/139246447): Assert no BOTTOM left.
    if (!callSiteOptimizationInfo.hasUsefulOptimizationInfo(appView, code.method())) {
      return;
    }
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    List<Assume<?>> assumeInstructions = new LinkedList<>();
    List<Instruction> constants = new LinkedList<>();
    int argumentsSeen = 0;
    InstructionListIterator iterator = code.entryBlock().listIterator(code);
    while (iterator.hasNext()) {
      Instruction instr = iterator.next();
      if (!instr.isArgument()) {
        break;
      }
      argumentsSeen++;
      Value originalArg = instr.asArgument().outValue();
      if (originalArg.hasLocalInfo() || !originalArg.getType().isReferenceType()) {
        continue;
      }
      int argIndex = argumentsSeen - 1;
      AbstractValue abstractValue = callSiteOptimizationInfo.getAbstractArgumentValue(argIndex);
      if (abstractValue.isSingleValue()) {
        assert appView.options().enablePropagationOfConstantsAtCallSites;
        SingleValue singleValue = abstractValue.asSingleValue();
        if (singleValue.isMaterializableInContext(appView, code.context())) {
          Instruction replacement =
              singleValue.createMaterializingInstruction(appView, code, instr);
          replacement.setPosition(instr.getPosition());
          affectedValues.addAll(originalArg.affectedValues());
          originalArg.replaceUsers(replacement.outValue());
          constants.add(replacement);
          continue;
        }
      }
      TypeElement dynamicUpperBoundType =
          callSiteOptimizationInfo.getDynamicUpperBoundType(argIndex);
      if (dynamicUpperBoundType == null) {
        continue;
      }
      if (dynamicUpperBoundType.isDefinitelyNull()) {
        ConstNumber nullInstruction = code.createConstNull();
        nullInstruction.setPosition(instr.getPosition());
        affectedValues.addAll(originalArg.affectedValues());
        originalArg.replaceUsers(nullInstruction.outValue());
        constants.add(nullInstruction);
        continue;
      }
      Value specializedArg;
      if (dynamicUpperBoundType.strictlyLessThan(originalArg.getType(), appView)) {
        specializedArg = code.createValue(originalArg.getType());
        affectedValues.addAll(originalArg.affectedValues());
        originalArg.replaceUsers(specializedArg);
        Assume<DynamicTypeAssumption> assumeType =
            Assume.createAssumeDynamicTypeInstruction(
                dynamicUpperBoundType, null, specializedArg, originalArg, instr, appView);
        assumeType.setPosition(instr.getPosition());
        assumeInstructions.add(assumeType);
      } else {
        specializedArg = originalArg;
      }
      assert specializedArg != null && specializedArg.getType().isReferenceType();
      if (dynamicUpperBoundType.isDefinitelyNotNull()) {
        // If we already knew `arg` is never null, e.g., receiver, skip adding non-null.
        if (!specializedArg.getType().isDefinitelyNotNull()) {
          Value nonNullArg =
              code.createValue(specializedArg.getType().asReferenceType().asMeetWithNotNull());
          affectedValues.addAll(specializedArg.affectedValues());
          specializedArg.replaceUsers(nonNullArg);
          Assume<NonNullAssumption> assumeNotNull =
              Assume.createAssumeNonNullInstruction(nonNullArg, specializedArg, instr, appView);
          assumeNotNull.setPosition(instr.getPosition());
          assumeInstructions.add(assumeNotNull);
        }
      }
    }
    assert argumentsSeen == code.method().method.getArity() + (code.method().isStatic() ? 0 : 1)
        : "args: "
            + argumentsSeen
            + " != "
            + "arity: "
            + code.method().method.getArity()
            + ", static: "
            + code.method().isStatic();
    // After packed Argument instructions, add Assume<?> and constant instructions.
    assert !iterator.peekPrevious().isArgument();
    iterator.previous();
    assert iterator.peekPrevious().isArgument();
    assumeInstructions.forEach(iterator::add);
    // TODO(b/69963623): Can update method signature and save more on call sites.
    constants.forEach(iterator::add);

    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
  }

  @Override
  public ProgramMethodSet methodsToRevisit() {
    mode = Mode.REVISIT;
    ProgramMethodSet targetsToRevisit = ProgramMethodSet.create();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramMethodMatching(
          definition -> {
            assert !definition.isObsolete();
            if (definition.shouldNotHaveCode()
                || !definition.hasCode()
                || definition.getCode().isEmptyVoidMethod()) {
              return false;
            }
            // TODO(b/139246447): Assert no BOTTOM left.
            CallSiteOptimizationInfo callSiteOptimizationInfo =
                definition.getCallSiteOptimizationInfo();
            return callSiteOptimizationInfo.hasUsefulOptimizationInfo(appView, definition);
          },
          method -> {
            targetsToRevisit.add(method);
            if (appView.options().testing.callSiteOptimizationInfoInspector != null) {
              appView.options().testing.callSiteOptimizationInfoInspector.accept(method);
            }
          });
    }
    if (revisitedMethods != null) {
      revisitedMethods.addAll(targetsToRevisit);
    }
    return targetsToRevisit;
  }

  @Override
  public Collection<CodeOptimization> codeOptimizationsForPostProcessing() {
    // Run IRConverter#optimize.
    return null;
  }
}
