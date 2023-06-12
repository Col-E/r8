// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import static com.android.tools.r8.utils.AndroidApiLevelUtils.isApiSafeForMemberRebinding;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.LibraryMethod;
import com.android.tools.r8.graph.MethodAccessInfoCollection;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BiForEachable;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.TriConsumer;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MemberRebindingAnalysis {

  private final AndroidApiLevelCompute androidApiLevelCompute;
  private final AppView<AppInfoWithLiveness> appView;
  private final MemberRebindingEventConsumer eventConsumer;
  private final InternalOptions options;

  private final MemberRebindingLens.Builder lensBuilder;

  public MemberRebindingAnalysis(AppView<AppInfoWithLiveness> appView) {
    assert appView.graphLens().isContextFreeForMethods(appView.codeLens());
    this.androidApiLevelCompute = appView.apiLevelCompute();
    this.appView = appView;
    this.eventConsumer = MemberRebindingEventConsumer.create(appView);
    this.options = appView.options();
    this.lensBuilder = MemberRebindingLens.builder(appView);
  }

  private AppView<AppInfoWithLiveness> appView() {
    return appView;
  }

  private DexMethod validMemberRebindingTargetForNonProgramMethod(
      DexClassAndMethod resolvedMethod,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethodSet contexts,
      InvokeType invokeType,
      DexMethod original) {
    assert !resolvedMethod.isProgramMethod();

    if (invokeType.isDirect()) {
      return original;
    }

    if (invokeType.isSuper() && options.canHaveSuperInvokeBug()) {
      // To preserve semantics we should find the first library method on the boundary.
      DexType firstLibraryTarget =
          firstLibraryClassOrFirstInterfaceTarget(
              resolutionResult.getResolvedHolder(),
              appView,
              resolvedMethod.getReference(),
              original.getHolderType(),
              DexClass::lookupMethod);
      if (firstLibraryTarget == null) {
        return original;
      }
      DexClass libraryHolder = appView.definitionFor(firstLibraryTarget);
      if (libraryHolder == null) {
        return original;
      }
      if (libraryHolder == resolvedMethod.getHolder()) {
        return resolvedMethod.getReference();
      }
      return resolvedMethod.getReference().withHolder(libraryHolder, appView.dexItemFactory());
    }

    LibraryMethod eligibleLibraryMethod = null;
    SingleResolutionResult<?> currentResolutionResult = resolutionResult;
    while (currentResolutionResult != null) {
      DexClassAndMethod currentResolvedMethod = currentResolutionResult.getResolutionPair();
      if (canRebindDirectlyToLibraryMethod(
          currentResolvedMethod,
          currentResolutionResult.withInitialResolutionHolder(
              currentResolutionResult.getResolvedHolder()),
          contexts,
          invokeType,
          original)) {
        eligibleLibraryMethod = currentResolvedMethod.asLibraryMethod();
      }
      if (appView.getAssumeInfoCollection().contains(currentResolvedMethod)) {
        break;
      }
      DexClass currentResolvedHolder = currentResolvedMethod.getHolder();
      if (resolvedMethod.getDefinition().belongsToVirtualPool()
          && !currentResolvedHolder.isInterface()
          && currentResolvedHolder.getSuperType() != null) {
        currentResolutionResult =
            appView
                .appInfo()
                .resolveMethodOnClassLegacy(currentResolvedHolder.getSuperType(), original)
                .asSingleResolution();
      } else {
        break;
      }
    }
    if (eligibleLibraryMethod != null) {
      return eligibleLibraryMethod.getReference();
    }

    DexType newHolder =
        firstLibraryClassOrFirstInterfaceTarget(
            resolvedMethod.getHolder(),
            appView,
            resolvedMethod.getReference(),
            original.getHolderType(),
            DexClass::lookupMethod);
    return newHolder != null ? original.withHolder(newHolder, appView.dexItemFactory()) : original;
  }

  private boolean canRebindDirectlyToLibraryMethod(
      DexClassAndMethod resolvedMethod,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethodSet contexts,
      InvokeType invokeType,
      DexMethod original) {
    // TODO(b/194422791): It could potentially be that `original.holder` is not a subtype of
    //  `original.holder` on all API levels, in which case it is not OK to rebind to the resolved
    //  method.
    return resolvedMethod.isLibraryMethod()
        && isAccessibleInAllContexts(resolvedMethod, resolutionResult, contexts)
        && !isInvokeSuperToInterfaceMethod(resolvedMethod, invokeType)
        && !isInvokeSuperToAbstractMethod(resolvedMethod, invokeType)
        && isApiSafeForMemberRebinding(
            resolvedMethod.asLibraryMethod(), original, androidApiLevelCompute, options);
  }

  private boolean isAccessibleInAllContexts(
      DexClassAndMethod resolvedMethod,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethodSet contexts) {
    if (resolvedMethod.getHolder().isPublic() && resolvedMethod.getDefinition().isPublic()) {
      return true;
    }
    return Iterables.all(
        contexts,
        context -> resolutionResult.isAccessibleFrom(context, appView, appView.appInfo()).isTrue());
  }

  private boolean isInvokeSuperToInterfaceMethod(DexClassAndMethod method, InvokeType invokeType) {
    return method.getHolder().isInterface() && invokeType.isSuper();
  }

  private boolean isInvokeSuperToAbstractMethod(DexClassAndMethod method, InvokeType invokeType) {
    return method.getAccessFlags().isAbstract() && invokeType.isSuper();
  }

  public static DexField validMemberRebindingTargetFor(
      DexDefinitionSupplier definitions, DexClassAndField field, DexField original) {
    if (field.isProgramField()) {
      return field.getReference();
    }
    DexClass fieldHolder = field.getHolder();
    DexType newHolder =
        firstLibraryClassOrFirstInterfaceTarget(
            fieldHolder,
            definitions,
            field.getReference(),
            original.getHolderType(),
            DexClass::lookupField);
    return newHolder != null
        ? original.withHolder(newHolder, definitions.dexItemFactory())
        : original;
  }

  private static <T> DexType firstLibraryClassOrFirstInterfaceTarget(
      DexClass holder,
      DexDefinitionSupplier definitions,
      T target,
      DexType current,
      BiFunction<DexClass, T, ?> lookup) {
    return holder.isInterface()
        ? firstLibraryClassForInterfaceTarget(definitions, target, current, lookup)
        : firstLibraryClass(definitions, current);
  }

  private static <T> DexType firstLibraryClassForInterfaceTarget(
      DexDefinitionSupplier definitions,
      T target,
      DexType current,
      BiFunction<DexClass, T, ?> lookup) {
    DexClass clazz = definitions.definitionFor(current);
    if (clazz == null) {
      return null;
    }
    Object potential = lookup.apply(clazz, target);
    if (potential != null) {
      // Found, return type.
      return current;
    }
    if (clazz.superType != null) {
      DexType matchingSuper =
          firstLibraryClassForInterfaceTarget(definitions, target, clazz.superType, lookup);
      if (matchingSuper != null) {
        // Found in supertype, return first library class.
        return clazz.isNotProgramClass() ? current : matchingSuper;
      }
    }
    for (DexType iface : clazz.getInterfaces()) {
      DexType matchingIface =
          firstLibraryClassForInterfaceTarget(definitions, target, iface, lookup);
      if (matchingIface != null) {
        // Found in interface, return first library class.
        return clazz.isNotProgramClass() ? current : matchingIface;
      }
    }
    return null;
  }

  private static DexType firstLibraryClass(DexDefinitionSupplier definitions, DexType bottom) {
    DexClass searchClass = definitions.contextIndependentDefinitionFor(bottom);
    while (searchClass != null && searchClass.isProgramClass()) {
      searchClass =
          definitions.definitionFor(searchClass.getSuperType(), searchClass.asProgramClass());
    }
    return searchClass != null ? searchClass.getType() : null;
  }

  private MethodResolutionResult resolveMethodOnClass(DexMethod method) {
    return appView.appInfo().resolveMethodOnClassLegacy(method.holder, method);
  }

  private MethodResolutionResult resolveMethodOnInterface(DexMethod method) {
    return appView.appInfo().resolveMethodOnInterfaceLegacy(method.holder, method);
  }

  private MethodResolutionResult resolveMethod(DexMethod method) {
    return appView.appInfo().unsafeResolveMethodDueToDexFormatLegacy(method);
  }

  private void computeMethodRebinding(MethodAccessInfoCollection methodAccessInfoCollection) {
    // Virtual invokes are on classes, so use class resolution.
    computeMethodRebinding(
        methodAccessInfoCollection::forEachVirtualInvoke,
        this::resolveMethodOnClass,
        InvokeType.VIRTUAL);
    // Interface invokes are always on interfaces, so use interface resolution.
    computeMethodRebinding(
        methodAccessInfoCollection::forEachInterfaceInvoke,
        this::resolveMethodOnInterface,
        InvokeType.INTERFACE);
    // Super invokes can be on both kinds, decide using the holder class.
    computeMethodRebinding(
        methodAccessInfoCollection::forEachSuperInvoke, this::resolveMethod, InvokeType.SUPER);
    // Likewise static invokes.
    computeMethodRebinding(
        methodAccessInfoCollection::forEachStaticInvoke, this::resolveMethod, InvokeType.STATIC);
  }

  private void computeMethodRebinding(
      BiForEachable<DexMethod, ProgramMethodSet> methodsWithContexts,
      Function<DexMethod, MethodResolutionResult> resolver,
      InvokeType invokeType) {
    Map<DexProgramClass, List<Pair<DexMethod, DexClassAndMethod>>> bridges =
        new IdentityHashMap<>();
    TriConsumer<DexProgramClass, DexMethod, DexClassAndMethod> addBridge =
        (bridgeHolder, method, target) ->
            bridges
                .computeIfAbsent(bridgeHolder, k -> new ArrayList<>())
                .add(new Pair<>(method, target));

    methodsWithContexts.forEach(
        (method, contexts) -> {
          MethodResolutionResult resolutionResult = resolver.apply(method);
          if (!resolutionResult.isSingleResolution()) {
            return;
          }

          if (method.getHolderType().isArrayType()) {
            assert resolutionResult.getResolvedHolder().getType()
                == appView.dexItemFactory().objectType;
            lensBuilder.map(
                method, resolutionResult.getResolvedMethod().getReference(), invokeType);
            return;
          }

          // TODO(b/128404854) Rebind to the lowest library class or program class. For now we allow
          //  searching in library for methods, but this should be done on classpath instead.
          DexClassAndMethod resolvedMethod = resolutionResult.getResolutionPair();
          if (resolvedMethod.getReference() == method) {
            return;
          }

          DexClass initialResolutionHolder = resolutionResult.getInitialResolutionHolder();
          DexMethod bridgeMethod = null;
          if (initialResolutionHolder.isProgramClass()) {
            // In Java bytecode, it is only possible to target interface methods that are in one of
            // the immediate super-interfaces via a super-invocation (see
            // IndirectSuperInterfaceTest).
            // To avoid introducing an IncompatibleClassChangeError at runtime we therefore insert a
            // bridge method when we are about to rebind to an interface method that is not the
            // original target.
            if (needsBridgeForInterfaceMethod(
                initialResolutionHolder, resolvedMethod, invokeType)) {
              bridgeMethod =
                  insertBridgeForInterfaceMethod(
                      method, resolvedMethod, initialResolutionHolder.asProgramClass(), addBridge);
            } else {
              // If the target class is not public but the targeted method is, we might run into
              // visibility problems when rebinding.
              if (contexts.stream()
                  .anyMatch(context -> mayNeedBridgeForVisibility(context, resolvedMethod))) {
                bridgeMethod =
                    insertBridgeForVisibilityIfNeeded(
                        method, resolvedMethod, initialResolutionHolder, addBridge);
              }
            }
          }
          if (bridgeMethod != null) {
            lensBuilder.map(method, bridgeMethod, invokeType);
          } else if (resolvedMethod.isProgramMethod()) {
            lensBuilder.map(method, resolvedMethod.getReference(), invokeType);
          } else {
            lensBuilder.map(
                method,
                validMemberRebindingTargetForNonProgramMethod(
                    resolvedMethod,
                    resolutionResult.asSingleResolution(),
                    contexts,
                    invokeType,
                    method),
                invokeType);
          }
        });

    bridges.forEach(
        (bridgeHolder, targets) -> {
          // Sorting the list of bridges within a class maintains a deterministic order of entries
          // in the method collection.
          targets.sort(Comparator.comparing(Pair::getFirst));
          for (Pair<DexMethod, DexClassAndMethod> pair : targets) {
            DexMethod method = pair.getFirst();
            DexClassAndMethod target = pair.getSecond();
            DexMethod bridgeMethod =
                method.withHolder(bridgeHolder.getType(), appView.dexItemFactory());
            if (bridgeHolder.getMethodCollection().getMethod(bridgeMethod) == null) {
              DexEncodedMethod targetDefinition = target.getDefinition();
              DexEncodedMethod bridgeMethodDefinition =
                  targetDefinition.toForwardingMethod(
                      bridgeHolder,
                      appView,
                      builder -> {
                        if (!targetDefinition.isAbstract()
                            && targetDefinition.getApiLevelForCode().isNotSetApiLevel()) {
                          assert target.isLibraryMethod()
                              || !appView.options().apiModelingOptions().enableLibraryApiModeling;
                          builder.setApiLevelForCode(
                              appView
                                  .apiLevelCompute()
                                  .computeApiLevelForLibraryReference(
                                      targetDefinition.getReference(),
                                      appView.computedMinApiLevel()));
                        }
                        builder.setIsLibraryMethodOverrideIf(
                            target.isLibraryMethod(), OptionalBool.TRUE);
                      });
              bridgeHolder.addMethod(bridgeMethodDefinition);
              eventConsumer.acceptMemberRebindingBridgeMethod(
                  bridgeMethodDefinition.asProgramMethod(bridgeHolder), target);
            }
            assert resolver.apply(method).getResolvedMethod().getReference() == bridgeMethod;
          }
        });
  }

  private boolean needsBridgeForInterfaceMethod(
      DexClass originalClass, DexClassAndMethod method, InvokeType invokeType) {
    return options.isGeneratingClassFiles()
        && invokeType == InvokeType.SUPER
        && method.getHolder() != originalClass
        && method.getHolder().isInterface();
  }

  private DexMethod insertBridgeForInterfaceMethod(
      DexMethod method,
      DexClassAndMethod target,
      DexProgramClass originalClass,
      TriConsumer<DexProgramClass, DexMethod, DexClassAndMethod> bridges) {
    // If `targetClass` is a class, then insert the bridge method on the upper-most super class that
    // implements the interface. Otherwise, if it is an interface, then insert the bridge method
    // directly on the interface (because that interface must be the immediate super type, assuming
    // that the super-invocation is not broken in advance).
    //
    // Note that, to support compiling from DEX to CF, we would need to rewrite the targets of
    // invoke-super instructions that hit indirect interface methods such that they always target
    // a method in an immediate super-interface, since this works on Art but not on the JVM.
    DexProgramClass bridgeHolder =
        findHolderForInterfaceMethodBridge(originalClass, target.getHolderType());
    assert bridgeHolder != null;
    assert bridgeHolder != target.getHolder();
    bridges.accept(bridgeHolder, method, target);
    return target.getReference().withHolder(bridgeHolder.getType(), appView.dexItemFactory());
  }

  private DexProgramClass findHolderForInterfaceMethodBridge(DexProgramClass clazz, DexType iface) {
    if (clazz.accessFlags.isInterface()) {
      return clazz;
    }
    DexClass superClass = appView.definitionFor(clazz.superType);
    if (superClass == null
        || superClass.isNotProgramClass()
        || !appView.appInfo().isSubtype(superClass.type, iface)) {
      return clazz;
    }
    return findHolderForInterfaceMethodBridge(superClass.asProgramClass(), iface);
  }

  private boolean mayNeedBridgeForVisibility(ProgramMethod context, DexClassAndMethod method) {
    DexType holderType = method.getHolderType();
    DexClass holder = appView.definitionFor(holderType);
    if (holder == null) {
      return false;
    }
    ConstraintWithTarget classVisibility =
        ConstraintWithTarget.deriveConstraint(
            context, holderType, holder.getAccessFlags(), appView);
    ConstraintWithTarget methodVisibility =
        ConstraintWithTarget.deriveConstraint(
            context, holderType, method.getAccessFlags(), appView);
    // We may need bridge for visibility if the target class is not visible while the target method
    // is visible from the calling context.
    return classVisibility == ConstraintWithTarget.NEVER
        && methodVisibility != ConstraintWithTarget.NEVER;
  }

  private DexMethod insertBridgeForVisibilityIfNeeded(
      DexMethod method,
      DexClassAndMethod target,
      DexClass originalClass,
      TriConsumer<DexProgramClass, DexMethod, DexClassAndMethod> bridges) {
    // If the original class is public and this method is public, it might have been called
    // from anywhere, so we need a bridge. Likewise, if the original is in a different
    // package, we might need a bridge, too.
    String packageDescriptor =
        originalClass.accessFlags.isPublic() ? null : method.holder.getPackageDescriptor();
    if (packageDescriptor == null
        || !packageDescriptor.equals(target.getHolderType().getPackageDescriptor())) {
      DexProgramClass bridgeHolder =
          findHolderForVisibilityBridge(originalClass, target.getHolder(), packageDescriptor);
      assert bridgeHolder != null;
      bridges.accept(bridgeHolder, method, target);
      return target.getReference().withHolder(bridgeHolder.getType(), appView.dexItemFactory());
    }
    return target.getReference();
  }

  private DexProgramClass findHolderForVisibilityBridge(
      DexClass originalClass, DexClass targetClass, String packageDescriptor) {
    if (originalClass == targetClass || originalClass.isNotProgramClass()) {
      return null;
    }
    DexProgramClass newHolder = null;
    // Recurse through supertype chain.
    if (appView.appInfo().isSubtype(originalClass.superType, targetClass.type)) {
      DexClass superClass = appView.definitionFor(originalClass.superType);
      newHolder = findHolderForVisibilityBridge(superClass, targetClass, packageDescriptor);
    } else {
      for (DexType iface : originalClass.interfaces.values) {
        if (appView.appInfo().isSubtype(iface, targetClass.type)) {
          DexClass interfaceClass = appView.definitionFor(iface);
          newHolder = findHolderForVisibilityBridge(interfaceClass, targetClass, packageDescriptor);
        }
      }
    }
    if (newHolder != null) {
      // A supertype fulfills the visibility requirements.
      return newHolder;
    } else if (originalClass.accessFlags.isPublic()
        || originalClass.type.getPackageDescriptor().equals(packageDescriptor)) {
      // This class is visible. Return it if it is a program class, otherwise null.
      return originalClass.asProgramClass();
    }
    return null;
  }

  private void recordNonReboundFieldAccesses(ExecutorService executorService)
      throws ExecutionException {
    assert verifyFieldAccessCollectionContainsAllNonReboundFieldReferences(executorService);
    FieldAccessInfoCollection<?> fieldAccessInfoCollection =
        appView.appInfo().getFieldAccessInfoCollection();
    fieldAccessInfoCollection.forEach(lensBuilder::recordNonReboundFieldAccesses);
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    AppInfoWithLiveness appInfo = appView.appInfo();
    computeMethodRebinding(appInfo.getMethodAccessInfoCollection());
    recordNonReboundFieldAccesses(executorService);
    appInfo.getFieldAccessInfoCollection().flattenAccessContexts();
    MemberRebindingLens memberRebindingLens = lensBuilder.build();
    appView.setGraphLens(memberRebindingLens);
    eventConsumer.finished(appView, memberRebindingLens);
    appView.notifyOptimizationFinishedForTesting();
  }

  private boolean verifyFieldAccessCollectionContainsAllNonReboundFieldReferences(
      ExecutorService executorService) throws ExecutionException {
    Set<DexField> nonReboundFieldReferences = computeNonReboundFieldReferences(executorService);
    FieldAccessInfoCollection<?> fieldAccessInfoCollection =
        appView.appInfo().getFieldAccessInfoCollection();
    fieldAccessInfoCollection.forEach(
        info -> {
          DexField reboundFieldReference = info.getField();
          info.forEachIndirectAccess(
              nonReboundFieldReference -> {
                assert reboundFieldReference != nonReboundFieldReference;
                assert reboundFieldReference
                    == appView
                        .appInfo()
                        .resolveField(nonReboundFieldReference)
                        .getResolvedFieldReference();
                nonReboundFieldReferences.remove(nonReboundFieldReference);
              });
        });
    assert nonReboundFieldReferences.isEmpty();
    return true;
  }

  private Set<DexField> computeNonReboundFieldReferences(ExecutorService executorService)
      throws ExecutionException {
    Set<DexField> nonReboundFieldReferences = Sets.newConcurrentHashSet();
    ThreadUtils.processItems(
        appView.appInfo()::forEachMethod,
        method -> {
          if (method.getDefinition().hasCode()) {
            method.registerCodeReferences(
                new UseRegistry<ProgramMethod>(appView, method) {

                  @Override
                  public void registerInstanceFieldRead(DexField field) {
                    registerFieldReference(field);
                  }

                  @Override
                  public void registerInstanceFieldWrite(DexField field) {
                    registerFieldReference(field);
                  }

                  @Override
                  public void registerStaticFieldRead(DexField field) {
                    registerFieldReference(field);
                  }

                  @Override
                  public void registerStaticFieldWrite(DexField field) {
                    registerFieldReference(field);
                  }

                  private void registerFieldReference(DexField field) {
                    appView()
                        .appInfo()
                        .resolveField(field)
                        .forEachSuccessfulFieldResolutionResult(
                            resolutionResult -> {
                              if (resolutionResult.getResolvedField().getReference() != field) {
                                nonReboundFieldReferences.add(field);
                              }
                            });
                  }

                  @Override
                  public void registerInitClass(DexType type) {
                    // Intentionally empty.
                  }

                  @Override
                  public void registerInvokeDirect(DexMethod method) {
                    // Intentionally empty.
                  }

                  @Override
                  public void registerInvokeInterface(DexMethod method) {
                    // Intentionally empty.
                  }

                  @Override
                  public void registerInvokeStatic(DexMethod method) {
                    // Intentionally empty.
                  }

                  @Override
                  public void registerInvokeSuper(DexMethod method) {
                    // Intentionally empty.
                  }

                  @Override
                  public void registerInvokeVirtual(DexMethod method) {
                    // Intentionally empty.
                  }

                  @Override
                  public void registerNewInstance(DexType type) {
                    // Intentionally empty.
                  }

                  @Override
                  public void registerInstanceOf(DexType type) {
                    // Intentionally empty.
                  }

                  @Override
                  public void registerTypeReference(DexType type) {
                    // Intentionally empty.
                  }
                });
          }
        },
        executorService);
    return nonReboundFieldReferences;
  }
}
