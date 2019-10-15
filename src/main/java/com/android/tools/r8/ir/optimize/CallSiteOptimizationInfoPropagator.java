// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.Assume.DynamicTypeAssumption;
import com.android.tools.r8.ir.code.Assume.NonNullAssumption;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MutableCallSiteOptimizationInfo;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Predicate;

public class CallSiteOptimizationInfoPropagator {

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
  private Set<DexEncodedMethod> revisitedMethods = null;
  private Mode mode = Mode.COLLECT;

  public CallSiteOptimizationInfoPropagator(AppView<AppInfoWithLiveness> appView) {
    assert appView.enableWholeProgramOptimizations();
    this.appView = appView;
    if (Log.isLoggingEnabledFor(CallSiteOptimizationInfoPropagator.class)) {
      revisitedMethods = Sets.newIdentityHashSet();
    }
  }

  public void logResults() {
    assert Log.ENABLED;
    if (revisitedMethods != null) {
      Log.info(getClass(), "# of methods to revisit: %s", revisitedMethods.size());
      for (DexEncodedMethod m : revisitedMethods) {
        Log.info(getClass(), "%s: %s",
            m.toSourceString(), m.getCallSiteOptimizationInfo().toString());
      }
    }
  }

  public void collectCallSiteOptimizationInfo(IRCode code) {
    // TODO(b/139246447): we could collect call site optimization during REVISIT mode as well,
    //   but that may require a separate copy of CallSiteOptimizationInfo.
    if (mode != Mode.COLLECT) {
      return;
    }
    DexEncodedMethod context = code.method;
    for (Instruction instruction : code.instructions()) {
      if (!instruction.isInvokeMethod() && !instruction.isInvokeCustom()) {
        continue;
      }
      if (!MutableCallSiteOptimizationInfo.hasArgumentsToRecord(instruction.inValues())) {
        continue;
      }
      if (instruction.isInvokeMethod()) {
        InvokeMethod invoke = instruction.asInvokeMethod();
        if (invoke.isInvokeMethodWithDynamicDispatch()) {
          DexMethod invokedMethod = invoke.getInvokedMethod();
          ResolutionResult resolutionResult =
              appView.appInfo().resolveMethod(invokedMethod.holder, invokedMethod);
          // For virtual and interface calls, proceed on valid results only (since it's enforced).
          if (!resolutionResult.isValidVirtualTarget(appView.options())) {
            continue;
          }
        }
        Collection<DexEncodedMethod> targets = invoke.lookupTargets(appView, context.method.holder);
        assert invoke.isInvokeMethodWithDynamicDispatch()
            // For other invocation types, the size of targets should be at most one.
            || targets == null || targets.size() <= 1;
        if (targets == null || targets.isEmpty()) {
          continue;
        }
        for (DexEncodedMethod target : targets) {
          recordArgumentsIfNecessary(context, target, invoke.inValues());
        }
      }
      // TODO(b/129458850): if lambda desugaring happens before IR processing, seeing invoke-custom
      //  means we can't find matched methods in the app, hence safe to ignore (only for DEX).
      if (instruction.isInvokeCustom()) {
        // Conservatively register argument info for all possible lambda implemented methods.
        Collection<DexEncodedMethod> targets =
            appView.appInfo().lookupLambdaImplementedMethods(
                instruction.asInvokeCustom().getCallSite());
        if (targets == null) {
          continue;
        }
        for (DexEncodedMethod target : targets) {
          recordArgumentsIfNecessary(context, target, instruction.inValues());
        }
      }
    }
  }

  private void recordArgumentsIfNecessary(
      DexEncodedMethod context, DexEncodedMethod target, List<Value> inValues) {
    assert !target.isObsolete();
    if (target.shouldNotHaveCode() || target.method.getArity() == 0) {
      return;
    }
    // If pinned, that method could be invoked via reflection.
    if (appView.appInfo().isPinned(target.method)) {
      return;
    }
    // If the program already has illegal accesses, method resolution results will reflect that too.
    // We should avoid recording arguments in that case. E.g., b/139823850: static methods can be a
    // result of virtual call targets, if that's the only method that matches name and signature.
    int argumentOffset = target.isStatic() ? 0 : 1;
    if (inValues.size() != argumentOffset + target.method.getArity()) {
      return;
    }
    MutableCallSiteOptimizationInfo optimizationInfo =
        target.getMutableCallSiteOptimizationInfo(appView);
    optimizationInfo.recordArguments(appView, context, inValues);
  }

  // If collected call site optimization info has something useful, e.g., non-null argument,
  // insert corresponding assume instructions for arguments.
  public void applyCallSiteOptimizationInfo(
      IRCode code, CallSiteOptimizationInfo callSiteOptimizationInfo) {
    if (mode != Mode.REVISIT
        || !callSiteOptimizationInfo.hasUsefulOptimizationInfo(appView, code.method)) {
      return;
    }
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    List<Assume<?>> assumeInstructions = new LinkedList<>();
    List<ConstInstruction> constants = new LinkedList<>();
    int argumentsSeen = 0;
    InstructionListIterator iterator = code.entryBlock().listIterator(code);
    while (iterator.hasNext()) {
      Instruction instr = iterator.next();
      if (!instr.isArgument()) {
        break;
      }
      argumentsSeen++;
      Value originalArg = instr.asArgument().outValue();
      if (originalArg.hasLocalInfo() || !originalArg.getTypeLattice().isReference()) {
        continue;
      }
      TypeLatticeElement dynamicType = callSiteOptimizationInfo.getDynamicType(argumentsSeen - 1);
      if (dynamicType == null) {
        continue;
      }
      if (dynamicType.isDefinitelyNull()) {
        ConstNumber nullInstruction = code.createConstNull();
        nullInstruction.setPosition(instr.getPosition());
        affectedValues.addAll(originalArg.affectedValues());
        originalArg.replaceUsers(nullInstruction.outValue());
        constants.add(nullInstruction);
        continue;
      }
      // TODO(b/69963623): Handle other kinds of constants, e.g. number, string, or class.
      Value specializedArg;
      if (dynamicType.strictlyLessThan(originalArg.getTypeLattice(), appView)) {
        specializedArg = code.createValue(originalArg.getTypeLattice());
        affectedValues.addAll(originalArg.affectedValues());
        originalArg.replaceUsers(specializedArg);
        Assume<DynamicTypeAssumption> assumeType =
            Assume.createAssumeDynamicTypeInstruction(
                dynamicType, null, specializedArg, originalArg, instr, appView);
        assumeType.setPosition(instr.getPosition());
        assumeInstructions.add(assumeType);
      } else {
        specializedArg = originalArg;
      }
      assert specializedArg != null && specializedArg.getTypeLattice().isReference();
      if (dynamicType.isDefinitelyNotNull()) {
        // If we already knew `arg` is never null, e.g., receiver, skip adding non-null.
        if (!specializedArg.getTypeLattice().isDefinitelyNotNull()) {
          Value nonNullArg = code.createValue(
              specializedArg.getTypeLattice().asReferenceTypeLatticeElement().asMeetWithNotNull());
          affectedValues.addAll(specializedArg.affectedValues());
          specializedArg.replaceUsers(nonNullArg);
          Assume<NonNullAssumption> assumeNotNull =
              Assume.createAssumeNonNullInstruction(nonNullArg, specializedArg, instr, appView);
          assumeNotNull.setPosition(instr.getPosition());
          assumeInstructions.add(assumeNotNull);
        }
      }
    }
    assert argumentsSeen == code.method.method.getArity() + (code.method.isStatic() ? 0 : 1)
        : "args: " + argumentsSeen + " != "
            + "arity: " + code.method.method.getArity() + ", static: " + code.method.isStatic();
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

  public <E extends Exception> void revisitMethods(
      ThrowingBiConsumer<DexEncodedMethod, Predicate<DexEncodedMethod>, E> consumer,
      ExecutorService executorService)
      throws ExecutionException {
    Set<DexEncodedMethod> targetsToRevisit = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedMethod method : clazz.methods()) {
        assert !method.isObsolete();
        if (method.shouldNotHaveCode()
            || method.getCallSiteOptimizationInfo().isDefaultCallSiteOptimizationInfo()) {
          continue;
        }
        MutableCallSiteOptimizationInfo optimizationInfo =
            method.getCallSiteOptimizationInfo().asMutableCallSiteOptimizationInfo();
        if (optimizationInfo.hasUsefulOptimizationInfo(appView, method)) {
          targetsToRevisit.add(method);
        }
      }
    }
    mode = Mode.REVISIT;
    if (targetsToRevisit.isEmpty()) {
      return;
    }
    if (revisitedMethods != null) {
      revisitedMethods.addAll(targetsToRevisit);
    }
    List<Future<?>> futures = new ArrayList<>();
    for (DexEncodedMethod method : targetsToRevisit) {
      futures.add(
          executorService.submit(
              () -> {
                consumer.accept(method, targetsToRevisit::contains);
                return null; // we want a Callable not a Runnable to be able to throw
              }));
    }
    ThreadUtils.awaitFutures(futures);
  }
}
