// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.InterfaceMethodDesugaringMode.ALL;
import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.InterfaceMethodDesugaringMode.EMULATED_INTERFACE_ONLY;
import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.InterfaceMethodDesugaringMode.NONE;
import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.getInterfaceMethodDesugaringMode;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.DerivedMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.icce.AlwaysThrowingInstructionDesugaring;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.InterfaceMethodDesugaringMode;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

//
// Default and static interface method desugaring rewriter (note that lambda
// desugaring should have already processed the code before this rewriter).
//
// In short, during default and static interface method desugaring
// the following actions are performed:
//
//   (1) All static interface methods are moved into companion classes. All calls
//       to these methods are redirected appropriately. All references to these
//       methods from method handles are reported as errors.
//
// Companion class is a synthesized class (<interface-name>-CC) created to host
// static and former default interface methods (see below) from the interface.
//
//   (2) All default interface methods are made static and moved into companion
//       class.
//
//   (3) All calls to default interface methods made via 'super' are changed
//       to directly call appropriate static methods in companion classes.
//
//   (4) All other calls or references to default interface methods are not changed.
//
//   (5) For all program classes explicitly implementing interfaces we analyze the
//       set of default interface methods missing and add them, the created methods
//       forward the call to an appropriate method in interface companion class.
//
public final class InterfaceMethodRewriter implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final InternalOptions options;
  final DexItemFactory factory;
  // If this is true, this will desugar only default methods from emulated interfaces present in
  // the emulated interface descriptor.
  private final InterfaceMethodDesugaringMode desugaringMode;
  private final InterfaceDesugaringSyntheticHelper helper;
  // The emulatedMethod set is there to avoid doing the emulated look-up too often.
  private final Set<DexString> emulatedMethods = Sets.newIdentityHashSet();

  // All forwarding methods and all throwing methods generated during desugaring.
  private final ProgramMethodSet synthesizedMethods = ProgramMethodSet.createConcurrent();

  // Caches default interface method info for already processed interfaces.
  private final Map<DexType, DefaultMethodsHelper.Collection> cache = new ConcurrentHashMap<>();

  // This is used to filter out double desugaring.
  private final Set<CfInstructionDesugaring> precedingDesugaringsForInvoke;
  private final Set<CfInstructionDesugaring> precedingDesugaringsForInvokeDynamic;

  /** Defines a minor variation in desugaring. */
  public enum Flavor {
    /** Process all application resources. */
    IncludeAllResources,
    /** Process all but DEX application resources. */
    ExcludeDexResources
  }

  public static InterfaceMethodRewriter create(
      AppView<?> appView,
      Set<CfInstructionDesugaring> precedingDesugaringsForInvoke,
      Set<CfInstructionDesugaring> precedingDesugaringsForInvokeDynamic) {
    InterfaceMethodDesugaringMode desugaringMode =
        getInterfaceMethodDesugaringMode(appView.options());
    if (desugaringMode == NONE) {
      return null;
    }
    return new InterfaceMethodRewriter(
        appView,
        precedingDesugaringsForInvoke,
        precedingDesugaringsForInvokeDynamic,
        desugaringMode);
  }

  private InterfaceMethodRewriter(
      AppView<?> appView,
      Set<CfInstructionDesugaring> precedingDesugaringsForInvoke,
      Set<CfInstructionDesugaring> precedingDesugaringsForInvokeDynamic,
      InterfaceMethodDesugaringMode desugaringMode) {
    this.appView = appView;
    this.precedingDesugaringsForInvoke = precedingDesugaringsForInvoke;
    this.precedingDesugaringsForInvokeDynamic = precedingDesugaringsForInvokeDynamic;
    this.options = appView.options();
    this.factory = appView.dexItemFactory();
    assert desugaringMode == EMULATED_INTERFACE_ONLY || desugaringMode == ALL;
    this.desugaringMode = desugaringMode;
    assert desugaringMode == EMULATED_INTERFACE_ONLY
        || appView.options().isInterfaceMethodDesugaringEnabled();
    this.helper = new InterfaceDesugaringSyntheticHelper(appView);
    initializeEmulatedInterfaceVariables();
  }

  public static void checkForAssumedLibraryTypes(AppInfo appInfo, InternalOptions options) {
    MachineDesugaredLibrarySpecification machineDesugaredLibrarySpecification =
        options.machineDesugaredLibrarySpecification;
    machineDesugaredLibrarySpecification
        .getEmulatedInterfaces()
        .forEach(
            (ei, descriptor) -> {
              registerType(appInfo, ei);
              registerType(appInfo, descriptor.getRewrittenType());
            });
    machineDesugaredLibrarySpecification
        .getCustomConversions()
        .forEach(
            (type, descriptor) -> {
              registerType(appInfo, type);
              registerType(appInfo, descriptor.getTo().getHolderType());
              registerType(appInfo, descriptor.getFrom().getHolderType());
            });
  }

  private static void registerType(AppInfo appInfo, DexType type) {
    appInfo.dexItemFactory().registerTypeNeededForDesugaring(type);
    DexClass clazz = appInfo.definitionFor(type);
    if (clazz != null && clazz.isLibraryClass() && clazz.isInterface()) {
      clazz.forEachMethod(
          m -> {
            if (m.isDefaultMethod()) {
              appInfo
                  .dexItemFactory()
                  .registerTypeNeededForDesugaring(m.getReference().proto.returnType);
              for (DexType param : m.getReference().proto.parameters.values) {
                appInfo.dexItemFactory().registerTypeNeededForDesugaring(param);
              }
            }
          });
    }
  }

  public Set<DexString> getEmulatedMethods() {
    return emulatedMethods;
  }

  private void initializeEmulatedInterfaceVariables() {
    Set<DexType> emulateLibraryInterface =
        options.machineDesugaredLibrarySpecification.getEmulatedInterfaces().keySet();
    for (DexType interfaceType : emulateLibraryInterface) {
      DexClass emulatedInterfaceClass = appView.definitionFor(interfaceType);
      if (emulatedInterfaceClass != null) {
        for (DexEncodedMethod encodedMethod :
            emulatedInterfaceClass.methods(DexEncodedMethod::isDefaultMethod)) {
          emulatedMethods.add(encodedMethod.getReference().name);
        }
      }
    }
  }

  private boolean isAlreadyDesugared(CfInvoke invoke, ProgramMethod context) {
    return Iterables.any(
        precedingDesugaringsForInvoke,
        desugaring -> desugaring.compute(invoke, context).needsDesugaring());
  }

  private boolean isAlreadyDesugared(CfInvokeDynamic invoke, ProgramMethod context) {
    return Iterables.any(
        precedingDesugaringsForInvokeDynamic,
        desugaring -> desugaring.compute(invoke, context).needsDesugaring());
  }

  @Override
  public boolean hasPreciseNeedsDesugaring() {
    return false;
  }

  /**
   * If the method is not required to be desugared, scanning is used to upgrade when required the
   * class file version, as well as reporting missing type.
   */
  @Override
  public void scan(ProgramMethod context, CfInstructionDesugaringEventConsumer eventConsumer) {
    if (desugaringMode == EMULATED_INTERFACE_ONLY) {
      return;
    }
    if (isSyntheticMethodThatShouldNotBeDoubleProcessed(context)) {
      leavingStaticInvokeToInterface(context);
      return;
    }
    CfCode code = context.getDefinition().getCode().asCfCode();
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction.isInvokeDynamic()
          && !isAlreadyDesugared(instruction.asInvokeDynamic(), context)) {
        reportInterfaceMethodHandleCallSite(instruction.asInvokeDynamic().getCallSite(), context);
      }
      compute(instruction, context).scan();
    }
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (isSyntheticMethodThatShouldNotBeDoubleProcessed(context)) {
      return DesugarDescription.nothing();
    }
    // Interface desugaring is only interested in invokes.
    CfInvoke invoke = instruction.asInvoke();
    if (invoke == null) {
      return DesugarDescription.nothing();
    }
    // Don't desugar if the invoke is to be desugared by preceeding desugar tasks.
    if (isAlreadyDesugared(invoke, context)) {
      return DesugarDescription.nothing();
    }
    // There should never be any calls to interface initializers.
    if (invoke.isInvokeSpecial() && invoke.isInvokeConstructor(factory)) {
      return DesugarDescription.nothing();
    }
    if (desugaringMode == EMULATED_INTERFACE_ONLY) {
      return computeInvokeForEmulatedInterfaceOnly(invoke, context);
    }
    // If the invoke is not an interface invoke, then there should generally not be any desugaring.
    // However, there are some cases where the insertion of forwarding methods can change behavior
    // so we need to identify them at the various call sites here.
    if (!invoke.isInterface()) {
      return computeNonInterfaceInvoke(invoke, context);
    }
    // If the target holder does not resolve we may want to issue diagnostics.
    DexClass holder = appView.definitionForHolder(invoke.getMethod(), context);
    if (holder == null) {
      if (invoke.isInvokeVirtual() || invoke.isInvokeInterface()) {
        // For virtual targets we should not report anything as any virtual dispatch just remains.
        return DesugarDescription.nothing();
      }
      // For static, private and special invokes, they may require desugaring and should warn.
      return DesugarDescription.builder()
          .addScanEffect(
              () -> {
                if (invoke.isInvokeStatic()) {
                  leavingStaticInvokeToInterface(context);
                }
                warnMissingType(context, invoke.getMethod().getHolderType());
              })
          .build();
    }
    // Continue with invoke type logic.
    if (invoke.isInvokeVirtual() || invoke.isInvokeInterface()) {
      return computeInvokeVirtualDispatch(holder, invoke, context);
    }
    if (invoke.isInvokeStatic()) {
      return computeInvokeStatic(holder, invoke, context);
    }
    if (invoke.isInvokeSpecial()) {
      return computeInvokeSpecial(holder, invoke, context);
    }
    return DesugarDescription.nothing();
  }

  private DesugarDescription computeInvokeForEmulatedInterfaceOnly(
      CfInvoke invoke, ProgramMethod context) {
    if (!invoke.getMethod().getHolderType().isClassType()) {
      return DesugarDescription.nothing();
    }
    DexClass holder = appView.definitionForHolder(invoke.getMethod(), context);
    if (holder == null) {
      return DesugarDescription.nothing();
    }

    if (!invoke.isInterface()) {
      if (invoke.isInvokeSpecial()) {
        return computeEmulatedInterfaceInvokeSpecial(holder, invoke.getMethod(), context);
      }
      if (invoke.isInvokeVirtual() || invoke.isInvokeInterface()) {
        DesugarDescription description = computeEmulatedInterfaceVirtualDispatchOrNull(invoke);
        return description == null ? DesugarDescription.nothing() : description;
      }
      return DesugarDescription.nothing();
    }

    if (invoke.isInvokeVirtual() || invoke.isInvokeInterface()) {
      AppInfoWithClassHierarchy appInfoForDesugaring = appView.appInfoForDesugaring();
      SingleResolutionResult<?> resolution =
          appInfoForDesugaring
              .resolveMethodLegacy(invoke.getMethod(), invoke.isInterface())
              .asSingleResolution();
      if (resolution != null
          && resolution.getResolvedMethod().isPrivate()
          && resolution.isAccessibleFrom(context, appView, appInfoForDesugaring).isTrue()) {
        return DesugarDescription.nothing();
      }
      if (resolution != null && resolution.getResolvedMethod().isStatic()) {
        return DesugarDescription.nothing();
      }
      DesugarDescription description = computeEmulatedInterfaceVirtualDispatchOrNull(invoke);
      return description != null ? description : DesugarDescription.nothing();
    }

    // Note: We do not rewrite invoke-static to emulated interfaces above api 24 and there is
    // no way to encode it, but we could add the support here.

    return DesugarDescription.nothing();
  }

  private DesugarDescription computeNonInterfaceInvoke(CfInvoke invoke, ProgramMethod context) {
    assert !invoke.isInterface();
    // Emulated interface desugaring will rewrite non-interface invokes.
    if (invoke.isInvokeSpecial()) {
      DexClass clazz = appView.definitionForHolder(invoke.getMethod(), context);
      if (clazz == null) {
        return DesugarDescription.nothing();
      }
      return computeEmulatedInterfaceInvokeSpecial(clazz, invoke.getMethod(), context);
    }
    if (!invoke.isInvokeVirtual() && !invoke.isInvokeInterface()) {
      return DesugarDescription.nothing();
    }
    DesugarDescription description = computeEmulatedInterfaceVirtualDispatchOrNull(invoke);
    if (description != null) {
      return description;
    }
    // It may be the case that a virtual invoke resolves to a static method. In such a case, if
    // a default method could give rise to a forwarding method in the resolution path, the program
    // would change behavior from throwing ICCE to dispatching to the companion class method.
    AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
    MethodResolutionResult resolution =
        appInfo.resolveMethodLegacy(invoke.getMethod(), invoke.isInterface());
    if (!resolution.isSingleResolution()
        || !resolution.asSingleResolution().getResolvedMethod().isStatic()) {
      return DesugarDescription.nothing();
    }
    DexClass holder = appInfo.definitionFor(invoke.getMethod().getHolderType(), context);
    DexClassAndMethod target = appInfo.lookupMaximallySpecificMethod(holder, invoke.getMethod());
    if (target != null && target.isDefaultMethod()) {
      // Rewrite the invoke to a throw ICCE as the default method forward would otherwise hide the
      // static / virtual mismatch.
      return computeInvokeAsThrowRewrite(invoke, resolution.asSingleResolution(), context);
    }
    return DesugarDescription.nothing();
  }

  private DesugarDescription computeInvokeSpecial(
      DexClass holder, CfInvoke invoke, ProgramMethod context) {
    if (invoke.isInvokeSuper(context.getHolderType())) {
      return rewriteInvokeSuper(invoke, context);
    }
    return computeInvokeDirect(holder, invoke, context);
  }

  private DesugarDescription computeInvokeStatic(
      DexClass holder, CfInvoke invoke, ProgramMethod context) {
    if (!holder.isInterface()) {
      return DesugarDescription.builder()
          .addScanEffect(() -> leavingStaticInvokeToInterface(context))
          .build();
    }
    // TODO(b/199135051): This should not be needed. Targeted synthetics should be in place.
    if (appView.getSyntheticItems().isPendingSynthetic(invoke.getMethod().getHolderType())) {
      // We did not create this code yet, but it will not require rewriting.
      return DesugarDescription.nothing();
    }
    if (isNonDesugaredLibraryClass(holder)) {
      // NOTE: we intentionally don't desugar static calls into static interface
      // methods coming from android.jar since it is only possible in case v24+
      // version of android.jar is provided.
      //
      // We assume such calls are properly guarded by if-checks like
      //    'if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.XYZ) { ... }'
      //
      // WARNING: This may result in incorrect code on older platforms!
      // Retarget call to an appropriate method of companion class.
      if (options.canLeaveStaticInterfaceMethodInvokes()) {
        // When leaving static interface method invokes upgrade the class file version.
        return DesugarDescription.builder()
            .addScanEffect(() -> leavingStaticInvokeToInterface(context))
            .build();
      }
      // On pre-L devices static calls to interface methods result in verifier
      // rejecting the whole class. We have to create special dispatch classes,
      // so the user class is not rejected because it make this call directly.
      // TODO(b/166247515): If this an incorrect invoke-static without the interface bit
      //  we end up "fixing" the code and remove and ICCE error.
      if (synthesizedMethods.contains(context)) {
        // When reprocessing the method generated below, the desugaring asserts this method
        // does not need any new desugaring, while the interface method rewriter tries
        // to outline again the invoke-static. Just do nothing instead.
        return DesugarDescription.nothing();
      }
      return DesugarDescription.builder()
          .setDesugarRewrite(
              (freshLocalProvider,
                  localStackAllocator,
                  eventConsumer,
                  context1,
                  methodProcessingContext,
                  desugaringCollection,
                  dexItemFactory) -> {
                ProgramMethod newProgramMethod =
                    appView
                        .getSyntheticItems()
                        .createMethod(
                            kind -> kind.STATIC_INTERFACE_CALL,
                            methodProcessingContext.createUniqueContext(),
                            appView,
                            syntheticMethodBuilder ->
                                syntheticMethodBuilder
                                    .setProto(invoke.getMethod().getProto())
                                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                                    .setCode(
                                        m ->
                                            ForwardMethodBuilder.builder(factory)
                                                .setStaticTarget(invoke.getMethod(), true)
                                                .setStaticSource(m)
                                                .build()));
                synthesizedMethods.add(newProgramMethod);
                eventConsumer.acceptInvokeStaticInterfaceOutliningMethod(
                    newProgramMethod, context1);
                // The synthetic dispatch class has static interface method invokes, so set
                // the class file version accordingly.
                leavingStaticInvokeToInterface(newProgramMethod);
                return getInvokeStaticInstructions(newProgramMethod.getReference());
              })
          .build();
    }

    SingleResolutionResult<?> resolutionResult =
        appView
            .appInfoForDesugaring()
            .resolveMethodOnInterfaceLegacy(holder, invoke.getMethod())
            .asSingleResolution();
    if (holder.isInterface() && shouldRewriteToInvokeToThrow(resolutionResult, true)) {
      return computeInvokeAsThrowRewrite(invoke, resolutionResult, context);
    }

    assert resolutionResult != null;
    assert resolutionResult.getResolvedMethod().isStatic();
    DexClassAndMethod method = resolutionResult.getResolutionPair();
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                eventConsumer,
                context12,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) -> {
              DexClassAndMethod companionMethod =
                  helper.ensureStaticAsMethodOfCompanionClassStub(method, eventConsumer);
              return getInvokeStaticInstructions(companionMethod.getReference());
            })
        .build();
  }

  private DesugarDescription computeInvokeVirtualDispatch(
      DexClass holder, CfInvoke invoke, ProgramMethod context) {
    AppInfoWithClassHierarchy appInfoForDesugaring = appView.appInfoForDesugaring();
    SingleResolutionResult<?> resolution =
        appInfoForDesugaring
            .resolveMethodLegacy(invoke.getMethod(), invoke.isInterface())
            .asSingleResolution();
    if (resolution != null
        && resolution.getResolvedMethod().isPrivate()
        && resolution.isAccessibleFrom(context, appView, appInfoForDesugaring).isTrue()) {
      // TODO(b/198267586): What about the private in-accessible case?
      return computeInvokeDirect(holder, invoke, context);
    }
    if (resolution != null && resolution.getResolvedMethod().isStatic()) {
      return computeInvokeAsThrowRewrite(invoke, resolution, context);
    }
    DesugarDescription description = computeEmulatedInterfaceVirtualDispatchOrNull(invoke);
    return description != null ? description : DesugarDescription.nothing();
  }

  private DesugarDescription computeEmulatedInterfaceVirtualDispatchOrNull(CfInvoke invoke) {
    MethodResolutionResult resolutionResult =
        appView
            .appInfoForDesugaring()
            .resolveMethodLegacy(invoke.getMethod(), invoke.isInterface());
    DerivedMethod emulatedDispatchMethod =
        helper.computeEmulatedInterfaceDispatchMethod(resolutionResult);
    if (emulatedDispatchMethod == null) {
      return null;
    }
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                eventConsumer,
                context1,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                getInvokeStaticInstructions(
                    helper
                        .ensureEmulatedInterfaceDispatchMethod(
                            emulatedDispatchMethod, eventConsumer)
                        .getReference()))
        .build();
  }

  @SuppressWarnings("ReferenceEquality")
  private DesugarDescription computeInvokeDirect(
      DexClass clazz, CfInvoke invoke, ProgramMethod context) {
    DexMethod invokedMethod = invoke.getMethod();
    if (!clazz.isInterface()) {
      return DesugarDescription.nothing();
    }

    if (clazz.isLibraryClass()) {
      throw new CompilationError(
          "Unexpected call to a private method "
              + "defined in library class "
              + clazz.toSourceString(),
          getMethodOrigin(context.getReference()));
    }

    MethodResolutionResult resolution =
        appView.appInfoForDesugaring().resolveMethodLegacy(invokedMethod, invoke.isInterface());
    if (resolution.isFailedResolution()) {
      return computeInvokeAsThrowRewrite(invoke, null, context);
    }

    SingleResolutionResult<?> singleResolution = resolution.asSingleResolution();
    if (singleResolution == null) {
      return DesugarDescription.nothing();
    }

    DexClassAndMethod directTarget = clazz.lookupClassMethod(invokedMethod);
    if (directTarget != null) {
      // TODO(b/199135051): Replace this by use of the resolution result.
      assert directTarget.getDefinition() == singleResolution.getResolutionPair().getDefinition();
      return DesugarDescription.builder()
          .setDesugarRewrite(
              (freshLocalProvider,
                  localStackAllocator,
                  eventConsumer,
                  context1,
                  methodProcessingContext,
                  desugaringCollection,
                  dexItemFactory) -> {
                // This can be a private instance method call. Note that the referenced
                // method is expected to be in the current class since it is private, but desugaring
                // may move some methods or their code into other classes.
                DexClassAndMethod companionMethodDefinition;
                DexMethod companionMethod;
                if (directTarget.getDefinition().isPrivateMethod()) {
                  if (directTarget.isProgramMethod()) {
                    companionMethodDefinition =
                        helper.ensurePrivateAsMethodOfProgramCompanionClassStub(
                            directTarget.asProgramMethod(), eventConsumer);
                    companionMethod = companionMethodDefinition.getReference();
                  } else {
                    // TODO(b/200938617): Why does this not create a stub on the class path?
                    companionMethod = helper.privateAsMethodOfCompanionClass(directTarget);
                  }
                } else {
                  companionMethodDefinition =
                      helper.ensureDefaultAsMethodOfCompanionClassStub(directTarget, eventConsumer);
                  companionMethod = companionMethodDefinition.getReference();
                }
                return getInvokeStaticInstructions(companionMethod);
              })
          .build();
    } else {
      // The method can be a default method in the interface hierarchy.
      DexClassAndMethod virtualTarget =
          appView.appInfoForDesugaring().lookupMaximallySpecificMethod(clazz, invokedMethod);
      if (virtualTarget != null) {
        // TODO(b/199135051): Replace this by use of the resolution result.
        assert virtualTarget.getDefinition()
            == singleResolution.getResolutionPair().getDefinition();
        return DesugarDescription.builder()
            .setDesugarRewrite(
                (freshLocalProvider,
                    localStackAllocator,
                    eventConsumer,
                    context12,
                    methodProcessingContext,
                    desugaringCollection,
                    dexItemFactory) -> {
                  // This is a invoke-direct call to a virtual method.
                  DexClassAndMethod companionMethod =
                      helper.ensureDefaultAsMethodOfCompanionClassStub(
                          virtualTarget, eventConsumer);
                  return getInvokeStaticInstructions(companionMethod.getReference());
                })
            .build();
      } else {
        // The below assert is here because a well-type program should have a target, but we
        // cannot throw a compilation error, since we have no knowledge about the input.
        assert false;
      }
    }
    return DesugarDescription.nothing();
  }

  private DesugarDescription computeInvokeAsThrowRewrite(
      CfInvoke invoke, SingleResolutionResult<?> resolution, ProgramMethod context) {
    assert !isAlreadyDesugared(invoke, context);
    return AlwaysThrowingInstructionDesugaring.computeInvokeAsThrowRewrite(
        appView, invoke, resolution);
  }

  private Collection<CfInstruction> getInvokeStaticInstructions(DexMethod newTarget) {
    return Collections.singletonList(
        new CfInvoke(org.objectweb.asm.Opcodes.INVOKESTATIC, newTarget, false));
  }

  private void leavingStaticInvokeToInterface(ProgramMethod method) {
    // When leaving static interface method invokes possibly upgrade the class file
    // version, but don't go above the initial class file version. If the input was
    // 1.7 or below, this will make a VerificationError on the input a VerificationError
    // on the output. If the input was 1.8 or above the runtime behaviour (potential ICCE)
    // will remain the same.
    upgradeCfVersionToSupportInterfaceMethodInvoke(method);
  }

  private void leavingSuperInvokeToInterface(ProgramMethod method) {
    // When leaving interface method invokes possibly upgrade the class file
    // version, but don't go above the initial class file version. If the input was
    // 1.7 or below, this will make a VerificationError on the input a VerificationError
    // on the output. If the input was 1.8 or above the runtime behaviour (potential ICCE)
    // will remain the same.
    upgradeCfVersionToSupportInterfaceMethodInvoke(method);
  }

  private void upgradeCfVersionToSupportInterfaceMethodInvoke(ProgramMethod method) {
    if (method.getHolder().hasClassFileVersion()) {
      method
          .getDefinition()
          .upgradeClassFileVersion(
              Ordered.min(CfVersion.V1_8, method.getHolder().getInitialClassFileVersion()));
    } else {
      method.getDefinition().upgradeClassFileVersion(CfVersion.V1_8);
    }
  }

  private boolean isSyntheticMethodThatShouldNotBeDoubleProcessed(ProgramMethod method) {
    return appView.getSyntheticItems().isSyntheticMethodThatShouldNotBeDoubleProcessed(method);
  }

  private void reportInterfaceMethodHandleCallSite(DexCallSite callSite, ProgramMethod context) {
    // Check that static interface methods are not referenced from invoke-custom instructions via
    // method handles.
    reportStaticInterfaceMethodHandle(context, callSite.bootstrapMethod);
    for (DexValue arg : callSite.bootstrapArgs) {
      if (arg.isDexValueMethodHandle()) {
        reportStaticInterfaceMethodHandle(context, arg.asDexValueMethodHandle().value);
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private DesugarDescription rewriteInvokeSuper(CfInvoke invoke, ProgramMethod context) {
    DexMethod invokedMethod = invoke.getMethod();
    DexClass clazz = appView.definitionFor(invokedMethod.holder, context);
    if (clazz == null) {
      // NOTE: leave unchanged those calls to undefined targets. This may lead to runtime
      // exception but we can not report it as error since it can also be the intended
      // behavior.
      return DesugarDescription.builder()
          .addScanEffect(() -> warnMissingType(context, invokedMethod.holder))
          .build();
    }

    SingleResolutionResult<?> resolutionResult =
        appView
            .appInfoForDesugaring()
            .resolveMethodOnLegacy(clazz, invokedMethod)
            .asSingleResolution();
    if (clazz.isInterface() && shouldRewriteToInvokeToThrow(resolutionResult, false)) {
      return computeInvokeAsThrowRewrite(invoke, resolutionResult, context);
    }

    if (clazz.isInterface() && !resolutionResult.getResolutionPair().getHolder().isLibraryClass()) {
      // NOTE: we intentionally don't desugar super calls into interface methods
      // coming from android.jar since it is only possible in case v24+ version
      // of android.jar is provided.
      //
      // We assume such calls are properly guarded by if-checks like
      //    'if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.XYZ) { ... }'
      //
      // WARNING: This may result in incorrect code on older platforms!
      // Retarget call to an appropriate method of companion class.
      if (resolutionResult.getResolvedMethod().isPrivateMethod()) {
        if (resolutionResult
            .isAccessibleFrom(context, appView, appView.appInfoForDesugaring())
            .isFalse()) {
          // TODO(b/145775365): This should throw IAE.
          return computeInvokeAsThrowRewrite(invoke, null, context);
        }
        return DesugarDescription.builder()
            .setDesugarRewrite(
                (freshLocalProvider,
                    localStackAllocator,
                    eventConsumer,
                    context1,
                    methodProcessingContext,
                    desugaringCollection,
                    dexItemFactory) -> {
                  DexClassAndMethod method = resolutionResult.getResolutionPair();
                  DexMethod companionMethod;
                  if (method.isProgramMethod()) {
                    ProgramMethod companionMethodDefinition =
                        helper.ensurePrivateAsMethodOfProgramCompanionClassStub(
                            method.asProgramMethod(), eventConsumer);
                    companionMethod = companionMethodDefinition.getReference();
                  } else {
                    companionMethod = helper.privateAsMethodOfCompanionClass(method);
                  }
                  return getInvokeStaticInstructions(companionMethod);
                })
            .build();
      } else {
        return DesugarDescription.builder()
            .setDesugarRewrite(
                (freshLocalProvider,
                    localStackAllocator,
                    eventConsumer,
                    context12,
                    methodProcessingContext,
                    desugaringCollection,
                    dexItemFactory) -> {
                  DexClassAndMethod method = resolutionResult.getResolutionPair();
                  // TODO(b/199135051): Why do this amend routine. We have done resolution, so would
                  //  that not be the correct target!? I think this is just legacy from before
                  //  resolution was implemented in full.
                  DexMethod amendedMethod =
                      amendDefaultMethod(context12.getHolder(), invokedMethod);
                  assert method.getReference() == amendedMethod;
                  DexClassAndMethod companionMethod =
                      helper.ensureDefaultAsMethodOfCompanionClassStub(method, eventConsumer);
                  return getInvokeStaticInstructions(companionMethod.getReference());
                })
            .build();
      }
    }

    DesugarDescription emulatedInterfaceDesugaring =
        computeEmulatedInterfaceInvokeSpecial(clazz, invokedMethod, context);
    if (!emulatedInterfaceDesugaring.needsDesugaring()) {
      if (context.isDefaultMethod()) {
        return AlwaysThrowingInstructionDesugaring.computeInvokeAsThrowNSMERewrite(
            appView,
            invoke,
            () ->
                appView
                    .reporter()
                    .warning(
                        new StringDiagnostic(
                            "Interface method desugaring has inserted NoSuchMethodError replacing a"
                                + " super call in "
                                + context.toSourceString(),
                            context.getOrigin())));
      } else {
        return DesugarDescription.builder()
            .addScanEffect(() -> leavingSuperInvokeToInterface(context))
            .build();
      }
    }

    return emulatedInterfaceDesugaring;
  }

  private DesugarDescription computeEmulatedInterfaceInvokeSpecial(
      DexClass clazz, DexMethod invokedMethod, ProgramMethod context) {
    AppInfoWithClassHierarchy appInfoForDesugaring = appView.appInfoForDesugaring();
    DexClassAndMethod superTarget =
        appView
            .appInfoForDesugaring()
            .lookupSuperTarget(invokedMethod, context, appView, appInfoForDesugaring);
    if (clazz.isInterface()
        && clazz.isLibraryClass()
        && helper.isInDesugaredLibrary(clazz)
        && !helper.isEmulatedInterface(clazz.type)) {
      if (superTarget != null && superTarget.getDefinition().isDefaultMethod()) {
        DexClass holder = superTarget.getHolder();
        if (holder.isLibraryClass() && holder.isInterface()) {
          return DesugarDescription.builder()
              .setDesugarRewrite(
                  (freshLocalProvider,
                      localStackAllocator,
                      eventConsumer,
                      context13,
                      methodProcessingContext,
                      desugaringCollection,
                      dexItemFactory) -> {
                    DexClassAndMethod companionTarget =
                        helper.ensureDefaultAsMethodOfCompanionClassStub(
                            superTarget, eventConsumer);
                    return getInvokeStaticInstructions(companionTarget.getReference());
                  })
              .build();
        }
      }
    }
    // That invoke super may not resolve since the super method may not be present
    // since it's in the emulated interface. We need to force resolution. If it resolves
    // to a library method, then it needs to be rewritten.
    // If it resolves to a program overrides, the invoke-super can remain.
    DerivedMethod forwardingMethod =
        helper.computeEmulatedInterfaceForwardingMethod(clazz, superTarget);
    if (forwardingMethod == null) {
      return DesugarDescription.nothing();
    }
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                eventConsumer,
                context14,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                getInvokeStaticInstructions(
                    helper.ensureEmulatedInterfaceForwardingMethod(
                        forwardingMethod, eventConsumer)))
        .build();
  }

  private boolean shouldRewriteToInvokeToThrow(
      SingleResolutionResult<?> resolutionResult, boolean isInvokeStatic) {
    return resolutionResult == null
        || resolutionResult.getResolvedMethod().isStatic() != isInvokeStatic;
  }

  private boolean isNonDesugaredLibraryClass(DexClass clazz) {
    return clazz.isLibraryClass() && !helper.isInDesugaredLibrary(clazz);
  }

  private void reportStaticInterfaceMethodHandle(ProgramMethod context, DexMethodHandle handle) {
    if (handle.type.isInvokeStatic()) {
      DexClass holderClass = appView.definitionFor(handle.asMethod().holder);
      // NOTE: If the class definition is missing we can't check. Let it be handled as any other
      // missing call target.
      if (holderClass == null) {
        warnMissingType(context, handle.asMethod().holder);
      } else if (holderClass.isInterface()) {
        throw new Unimplemented(
            "Desugaring of static interface method handle in `"
                + context.toSourceString()
                + "` is not yet supported.");
      }
    }
  }

  // It is possible that referenced method actually points to an interface which does
  // not define this default methods, but inherits it. We are making our best effort
  // to find an appropriate method, but still use the original one in case we fail.
  private DexMethod amendDefaultMethod(DexClass classToDesugar, DexMethod method) {
    DexMethod singleCandidate =
        getOrCreateInterfaceInfo(classToDesugar, classToDesugar, method.holder)
            .getSingleCandidate(method);
    return singleCandidate != null ? singleCandidate : method;
  }

  public InterfaceMethodProcessorFacade getPostProcessingDesugaringD8(
      Flavor flavour, InterfaceProcessor interfaceProcessor) {
    return new InterfaceMethodProcessorFacade(
        appView, flavour, m -> true, interfaceProcessor, desugaringMode);
  }

  public InterfaceMethodProcessorFacade getPostProcessingDesugaringR8(
      Flavor flavour,
      Predicate<ProgramMethod> isLiveMethod,
      InterfaceProcessor interfaceProcessor) {
    return new InterfaceMethodProcessorFacade(
        appView, flavour, isLiveMethod, interfaceProcessor, desugaringMode);
  }

  private Origin getMethodOrigin(DexMethod method) {
    DexType holder = method.holder;
    if (InterfaceDesugaringSyntheticHelper.isCompanionClassType(holder)) {
      holder = helper.getInterfaceClassType(holder);
    }
    DexClass clazz = appView.definitionFor(holder);
    return clazz == null ? Origin.unknown() : clazz.getOrigin();
  }

  final DefaultMethodsHelper.Collection getOrCreateInterfaceInfo(
      DexClass classToDesugar, DexClass implementing, DexType iface) {
    DefaultMethodsHelper.Collection collection = cache.get(iface);
    if (collection != null) {
      return collection;
    }
    collection = createInterfaceInfo(classToDesugar, implementing, iface);
    DefaultMethodsHelper.Collection existing = cache.putIfAbsent(iface, collection);
    return existing != null ? existing : collection;
  }

  private DefaultMethodsHelper.Collection createInterfaceInfo(
      DexClass classToDesugar, DexClass implementing, DexType iface) {
    DefaultMethodsHelper helper = new DefaultMethodsHelper();
    DexClass definedInterface = appView.definitionFor(iface);
    if (definedInterface == null) {
      this.helper.warnMissingInterface(classToDesugar, implementing, iface);
      return helper.wrapInCollection();
    }
    if (!definedInterface.isInterface()) {
      throw new CompilationError(
          "Type "
              + iface.toSourceString()
              + " is referenced as an interface from `"
              + implementing.toString()
              + "`.");
    }

    if (isNonDesugaredLibraryClass(definedInterface)) {
      // NOTE: We intentionally ignore all candidates coming from android.jar
      // since it is only possible in case v24+ version of android.jar is provided.
      // WARNING: This may result in incorrect code if something else than Android bootclasspath
      // classes are given as libraries!
      return helper.wrapInCollection();
    }

    // At this point we likely have a non-library type that may depend on default method information
    // from its interfaces and the dependency should be reported.
    if (implementing.isProgramClass() && !definedInterface.isLibraryClass()) {
      appView.appInfo().reportDependencyEdge(implementing.asProgramClass(), definedInterface);
    }

    // Merge information from all superinterfaces.
    for (DexType superinterface : definedInterface.interfaces.values) {
      helper.merge(getOrCreateInterfaceInfo(classToDesugar, definedInterface, superinterface));
    }

    // Hide by virtual methods of this interface.
    for (DexEncodedMethod virtual : definedInterface.virtualMethods()) {
      helper.hideMatches(virtual.getReference());
    }

    // Add all default methods of this interface.
    for (DexEncodedMethod encoded : definedInterface.virtualMethods()) {
      if (this.helper.isCompatibleDefaultMethod(encoded)) {
        helper.addDefaultMethod(encoded);
      }
    }

    return helper.wrapInCollection();
  }

  private void warnMissingType(ProgramMethod context, DexType missing) {
    // Companion/Emulated interface/Conversion classes for desugared library won't be missing,
    // they are in the desugared library.
    if (helper.shouldIgnoreFromReports(missing)) {
      return;
    }
    DexMethod method = appView.graphLens().getOriginalMethodSignature(context.getReference());
    Origin origin = getMethodOrigin(method);
    MethodPosition position = new MethodPosition(method.asMethodReference());
    options.warningMissingTypeForDesugar(origin, position, missing, method);
  }
}
