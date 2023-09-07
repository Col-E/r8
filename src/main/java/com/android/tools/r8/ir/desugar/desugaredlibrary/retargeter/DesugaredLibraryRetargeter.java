// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import java.util.Collections;
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
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (instruction.isStaticFieldGet()) {
      return computeStaticFieldGetDescription(instruction, context);
    }
    if (instruction.isInvoke()) {
      return computeInvokeDescription(instruction, context);
    }
    return DesugarDescription.nothing();
  }

  private DesugarDescription computeInvokeDescription(
      CfInstruction instruction, ProgramMethod context) {
    if (appView.dexItemFactory().multiDexTypes.contains(context.getContextType())) {
      return DesugarDescription.nothing();
    }
    CfInvoke cfInvoke = instruction.asInvoke();
    DexMethod invokedMethod = cfInvoke.getMethod();
    AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodLegacy(invokedMethod, cfInvoke.isInterface());
    if (!resolutionResult.isSingleResolution()) {
      return DesugarDescription.nothing();
    }
    assert resolutionResult.getSingleTarget() != null;
    DexMethod singleTarget = resolutionResult.getSingleTarget().getReference();
    if (cfInvoke.isInvokeStatic()) {
      DexMethod retarget = staticRetarget.get(singleTarget);
      return ensureInvokeRetargetingResult(retarget);
    }
    DesugarDescription retarget = computeNonStaticRetarget(singleTarget, false);
    if (!retarget.needsDesugaring()) {
      return DesugarDescription.nothing();
    }
    if (cfInvoke.isInvokeSuper(context.getHolderType())) {
      DexClassAndMethod superTarget =
          appInfo.lookupSuperTarget(invokedMethod, context, appView, appInfo);
      if (superTarget != null) {
        assert !superTarget.getDefinition().isStatic();
        return computeNonStaticRetarget(superTarget.getReference(), true);
      }
    }
    return retarget;
  }

  private DesugarDescription createWithTarget(
      BiFunction<CfInstructionDesugaringEventConsumer, MethodProcessingContext, DexMethod>
          methodProvider) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugarings,
                dexItemFactory) -> {
              DexMethod newInvokeTarget =
                  methodProvider.apply(eventConsumer, methodProcessingContext);
              assert appView.definitionFor(newInvokeTarget.getHolderType()) != null;
              assert !appView.definitionFor(newInvokeTarget.getHolderType()).isInterface();
              return Collections.singletonList(
                  new CfInvoke(Opcodes.INVOKESTATIC, newInvokeTarget, false));
            })
        .build();
  }

  private DesugarDescription computeStaticFieldGetDescription(
      CfInstruction instruction, ProgramMethod context) {
    CfFieldInstruction fieldInstruction = instruction.asFieldInstruction();
    DexField fieldRetarget = fieldRetarget(fieldInstruction, context);
    if (fieldRetarget == null) {
      return DesugarDescription.nothing();
    }
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context1,
                methodProcessingContext,
                desugarings,
                dexItemFactory) ->
                Collections.singletonList(fieldInstruction.createWithField(fieldRetarget)))
        .build();
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

  DesugarDescription ensureInvokeRetargetingResult(DexMethod retarget) {
    if (retarget == null) {
      return DesugarDescription.nothing();
    }
    return createWithTarget(
        (eventConsumer, methodProcessingContext) ->
            syntheticHelper.ensureRetargetMethod(retarget, eventConsumer));
  }

  private DesugarDescription computeNonStaticRetarget(DexMethod singleTarget, boolean superInvoke) {
    EmulatedDispatchMethodDescriptor descriptor = emulatedVirtualRetarget.get(singleTarget);
    if (descriptor != null) {
      return createWithTarget(
          (eventConsumer, methodProcessingContext) ->
              superInvoke
                  ? syntheticHelper.ensureForwardingMethod(descriptor, eventConsumer)
                  : syntheticHelper.ensureEmulatedHolderDispatchMethod(descriptor, eventConsumer));
    }
    if (covariantRetarget.containsKey(singleTarget)) {
      return createWithTarget(
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
