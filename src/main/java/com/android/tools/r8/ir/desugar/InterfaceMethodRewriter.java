// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import static com.android.tools.r8.ir.code.Invoke.Type.DIRECT;
import static com.android.tools.r8.ir.code.Invoke.Type.STATIC;
import static com.android.tools.r8.ir.code.Invoke.Type.SUPER;
import static com.android.tools.r8.ir.code.Invoke.Type.VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_CUSTOM;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_SUPER;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.desugar.DesugaredLibraryRetargeter.getRetargetPackageAndClassPrefixDescriptor;
import static com.android.tools.r8.ir.desugar.DesugaredLibraryWrapperSynthesizer.TYPE_WRAPPER_SUFFIX;
import static com.android.tools.r8.ir.desugar.DesugaredLibraryWrapperSynthesizer.VIVIFIED_TYPE_WRAPPER_SUFFIX;

import com.android.tools.r8.DesugarGraphConsumer;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.GenericSignature;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodCollection;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeSuper;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.desugar.DefaultMethodsHelper.Collection;
import com.android.tools.r8.ir.desugar.InterfaceProcessor.InterfaceProcessorNestedGraphLens;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.MethodSynthesizerConsumer;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.UtilityMethodForCodeOptimizations;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.IteratorUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
public final class InterfaceMethodRewriter {

  // Public for testing.
  public static final String EMULATE_LIBRARY_CLASS_NAME_SUFFIX = "$-EL";
  public static final String COMPANION_CLASS_NAME_SUFFIX = "$-CC";
  public static final String DEFAULT_METHOD_PREFIX = "$default$";
  public static final String PRIVATE_METHOD_PREFIX = "$private$";

  private final AppView<?> appView;
  private final IRConverter converter;
  private final InternalOptions options;
  final DexItemFactory factory;
  private final Map<DexType, DexType> emulatedInterfaces;
  // The emulatedMethod set is there to avoid doing the emulated look-up too often.
  private final Set<DexString> emulatedMethods = Sets.newIdentityHashSet();

  // All forwarding methods generated during desugaring. We don't synchronize access
  // to this collection since it is only filled in ClassProcessor running synchronously.
  private final SortedProgramMethodSet synthesizedMethods = SortedProgramMethodSet.create();

  // Caches default interface method info for already processed interfaces.
  private final Map<DexType, DefaultMethodsHelper.Collection> cache = new ConcurrentHashMap<>();

  private final Predicate<DexType> shouldIgnoreFromReportsPredicate;
  /**
   * Defines a minor variation in desugaring.
   */
  public enum Flavor {
    /**
     * Process all application resources.
     */
    IncludeAllResources,
    /**
     * Process all but DEX application resources.
     */
    ExcludeDexResources
  }

  public InterfaceMethodRewriter(AppView<?> appView, IRConverter converter) {
    assert converter != null;
    this.appView = appView;
    this.converter = converter;
    this.options = appView.options();
    this.factory = appView.dexItemFactory();
    this.emulatedInterfaces = options.desugaredLibraryConfiguration.getEmulateLibraryInterface();
    this.shouldIgnoreFromReportsPredicate = getShouldIgnoreFromReportsPredicate(appView);
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

  private void initializeEmulatedInterfaceVariables() {
    Map<DexType, DexType> emulateLibraryInterface =
        options.desugaredLibraryConfiguration.getEmulateLibraryInterface();
    for (DexType interfaceType : emulateLibraryInterface.keySet()) {
      addRewritePrefix(interfaceType, emulateLibraryInterface.get(interfaceType).toSourceString());
      DexClass emulatedInterfaceClass = appView.definitionFor(interfaceType);
      if (emulatedInterfaceClass != null) {
        for (DexEncodedMethod encodedMethod :
            emulatedInterfaceClass.methods(DexEncodedMethod::isDefaultMethod)) {
          emulatedMethods.add(encodedMethod.getReference().name);
        }
      }
    }
  }

  private void addRewritePrefix(DexType interfaceType, String rewrittenType) {
    appView.rewritePrefix.rewriteType(
        getCompanionClassType(interfaceType),
        factory.createType(
            DescriptorUtils.javaTypeToDescriptor(rewrittenType + COMPANION_CLASS_NAME_SUFFIX)));
    appView.rewritePrefix.rewriteType(
        getEmulateLibraryInterfaceClassType(interfaceType, factory),
        factory.createType(
            DescriptorUtils.javaTypeToDescriptor(
                rewrittenType + EMULATE_LIBRARY_CLASS_NAME_SUFFIX)));
  }

  boolean isEmulatedInterface(DexType itf) {
    return emulatedInterfaces.containsKey(itf);
  }

  public boolean needsRewriting(DexMethod method, Type invokeType) {
    if (invokeType == SUPER || invokeType == STATIC || invokeType == DIRECT) {
      DexClass clazz = appView.appInfo().definitionFor(method.getHolderType());
      if (clazz != null && clazz.isInterface()) {
        return true;
      }
    }
    return emulatedMethods.contains(method.getName());
  }

  DexType getEmulatedInterface(DexType itf) {
    return emulatedInterfaces.get(itf);
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

  // Rewrites the references to static and default interface methods.
  // NOTE: can be called for different methods concurrently.
  public void rewriteMethodReferences(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    ProgramMethod context = code.context();
    if (synthesizedMethods.contains(context)) {
      return;
    }

    Set<Value> affectedValues = Sets.newIdentityHashSet();
    Set<BasicBlock> blocksToRemove = Sets.newIdentityHashSet();
    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      if (blocksToRemove.contains(block)) {
        continue;
      }
      InstructionListIterator instructions = block.listIterator(code);
      while (instructions.hasNext()) {
        Instruction instruction = instructions.next();
        switch (instruction.opcode()) {
          case INVOKE_CUSTOM:
            rewriteInvokeCustom(instruction.asInvokeCustom(), context);
            break;
          case INVOKE_DIRECT:
            rewriteInvokeDirect(instruction.asInvokeDirect(), instructions, context);
            break;
          case INVOKE_STATIC:
            rewriteInvokeStatic(
                instruction.asInvokeStatic(),
                code,
                blocks,
                instructions,
                affectedValues,
                blocksToRemove,
                methodProcessor,
                methodProcessingContext);
            break;
          case INVOKE_SUPER:
            rewriteInvokeSuper(
                instruction.asInvokeSuper(),
                code,
                blocks,
                instructions,
                affectedValues,
                blocksToRemove,
                methodProcessor,
                methodProcessingContext);
            break;
          case INVOKE_INTERFACE:
          case INVOKE_VIRTUAL:
            rewriteInvokeInterfaceOrInvokeVirtual(
                instruction.asInvokeMethodWithReceiver(), instructions);
            break;
          default:
            // Intentionally empty.
            break;
        }
      }
    }

    code.removeBlocks(blocksToRemove);

    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }

    assert code.isConsistentSSA();
  }

  private void rewriteInvokeCustom(InvokeCustom invoke, ProgramMethod context) {
    // Check that static interface methods are not referenced from invoke-custom instructions via
    // method handles.
    DexCallSite callSite = invoke.getCallSite();
    reportStaticInterfaceMethodHandle(context, callSite.bootstrapMethod);
    for (DexValue arg : callSite.bootstrapArgs) {
      if (arg.isDexValueMethodHandle()) {
        reportStaticInterfaceMethodHandle(context, arg.asDexValueMethodHandle().value);
      }
    }
  }

  private void rewriteInvokeDirect(
      InvokeDirect invoke, InstructionListIterator instructions, ProgramMethod context) {
    DexMethod method = invoke.getInvokedMethod();
    if (factory.isConstructor(method)) {
      return;
    }

    DexClass clazz = appView.definitionForHolder(method, context);
    if (clazz == null) {
      // Report missing class since we don't know if it is an interface.
      warnMissingType(context, method.holder);
      return;
    }

    if (!clazz.isInterface()) {
      return;
    }

    if (clazz.isLibraryClass()) {
      throw new CompilationError(
          "Unexpected call to a private method "
              + "defined in library class "
              + clazz.toSourceString(),
          getMethodOrigin(context.getReference()));
    }

    DexClassAndMethod directTarget = clazz.lookupClassMethod(method);
    if (directTarget != null) {
      // This can be a private instance method call. Note that the referenced
      // method is expected to be in the current class since it is private, but desugaring
      // may move some methods or their code into other classes.
      assert needsRewriting(method, DIRECT);
      instructions.replaceCurrentInstruction(
          new InvokeStatic(
              directTarget.getDefinition().isPrivateMethod()
                  ? privateAsMethodOfCompanionClass(directTarget)
                  : defaultAsMethodOfCompanionClass(directTarget),
              invoke.outValue(),
              invoke.arguments()));
    } else {
      // The method can be a default method in the interface hierarchy.
      DexClassAndMethod virtualTarget =
          appView.appInfoForDesugaring().lookupMaximallySpecificMethod(clazz, method);
      if (virtualTarget != null) {
        // This is a invoke-direct call to a virtual method.
        assert needsRewriting(method, DIRECT);
        instructions.replaceCurrentInstruction(
            new InvokeStatic(
                defaultAsMethodOfCompanionClass(virtualTarget),
                invoke.outValue(),
                invoke.arguments()));
      } else {
        // The below assert is here because a well-type program should have a target, but we
        // cannot throw a compilation error, since we have no knowledge about the input.
        assert false;
      }
    }
  }

  private void rewriteInvokeStatic(
      InvokeStatic invoke,
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator instructions,
      Set<Value> affectedValues,
      Set<BasicBlock> blocksToRemove,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (appView.getSyntheticItems().isPendingSynthetic(invokedMethod.holder)) {
      // We did not create this code yet, but it will not require rewriting.
      return;
    }

    ProgramMethod context = code.context();
    DexClass clazz = appView.definitionFor(invokedMethod.holder, context);
    if (clazz == null) {
      // NOTE: leave unchanged those calls to undefined targets. This may lead to runtime
      // exception but we can not report it as error since it can also be the intended
      // behavior.
      if (invoke.getInterfaceBit()) {
        leavingStaticInvokeToInterface(context);
      }
      warnMissingType(context, invokedMethod.holder);
      return;
    }

    if (!clazz.isInterface()) {
      if (invoke.getInterfaceBit()) {
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

      if (!options.canLeaveStaticInterfaceMethodInvokes()) {
        // On pre-L devices static calls to interface methods result in verifier
        // rejecting the whole class. We have to create special dispatch classes,
        // so the user class is not rejected because it make this call directly.
        // TODO(b/166247515): If this an incorrect invoke-static without the interface bit
        //  we end up "fixing" the code and remove and ICCE error.
        ProgramMethod newProgramMethod =
            appView
                .getSyntheticItems()
                .createMethod(
                    SyntheticNaming.SyntheticKind.STATIC_INTERFACE_CALL,
                    methodProcessingContext.createUniqueContext(),
                    factory,
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
        assert needsRewriting(invokedMethod, STATIC);
        instructions.replaceCurrentInstruction(
            new InvokeStatic(
                newProgramMethod.getReference(), invoke.outValue(), invoke.arguments()));
        synchronized (synthesizedMethods) {
          // The synthetic dispatch class has static interface method invokes, so set
          // the class file version accordingly.
          newProgramMethod.getDefinition().upgradeClassFileVersion(CfVersion.V1_8);
          synthesizedMethods.add(newProgramMethod);
        }
      } else {
        // When leaving static interface method invokes upgrade the class file version.
        context.getDefinition().upgradeClassFileVersion(CfVersion.V1_8);
      }
      return;
    }

    SingleResolutionResult resolutionResult =
        appView
            .appInfoForDesugaring()
            .resolveMethodOnInterface(clazz, invokedMethod)
            .asSingleResolution();
    if (clazz.isInterface()
        && rewriteInvokeToThrow(
            invoke,
            resolutionResult,
            code,
            blockIterator,
            instructions,
            affectedValues,
            blocksToRemove,
            methodProcessor,
            methodProcessingContext)) {
      assert needsRewriting(invoke.getInvokedMethod(), STATIC);
      return;
    }

    assert resolutionResult != null;
    assert resolutionResult.getResolvedMethod().isStatic();
    assert needsRewriting(invokedMethod, STATIC);

    instructions.replaceCurrentInstruction(
        new InvokeStatic(
            staticAsMethodOfCompanionClass(resolutionResult.getResolutionPair()),
            invoke.outValue(),
            invoke.arguments()));
  }

  private void rewriteInvokeSuper(
      InvokeSuper invoke,
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator instructions,
      Set<Value> affectedValues,
      Set<BasicBlock> blocksToRemove,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    ProgramMethod context = code.context();
    DexMethod invokedMethod = invoke.getInvokedMethod();
    DexClass clazz = appView.definitionFor(invokedMethod.holder, context);
    if (clazz == null) {
      // NOTE: leave unchanged those calls to undefined targets. This may lead to runtime
      // exception but we can not report it as error since it can also be the intended
      // behavior.
      warnMissingType(context, invokedMethod.holder);
      return;
    }

    SingleResolutionResult resolutionResult =
        appView.appInfoForDesugaring().resolveMethodOn(clazz, invokedMethod).asSingleResolution();
    if (clazz.isInterface()
        && rewriteInvokeToThrow(
            invoke,
            resolutionResult,
            code,
            blockIterator,
            instructions,
            affectedValues,
            blocksToRemove,
            methodProcessor,
            methodProcessingContext)) {
      assert needsRewriting(invoke.getInvokedMethod(), SUPER);
      return;
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
      assert needsRewriting(invokedMethod, SUPER);
      DexMethod amendedMethod = amendDefaultMethod(context.getHolder(), invokedMethod);
      instructions.replaceCurrentInstruction(
          new InvokeStatic(
              defaultAsMethodOfCompanionClass(amendedMethod, appView.dexItemFactory()),
              invoke.outValue(),
              invoke.arguments()));
    } else {
      DexType emulatedItf = maximallySpecificEmulatedInterfaceOrNull(invokedMethod);
      if (emulatedItf == null) {
        if (clazz.isInterface() && appView.rewritePrefix.hasRewrittenType(clazz.type, appView)) {
          DexClassAndMethod target =
              appView.appInfoForDesugaring().lookupSuperTarget(invokedMethod, context);
          if (target != null && target.getDefinition().isDefaultMethod()) {
            DexClass holder = target.getHolder();
            if (holder.isLibraryClass() && holder.isInterface()) {
              assert needsRewriting(invokedMethod, SUPER);
              instructions.replaceCurrentInstruction(
                  new InvokeStatic(
                      defaultAsMethodOfCompanionClass(target),
                      invoke.outValue(),
                      invoke.arguments()));
            }
          }
        }
      } else {
        // That invoke super may not resolve since the super method may not be present
        // since it's in the emulated interface. We need to force resolution. If it resolves
        // to a library method, then it needs to be rewritten.
        // If it resolves to a program overrides, the invoke-super can remain.
        DexClassAndMethod superTarget =
            appView.appInfoForDesugaring().lookupSuperTarget(invoke.getInvokedMethod(), context);
        if (superTarget != null && superTarget.isLibraryMethod()) {
          // Rewriting is required because the super invoke resolves into a missing
          // method (method is on desugared library). Find out if it needs to be
          // retarget or if it just calls a companion class method and rewrite.
          DexMethod retargetMethod =
              options.desugaredLibraryConfiguration.retargetMethod(superTarget, appView);
          if (retargetMethod == null) {
            DexMethod originalCompanionMethod = defaultAsMethodOfCompanionClass(superTarget);
            DexMethod companionMethod =
                factory.createMethod(
                    getCompanionClassType(emulatedItf),
                    factory.protoWithDifferentFirstParameter(
                        originalCompanionMethod.proto, emulatedItf),
                    originalCompanionMethod.name);
            assert needsRewriting(invokedMethod, SUPER);
            instructions.replaceCurrentInstruction(
                new InvokeStatic(companionMethod, invoke.outValue(), invoke.arguments()));
          } else {
            assert needsRewriting(invokedMethod, SUPER);
            instructions.replaceCurrentInstruction(
                new InvokeStatic(retargetMethod, invoke.outValue(), invoke.arguments()));
          }
        }
      }
    }
  }

  private void rewriteInvokeInterfaceOrInvokeVirtual(
      InvokeMethodWithReceiver invoke, InstructionListIterator instructions) {
    DexMethod invokedMethod = invoke.getInvokedMethod();
    DexType emulatedItf = maximallySpecificEmulatedInterfaceOrNull(invokedMethod);
    if (emulatedItf == null) {
      return;
    }

    // The call potentially ends up in a library class, in which case we need to rewrite, since the
    // code may be in the desugared library.
    SingleResolutionResult resolution =
        appView
            .appInfoForDesugaring()
            .resolveMethod(invokedMethod, invoke.getInterfaceBit())
            .asSingleResolution();
    if (resolution != null
        && (resolution.getResolvedHolder().isLibraryClass()
            || appView.options().isDesugaredLibraryCompilation())) {
      assert needsRewriting(invokedMethod, VIRTUAL);
      rewriteCurrentInstructionToEmulatedInterfaceCall(
          emulatedItf, invokedMethod, invoke, instructions);
    }
  }

  private boolean rewriteInvokeToThrow(
      InvokeMethod invoke,
      SingleResolutionResult resolutionResult,
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator instructions,
      Set<Value> affectedValues,
      Set<BasicBlock> blocksToRemove,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    MethodSynthesizerConsumer methodSynthesizerConsumer;
    if (resolutionResult == null) {
      methodSynthesizerConsumer =
          UtilityMethodsForCodeOptimizations::synthesizeThrowNoSuchMethodErrorMethod;
    } else if (resolutionResult.getResolvedMethod().isStatic() != invoke.isInvokeStatic()) {
      methodSynthesizerConsumer =
          UtilityMethodsForCodeOptimizations::synthesizeThrowIncompatibleClassChangeErrorMethod;
    } else {
      return false;
    }

    // Replace by throw new SomeException.
    UtilityMethodForCodeOptimizations throwMethod =
        methodSynthesizerConsumer.synthesizeMethod(appView, methodProcessingContext);
    throwMethod.optimize(methodProcessor);

    InvokeStatic throwInvoke =
        InvokeStatic.builder()
            .setMethod(throwMethod.getMethod())
            .setFreshOutValue(appView, code)
            .setPosition(invoke)
            .build();
    instructions.previous();

    // Split the block before the invoke instruction, and position the block iterator at the newly
    // created throw block (this involves rewinding the block iterator back over the blocks created
    // as a result of critical edge splitting, if any).
    BasicBlock throwBlock = instructions.splitCopyCatchHandlers(code, blockIterator, options);
    IteratorUtils.previousUntil(blockIterator, block -> block == throwBlock);
    blockIterator.next();

    // Insert the `SomeException e = throwSomeException()` invoke before the goto
    // instruction.
    instructions.previous();
    instructions.add(throwInvoke);

    // Insert the `throw e` instruction in the newly created throw block.
    InstructionListIterator throwBlockIterator = throwBlock.listIterator(code);
    throwBlockIterator.next();
    throwBlockIterator.replaceCurrentInstructionWithThrow(
        appView, code, blockIterator, throwInvoke.outValue(), blocksToRemove, affectedValues);
    return true;
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
    if (!singleTarget.isAbstract() && isEmulatedInterface(singleTarget.getHolderType())) {
      return singleTarget.getHolderType();
    }
    return null;
  }

  private void rewriteCurrentInstructionToEmulatedInterfaceCall(
      DexType emulatedItf,
      DexMethod invokedMethod,
      InvokeMethod invokeMethod,
      InstructionListIterator instructions) {
    DexClassAndMethod defaultMethod =
        appView.definitionFor(emulatedItf).lookupClassMethod(invokedMethod);
    if (defaultMethod != null && !dontRewrite(defaultMethod)) {
      assert !defaultMethod.getAccessFlags().isAbstract();
      instructions.replaceCurrentInstruction(
          new InvokeStatic(
              emulateInterfaceLibraryMethod(defaultMethod),
              invokeMethod.outValue(),
              invokeMethod.arguments()));
    }
  }

  private boolean isNonDesugaredLibraryClass(DexClass clazz) {
    return clazz.isLibraryClass() && !isInDesugaredLibrary(clazz);
  }

  boolean isInDesugaredLibrary(DexClass clazz) {
    assert clazz.isLibraryClass() || options.isDesugaredLibraryCompilation();
    if (emulatedInterfaces.containsKey(clazz.type)) {
      return true;
    }
    return appView.rewritePrefix.hasRewrittenType(clazz.type, appView);
  }

  boolean dontRewrite(DexClassAndMethod method) {
    for (Pair<DexType, DexString> dontRewrite :
        options.desugaredLibraryConfiguration.getDontRewriteInvocation()) {
      if (method.getHolderType() == dontRewrite.getFirst()
          && method.getName() == dontRewrite.getSecond()) {
        return true;
      }
    }
    return false;
  }

  private void warnMissingEmulatedInterface(DexType interfaceType) {
    StringDiagnostic warning =
        new StringDiagnostic(
            "Cannot emulate interface "
                + interfaceType.getName()
                + " because the interface is missing.");
    options.reporter.warning(warning);
  }

  private void generateEmulateInterfaceLibrary(Builder<?> builder) {
    // Emulated library interfaces should generate the Emulated Library EL dispatch class.
    Map<DexType, List<DexType>> emulatedInterfacesHierarchy = processEmulatedInterfaceHierarchy();
    AppInfo appInfo = appView.appInfo();
    for (DexType interfaceType : emulatedInterfaces.keySet()) {
      DexClass theInterface = appInfo.definitionFor(interfaceType);
      if (theInterface == null) {
        warnMissingEmulatedInterface(interfaceType);
      } else if (theInterface.isProgramClass()) {
        DexProgramClass theProgramInterface = theInterface.asProgramClass();
        DexProgramClass synthesizedClass =
            synthesizeEmulateInterfaceLibraryClass(
                theProgramInterface, emulatedInterfacesHierarchy);
        if (synthesizedClass != null) {
          builder.addSynthesizedClass(synthesizedClass);
          appInfo.addSynthesizedClass(synthesizedClass, theProgramInterface);
        }
      }
    }
  }

  private Map<DexType, List<DexType>> processEmulatedInterfaceHierarchy() {
    Map<DexType, List<DexType>> emulatedInterfacesHierarchy = new IdentityHashMap<>();
    Set<DexType> processed = Sets.newIdentityHashSet();
    ArrayList<DexType> emulatedInterfacesSorted = new ArrayList<>(emulatedInterfaces.keySet());
    emulatedInterfacesSorted.sort(DexType::compareTo);
    for (DexType interfaceType : emulatedInterfacesSorted) {
      processEmulatedInterfaceHierarchy(interfaceType, processed, emulatedInterfacesHierarchy);
    }
    return emulatedInterfacesHierarchy;
  }

  private void processEmulatedInterfaceHierarchy(
      DexType interfaceType,
      Set<DexType> processed,
      Map<DexType, List<DexType>> emulatedInterfacesHierarchy) {
    if (processed.contains(interfaceType)) {
      return;
    }
    emulatedInterfacesHierarchy.put(interfaceType, new ArrayList<>());
    processed.add(interfaceType);
    DexClass theInterface = appView.definitionFor(interfaceType);
    if (theInterface == null) {
      warnMissingEmulatedInterface(interfaceType);
      return;
    }
    LinkedList<DexType> workList = new LinkedList<>(Arrays.asList(theInterface.interfaces.values));
    while (!workList.isEmpty()) {
      DexType next = workList.removeLast();
      if (emulatedInterfaces.containsKey(next)) {
        processEmulatedInterfaceHierarchy(next, processed, emulatedInterfacesHierarchy);
        emulatedInterfacesHierarchy.get(next).add(interfaceType);
        DexClass nextClass = appView.definitionFor(next);
        if (nextClass != null) {
          workList.addAll(Arrays.asList(nextClass.interfaces.values));
        }
      }
    }
  }

  private boolean implementsInterface(DexClass clazz, DexType interfaceType) {
    LinkedList<DexType> workList = new LinkedList<>(Arrays.asList(clazz.interfaces.values));
    while (!workList.isEmpty()) {
      DexType next = workList.removeLast();
      if (interfaceType == next) {
        return true;
      }
      DexClass nextClass = appView.definitionFor(next);
      if (nextClass != null) {
        workList.addAll(Arrays.asList(nextClass.interfaces.values));
      }
    }
    return false;
  }

  private DexMethod emulateInterfaceLibraryMethod(DexClassAndMethod method) {
    return factory.createMethod(
        getEmulateLibraryInterfaceClassType(method.getHolderType(), factory),
        factory.prependTypeToProto(method.getHolderType(), method.getProto()),
        method.getName());
  }

  private DexProgramClass synthesizeEmulateInterfaceLibraryClass(
      DexProgramClass theInterface, Map<DexType, List<DexType>> emulatedInterfacesHierarchy) {
    List<DexEncodedMethod> emulationMethods = new ArrayList<>();
    theInterface.forEachProgramMethodMatching(
        DexEncodedMethod::isDefaultMethod,
        method -> {
          DexMethod libraryMethod =
              method.getReference().withHolder(emulatedInterfaces.get(theInterface.type), factory);
          DexMethod companionMethod =
              method.getAccessFlags().isStatic()
                  ? staticAsMethodOfCompanionClass(method)
                  : defaultAsMethodOfCompanionClass(method);

          // To properly emulate the library interface call, we need to compute the interfaces
          // inheriting from the interface and manually implement the dispatch with instance of.
          // The list guarantees that an interface is always after interfaces it extends,
          // hence reverse iteration.
          List<DexType> subInterfaces = emulatedInterfacesHierarchy.get(theInterface.type);
          List<Pair<DexType, DexMethod>> extraDispatchCases = new ArrayList<>();
          // In practice, there is usually a single case (except for tests),
          // so we do not bother to make the following loop more clever.
          Map<DexString, Map<DexType, DexType>> retargetCoreLibMember =
              options.desugaredLibraryConfiguration.getRetargetCoreLibMember();
          for (DexString methodName : retargetCoreLibMember.keySet()) {
            if (method.getName() == methodName) {
              for (DexType inType : retargetCoreLibMember.get(methodName).keySet()) {
                DexClass inClass = appView.definitionFor(inType);
                if (inClass != null && implementsInterface(inClass, theInterface.type)) {
                  extraDispatchCases.add(
                      new Pair<>(
                          inType,
                          factory.createMethod(
                              retargetCoreLibMember.get(methodName).get(inType),
                              factory.protoWithDifferentFirstParameter(
                                  companionMethod.proto, inType),
                              method.getName())));
                }
              }
            }
          }
          if (subInterfaces != null) {
            for (int i = subInterfaces.size() - 1; i >= 0; i--) {
              DexClass subInterfaceClass = appView.definitionFor(subInterfaces.get(i));
              assert subInterfaceClass
                  != null; // Else computation of subInterface would have failed.
              // if the method is implemented, extra dispatch is required.
              DexEncodedMethod result =
                  subInterfaceClass.lookupVirtualMethod(method.getReference());
              if (result != null && !result.isAbstract()) {
                extraDispatchCases.add(
                    new Pair<>(
                        subInterfaceClass.type,
                        factory.createMethod(
                            getCompanionClassType(subInterfaceClass.type),
                            factory.protoWithDifferentFirstParameter(
                                companionMethod.proto, subInterfaceClass.type),
                            companionMethod.name)));
              }
            }
          }
          emulationMethods.add(
              DexEncodedMethod.toEmulateDispatchLibraryMethod(
                  method.getHolderType(),
                  emulateInterfaceLibraryMethod(method),
                  companionMethod,
                  libraryMethod,
                  extraDispatchCases,
                  appView));
        });
    if (emulationMethods.isEmpty()) {
      return null;
    }
    DexType emulateLibraryClassType =
        getEmulateLibraryInterfaceClassType(theInterface.type, factory);
    ClassAccessFlags emulateLibraryClassFlags = theInterface.accessFlags.copy();
    emulateLibraryClassFlags.unsetAbstract();
    emulateLibraryClassFlags.unsetInterface();
    emulateLibraryClassFlags.unsetAnnotation();
    emulateLibraryClassFlags.setFinal();
    emulateLibraryClassFlags.setSynthetic();
    emulateLibraryClassFlags.setPublic();
    DexProgramClass clazz =
        new DexProgramClass(
            emulateLibraryClassType,
            null,
            new SynthesizedOrigin("interface desugaring (libs)", getClass()),
            emulateLibraryClassFlags,
            factory.objectType,
            DexTypeList.empty(),
            theInterface.sourceFile,
            null,
            Collections.emptyList(),
            null,
            Collections.emptyList(),
            ClassSignature.noSignature(),
            DexAnnotationSet.empty(),
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedField.EMPTY_ARRAY,
            // All synthesized methods are static in this case.
            emulationMethods.toArray(DexEncodedMethod.EMPTY_ARRAY),
            DexEncodedMethod.EMPTY_ARRAY,
            factory.getSkipNameValidationForTesting(),
            DexProgramClass::checksumFromType);
    clazz.forEachProgramMethod(synthesizedMethods::add);
    return clazz;
  }

  private static String getEmulateLibraryInterfaceClassDescriptor(String descriptor) {
    return descriptor.substring(0, descriptor.length() - 1)
        + EMULATE_LIBRARY_CLASS_NAME_SUFFIX
        + ";";
  }

  static DexType getEmulateLibraryInterfaceClassType(DexType type, DexItemFactory factory) {
    assert type.isClassType();
    String descriptor = type.descriptor.toString();
    String elTypeDescriptor = getEmulateLibraryInterfaceClassDescriptor(descriptor);
    return factory.createSynthesizedType(elTypeDescriptor);
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

  public static String getCompanionClassDescriptor(String descriptor) {
    return descriptor.substring(0, descriptor.length() - 1) + COMPANION_CLASS_NAME_SUFFIX + ";";
  }

  // Gets the companion class for the interface `type`.
  static DexType getCompanionClassType(DexType type, DexItemFactory factory) {
    assert type.isClassType();
    String descriptor = type.descriptor.toString();
    String ccTypeDescriptor = getCompanionClassDescriptor(descriptor);
    return factory.createSynthesizedType(ccTypeDescriptor);
  }

  DexType getCompanionClassType(DexType type) {
    return getCompanionClassType(type, factory);
  }

  // Checks if `type` is a companion class.
  public static boolean isCompanionClassType(DexType type) {
    return type.descriptor.toString().endsWith(COMPANION_CLASS_NAME_SUFFIX + ";");
  }

  public static boolean isEmulatedLibraryClassType(DexType type) {
    return type.descriptor.toString().endsWith(EMULATE_LIBRARY_CLASS_NAME_SUFFIX + ";");
  }

  // Gets the interface class for a companion class `type`.
  private DexType getInterfaceClassType(DexType type) {
    return getInterfaceClassType(type, factory);
  }

  // Gets the interface class for a companion class `type`.
  public static DexType getInterfaceClassType(DexType type, DexItemFactory factory) {
    assert isCompanionClassType(type);
    String descriptor = type.descriptor.toString();
    String interfaceTypeDescriptor = descriptor.substring(0,
        descriptor.length() - 1 - COMPANION_CLASS_NAME_SUFFIX.length()) + ";";
    return factory.createType(interfaceTypeDescriptor);
  }

  // Represent a static interface method as a method of companion class.
  final DexMethod staticAsMethodOfCompanionClass(DexClassAndMethod method) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexType companionClassType = getCompanionClassType(method.getHolderType(), dexItemFactory);
    DexMethod rewritten = method.getReference().withHolder(companionClassType, dexItemFactory);
    recordCompanionClassReference(appView, method, rewritten);
    return rewritten;
  }

  private static DexMethod instanceAsMethodOfCompanionClass(
      DexMethod method, String prefix, DexItemFactory factory) {
    // Add an implicit argument to represent the receiver.
    DexType[] params = method.proto.parameters.values;
    DexType[] newParams = new DexType[params.length + 1];
    newParams[0] = method.holder;
    System.arraycopy(params, 0, newParams, 1, params.length);

    // Add prefix to avoid name conflicts.
    return factory.createMethod(
        getCompanionClassType(method.holder, factory),
        factory.createProto(method.proto.returnType, newParams),
        factory.createString(prefix + method.name.toString()));
  }

  // It is possible that referenced method actually points to an interface which does
  // not define this default methods, but inherits it. We are making our best effort
  // to find an appropriate method, but still use the original one in case we fail.
  private DexMethod amendDefaultMethod(DexClass classToDesugar, DexMethod method) {
    DexMethod singleCandidate = getOrCreateInterfaceInfo(
        classToDesugar, classToDesugar, method.holder).getSingleCandidate(method);
    return singleCandidate != null ? singleCandidate : method;
  }

  // Represent a default interface method as a method of companion class.
  public static DexMethod defaultAsMethodOfCompanionClass(
      DexMethod method, DexItemFactory factory) {
    return instanceAsMethodOfCompanionClass(method, DEFAULT_METHOD_PREFIX, factory);
  }

  final DexMethod defaultAsMethodOfCompanionClass(DexClassAndMethod method) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexMethod rewritten = defaultAsMethodOfCompanionClass(method.getReference(), dexItemFactory);
    recordCompanionClassReference(appView, method, rewritten);
    return rewritten;
  }

  // Represent a private instance interface method as a method of companion class.
  static DexMethod privateAsMethodOfCompanionClass(DexMethod method, DexItemFactory factory) {
    // Add an implicit argument to represent the receiver.
    return instanceAsMethodOfCompanionClass(method, PRIVATE_METHOD_PREFIX, factory);
  }

  private DexMethod privateAsMethodOfCompanionClass(DexClassAndMethod method) {
    return privateAsMethodOfCompanionClass(method.getReference(), factory);
  }

  private static void recordCompanionClassReference(
      AppView<?> appView, DexClassAndMethod method, DexMethod rewritten) {
    // If the interface class is a program class, we shouldn't need to synthesize the companion
    // class on the classpath.
    if (method.getHolder().isProgramClass()) {
      return;
    }

    // If the companion class does not exist, then synthesize it on the classpath.
    DexClass companionClass = appView.definitionFor(rewritten.getHolderType());
    if (companionClass == null) {
      companionClass =
          synthesizeEmptyCompanionClass(appView, rewritten.getHolderType(), method.getHolder());
    }

    // If the companion class is a classpath class, then synthesize the companion class method if it
    // does not exist.
    if (companionClass.isClasspathClass()) {
      synthetizeCompanionClassMethodIfNotPresent(companionClass.asClasspathClass(), rewritten);
    }
  }

  private static DexClasspathClass synthesizeEmptyCompanionClass(
      AppView<?> appView, DexType type, DexClass context) {
    return appView
        .getSyntheticItems()
        .createClasspathClass(
            SyntheticKind.COMPANION_CLASS, type, context, appView.dexItemFactory());
  }

  private static void synthetizeCompanionClassMethodIfNotPresent(
      DexClasspathClass companionClass, DexMethod method) {
    MethodCollection methodCollection = companionClass.getMethodCollection();
    synchronized (methodCollection) {
      if (methodCollection.getMethod(method) == null) {
        boolean d8R8Synthesized = true;
        methodCollection.addDirectMethod(
            new DexEncodedMethod(
                method,
                MethodAccessFlags.createPublicStaticSynthetic(),
                MethodTypeSignature.noSignature(),
                DexAnnotationSet.empty(),
                ParameterAnnotationsList.empty(),
                DexEncodedMethod.buildEmptyThrowingCfCode(method),
                d8R8Synthesized));
      }
    }
  }

  private void renameEmulatedInterfaces() {
    // Rename manually the emulated interfaces, we do not use the PrefixRenamingLens
    // because both the normal and emulated interfaces are used.
    for (DexClass clazz : appView.appInfo().classes()) {
      if (clazz.isInterface()) {
        DexType newType = inferEmulatedInterfaceName(clazz);
        if (newType != null && !appView.rewritePrefix.hasRewrittenType(clazz.type, appView)) {
          // We do not rewrite if it is already going to be rewritten using the a rewritingPrefix.
          addRewritePrefix(clazz.type, newType.toString());
          renameEmulatedInterfaces(clazz, newType);
        }
      }
    }
  }

  private DexType inferEmulatedInterfaceName(DexClass subInterface) {
    DexType newType = emulatedInterfaces.get(subInterface.type);
    if (newType != null) {
      return newType;
    }
    LinkedList<DexType> workList = new LinkedList<>(Arrays.asList(subInterface.interfaces.values));
    while (!workList.isEmpty()) {
      DexType next = workList.removeFirst();
      if (emulatedInterfaces.get(next) != null) {
        return inferEmulatedInterfaceName(subInterface.type, next);
      }
      DexClass nextClass = appView.definitionFor(next);
      if (nextClass != null) {
        workList.addAll(Arrays.asList(nextClass.interfaces.values));
      }
    }
    return null;
  }

  private DexType inferEmulatedInterfaceName(DexType subInterfaceType, DexType interfaceType) {
    String initialPrefix = interfaceType.getPackageName();
    String rewrittenPrefix = emulatedInterfaces.get(interfaceType).getPackageName();
    String suffix = subInterfaceType.toString().substring(initialPrefix.length());
    return factory.createType(DescriptorUtils.javaTypeToDescriptor(rewrittenPrefix + suffix));
  }

  private void renameEmulatedInterfaces(DexClass theInterface, DexType renamedInterface) {
    theInterface.type = renamedInterface;
    theInterface.setVirtualMethods(renameHolder(theInterface.virtualMethods(), renamedInterface));
    theInterface.setDirectMethods(renameHolder(theInterface.directMethods(), renamedInterface));
  }

  private DexEncodedMethod[] renameHolder(Iterable<DexEncodedMethod> methods, DexType newName) {
    return renameHolder(IterableUtils.toNewArrayList(methods), newName);
  }

  private DexEncodedMethod[] renameHolder(List<DexEncodedMethod> methods, DexType newName) {
    DexEncodedMethod[] newMethods = new DexEncodedMethod[methods.size()];
    for (int i = 0; i < newMethods.length; i++) {
      newMethods[i] = methods.get(i).toRenamedHolderMethod(newName, factory);
    }
    return newMethods;
  }

  /**
   * Move static and default interface methods to companion classes, add missing methods to forward
   * to moved default methods implementation.
   */
  public void desugarInterfaceMethods(
      Builder<?> builder,
      Flavor flavour,
      ExecutorService executorService)
      throws ExecutionException {
    if (appView.options().isDesugaredLibraryCompilation()) {
      generateEmulateInterfaceLibrary(builder);
    }

    // Process all classes first. Add missing forwarding methods to
    // replace desugared default interface methods.
    processClasses(builder, flavour, synthesizedMethods::add);
    transformEmulatedInterfaces();

    // Process interfaces, create companion or dispatch class if needed, move static
    // methods to companion class, copy default interface methods to companion classes,
    // make original default methods abstract, remove bridge methods, create dispatch
    // classes if needed.
    AppInfo appInfo = appView.appInfo();
    InterfaceProcessorNestedGraphLens.Builder graphLensBuilder =
        InterfaceProcessorNestedGraphLens.builder();
    Map<DexClass, DexProgramClass> classMapping =
        processInterfaces(builder, flavour, graphLensBuilder, synthesizedMethods::add);
    InterfaceProcessorNestedGraphLens graphLens = graphLensBuilder.build(appView);
    if (appView.enableWholeProgramOptimizations() && graphLens != null) {
      appView.setGraphLens(graphLens);
    }
    classMapping.forEach(
        (interfaceClass, synthesizedClass) -> {
          // Don't need to optimize synthesized class since all of its methods
          // are just moved from interfaces and don't need to be re-processed.
          builder.addSynthesizedClass(synthesizedClass);
          appInfo.addSynthesizedClass(synthesizedClass, interfaceClass.asProgramClass());
        });
    new InterfaceMethodRewriterFixup(appView, graphLens).run();
    if (appView.options().isDesugaredLibraryCompilation()) {
      renameEmulatedInterfaces();
    }

    converter.processMethodsConcurrently(synthesizedMethods, executorService);

    // Cached data is not needed any more.
    clear();
  }

  private void transformEmulatedInterfaces() {
    for (DexType dexType : emulatedInterfaces.keySet()) {
      DexClass dexClass = appView.definitionFor(dexType);
      if (dexClass != null && dexClass.isProgramClass()) {
        transformEmulatedInterfaces(dexClass.asProgramClass());
      }
    }
  }

  // The method transforms emulated interface such as they implement the rewritten version
  // of each emulated interface they implement. Such change should have no effect on the look-up
  // results, since each class implementing an emulated interface should also implement the
  // rewritten one.
  private void transformEmulatedInterfaces(DexProgramClass clazz) {
    if (appView.isAlreadyLibraryDesugared(clazz)) {
      return;
    }
    List<GenericSignature.ClassTypeSignature> newInterfaces = new ArrayList<>();
    GenericSignature.ClassSignature classSignature = clazz.getClassSignature();
    for (int i = 0; i < clazz.interfaces.size(); i++) {
      DexType itf = clazz.interfaces.values[i];
      if (emulatedInterfaces.containsKey(itf)) {
        List<GenericSignature.FieldTypeSignature> typeArguments;
        if (classSignature == null) {
          typeArguments = Collections.emptyList();
        } else {
          GenericSignature.ClassTypeSignature classTypeSignature =
              classSignature.superInterfaceSignatures().get(i);
          assert itf == classTypeSignature.type();
          typeArguments = classTypeSignature.typeArguments();
        }
        newInterfaces.add(
            new GenericSignature.ClassTypeSignature(emulatedInterfaces.get(itf), typeArguments));
      }
    }
    clazz.replaceInterfaces(newInterfaces);
  }

  private void clear() {
    this.cache.clear();
    this.synthesizedMethods.clear();
  }

  private static boolean shouldProcess(
      DexProgramClass clazz, Flavor flavour, boolean mustBeInterface) {
    return (!clazz.originatesFromDexResource() || flavour == Flavor.IncludeAllResources)
        && clazz.isInterface() == mustBeInterface;
  }

  private Map<DexClass, DexProgramClass> processInterfaces(
      Builder<?> builder,
      Flavor flavour,
      InterfaceProcessorNestedGraphLens.Builder graphLensBuilder,
      Consumer<ProgramMethod> newSynthesizedMethodConsumer) {
    InterfaceProcessor processor = new InterfaceProcessor(appView, this);
    for (DexProgramClass clazz : builder.getProgramClasses()) {
      if (shouldProcess(clazz, flavour, true)) {
        processor.process(clazz, graphLensBuilder, newSynthesizedMethodConsumer);
      }
    }
    return processor.syntheticClasses;
  }

  private void processClasses(
      Builder<?> builder, Flavor flavour, Consumer<ProgramMethod> newSynthesizedMethodConsumer) {
    ClassProcessor processor = new ClassProcessor(appView, this, newSynthesizedMethodConsumer);
    // First we compute all desugaring *without* introducing forwarding methods.
    assert appView.getSyntheticItems().verifyNonLegacySyntheticsAreCommitted();
    for (DexProgramClass clazz : builder.getProgramClasses()) {
      if (shouldProcess(clazz, flavour, false)) {
        if (appView.isAlreadyLibraryDesugared(clazz)) {
          continue;
        }
        processor.processClass(clazz);
      }
    }
    // Then we introduce forwarding methods.
    processor.addSyntheticMethods();
  }

  final boolean isDefaultMethod(DexEncodedMethod method) {
    assert !method.accessFlags.isConstructor();
    assert !method.accessFlags.isStatic();

    if (method.accessFlags.isAbstract()) {
      return false;
    }
    if (method.accessFlags.isNative()) {
      throw new Unimplemented("Native default interface methods are not yet supported.");
    }
    if (!method.accessFlags.isPublic()) {
      // NOTE: even though the class is allowed to have non-public interface methods
      // with code, for example private methods, all such methods we are aware of are
      // created by the compiler for stateful lambdas and they must be converted into
      // static methods by lambda desugaring by this time.
      throw new Unimplemented("Non public default interface methods are not yet supported.");
    }
    return true;
  }

  private Predicate<DexType> getShouldIgnoreFromReportsPredicate(AppView<?> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    InternalOptions options = appView.options();
    DexString retargetPackageAndClassPrefixDescriptor =
        dexItemFactory.createString(
            getRetargetPackageAndClassPrefixDescriptor(options.desugaredLibraryConfiguration));
    DexString typeWrapperClassNameDescriptorSuffix =
        dexItemFactory.createString(TYPE_WRAPPER_SUFFIX + ';');
    DexString vivifiedTypeWrapperClassNameDescriptorSuffix =
        dexItemFactory.createString(VIVIFIED_TYPE_WRAPPER_SUFFIX + ';');
    DexString companionClassNameDescriptorSuffix =
        dexItemFactory.createString(COMPANION_CLASS_NAME_SUFFIX + ";");

    return type -> {
      DexString descriptor = type.getDescriptor();
      return appView.rewritePrefix.hasRewrittenType(type, appView)
          || descriptor.endsWith(typeWrapperClassNameDescriptorSuffix)
          || descriptor.endsWith(vivifiedTypeWrapperClassNameDescriptorSuffix)
          || descriptor.endsWith(companionClassNameDescriptorSuffix)
          || emulatedInterfaces.containsValue(type)
          || options.desugaredLibraryConfiguration.getCustomConversions().containsValue(type)
          || appView.getDontWarnConfiguration().matches(type)
          || descriptor.startsWith(retargetPackageAndClassPrefixDescriptor);
    };
  }

  private boolean shouldIgnoreFromReports(DexType missing) {
    return shouldIgnoreFromReportsPredicate.test(missing);
  }

  void warnMissingInterface(DexClass classToDesugar, DexClass implementing, DexType missing) {
    // We use contains() on non hashed collection, but we know it's a 8 cases collection.
    // j$ interfaces won't be missing, they are in the desugared library.
    if (shouldIgnoreFromReports(missing)) {
      return;
    }
    options.warningMissingInterfaceForDesugar(classToDesugar, implementing, missing);
  }

  private void warnMissingType(ProgramMethod context, DexType missing) {
    // Companion/Emulated interface/Conversion classes for desugared library won't be missing,
    // they are in the desugared library.
    if (shouldIgnoreFromReports(missing)) {
      return;
    }
    DexMethod method = appView.graphLens().getOriginalMethodSignature(context.getReference());
    Origin origin = getMethodOrigin(method);
    MethodPosition position = new MethodPosition(method.asMethodReference());
    options.warningMissingTypeForDesugar(origin, position, missing, method);
  }

  private Origin getMethodOrigin(DexMethod method) {
    DexType holder = method.holder;
    if (isCompanionClassType(holder)) {
      holder = getInterfaceClassType(holder);
    }
    DexClass clazz = appView.definitionFor(holder);
    return clazz == null ? Origin.unknown() : clazz.getOrigin();
  }

  final DefaultMethodsHelper.Collection getOrCreateInterfaceInfo(
      DexClass classToDesugar,
      DexClass implementing,
      DexType iface) {
    DefaultMethodsHelper.Collection collection = cache.get(iface);
    if (collection != null) {
      return collection;
    }
    collection = createInterfaceInfo(classToDesugar, implementing, iface);
    Collection existing = cache.putIfAbsent(iface, collection);
    return existing != null ? existing : collection;
  }

  private DefaultMethodsHelper.Collection createInterfaceInfo(
      DexClass classToDesugar,
      DexClass implementing,
      DexType iface) {
    DefaultMethodsHelper helper = new DefaultMethodsHelper();
    DexClass definedInterface = appView.definitionFor(iface);
    if (definedInterface == null) {
      warnMissingInterface(classToDesugar, implementing, iface);
      return helper.wrapInCollection();
    }
    if (!definedInterface.isInterface()) {
      throw new CompilationError(
          "Type " + iface.toSourceString() + " is referenced as an interface from `"
              + implementing.toString() + "`.");
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
      if (isDefaultMethod(encoded)) {
        helper.addDefaultMethod(encoded);
      }
    }

    return helper.wrapInCollection();
  }

  public static void reportDependencyEdge(
      DexClass dependent, DexClass dependency, AppInfo appInfo) {
    assert !dependent.isLibraryClass();
    assert !dependency.isLibraryClass();
    DesugarGraphConsumer consumer = appInfo.app().options.desugarGraphConsumer;
    if (consumer != null) {
      Origin dependencyOrigin = dependency.getOrigin();
      java.util.Collection<DexType> dependents =
          appInfo.getSyntheticItems().getSynthesizingContexts(dependent.getType());
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
