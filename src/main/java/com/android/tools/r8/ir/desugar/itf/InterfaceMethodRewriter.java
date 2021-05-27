// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import static com.android.tools.r8.ir.code.Invoke.Type.DIRECT;
import static com.android.tools.r8.ir.code.Invoke.Type.INTERFACE;
import static com.android.tools.r8.ir.code.Invoke.Type.STATIC;
import static com.android.tools.r8.ir.code.Invoke.Type.SUPER;
import static com.android.tools.r8.ir.code.Invoke.Type.VIRTUAL;
import static com.android.tools.r8.ir.desugar.DesugaredLibraryRetargeter.getRetargetPackageAndClassPrefixDescriptor;
import static com.android.tools.r8.ir.desugar.DesugaredLibraryWrapperSynthesizer.TYPE_WRAPPER_SUFFIX;
import static com.android.tools.r8.ir.desugar.DesugaredLibraryWrapperSynthesizer.VIVIFIED_TYPE_WRAPPER_SUFFIX;

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
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfiguration;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.desugar.lambda.LambdaInstructionDesugaring;
import com.android.tools.r8.ir.desugar.stringconcat.StringConcatInstructionDesugaring;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.MethodSynthesizerConsumer;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.UtilityMethodForCodeOptimizations;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IteratorUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
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

  // All forwarding methods and all throwing methods generated during desugaring.
  private final ProgramMethodSet synthesizedMethods = ProgramMethodSet.createConcurrent();

  // Caches default interface method info for already processed interfaces.
  private final Map<DexType, DefaultMethodsHelper.Collection> cache = new ConcurrentHashMap<>();

  private final Predicate<DexType> shouldIgnoreFromReportsPredicate;

  // This is used to filter out double desugaring on backported methods.
  private final BackportedMethodRewriter backportedMethodRewriter;

  /** Defines a minor variation in desugaring. */
  public enum Flavor {
    /** Process all application resources. */
    IncludeAllResources,
    /** Process all but DEX application resources. */
    ExcludeDexResources
  }

  // Constructor for cf to cf desugaring.
  public InterfaceMethodRewriter(AppView<?> appView, BackportedMethodRewriter rewriter) {
    this.appView = appView;
    this.converter = null;
    this.backportedMethodRewriter = rewriter;
    this.options = appView.options();
    this.factory = appView.dexItemFactory();
    this.emulatedInterfaces = options.desugaredLibraryConfiguration.getEmulateLibraryInterface();
    this.shouldIgnoreFromReportsPredicate = getShouldIgnoreFromReportsPredicate(appView);
    initializeEmulatedInterfaceVariables();
  }

  // Constructor for IR desugaring.
  public InterfaceMethodRewriter(AppView<?> appView, IRConverter converter) {
    assert converter != null;
    this.appView = appView;
    this.converter = converter;
    this.backportedMethodRewriter = null;
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
        getEmulateLibraryInterfaceClassType(emulatedInterface, factory),
        factory.createType(
            DescriptorUtils.javaTypeToDescriptor(
                rewrittenEmulatedInterface + EMULATE_LIBRARY_CLASS_NAME_SUFFIX)));
  }

  void addCompanionClassRewriteRule(DexType interfaceType, String rewrittenType) {
    appView.rewritePrefix.rewriteType(
        getCompanionClassType(interfaceType),
        factory.createType(
            DescriptorUtils.javaTypeToDescriptor(rewrittenType + COMPANION_CLASS_NAME_SUFFIX)));
  }

  boolean isEmulatedInterface(DexType itf) {
    return emulatedInterfaces.containsKey(itf);
  }

  public boolean needsRewriting(DexMethod method, Type invokeType, ProgramMethod context) {
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
      if (resolution != null && resolution.getResolvedMethod().isPrivateMethod()) {
        return true;
      }
      return defaultMethodForEmulatedDispatchOrNull(method, invokeType == INTERFACE) != null;
    }
    return true;
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
        if (backportedMethodRewriter.methodIsBackport(cfInvoke.getMethod())) {
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
    if (backportedMethodRewriter.methodIsBackport(invoke.getMethod())) {
      return null;
    }

    Function<DexMethod, Collection<CfInstruction>> rewriteInvoke =
        (newTarget) ->
            Collections.singletonList(
                new CfInvoke(org.objectweb.asm.Opcodes.INVOKESTATIC, newTarget, false));

    Function<SingleResolutionResult, Collection<CfInstruction>> rewriteToThrow =
        (resolutionResult) ->
            rewriteInvokeToThrowCf(
                invoke, resolutionResult, eventConsumer, context, methodProcessingContext);

    if (invoke.isInvokeVirtual() || invoke.isInvokeInterface()) {
      AppInfoWithClassHierarchy appInfoForDesugaring = appView.appInfoForDesugaring();
      SingleResolutionResult resolution =
          appInfoForDesugaring
              .resolveMethod(invoke.getMethod(), invoke.isInterface())
              .asSingleResolution();
      if (resolution != null
          && resolution.getResolvedMethod().isPrivateMethod()
          && resolution.isAccessibleFrom(context, appInfoForDesugaring).isTrue()) {
        return rewriteInvokeDirect(invoke.getMethod(), context, rewriteInvoke);
      }
      return rewriteInvokeInterfaceOrInvokeVirtual(
          invoke.getMethod(), invoke.isInterface(), rewriteInvoke);
    }
    if (invoke.isInvokeStatic()) {
      Consumer<ProgramMethod> staticOutliningMethodConsumer =
          staticOutliningMethod -> {
            synthesizedMethods.add(staticOutliningMethod);
            eventConsumer.acceptInvokeStaticInterfaceOutliningMethod(
                staticOutliningMethod, context);
          };
      return rewriteInvokeStatic(
          invoke.getMethod(),
          invoke.isInterface(),
          methodProcessingContext,
          context,
          staticOutliningMethodConsumer,
          rewriteInvoke,
          rewriteToThrow);
    }
    assert invoke.isInvokeSpecial();
    if (invoke.isInvokeSuper(context.getHolderType())) {
      return rewriteInvokeSuper(invoke.getMethod(), context, rewriteInvoke, rewriteToThrow);
    }
    return rewriteInvokeDirect(invoke.getMethod(), context, rewriteInvoke);
  }

  private Collection<CfInstruction> rewriteInvokeToThrowCf(
      CfInvoke invoke,
      SingleResolutionResult resolutionResult,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    if (backportedMethodRewriter != null
        && backportedMethodRewriter.methodIsBackport(invoke.getMethod())) {
      // In Cf to Cf it is not allowed to desugar twice the same instruction, if the backported
      // method rewriter already desugars the instruction, it takes precedence and nothing has
      // to be done here.
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
    }
    return replacement;
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
    if (isSyntheticMethodThatShouldNotBeDoubleProcessed(code.context())) {
      // As the synthetics for dispatching to static interface methods are not desugared again
      // this can leave a static invoke to a static method on an interface.
      leavingStaticInvokeToInterface(context.asProgramMethod());
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
        rewriteMethodReferences(
            code,
            methodProcessor,
            methodProcessingContext,
            context,
            affectedValues,
            blocksToRemove,
            blocks,
            instructions,
            instruction);
      }
    }

    code.removeBlocks(blocksToRemove);

    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }

    assert code.isConsistentSSA();
  }

  private void rewriteMethodReferences(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      ProgramMethod context,
      Set<Value> affectedValues,
      Set<BasicBlock> blocksToRemove,
      ListIterator<BasicBlock> blocks,
      InstructionListIterator instructions,
      Instruction instruction) {
    if (instruction.isInvokeCustom()) {
      reportInterfaceMethodHandleCallSite(instruction.asInvokeCustom().getCallSite(), context);
      return;
    }
    if (!instruction.isInvokeMethod()) {
      return;
    }
    InvokeMethod invoke = instruction.asInvokeMethod();
    Function<DexMethod, Collection<CfInstruction>> rewriteInvoke =
        (newTarget) -> {
          instructions.replaceCurrentInstruction(
              new InvokeStatic(newTarget, invoke.outValue(), invoke.arguments()));
          return null;
        };
    if (instruction.isInvokeDirect()) {
      rewriteInvokeDirect(invoke.getInvokedMethod(), context, rewriteInvoke);
    } else if (instruction.isInvokeVirtual() || instruction.isInvokeInterface()) {
      rewriteInvokeInterfaceOrInvokeVirtual(
          invoke.getInvokedMethod(), invoke.getInterfaceBit(), rewriteInvoke);
    } else {
      Function<SingleResolutionResult, Collection<CfInstruction>> rewriteToThrow =
          (resolutionResult) ->
              rewriteInvokeToThrowIR(
                  invoke,
                  resolutionResult,
                  code,
                  blocks,
                  instructions,
                  affectedValues,
                  blocksToRemove,
                  methodProcessor,
                  methodProcessingContext);
      if (instruction.isInvokeStatic()) {
        rewriteInvokeStatic(
            invoke.getInvokedMethod(),
            invoke.getInterfaceBit(),
            methodProcessingContext,
            context,
            synthesizedMethods::add,
            rewriteInvoke,
            rewriteToThrow);
      } else {
        assert instruction.isInvokeSuper();
        rewriteInvokeSuper(invoke.getInvokedMethod(), context, rewriteInvoke, rewriteToThrow);
      }
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
      Function<DexMethod, Collection<CfInstruction>> rewriteInvoke) {
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
      return rewriteInvoke.apply(
          directTarget.getDefinition().isPrivateMethod()
              ? privateAsMethodOfCompanionClass(directTarget)
              : defaultAsMethodOfCompanionClass(directTarget));
    } else {
      // The method can be a default method in the interface hierarchy.
      DexClassAndMethod virtualTarget =
          appView.appInfoForDesugaring().lookupMaximallySpecificMethod(clazz, invokedMethod);
      if (virtualTarget != null) {
        // This is a invoke-direct call to a virtual method.
        assert invokeNeedsRewriting(invokedMethod, DIRECT);
        return rewriteInvoke.apply(defaultAsMethodOfCompanionClass(virtualTarget));
      } else {
        // The below assert is here because a well-type program should have a target, but we
        // cannot throw a compilation error, since we have no knowledge about the input.
        assert false;
      }
    }
    return null;
  }

  private Collection<CfInstruction> rewriteInvokeStatic(
      DexMethod invokedMethod,
      boolean interfaceBit,
      MethodProcessingContext methodProcessingContext,
      ProgramMethod context,
      Consumer<ProgramMethod> staticOutliningMethodConsumer,
      Function<DexMethod, Collection<CfInstruction>> rewriteInvoke,
      Function<SingleResolutionResult, Collection<CfInstruction>> rewriteToThrow) {
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
        if (backportedMethodRewriter != null
            && backportedMethodRewriter.methodIsBackport(invokedMethod)) {
          // In Cf to Cf it is not allowed to desugar twice the same instruction, if the backported
          // method rewriter already desugars the instruction, it takes precedence and nothing has
          // to be done here.
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

    return rewriteInvoke.apply(
        staticAsMethodOfCompanionClass(resolutionResult.getResolutionPair()));
  }

  private Collection<CfInstruction> rewriteInvokeSuper(
      DexMethod invokedMethod,
      ProgramMethod context,
      Function<DexMethod, Collection<CfInstruction>> rewriteInvoke,
      Function<SingleResolutionResult, Collection<CfInstruction>> rewriteToThrow) {
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
        return rewriteInvoke.apply(
            privateAsMethodOfCompanionClass(resolutionResult.getResolutionPair()));
      } else {
        DexMethod amendedMethod = amendDefaultMethod(context.getHolder(), invokedMethod);
        return rewriteInvoke.apply(
            defaultAsMethodOfCompanionClass(amendedMethod, appView.dexItemFactory()));
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
            return rewriteInvoke.apply(defaultAsMethodOfCompanionClass(target));
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
        assert invokeNeedsRewriting(invokedMethod, SUPER);
        return rewriteInvoke.apply(companionMethod);
      } else {
        assert invokeNeedsRewriting(invokedMethod, SUPER);
        return rewriteInvoke.apply(retargetMethod);
      }
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
            || appView.options().isDesugaredLibraryCompilation())) {
      DexClassAndMethod defaultMethod =
          appView.definitionFor(emulatedItf).lookupClassMethod(invokedMethod);
      if (defaultMethod != null && !dontRewrite(defaultMethod)) {
        assert !defaultMethod.getAccessFlags().isAbstract();
        return defaultMethod;
      }
    }
    return null;
  }

  private Collection<CfInstruction> rewriteInvokeInterfaceOrInvokeVirtual(
      DexMethod invokedMethod,
      boolean interfaceBit,
      Function<DexMethod, Collection<CfInstruction>> rewriteInvoke) {
    DexClassAndMethod defaultMethod =
        defaultMethodForEmulatedDispatchOrNull(invokedMethod, interfaceBit);
    if (defaultMethod != null) {
      return rewriteInvoke.apply(emulateInterfaceLibraryMethod(defaultMethod, factory));
    }
    return null;
  }

  private boolean shouldRewriteToInvokeToThrow(
      SingleResolutionResult resolutionResult, boolean isInvokeStatic) {
    return resolutionResult == null
        || resolutionResult.getResolvedMethod().isStatic() != isInvokeStatic;
  }

  private Collection<CfInstruction> rewriteInvokeToThrowIR(
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
      assert false;
      return null;
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
    return null;
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

  public static DexMethod emulateInterfaceLibraryMethod(
      DexClassAndMethod method, DexItemFactory factory) {
    return factory.createMethod(
        getEmulateLibraryInterfaceClassType(method.getHolderType(), factory),
        factory.prependTypeToProto(method.getHolderType(), method.getProto()),
        method.getName());
  }

  private static String getEmulateLibraryInterfaceClassDescriptor(String descriptor) {
    return descriptor.substring(0, descriptor.length() - 1)
        + EMULATE_LIBRARY_CLASS_NAME_SUFFIX
        + ";";
  }

  public static DexType getEmulateLibraryInterfaceClassType(DexType type, DexItemFactory factory) {
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

  public DexType getCompanionClassType(DexType type) {
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
    String interfaceTypeDescriptor =
        descriptor.substring(0, descriptor.length() - 1 - COMPANION_CLASS_NAME_SUFFIX.length())
            + ";";
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
    DexMethod singleCandidate =
        getOrCreateInterfaceInfo(classToDesugar, classToDesugar, method.holder)
            .getSingleCandidate(method);
    return singleCandidate != null ? singleCandidate : method;
  }

  // Represent a default interface method as a method of companion class.
  public static DexMethod defaultAsMethodOfCompanionClass(
      DexMethod method, DexItemFactory factory) {
    return instanceAsMethodOfCompanionClass(method, DEFAULT_METHOD_PREFIX, factory);
  }

  public final DexMethod defaultAsMethodOfCompanionClass(DexClassAndMethod method) {
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
    ClasspathOrLibraryClass context = method.getHolder().asClasspathOrLibraryClass();
    // If the interface class is a program class, we shouldn't need to synthesize the companion
    // class on the classpath.
    if (context == null) {
      return;
    }
    appView
        .getSyntheticItems()
        .ensureDirectMethodOnSyntheticClasspathClassWhileMigrating(
            SyntheticKind.COMPANION_CLASS,
            rewritten.getHolderType(),
            context,
            appView,
            rewritten,
            builder ->
                builder
                    .setName(rewritten.name)
                    .setProto(rewritten.proto)
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setCode(DexEncodedMethod::buildEmptyThrowingCfCode));
  }

  /**
   * Move static and default interface methods to companion classes, add missing methods to forward
   * to moved default methods implementation.
   */
  public void desugarInterfaceMethods(
      Builder<?> builder, Flavor flavour, ExecutorService executorService)
      throws ExecutionException {
    // During L8 compilation, emulated interfaces are processed to be renamed, to have
    // their interfaces fixed-up and to generate the emulated dispatch code.
    EmulatedInterfaceProcessor emulatedInterfaceProcessor =
        new EmulatedInterfaceProcessor(appView, this);

    // Process all classes first. Add missing forwarding methods to
    // replace desugared default interface methods.
    ClassProcessor classProcessor = new ClassProcessor(appView, this);

    // Process interfaces, create companion or dispatch class if needed, move static
    // methods to companion class, copy default interface methods to companion classes,
    // make original default methods abstract, remove bridge methods, create dispatch
    // classes if needed.
    InterfaceProcessor interfaceProcessor = new InterfaceProcessor(appView, this);

    // The interface processors must be ordered so that finalization of the processing is performed
    // in that order. The emulatedInterfaceProcessor has to be last at this point to avoid renaming
    // emulated interfaces before the other processing.
    ImmutableList<InterfaceDesugaringProcessor> orderedInterfaceDesugaringProcessors =
        ImmutableList.of(classProcessor, interfaceProcessor, emulatedInterfaceProcessor);
    processClassesConcurrently(
        orderedInterfaceDesugaringProcessors, builder, flavour, executorService);

    SortedProgramMethodSet sortedSynthesizedMethods = SortedProgramMethodSet.create();
    sortedSynthesizedMethods.addAll(synthesizedMethods);
    converter.processMethodsConcurrently(sortedSynthesizedMethods, executorService);

    // Cached data is not needed any more.
    clear();
  }

  private void clear() {
    this.cache.clear();
    this.synthesizedMethods.clear();
  }

  private boolean shouldProcess(DexProgramClass clazz, Flavor flavour) {
    if (appView.isAlreadyLibraryDesugared(clazz)) {
      return false;
    }
    return (!clazz.originatesFromDexResource() || flavour == Flavor.IncludeAllResources);
  }

  private void processClassesConcurrently(
      List<InterfaceDesugaringProcessor> processors,
      Builder<?> builder,
      Flavor flavour,
      ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        Iterables.filter(
            builder.getProgramClasses(), (DexProgramClass clazz) -> shouldProcess(clazz, flavour)),
        clazz -> {
          for (InterfaceDesugaringProcessor processor : processors) {
            processor.process(clazz, synthesizedMethods);
          }
        },
        executorService);
    for (InterfaceDesugaringProcessor processor : processors) {
      processor.finalizeProcessing(builder, synthesizedMethods);
    }
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
      warnMissingInterface(classToDesugar, implementing, iface);
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
