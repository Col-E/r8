// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import static com.android.tools.r8.ir.code.Invoke.Type.DIRECT;
import static com.android.tools.r8.ir.code.Invoke.Type.INTERFACE;
import static com.android.tools.r8.ir.code.Invoke.Type.STATIC;
import static com.android.tools.r8.ir.code.Invoke.Type.SUPER;
import static com.android.tools.r8.ir.code.Invoke.Type.VIRTUAL;

import com.android.tools.r8.DesugarGraphConsumer;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
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
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryConfiguration;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryRetargeter;
import com.android.tools.r8.ir.desugar.lambda.LambdaInstructionDesugaring;
import com.android.tools.r8.ir.desugar.stringconcat.StringConcatInstructionDesugaring;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.MethodSynthesizerConsumer;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.UtilityMethodForCodeOptimizations;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
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
  private final InterfaceDesugaringSyntheticHelper helper;
  // The emulatedMethod set is there to avoid doing the emulated look-up too often.
  private final Set<DexString> emulatedMethods = Sets.newIdentityHashSet();

  // All forwarding methods and all throwing methods generated during desugaring.
  private final ProgramMethodSet synthesizedMethods = ProgramMethodSet.createConcurrent();

  // Caches default interface method info for already processed interfaces.
  private final Map<DexType, DefaultMethodsHelper.Collection> cache = new ConcurrentHashMap<>();

  // This is used to filter out double desugaring on backported methods.
  private final BackportedMethodRewriter backportedMethodRewriter;
  private final DesugaredLibraryRetargeter desugaredLibraryRetargeter;

  /** Defines a minor variation in desugaring. */
  public enum Flavor {
    /** Process all application resources. */
    IncludeAllResources,
    /** Process all but DEX application resources. */
    ExcludeDexResources
  }

  // Constructor for cf to cf desugaring.
  public InterfaceMethodRewriter(
      AppView<?> appView,
      BackportedMethodRewriter rewriter,
      DesugaredLibraryRetargeter desugaredLibraryRetargeter) {
    this.appView = appView;
    this.backportedMethodRewriter = rewriter;
    this.desugaredLibraryRetargeter = desugaredLibraryRetargeter;
    this.options = appView.options();
    this.factory = appView.dexItemFactory();
    this.helper = new InterfaceDesugaringSyntheticHelper(appView);
    initializeEmulatedInterfaceVariables();
  }

  // Constructor for IR desugaring.
  public InterfaceMethodRewriter(AppView<?> appView, IRConverter converter) {
    assert converter != null;
    this.appView = appView;
    this.backportedMethodRewriter = null;
    this.desugaredLibraryRetargeter = null;
    this.options = appView.options();
    this.factory = appView.dexItemFactory();
    this.helper = new InterfaceDesugaringSyntheticHelper(appView);
    initializeEmulatedInterfaceVariables();
  }

  public static void checkForAssumedLibraryTypes(AppInfo appInfo, InternalOptions options) {
    DesugaredLibraryConfiguration config = options.desugaredLibraryConfiguration;
    BiConsumer<DexType, DexType> registerEntry = registerMapEntry(appInfo);
    config.getEmulateLibraryInterface().forEach(registerEntry);
    config.getCustomConversions().forEach(registerEntry);
    config.getRetargetCoreLibMember().forEach((method, types) -> types.forEach(registerEntry));
  }

  private static BiConsumer<DexType, DexType> registerMapEntry(AppInfo appInfo) {
    return (key, value) -> {
      registerType(appInfo, key);
      registerType(appInfo, value);
    };
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
    Map<DexType, DexType> emulateLibraryInterface =
        options.desugaredLibraryConfiguration.getEmulateLibraryInterface();
    for (DexType interfaceType : emulateLibraryInterface.keySet()) {
      addRewriteRulesForEmulatedInterface(
          interfaceType, emulateLibraryInterface.get(interfaceType).toSourceString());
      DexClass emulatedInterfaceClass = appView.definitionFor(interfaceType);
      if (emulatedInterfaceClass != null) {
        for (DexEncodedMethod encodedMethod :
            emulatedInterfaceClass.methods(DexEncodedMethod::isDefaultMethod)) {
          emulatedMethods.add(encodedMethod.getReference().name);
        }
      }
    }
  }

  void addRewriteRulesForEmulatedInterface(
      DexType emulatedInterface, String rewrittenEmulatedInterface) {
    addCompanionClassRewriteRule(emulatedInterface, rewrittenEmulatedInterface);
    appView.rewritePrefix.rewriteType(
        InterfaceDesugaringSyntheticHelper.getEmulateLibraryInterfaceClassType(
            emulatedInterface, factory),
        factory.createType(
            DescriptorUtils.javaTypeToDescriptor(
                rewrittenEmulatedInterface
                    + InterfaceDesugaringSyntheticHelper.EMULATE_LIBRARY_CLASS_NAME_SUFFIX)));
  }

  private void addCompanionClassRewriteRule(DexType interfaceType, String rewrittenType) {
    addCompanionClassRewriteRule(interfaceType, rewrittenType, appView);
  }

  static void addCompanionClassRewriteRule(
      DexType interfaceType, String rewrittenType, AppView<?> appView) {
    appView.rewritePrefix.rewriteType(
        InterfaceDesugaringSyntheticHelper.getCompanionClassType(
            interfaceType, appView.dexItemFactory()),
        appView
            .dexItemFactory()
            .createType(
                DescriptorUtils.javaTypeToDescriptor(
                    rewrittenType
                        + InterfaceDesugaringSyntheticHelper.COMPANION_CLASS_NAME_SUFFIX)));
  }

  private boolean needsRewriting(DexMethod method, Type invokeType, ProgramMethod context) {
    return !isSyntheticMethodThatShouldNotBeDoubleProcessed(context)
        && invokeNeedsRewriting(method, invokeType);
  }

  private boolean invokeNeedsRewriting(DexMethod method, Type invokeType) {
    // TODO(b/187913003): Refactor the implementation of needsDesugaring and desugarInstruction so
    //  that the identification is shared and thus guaranteed to be equivalent.
    if (invokeType == SUPER || invokeType == STATIC || invokeType == DIRECT) {
      DexClass clazz = appView.appInfo().definitionFor(method.getHolderType());
      if (clazz != null && clazz.isInterface()) {
        return true;
      }
      return emulatedMethods.contains(method.getName());
    }
    if (invokeType == VIRTUAL || invokeType == INTERFACE) {
      // A virtual dispatch can target a private method, on self or on a nest mate.
      AppInfoWithClassHierarchy appInfoForDesugaring = appView.appInfoForDesugaring();
      SingleResolutionResult resolution =
          appInfoForDesugaring.resolveMethod(method, invokeType == INTERFACE).asSingleResolution();
      if (resolution != null
          && (resolution.getResolvedMethod().isPrivate()
              || resolution.getResolvedMethod().isStatic())) {
        return true;
      }
      return defaultMethodForEmulatedDispatchOrNull(method, invokeType == INTERFACE) != null;
    }
    return true;
  }

  private boolean isAlreadyDesugared(CfInvoke invoke, ProgramMethod context) {
    // In Cf to Cf it is forbidden to desugar twice the same instruction, if the backported
    // method rewriter or the desugared library retargeter already desugar the instruction, they
    // take precedence and nothing has to be done here.
    return (backportedMethodRewriter != null
            && backportedMethodRewriter.needsDesugaring(invoke, context))
        || (desugaredLibraryRetargeter != null
            && desugaredLibraryRetargeter.needsDesugaring(invoke, context));
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
    if (isSyntheticMethodThatShouldNotBeDoubleProcessed(context)) {
      leavingStaticInvokeToInterface(context);
      return;
    }
    CfCode code = context.getDefinition().getCode().asCfCode();
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction.isInvokeDynamic()
          && !LambdaInstructionDesugaring.isLambdaInvoke(instruction, context, appView)
          && !StringConcatInstructionDesugaring.isStringConcatInvoke(
              instruction, appView.dexItemFactory())) {
        reportInterfaceMethodHandleCallSite(instruction.asInvokeDynamic().getCallSite(), context);
        continue;
      }
      if (instruction.isInvoke()) {
        CfInvoke cfInvoke = instruction.asInvoke();
        if (isAlreadyDesugared(cfInvoke, context)) {
          continue;
        }
        if (cfInvoke.isInvokeStatic()) {
          scanInvokeStatic(cfInvoke, context);
        } else if (cfInvoke.isInvokeSpecial()) {
          scanInvokeDirectOrSuper(cfInvoke, context);
        }
      }
    }
  }

  private void scanInvokeDirectOrSuper(CfInvoke cfInvoke, ProgramMethod context) {
    if (cfInvoke.isInvokeConstructor(factory)) {
      return;
    }
    DexMethod invokedMethod = cfInvoke.getMethod();
    DexClass clazz = appView.definitionFor(invokedMethod.holder, context);
    if (clazz == null) {
      // NOTE: For invoke-super, this leaves unchanged those calls to undefined targets.
      // This may lead to runtime exception but we can not report it as error since it can also be
      // the intended behavior.
      // For invoke-direct, this reports the missing class since we don't know if it is an
      // interface.
      warnMissingType(context, invokedMethod.holder);
    }
  }

  private void scanInvokeStatic(CfInvoke cfInvoke, ProgramMethod context) {
    DexMethod invokedMethod = cfInvoke.getMethod();
    DexClass clazz = appView.definitionFor(invokedMethod.holder, context);
    if (clazz == null) {
      // NOTE: leave unchanged those calls to undefined targets. This may lead to runtime
      // exception but we can not report it as error since it can also be the intended
      // behavior.
      if (cfInvoke.isInterface()) {
        leavingStaticInvokeToInterface(context);
        warnMissingType(context, invokedMethod.holder);
      }
      return;
    }

    if (!clazz.isInterface()) {
      if (cfInvoke.isInterface()) {
        leavingStaticInvokeToInterface(context);
      }
      return;
    }

    if (isNonDesugaredLibraryClass(clazz)) {
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
        leavingStaticInvokeToInterface(context);
      }
    }
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    if (instruction.isInvoke()) {
      CfInvoke cfInvoke = instruction.asInvoke();
      if (isAlreadyDesugared(cfInvoke, context)) {
        return false;
      }
      return needsRewriting(cfInvoke.getMethod(), cfInvoke.getInvokeType(context), context);
    }
    return false;
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
    if (!instruction.isInvoke() || isSyntheticMethodThatShouldNotBeDoubleProcessed(context)) {
      return null;
    }
    CfInvoke invoke = instruction.asInvoke();
    if (isAlreadyDesugared(invoke, context)) {
      return null;
    }

    Function<DexMethod, Collection<CfInstruction>> rewriteInvoke =
        (newTarget) ->
            Collections.singletonList(
                new CfInvoke(org.objectweb.asm.Opcodes.INVOKESTATIC, newTarget, false));

    Function<SingleResolutionResult, Collection<CfInstruction>> rewriteToThrow =
        (resolutionResult) ->
            rewriteInvokeToThrowCf(
                invoke,
                resolutionResult,
                localStackAllocator,
                eventConsumer,
                context,
                methodProcessingContext);

    if (invoke.isInvokeVirtual() || invoke.isInvokeInterface()) {
      AppInfoWithClassHierarchy appInfoForDesugaring = appView.appInfoForDesugaring();
      SingleResolutionResult resolution =
          appInfoForDesugaring
              .resolveMethod(invoke.getMethod(), invoke.isInterface())
              .asSingleResolution();
      if (resolution != null
          && resolution.getResolvedMethod().isPrivate()
          && resolution.isAccessibleFrom(context, appInfoForDesugaring).isTrue()) {
        // TODO(b/198267586): What about the private in-accessible case?
        return rewriteInvokeDirect(invoke.getMethod(), context, rewriteInvoke, eventConsumer);
      }
      if (resolution != null && resolution.getResolvedMethod().isStatic()) {
        return rewriteToThrow.apply(resolution);
      }
      // TODO(b/198267586): What about an invoke <init>?
      return rewriteInvokeInterfaceOrInvokeVirtual(
          invoke.getMethod(), invoke.isInterface(), rewriteInvoke, eventConsumer);
    }
    if (invoke.isInvokeStatic()) {
      Consumer<ProgramMethod> staticOutliningMethodConsumer =
          staticOutliningMethod -> {
            synthesizedMethods.add(staticOutliningMethod);
            eventConsumer.acceptInvokeStaticInterfaceOutliningMethod(
                staticOutliningMethod, context);
          };
      // TODO(b/192439456): Make a test to prove resolution is needed here and fix it.
      return rewriteInvokeStatic(
          invoke,
          methodProcessingContext,
          context,
          staticOutliningMethodConsumer,
          rewriteInvoke,
          rewriteToThrow,
          eventConsumer);
    }
    assert invoke.isInvokeSpecial();
    if (invoke.isInvokeSuper(context.getHolderType())) {
      return rewriteInvokeSuper(
          invoke.getMethod(), context, rewriteInvoke, rewriteToThrow, eventConsumer);
    }
    return rewriteInvokeDirect(invoke.getMethod(), context, rewriteInvoke, eventConsumer);
  }

  private Collection<CfInstruction> rewriteInvokeToThrowCf(
      CfInvoke invoke,
      SingleResolutionResult resolutionResult,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    if (isAlreadyDesugared(invoke, context)) {
      return null;
    }

    MethodSynthesizerConsumer methodSynthesizerConsumer;
    if (resolutionResult == null) {
      methodSynthesizerConsumer =
          UtilityMethodsForCodeOptimizations::synthesizeThrowNoSuchMethodErrorMethod;
    } else if (resolutionResult.getResolvedMethod().isStatic() != invoke.isInvokeStatic()) {
      methodSynthesizerConsumer =
          UtilityMethodsForCodeOptimizations::synthesizeThrowIncompatibleClassChangeErrorMethod;
    } else {
      assert false;
      return null;
    }

    assert needsDesugaring(invoke, context);

    // Replace the entire effect of the invoke by by call to the throwing helper:
    //   ...
    //   invoke <method> [receiver] args*
    // =>
    //   ...
    //   (pop arg)*
    //   [pop receiver]
    //   invoke <throwing-method>
    //   pop exception result
    //   [push fake result for <method>]
    UtilityMethodForCodeOptimizations throwMethod =
        methodSynthesizerConsumer.synthesizeMethod(appView, methodProcessingContext);
    ProgramMethod throwProgramMethod = throwMethod.uncheckedGetMethod();
    eventConsumer.acceptThrowMethod(throwProgramMethod, context);

    ArrayList<CfInstruction> replacement = new ArrayList<>();
    DexTypeList parameters = invoke.getMethod().getParameters();
    for (int i = parameters.values.length - 1; i >= 0; i--) {
      replacement.add(
          new CfStackInstruction(
              parameters.get(i).isWideType()
                  ? CfStackInstruction.Opcode.Pop2
                  : CfStackInstruction.Opcode.Pop));
    }
    if (!invoke.isInvokeStatic()) {
      replacement.add(new CfStackInstruction(CfStackInstruction.Opcode.Pop));
    }

    CfInvoke throwInvoke =
        new CfInvoke(
            org.objectweb.asm.Opcodes.INVOKESTATIC, throwProgramMethod.getReference(), false);
    assert throwInvoke.getMethod().getReturnType().isClassType();
    replacement.add(throwInvoke);
    replacement.add(new CfStackInstruction(CfStackInstruction.Opcode.Pop));

    DexType returnType = invoke.getMethod().getReturnType();
    if (returnType != factory.voidType) {
      replacement.add(
          returnType.isPrimitiveType()
              ? new CfConstNumber(0, ValueType.fromDexType(returnType))
              : new CfConstNull());
    } else {
      // If the return type is void, the stack may need an extra slot to fit the return type of
      // the call to the throwing method.
      localStackAllocator.allocateLocalStack(1);
    }
    return replacement;
  }

  private void leavingStaticInvokeToInterface(ProgramMethod method) {
    // When leaving static interface method invokes possibly upgrade the class file
    // version, but don't go above the initial class file version. If the input was
    // 1.7 or below, this will make a VerificationError on the input a VerificationError
    // on the output. If the input was 1.8 or above the runtime behaviour (potential ICCE)
    // will remain the same.
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

  private Collection<CfInstruction> rewriteInvokeDirect(
      DexMethod invokedMethod,
      ProgramMethod context,
      Function<DexMethod, Collection<CfInstruction>> rewriteInvoke,
      InterfaceMethodDesugaringEventConsumer eventConsumer) {
    if (factory.isConstructor(invokedMethod)) {
      return null;
    }

    DexClass clazz = appView.definitionForHolder(invokedMethod, context);
    if (clazz == null) {
      // Report missing class since we don't know if it is an interface.
      warnMissingType(context, invokedMethod.holder);
      return null;
    }

    if (!clazz.isInterface()) {
      return null;
    }

    if (clazz.isLibraryClass()) {
      throw new CompilationError(
          "Unexpected call to a private method "
              + "defined in library class "
              + clazz.toSourceString(),
          getMethodOrigin(context.getReference()));
    }

    DexClassAndMethod directTarget = clazz.lookupClassMethod(invokedMethod);
    if (directTarget != null) {
      // This can be a private instance method call. Note that the referenced
      // method is expected to be in the current class since it is private, but desugaring
      // may move some methods or their code into other classes.
      assert invokeNeedsRewriting(invokedMethod, DIRECT);
      DexClassAndMethod companionMethodDefinition = null;
      DexMethod companionMethod;
      if (directTarget.getDefinition().isPrivateMethod()) {
        if (directTarget.isProgramMethod()) {
          companionMethodDefinition =
              helper.ensurePrivateAsMethodOfProgramCompanionClassStub(
                  directTarget.asProgramMethod());
          companionMethod = companionMethodDefinition.getReference();
        } else {
          // TODO(b/183998768): Why does this not create a stub on the class path?
          companionMethod = helper.privateAsMethodOfCompanionClass(directTarget);
        }
      } else {
        companionMethodDefinition = helper.ensureDefaultAsMethodOfCompanionClassStub(directTarget);
        companionMethod = companionMethodDefinition.getReference();
      }
      if (companionMethodDefinition != null) {
        acceptCompanionMethod(directTarget, companionMethodDefinition, eventConsumer);
      }
      return rewriteInvoke.apply(companionMethod);
    } else {
      // The method can be a default method in the interface hierarchy.
      DexClassAndMethod virtualTarget =
          appView.appInfoForDesugaring().lookupMaximallySpecificMethod(clazz, invokedMethod);
      if (virtualTarget != null) {
        // This is a invoke-direct call to a virtual method.
        assert invokeNeedsRewriting(invokedMethod, DIRECT);
        DexClassAndMethod companionMethod =
            helper.ensureDefaultAsMethodOfCompanionClassStub(virtualTarget);
        acceptCompanionMethod(virtualTarget, companionMethod, eventConsumer);
        return rewriteInvoke.apply(companionMethod.getReference());
      } else {
        // The below assert is here because a well-type program should have a target, but we
        // cannot throw a compilation error, since we have no knowledge about the input.
        assert false;
      }
    }
    return null;
  }

  private void acceptCompanionMethod(
      DexClassAndMethod method,
      DexClassAndMethod companion,
      InterfaceMethodDesugaringEventConsumer eventConsumer) {
    assert method.isProgramMethod() == companion.isProgramMethod();
    if (method.isProgramMethod()) {
      eventConsumer.acceptCompanionMethod(method.asProgramMethod(), companion.asProgramMethod());
    }
  }

  private Collection<CfInstruction> rewriteInvokeStatic(
      CfInvoke invoke,
      MethodProcessingContext methodProcessingContext,
      ProgramMethod context,
      Consumer<ProgramMethod> staticOutliningMethodConsumer,
      Function<DexMethod, Collection<CfInstruction>> rewriteInvoke,
      Function<SingleResolutionResult, Collection<CfInstruction>> rewriteToThrow,
      CfInstructionDesugaringEventConsumer eventConsumer) {
    DexMethod invokedMethod = invoke.getMethod();
    boolean interfaceBit = invoke.isInterface();
    if (appView.getSyntheticItems().isPendingSynthetic(invokedMethod.holder)) {
      // We did not create this code yet, but it will not require rewriting.
      return null;
    }

    DexClass clazz = appView.definitionFor(invokedMethod.holder, context);
    if (clazz == null) {
      // NOTE: leave unchanged those calls to undefined targets. This may lead to runtime
      // exception but we can not report it as error since it can also be the intended
      // behavior.
      if (interfaceBit) {
        leavingStaticInvokeToInterface(context);
      }
      warnMissingType(context, invokedMethod.holder);
      return null;
    }

    if (!clazz.isInterface()) {
      if (interfaceBit) {
        leavingStaticInvokeToInterface(context);
      }
      return null;
    }

    if (isNonDesugaredLibraryClass(clazz)) {
      // NOTE: we intentionally don't desugar static calls into static interface
      // methods coming from android.jar since it is only possible in case v24+
      // version of android.jar is provided.
      //
      // We assume such calls are properly guarded by if-checks like
      //    'if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.XYZ) { ... }'
      //
      // WARNING: This may result in incorrect code on older platforms!
      // Retarget call to an appropriate method of companion class.

      if (!options.canLeaveStaticInterfaceMethodInvokes()) {
        // On pre-L devices static calls to interface methods result in verifier
        // rejecting the whole class. We have to create special dispatch classes,
        // so the user class is not rejected because it make this call directly.
        // TODO(b/166247515): If this an incorrect invoke-static without the interface bit
        //  we end up "fixing" the code and remove and ICCE error.
        if (synthesizedMethods.contains(context)) {
          // When reprocessing the method generated below, the desugaring asserts this method
          // does not need any new desugaring, while the interface method rewriter tries
          // to outline again the invoke-static. Just do nothing instead.
          return null;
        }
        if (isAlreadyDesugared(invoke, context)) {
          return null;
        }
        ProgramMethod newProgramMethod =
            appView
                .getSyntheticItems()
                .createMethod(
                    SyntheticNaming.SyntheticKind.STATIC_INTERFACE_CALL,
                    methodProcessingContext.createUniqueContext(),
                    appView,
                    syntheticMethodBuilder ->
                        syntheticMethodBuilder
                            .setProto(invokedMethod.proto)
                            .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                            .setCode(
                                m ->
                                    ForwardMethodBuilder.builder(factory)
                                        .setStaticTarget(invokedMethod, true)
                                        .setStaticSource(m)
                                        .build()));
        staticOutliningMethodConsumer.accept(newProgramMethod);
        assert invokeNeedsRewriting(invokedMethod, STATIC);
        // The synthetic dispatch class has static interface method invokes, so set
        // the class file version accordingly.
        leavingStaticInvokeToInterface(newProgramMethod);
        return rewriteInvoke.apply(newProgramMethod.getReference());
      } else {
        // When leaving static interface method invokes upgrade the class file version.
        leavingStaticInvokeToInterface(context);
      }
      return null;
    }

    SingleResolutionResult resolutionResult =
        appView
            .appInfoForDesugaring()
            .resolveMethodOnInterface(clazz, invokedMethod)
            .asSingleResolution();
    if (clazz.isInterface() && shouldRewriteToInvokeToThrow(resolutionResult, true)) {
      assert invokeNeedsRewriting(invokedMethod, STATIC);
      return rewriteToThrow.apply(resolutionResult);
    }

    assert resolutionResult != null;
    assert resolutionResult.getResolvedMethod().isStatic();
    assert invokeNeedsRewriting(invokedMethod, STATIC);
    DexClassAndMethod method = resolutionResult.getResolutionPair();
    DexClassAndMethod companionMethod =
        helper.ensureStaticAsMethodOfCompanionClassStub(method, eventConsumer);
    return rewriteInvoke.apply(companionMethod.getReference());
  }

  private Collection<CfInstruction> rewriteInvokeSuper(
      DexMethod invokedMethod,
      ProgramMethod context,
      Function<DexMethod, Collection<CfInstruction>> rewriteInvoke,
      Function<SingleResolutionResult, Collection<CfInstruction>> rewriteToThrow,
      InterfaceMethodDesugaringEventConsumer eventConsumer) {
    DexClass clazz = appView.definitionFor(invokedMethod.holder, context);
    if (clazz == null) {
      // NOTE: leave unchanged those calls to undefined targets. This may lead to runtime
      // exception but we can not report it as error since it can also be the intended
      // behavior.
      warnMissingType(context, invokedMethod.holder);
      return null;
    }

    SingleResolutionResult resolutionResult =
        appView.appInfoForDesugaring().resolveMethodOn(clazz, invokedMethod).asSingleResolution();
    if (clazz.isInterface() && shouldRewriteToInvokeToThrow(resolutionResult, false)) {
      assert invokeNeedsRewriting(invokedMethod, SUPER);
      return rewriteToThrow.apply(resolutionResult);
    }

    if (clazz.isInterface() && !clazz.isLibraryClass()) {
      // NOTE: we intentionally don't desugar super calls into interface methods
      // coming from android.jar since it is only possible in case v24+ version
      // of android.jar is provided.
      //
      // We assume such calls are properly guarded by if-checks like
      //    'if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.XYZ) { ... }'
      //
      // WARNING: This may result in incorrect code on older platforms!
      // Retarget call to an appropriate method of companion class.
      assert invokeNeedsRewriting(invokedMethod, SUPER);
      if (resolutionResult.getResolvedMethod().isPrivateMethod()) {
        if (resolutionResult.isAccessibleFrom(context, appView.appInfoForDesugaring()).isFalse()) {
          // TODO(b/145775365): This should throw IAE.
          return rewriteToThrow.apply(null);
        }
        DexClassAndMethod method = resolutionResult.getResolutionPair();
        DexMethod companionMethod;
        if (method.isProgramMethod()) {
          ProgramMethod companionMethodDefinition =
              helper.ensurePrivateAsMethodOfProgramCompanionClassStub(method.asProgramMethod());
          companionMethod = companionMethodDefinition.getReference();
          eventConsumer.acceptCompanionMethod(method.asProgramMethod(), companionMethodDefinition);
        } else {
          companionMethod = helper.privateAsMethodOfCompanionClass(method);
        }
        return rewriteInvoke.apply(companionMethod);
      } else {
        DexClassAndMethod method = resolutionResult.getResolutionPair();
        // TODO(b/183998768): Why do this amend routine. We have done resolution, so would that
        //  not be the correct target!? I think this is just legacy from before resolution was
        //  implemented in full.
        DexMethod amendedMethod = amendDefaultMethod(context.getHolder(), invokedMethod);
        assert method.getReference() == amendedMethod;
        DexClassAndMethod companionMethod =
            helper.ensureDefaultAsMethodOfCompanionClassStub(method);
        acceptCompanionMethod(method, companionMethod, eventConsumer);
        return rewriteInvoke.apply(
            InterfaceDesugaringSyntheticHelper.defaultAsMethodOfCompanionClass(
                amendedMethod, appView.dexItemFactory()));
      }
    }

    DexType emulatedItf = maximallySpecificEmulatedInterfaceOrNull(invokedMethod);
    if (emulatedItf == null) {
      if (clazz.isInterface() && appView.rewritePrefix.hasRewrittenType(clazz.type, appView)) {
        DexClassAndMethod target =
            appView.appInfoForDesugaring().lookupSuperTarget(invokedMethod, context);
        if (target != null && target.getDefinition().isDefaultMethod()) {
          DexClass holder = target.getHolder();
          if (holder.isLibraryClass() && holder.isInterface()) {
            assert invokeNeedsRewriting(invokedMethod, SUPER);
            DexClassAndMethod companionTarget =
                helper.ensureDefaultAsMethodOfCompanionClassStub(target);
            acceptCompanionMethod(target, companionTarget, eventConsumer);
            return rewriteInvoke.apply(companionTarget.getReference());
          }
        }
      }
      return null;
    }
    // That invoke super may not resolve since the super method may not be present
    // since it's in the emulated interface. We need to force resolution. If it resolves
    // to a library method, then it needs to be rewritten.
    // If it resolves to a program overrides, the invoke-super can remain.
    DexClassAndMethod superTarget =
        appView.appInfoForDesugaring().lookupSuperTarget(invokedMethod, context);
    if (superTarget != null && superTarget.isLibraryMethod()) {
      assert invokeNeedsRewriting(invokedMethod, SUPER);
      // Rewriting is required because the super invoke resolves into a missing
      // method (method is on desugared library). Find out if it needs to be
      // retargeted or if it just calls a companion class method and rewrite.
      DexMethod retargetMethod =
          options.desugaredLibraryConfiguration.retargetMethod(superTarget, appView);
      if (retargetMethod != null) {
        return rewriteInvoke.apply(retargetMethod);
      }
      DexClassAndMethod emulatedMethod =
          superTarget.getReference().lookupMemberOnClass(appView.definitionFor(emulatedItf));
      if (emulatedMethod == null) {
        assert false;
        return null;
      }
      DexClassAndMethod companionMethod =
          helper.ensureDefaultAsMethodOfCompanionClassStub(emulatedMethod);
      return rewriteInvoke.apply(companionMethod.getReference());
    }
    return null;
  }

  private DexClassAndMethod defaultMethodForEmulatedDispatchOrNull(
      DexMethod invokedMethod, boolean interfaceBit) {
    DexType emulatedItf = maximallySpecificEmulatedInterfaceOrNull(invokedMethod);
    if (emulatedItf == null) {
      return null;
    }
    // The call potentially ends up in a library class, in which case we need to rewrite, since the
    // code may be in the desugared library.
    SingleResolutionResult resolution =
        appView
            .appInfoForDesugaring()
            .resolveMethod(invokedMethod, interfaceBit)
            .asSingleResolution();
    if (resolution != null
        && (resolution.getResolvedHolder().isLibraryClass()
            || helper.isEmulatedInterface(resolution.getResolvedHolder().type))) {
      DexClassAndMethod defaultMethod =
          appView.definitionFor(emulatedItf).lookupClassMethod(invokedMethod);
      if (defaultMethod != null && !helper.dontRewrite(defaultMethod)) {
        assert !defaultMethod.getAccessFlags().isAbstract();
        return defaultMethod;
      }
    }
    return null;
  }

  private Collection<CfInstruction> rewriteInvokeInterfaceOrInvokeVirtual(
      DexMethod invokedMethod,
      boolean interfaceBit,
      Function<DexMethod, Collection<CfInstruction>> rewriteInvoke,
      CfInstructionDesugaringEventConsumer eventConsumer) {
    DexClassAndMethod defaultMethod =
        defaultMethodForEmulatedDispatchOrNull(invokedMethod, interfaceBit);
    if (defaultMethod != null) {
      return rewriteInvoke.apply(
          helper.ensureEmulatedInterfaceMethod(defaultMethod, eventConsumer).getReference());
    }
    return null;
  }

  private boolean shouldRewriteToInvokeToThrow(
      SingleResolutionResult resolutionResult, boolean isInvokeStatic) {
    return resolutionResult == null
        || resolutionResult.getResolvedMethod().isStatic() != isInvokeStatic;
  }

  private DexType maximallySpecificEmulatedInterfaceOrNull(DexMethod invokedMethod) {
    // Here we try to avoid doing the expensive look-up on all invokes.
    if (!emulatedMethods.contains(invokedMethod.name)) {
      return null;
    }
    DexClass dexClass = appView.definitionFor(invokedMethod.holder);
    // We cannot rewrite the invoke we do not know what the class is.
    if (dexClass == null) {
      return null;
    }
    DexEncodedMethod singleTarget = null;
    if (dexClass.isInterface()) {
      // Look for exact method on the interface.
      singleTarget = dexClass.lookupMethod(invokedMethod);
    }
    if (singleTarget == null) {
      DexClassAndMethod result =
          appView.appInfoForDesugaring().lookupMaximallySpecificMethod(dexClass, invokedMethod);
      if (result != null) {
        singleTarget = result.getDefinition();
      }
    }
    if (singleTarget == null) {
      // At this point we are in a library class. Failures can happen with NoSuchMethod if a
      // library class implement a method with same signature but not related to emulated
      // interfaces.
      return null;
    }
    if (!singleTarget.isAbstract() && helper.isEmulatedInterface(singleTarget.getHolderType())) {
      return singleTarget.getHolderType();
    }
    return null;
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

  public InterfaceMethodProcessorFacade getPostProcessingDesugaringD8(Flavor flavour) {
    return new InterfaceMethodProcessorFacade(appView, flavour, m -> true);
  }

  public InterfaceMethodProcessorFacade getPostProcessingDesugaringR8(
      Flavor flavour,
      Predicate<ProgramMethod> isLiveMethod,
      InterfaceProcessor interfaceProcessor) {
    return new InterfaceMethodProcessorFacade(appView, flavour, isLiveMethod, interfaceProcessor);
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
      reportDependencyEdge(implementing.asProgramClass(), definedInterface, appView.appInfo());
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

  public static void reportDependencyEdge(
      DexClass dependent, DexClass dependency, AppInfo appInfo) {
    assert !dependent.isLibraryClass();
    assert !dependency.isLibraryClass();
    DesugarGraphConsumer consumer = appInfo.app().options.desugarGraphConsumer;
    if (consumer != null) {
      Origin dependencyOrigin = dependency.getOrigin();
      java.util.Collection<DexType> dependents =
          appInfo.getSyntheticItems().getSynthesizingContextTypes(dependent.getType());
      if (dependents.isEmpty()) {
        reportDependencyEdge(consumer, dependencyOrigin, dependent);
      } else {
        for (DexType type : dependents) {
          reportDependencyEdge(consumer, dependencyOrigin, appInfo.definitionFor(type));
        }
      }
    }
  }

  private static void reportDependencyEdge(
      DesugarGraphConsumer consumer, Origin dependencyOrigin, DexClass clazz) {
    Origin dependentOrigin = clazz.getOrigin();
    if (dependentOrigin != dependencyOrigin) {
      consumer.accept(dependentOrigin, dependencyOrigin);
    }
  }
}
