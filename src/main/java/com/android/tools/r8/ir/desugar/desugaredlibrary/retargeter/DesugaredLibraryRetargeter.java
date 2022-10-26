// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeter.InvokeRetargetingResult.NO_REWRITING;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterInstructionEventConsumer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;

public class DesugaredLibraryRetargeter implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final DesugaredLibraryRetargeterSyntheticHelper syntheticHelper;

  private final Map<DexField, DexField> staticFieldRetarget;
  private final Map<DexMethod, DexMethod> covariantRetarget;
  private final Map<DexMethod, DexMethod> staticRetarget;
  private final Map<DexMethod, DexMethod> nonEmulatedVirtualRetarget;
  private final Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedVirtualRetarget;

  public DesugaredLibraryRetargeter(AppView<?> appView) {
    this.appView = appView;
    this.syntheticHelper = new DesugaredLibraryRetargeterSyntheticHelper(appView);
    MachineDesugaredLibrarySpecification specification =
        appView.options().machineDesugaredLibrarySpecification;
    staticFieldRetarget = specification.getStaticFieldRetarget();
    covariantRetarget = specification.getCovariantRetarget();
    staticRetarget = specification.getStaticRetarget();
    nonEmulatedVirtualRetarget = specification.getNonEmulatedVirtualRetarget();
    emulatedVirtualRetarget = specification.getEmulatedVirtualRetarget();
  }

  // Used by the ListOfBackportedMethods utility.
  public void visit(Consumer<DexMethod> consumer) {
    staticRetarget.keySet().forEach(consumer);
    nonEmulatedVirtualRetarget.keySet().forEach(consumer);
    emulatedVirtualRetarget.keySet().forEach(consumer);
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      CfInstructionDesugaringCollection desugaringCollection,
      DexItemFactory dexItemFactory) {
    if (instruction.isStaticFieldGet() && needsDesugaring(instruction, context)) {
      return desugarFieldInstruction(instruction.asFieldInstruction(), context);
    } else if (instruction.isInvoke() && needsDesugaring(instruction, context)) {
      return desugarInvoke(instruction.asInvoke(), eventConsumer, context, methodProcessingContext);
    }
    return null;
  }

  private Collection<CfInstruction> desugarFieldInstruction(
      CfFieldInstruction fieldInstruction, ProgramMethod context) {
    DexField fieldRetarget = fieldRetarget(fieldInstruction, context);
    assert fieldRetarget != null;
    assert fieldInstruction.isStaticFieldGet();
    return Collections.singletonList(fieldInstruction.createWithField(fieldRetarget));
  }

  private List<CfInstruction> desugarInvoke(
      CfInvoke invoke,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    InvokeRetargetingResult invokeRetargetingResult = computeNewInvokeTarget(invoke, context);
    assert invokeRetargetingResult.hasNewInvokeTarget();
    DexMethod newInvokeTarget =
        invokeRetargetingResult.getNewInvokeTarget(eventConsumer, methodProcessingContext);
    return Collections.singletonList(
        new CfInvoke(Opcodes.INVOKESTATIC, newInvokeTarget, invoke.isInterface()));
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    if (instruction.isStaticFieldGet()) {
      return fieldRetarget(instruction.asFieldInstruction(), context) != null;
    } else if (instruction.isInvoke()) {
      return computeNewInvokeTarget(instruction.asInvoke(), context).hasNewInvokeTarget();
    }
    return false;
  }

  private DexField fieldRetarget(CfFieldInstruction fieldInstruction, ProgramMethod context) {
    DexEncodedField resolvedField =
        appView
            .appInfoForDesugaring()
            .resolveField(fieldInstruction.getField(), context)
            .getResolvedField();
    if (resolvedField != null) {
      assert resolvedField.isStatic()
          || !staticFieldRetarget.containsKey(resolvedField.getReference());
      return staticFieldRetarget.get(resolvedField.getReference());
    }
    return null;
  }

  InvokeRetargetingResult ensureInvokeRetargetingResult(DexMethod retarget) {
    if (retarget == null) {
      return NO_REWRITING;
    }
    return new InvokeRetargetingResult(
        true,
        (eventConsumer, methodProcessingContext) -> {
          syntheticHelper.ensureRetargetMethod(retarget, eventConsumer);
          return retarget;
        });
  }

  static class InvokeRetargetingResult {

    static InvokeRetargetingResult NO_REWRITING =
        new InvokeRetargetingResult(false, (ignored, alsoIgnored) -> null);

    private final boolean hasNewInvokeTarget;
    private final BiFunction<
            DesugaredLibraryRetargeterInstructionEventConsumer, MethodProcessingContext, DexMethod>
        newInvokeTargetSupplier;

    private InvokeRetargetingResult(
        boolean hasNewInvokeTarget,
        BiFunction<
                DesugaredLibraryRetargeterInstructionEventConsumer,
                MethodProcessingContext,
                DexMethod>
            newInvokeTargetSupplier) {
      this.hasNewInvokeTarget = hasNewInvokeTarget;
      this.newInvokeTargetSupplier = newInvokeTargetSupplier;
    }

    public boolean hasNewInvokeTarget() {
      return hasNewInvokeTarget;
    }

    public DexMethod getNewInvokeTarget(
        DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer,
        MethodProcessingContext methodProcessingContext) {
      assert hasNewInvokeTarget();
      return newInvokeTargetSupplier.apply(eventConsumer, methodProcessingContext);
    }
  }

  private InvokeRetargetingResult computeNewInvokeTarget(
      CfInvoke instruction, ProgramMethod context) {
    if (appView.dexItemFactory().multiDexTypes.contains(context.getContextType())) {
      return NO_REWRITING;
    }
    CfInvoke cfInvoke = instruction.asInvoke();
    DexMethod invokedMethod = cfInvoke.getMethod();
    AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodLegacy(invokedMethod, cfInvoke.isInterface());
    if (!resolutionResult.isSingleResolution()) {
      return NO_REWRITING;
    }
    assert resolutionResult.getSingleTarget() != null;
    DexMethod singleTarget = resolutionResult.getSingleTarget().getReference();
    if (cfInvoke.isInvokeStatic()) {
      DexMethod retarget = staticRetarget.get(singleTarget);
      return retarget == null ? NO_REWRITING : ensureInvokeRetargetingResult(retarget);
    }
    InvokeRetargetingResult retarget = computeNonStaticRetarget(singleTarget, false);
    if (!retarget.hasNewInvokeTarget()) {
      return NO_REWRITING;
    }
    if (cfInvoke.isInvokeSuper(context.getHolderType())) {
      DexClassAndMethod superTarget = appInfo.lookupSuperTarget(invokedMethod, context);
      if (superTarget != null) {
        assert !superTarget.getDefinition().isStatic();
        return computeNonStaticRetarget(superTarget.getReference(), true);
      }
    }
    return retarget;
  }

  private InvokeRetargetingResult computeNonStaticRetarget(
      DexMethod singleTarget, boolean superInvoke) {
    EmulatedDispatchMethodDescriptor descriptor = emulatedVirtualRetarget.get(singleTarget);
    if (descriptor != null) {
      return new InvokeRetargetingResult(
          true,
          (eventConsumer, methodProcessingContext) ->
              superInvoke
                  ? syntheticHelper.ensureForwardingMethod(descriptor, eventConsumer)
                  : syntheticHelper.ensureEmulatedHolderDispatchMethod(descriptor, eventConsumer));
    }
    if (covariantRetarget.containsKey(singleTarget)) {
      return new InvokeRetargetingResult(
          true,
          (eventConsumer, methodProcessingContext) ->
              syntheticHelper.ensureCovariantRetargetMethod(
                  singleTarget,
                  covariantRetarget.get(singleTarget),
                  eventConsumer,
                  methodProcessingContext));
    }
    return ensureInvokeRetargetingResult(nonEmulatedVirtualRetarget.get(singleTarget));
  }
}
