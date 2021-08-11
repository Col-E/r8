// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryRetargeter.InvokeRetargetingResult.NO_REWRITING;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.utils.collections.DexClassAndMethodSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;

public class DesugaredLibraryRetargeter implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final DesugaredLibraryRetargeterSyntheticHelper syntheticHelper;

  private final RetargetingInfo retargetingInfo;
  private final Map<DexMethod, DexMethod> retargetLibraryMember;
  private final Map<DexString, List<DexMethod>> nonFinalHolderRewrites;
  private final DexClassAndMethodSet emulatedDispatchMethods;

  public DesugaredLibraryRetargeter(AppView<?> appView) {
    this.appView = appView;
    this.syntheticHelper = new DesugaredLibraryRetargeterSyntheticHelper(appView);
    retargetingInfo = RetargetingInfo.get(appView);
    retargetLibraryMember = retargetingInfo.getRetargetLibraryMember();
    nonFinalHolderRewrites = retargetingInfo.getNonFinalHolderRewrites();
    emulatedDispatchMethods = retargetingInfo.getEmulatedDispatchMethods();
  }

  // Used by the ListOfBackportedMethods utility.
  public void visit(Consumer<DexMethod> consumer) {
    retargetLibraryMember.keySet().forEach(consumer);
  }

  public RetargetingInfo getRetargetingInfo() {
    return retargetingInfo;
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      DexItemFactory dexItemFactory) {
    InvokeRetargetingResult invokeRetargetingResult = computeNewInvokeTarget(instruction, context);

    if (!invokeRetargetingResult.hasNewInvokeTarget()) {
      return null;
    }

    DexMethod newInvokeTarget = invokeRetargetingResult.getNewInvokeTarget(eventConsumer);
    return Collections.singletonList(
        new CfInvoke(Opcodes.INVOKESTATIC, newInvokeTarget, instruction.asInvoke().isInterface()));
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    return computeNewInvokeTarget(instruction, context).hasNewInvokeTarget();
  }

  @Deprecated // Use Cf to Cf desugaring instead.
  public void desugar(IRCode code) {
    if (retargetLibraryMember.isEmpty()) {
      return;
    }

    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (!instruction.isInvokeMethod()) {
        continue;
      }

      InvokeMethod invoke = instruction.asInvokeMethod();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      boolean isInterface = invoke.getInterfaceBit();

      InvokeRetargetingResult invokeRetargetingResult =
          computeNewInvokeTarget(
              invokedMethod, isInterface, invoke.isInvokeSuper(), code.context());
      if (invokeRetargetingResult.hasNewInvokeTarget()) {
        DexMethod newInvokeTarget = invokeRetargetingResult.getNewInvokeTarget(null);
        iterator.replaceCurrentInstruction(
            new InvokeStatic(newInvokeTarget, invoke.outValue(), invoke.inValues()));
      }
    }
  }

  static class InvokeRetargetingResult {

    static InvokeRetargetingResult NO_REWRITING =
        new InvokeRetargetingResult(false, ignored -> null);

    private final boolean hasNewInvokeTarget;
    private final Function<DesugaredLibraryRetargeterInstructionEventConsumer, DexMethod>
        newInvokeTargetSupplier;

    static InvokeRetargetingResult createInvokeRetargetingResult(DexMethod retarget) {
      if (retarget == null) {
        return NO_REWRITING;
      }
      return new InvokeRetargetingResult(true, ignored -> retarget);
    }

    private InvokeRetargetingResult(
        boolean hasNewInvokeTarget,
        Function<DesugaredLibraryRetargeterInstructionEventConsumer, DexMethod>
            newInvokeTargetSupplier) {
      this.hasNewInvokeTarget = hasNewInvokeTarget;
      this.newInvokeTargetSupplier = newInvokeTargetSupplier;
    }

    public boolean hasNewInvokeTarget() {
      return hasNewInvokeTarget;
    }

    public DexMethod getNewInvokeTarget(
        DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer) {
      assert hasNewInvokeTarget();
      return newInvokeTargetSupplier.apply(eventConsumer);
    }
  }

  public boolean hasNewInvokeTarget(
      DexMethod invokedMethod, boolean isInterface, boolean isInvokeSuper, ProgramMethod context) {
    return computeNewInvokeTarget(invokedMethod, isInterface, isInvokeSuper, context)
        .hasNewInvokeTarget();
  }

  private InvokeRetargetingResult computeNewInvokeTarget(
      CfInstruction instruction, ProgramMethod context) {
    if (retargetLibraryMember.isEmpty() || !instruction.isInvoke()) {
      return NO_REWRITING;
    }
    CfInvoke cfInvoke = instruction.asInvoke();
    return computeNewInvokeTarget(
        cfInvoke.getMethod(),
        cfInvoke.isInterface(),
        cfInvoke.isInvokeSuper(context.getHolderType()),
        context);
  }

  private InvokeRetargetingResult computeNewInvokeTarget(
      DexMethod invokedMethod, boolean isInterface, boolean isInvokeSuper, ProgramMethod context) {
    InvokeRetargetingResult retarget = computeRetargetedMethod(invokedMethod, isInterface);
    if (!retarget.hasNewInvokeTarget()) {
      return NO_REWRITING;
    }
    if (isInvokeSuper && matchesNonFinalHolderRewrite(invokedMethod)) {
      DexClassAndMethod superTarget =
          appView.appInfoForDesugaring().lookupSuperTarget(invokedMethod, context);
      // Final methods can be rewritten as a normal invoke.
      if (superTarget != null && !superTarget.getAccessFlags().isFinal()) {
        return InvokeRetargetingResult.createInvokeRetargetingResult(
            appView.options().desugaredLibraryConfiguration.retargetMethod(superTarget, appView));
      }
    }
    return retarget;
  }

  private InvokeRetargetingResult computeRetargetedMethod(
      DexMethod invokedMethod, boolean isInterface) {
    InvokeRetargetingResult invokeRetargetingResult = computeRetargetLibraryMember(invokedMethod);
    if (!invokeRetargetingResult.hasNewInvokeTarget()) {
      if (!matchesNonFinalHolderRewrite(invokedMethod)) {
        return NO_REWRITING;
      }
      // We need to force resolution, even on d8, to know if the invoke has to be rewritten.
      MethodResolutionResult resolutionResult =
          appView.appInfoForDesugaring().resolveMethod(invokedMethod, isInterface);
      if (resolutionResult.isFailedResolution()) {
        return NO_REWRITING;
      }
      DexEncodedMethod singleTarget = resolutionResult.getSingleTarget();
      assert singleTarget != null;
      invokeRetargetingResult = computeRetargetLibraryMember(singleTarget.getReference());
    }
    return invokeRetargetingResult;
  }

  private InvokeRetargetingResult computeRetargetLibraryMember(DexMethod method) {
    DexClassAndMethod emulatedMethod = emulatedDispatchMethods.get(method);
    if (emulatedMethod != null) {
      assert !emulatedMethod.getAccessFlags().isStatic();
      return new InvokeRetargetingResult(
          true,
          eventConsumer -> {
            DexType newHolder =
                syntheticHelper.ensureEmulatedHolderDispatchMethod(emulatedMethod, eventConsumer)
                    .type;
            return computeRetargetMethod(
                method, emulatedMethod.getAccessFlags().isStatic(), newHolder);
          });
    }
    return InvokeRetargetingResult.createInvokeRetargetingResult(retargetLibraryMember.get(method));
  }

  private boolean matchesNonFinalHolderRewrite(DexMethod method) {
    List<DexMethod> dexMethods = nonFinalHolderRewrites.get(method.name);
    if (dexMethods == null) {
      return false;
    }
    for (DexMethod dexMethod : dexMethods) {
      if (method.match(dexMethod)) {
        return true;
      }
    }
    return false;
  }

  DexMethod computeRetargetMethod(DexMethod method, boolean isStatic, DexType newHolder) {
    DexItemFactory factory = appView.dexItemFactory();
    DexProto newProto = isStatic ? method.getProto() : factory.prependHolderToProto(method);
    return factory.createMethod(newHolder, newProto, method.getName());
  }
}
