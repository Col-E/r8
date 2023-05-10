// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.InterfaceMethodDesugaringMode.EMULATED_INTERFACE_ONLY;
import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.InterfaceMethodDesugaringMode.NONE;
import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.getInterfaceMethodDesugaringMode;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInvokeSuper;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InvalidCode;
import com.android.tools.r8.graph.MethodCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.NestedGraphLens;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper.InterfaceMethodDesugaringMode;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.collections.BidirectionalManyToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

// Default and static method interface desugaring processor for interfaces.
//
// Makes default interface methods abstract, moves their implementation to
// a companion class. Removes bridge default methods.
//
// Also moves static interface methods into a companion class.
public final class InterfaceProcessor {

  private final AppView<?> appView;
  private final InterfaceDesugaringSyntheticHelper helper;
  private final Map<DexProgramClass, PostProcessingInterfaceInfo> postProcessingInterfaceInfos =
      new ConcurrentHashMap<>();
  private final InterfaceMethodDesugaringMode desugaringMode;

  public static InterfaceProcessor create(AppView<?> appView) {
    InterfaceMethodDesugaringMode desugaringMode =
        getInterfaceMethodDesugaringMode(appView.options());
    if (desugaringMode == NONE) {
      return null;
    }
    return new InterfaceProcessor(appView, desugaringMode);
  }

  public InterfaceProcessor(AppView<?> appView, InterfaceMethodDesugaringMode desugaringMode) {
    this.appView = appView;
    helper = new InterfaceDesugaringSyntheticHelper(appView);
    this.desugaringMode = desugaringMode;
  }

  public InterfaceDesugaringSyntheticHelper getHelper() {
    return helper;
  }

  public void processMethod(
      ProgramMethod method, InterfaceMethodDesugaringBaseEventConsumer eventConsumer) {
    assert !appView.enableWholeProgramOptimizations();
    if (!method.getHolder().isInterface()) {
      return;
    }
    if (desugaringMode == EMULATED_INTERFACE_ONLY) {
      processEmulatedInterfaceOnly(method, eventConsumer);
      return;
    }
    if (method.getDefinition().belongsToDirectPool()) {
      processDirectInterfaceMethod(method, eventConsumer);
    } else {
      assert method.getDefinition().belongsToVirtualPool();
      processVirtualInterfaceMethod(method, eventConsumer);
      if (!interfaceMethodRemovalChangesApi(method)) {
        getPostProcessingInterfaceInfo(method.getHolder()).setHasBridgesToRemove();
      }
    }
  }

  private void processEmulatedInterfaceOnly(
      ProgramMethod method, InterfaceMethodDesugaringBaseEventConsumer eventConsumer) {
    if (!appView.options().isDesugaredLibraryCompilation()) {
      return;
    }
    if (method.getDefinition().belongsToDirectPool()) {
      return;
    }
    if (helper.isEmulatedInterface(method.getHolderType())) {
      EmulatedDispatchMethodDescriptor emulatedDispatchDescriptor =
          helper.getEmulatedDispatchDescriptor(method.getHolder(), method);
      if (emulatedDispatchDescriptor != null) {
        processVirtualInterfaceMethod(method, eventConsumer);
        if (!interfaceMethodRemovalChangesApi(method)) {
          getPostProcessingInterfaceInfo(method.getHolder()).setHasBridgesToRemove();
        }
      }
    }
  }

  static ProgramMethod ensureCompanionMethod(
      DexProgramClass iface,
      DexString methodName,
      DexProto methodProto,
      AppView<?> appView,
      Consumer<SyntheticMethodBuilder> methodBuilderCallback,
      Consumer<ProgramMethod> newMethodCallback) {
    return appView
        .getSyntheticItems()
        .ensureFixedClassMethod(
            methodName,
            methodProto,
            kinds -> kinds.COMPANION_CLASS,
            iface,
            appView,
            builder -> builder.setSourceFile(iface.sourceFile),
            methodBuilderCallback,
            newMethodCallback);
  }

  private void processVirtualInterfaceMethod(
      ProgramMethod method, InterfaceMethodDesugaringBaseEventConsumer eventConsumer) {
    if (helper.isCompatibleDefaultMethod(method.getDefinition())) {
      // Create a new method in a companion class to represent default method implementation.
      ProgramMethod companion =
          helper.ensureDefaultAsMethodOfProgramCompanionClassStub(method, eventConsumer);
      finalizeMoveToCompanionMethod(method, companion);
    }
  }

  private void processDirectInterfaceMethod(
      ProgramMethod method, InterfaceMethodDesugaringBaseEventConsumer eventConsumer) {
    if (!method.getDefinition().isClassInitializer()) {
      getPostProcessingInterfaceInfo(method.getHolder()).setHasNonClinitDirectMethods();
      ProgramMethod companion =
          helper.ensureMethodOfProgramCompanionClassStub(method, eventConsumer);
      finalizeMoveToCompanionMethod(method, companion);
    }
  }

  public void finalizeMoveToCompanionMethod(ProgramMethod method, ProgramMethod companion) {
    assert InvalidCode.isInvalidCode(companion.getDefinition().getCode());
    if (method.getDefinition().getCode() == null) {
      throw new CompilationError(
          "Code is missing for private instance "
              + "interface method: "
              + method.getReference().toSourceString(),
          method.getOrigin());
    }
    if (!canMoveToCompanionClass(method)) {
      throw new CompilationError(
          "One or more instruction is preventing default interface "
              + "method from being desugared: "
              + method.toSourceString(),
          method.getOrigin());
    }
    DexProgramClass iface = method.getHolder();
    DexEncodedMethod definition = method.getDefinition();
    assert !definition.isInitializer();
    assert !definition.isStatic() || definition.isPrivate() || definition.isPublic()
        : "Static interface method "
            + method.toSourceString()
            + " is expected to "
            + "either be public or private in "
            + method.getOrigin();

    if (definition.isStatic() || definition.isPrivate()) {
      getPostProcessingInterfaceInfo(iface).setHasNonClinitDirectMethods();
      getPostProcessingInterfaceInfo(iface)
          .moveMethod(method.getReference(), companion.getReference());
    } else {
      assert helper.isCompatibleDefaultMethod(definition);
      getPostProcessingInterfaceInfo(iface)
          .mapDefaultMethodToCompanionMethod(method.getDefinition(), companion.getDefinition());
    }
    if (definition.hasClassFileVersion()) {
      companion.getDefinition().downgradeClassFileVersion(definition.getClassFileVersion());
    }
    Code code =
        definition
            .getCode()
            .getCodeAsInlining(
                companion.getReference(), method.getDefinition(), appView.dexItemFactory());
    if (!definition.isStatic()) {
      DexEncodedMethod.setDebugInfoWithFakeThisParameter(
          code, companion.getReference().getArity(), appView);
    }
    companion.setCode(code, appView);
    method.setCode(InvalidCode.getInstance(), appView);
  }

  private void clearDirectMethods(DexProgramClass iface) {
    DexEncodedMethod clinit = iface.getClassInitializer();
    MethodCollection methodCollection = iface.getMethodCollection();
    if (clinit != null) {
      methodCollection.setSingleDirectMethod(clinit);
    } else {
      methodCollection.clearDirectMethods();
    }
  }

  private static boolean canMoveToCompanionClass(ProgramMethod method) {
    Code code = method.getDefinition().getCode();
    assert code != null;
    if (code.isDexCode()) {
      for (DexInstruction insn : code.asDexCode().instructions) {
        if (insn instanceof DexInvokeSuper) {
          return false;
        }
      }
    } else {
      assert code.isCfCode();
      for (CfInstruction insn : code.asCfCode().getInstructions()) {
        if (insn instanceof CfInvoke && ((CfInvoke) insn).isInvokeSuper(method.getHolderType())) {
          return false;
        }
      }
    }
    return true;
  }

  private DexClass definitionForDependency(DexType dependency, DexClass dependent) {
    return dependent.isProgramClass()
        ? appView.appInfo().definitionForDesugarDependency(dependent.asProgramClass(), dependency)
        : appView.definitionFor(dependency);
  }

  // Returns true if the given interface method must be kept on [iface] after moving its
  // implementation to the companion class of [iface]. This is always the case for non-bridge
  // methods. Bridge methods that does not override an implementation in a super-interface must
  // also be kept (such a situation can happen if the vertical class merger merges two interfaces).
  private boolean interfaceMethodRemovalChangesApi(ProgramMethod method) {
    assert !appView.enableWholeProgramOptimizations();
    DexProgramClass iface = method.getHolder();
    if (method.getAccessFlags().isBridge()) {
      if (appView.options().isCfDesugaring()) {
        // TODO(b/187176895): Find the compilation causing this to not be removed.
        return false;
      }
      Deque<Pair<DexClass, DexType>> worklist = new ArrayDeque<>();
      Set<DexType> seenBefore = new HashSet<>();
      addSuperTypes(iface, worklist);
      while (!worklist.isEmpty()) {
        Pair<DexClass, DexType> item = worklist.pop();
        DexClass clazz = definitionForDependency(item.getSecond(), item.getFirst());
        if (clazz == null || !seenBefore.add(clazz.type)) {
          continue;
        }
        if (clazz.lookupVirtualMethod(method.getReference()) != null) {
          return false;
        }
        addSuperTypes(clazz, worklist);
      }
    }
    return true;
  }

  private static void addSuperTypes(DexClass clazz, Deque<Pair<DexClass, DexType>> worklist) {
    if (clazz.superType != null) {
      worklist.add(new Pair<>(clazz, clazz.superType));
    }
    for (DexType iface : clazz.interfaces.values) {
      worklist.add(new Pair<>(clazz, iface));
    }
  }

  private InterfaceProcessorNestedGraphLens postProcessInterfaces() {
    InterfaceProcessorNestedGraphLens.Builder graphLensBuilder =
        InterfaceProcessorNestedGraphLens.builder();
    postProcessingInterfaceInfos.forEach(
        (iface, info) -> {
          if (info.hasNonClinitDirectMethods() || appView.enableWholeProgramOptimizations()) {
            clearDirectMethods(iface);
          }
          if (info.hasDefaultMethodsToImplementationMap()) {
            info.getDefaultMethodsToImplementation()
                .forEach(
                    (defaultMethod, companionMethod) -> {
                      assert InvalidCode.isInvalidCode(defaultMethod.getCode());
                      assert !InvalidCode.isInvalidCode(companionMethod.getCode());
                      defaultMethod.accessFlags.setAbstract();
                      defaultMethod.unsetCode();
                      graphLensBuilder.recordCodeMovedToCompanionClass(
                          defaultMethod.getReference(), companionMethod.getReference());
                    });
          }
          if (info.hasMethodsToMove()) {
            info.getMethodsToMove().forEach(graphLensBuilder::move);
          }
          if (info.hasBridgesToRemove()) {
            // D8 can remove bridges at this point.
            assert !appView.enableWholeProgramOptimizations();
            removeBridges(iface);
          }
        });
    return graphLensBuilder.build(appView);
  }

  private void removeBridges(DexProgramClass iface) {
    assert !appView.enableWholeProgramOptimizations();
    List<DexEncodedMethod> newVirtualMethods = new ArrayList<>();
    for (ProgramMethod method : iface.virtualProgramMethods()) {
      // Remove bridge methods.
      if (interfaceMethodRemovalChangesApi(method)) {
        newVirtualMethods.add(method.getDefinition());
      }
    }

    // If at least one bridge method was removed then update the table.
    if (newVirtualMethods.size() < iface.getMethodCollection().numberOfVirtualMethods()) {
      iface.setVirtualMethods(newVirtualMethods.toArray(DexEncodedMethod.EMPTY_ARRAY));
    } else {
      assert false
          : "Interface "
              + iface
              + " was analysed as having bridges to remove, but no bridges were found.";
    }
  }

  public void finalizeProcessing() {
    // TODO(b/196337368): Simplify this fix-up to be specific for the move of companion methods
    //  rather than be based on a graph lens.
    InterfaceProcessorNestedGraphLens graphLens = postProcessInterfaces();
    if (graphLens != null) {
      new InterfaceMethodRewriterFixup(appView, graphLens).run();
    }
  }

  private PostProcessingInterfaceInfo getPostProcessingInterfaceInfo(DexProgramClass iface) {
    return postProcessingInterfaceInfos.computeIfAbsent(
        iface, ignored -> new PostProcessingInterfaceInfo());
  }

  public void forEachMethodToMove(BiConsumer<DexMethod, DexMethod> fn) {
    postProcessingInterfaceInfos.forEach(
        (iface, info) -> {
          if (info.methodsToMove != null) {
            info.methodsToMove.forEach(fn);
          }
        });
  }

  static class PostProcessingInterfaceInfo {
    private Map<DexEncodedMethod, DexEncodedMethod> defaultMethodsToImplementation;
    private Map<DexMethod, DexMethod> methodsToMove;
    private boolean hasNonClinitDirectMethods;
    private boolean hasBridgesToRemove;

    public void mapDefaultMethodToCompanionMethod(
        DexEncodedMethod defaultMethod, DexEncodedMethod companionMethod) {
      if (defaultMethodsToImplementation == null) {
        defaultMethodsToImplementation = new IdentityHashMap<>();
      }
      defaultMethodsToImplementation.put(defaultMethod, companionMethod);
    }

    public Map<DexEncodedMethod, DexEncodedMethod> getDefaultMethodsToImplementation() {
      return defaultMethodsToImplementation;
    }

    boolean hasDefaultMethodsToImplementationMap() {
      return defaultMethodsToImplementation != null;
    }

    public void moveMethod(DexMethod ifaceMethod, DexMethod companionMethod) {
      if (methodsToMove == null) {
        methodsToMove = new IdentityHashMap<>();
      }
      methodsToMove.put(ifaceMethod, companionMethod);
    }

    public Map<DexMethod, DexMethod> getMethodsToMove() {
      return methodsToMove;
    }

    public boolean hasMethodsToMove() {
      return methodsToMove != null;
    }

    boolean hasNonClinitDirectMethods() {
      return hasNonClinitDirectMethods;
    }

    void setHasNonClinitDirectMethods() {
      hasNonClinitDirectMethods = true;
    }

    boolean hasBridgesToRemove() {
      return hasBridgesToRemove;
    }

    void setHasBridgesToRemove() {
      hasBridgesToRemove = true;
    }
  }

  // Specific lens which remaps invocation types to static since all rewrites performed here
  // are to static companion methods.
  // TODO(b/196337368): Replace this by a desugaring lens shared for D8 and R8.
  public static class InterfaceProcessorNestedGraphLens extends NestedGraphLens {

    private final BidirectionalManyToManyRepresentativeMap<DexMethod, DexMethod>
        extraNewMethodSignatures;

    public InterfaceProcessorNestedGraphLens(
        AppView<?> appView,
        BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
        BidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod> methodMap,
        Map<DexType, DexType> typeMap,
        BidirectionalOneToOneMap<DexMethod, DexMethod> extraNewMethodSignatures) {
      super(appView, fieldMap, methodMap, typeMap);
      this.extraNewMethodSignatures = extraNewMethodSignatures;
    }

    public BidirectionalManyToManyRepresentativeMap<DexMethod, DexMethod>
        getExtraNewMethodSignatures() {
      return extraNewMethodSignatures;
    }

    @Override
    public boolean isLegitimateToHaveEmptyMappings() {
      return true;
    }

    @Override
    public DexMethod getPreviousMethodSignature(DexMethod method) {
      return extraNewMethodSignatures.getRepresentativeKeyOrDefault(
          method, newMethodSignatures.getRepresentativeKeyOrDefault(method, method));
    }

    @Override
    public DexMethod getNextMethodSignature(DexMethod method) {
      return newMethodSignatures.getRepresentativeValueOrDefault(
          method, extraNewMethodSignatures.getRepresentativeValueOrDefault(method, method));
    }

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder extends GraphLens.Builder {

      private final MutableBidirectionalOneToOneMap<DexMethod, DexMethod> extraNewMethodSignatures =
          new BidirectionalOneToOneHashMap<>();

      public void recordCodeMovedToCompanionClass(DexMethod from, DexMethod to) {
        extraNewMethodSignatures.put(from, to);
      }

      @Override
      public InterfaceProcessorNestedGraphLens build(AppView<?> appView) {
        if (fieldMap.isEmpty() && methodMap.isEmpty() && extraNewMethodSignatures.isEmpty()) {
          return null;
        }
        return new InterfaceProcessorNestedGraphLens(
            appView, fieldMap, methodMap, typeMap, extraNewMethodSignatures);
      }
    }
  }
}
