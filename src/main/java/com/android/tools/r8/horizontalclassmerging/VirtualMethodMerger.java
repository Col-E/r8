// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class VirtualMethodMerger {

  static class SuperMethodReference {

    DexMethod reference;
    DexMethod reboundReference;

    SuperMethodReference(DexMethod reference, DexMethod reboundReference) {
      this.reference = reference;
      this.reboundReference = reboundReference;
    }

    public DexMethod getReference() {
      return reference;
    }

    public DexMethod getReboundReference() {
      return reboundReference;
    }
  }

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final DexItemFactory dexItemFactory;
  private final MergeGroup group;
  private final List<ProgramMethod> methods;
  private final SuperMethodReference superMethod;

  public VirtualMethodMerger(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      MergeGroup group,
      List<ProgramMethod> methods,
      SuperMethodReference superMethod) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.group = group;
    this.methods = methods;
    this.superMethod = superMethod;
  }

  public static class Builder {
    private final List<ProgramMethod> methods = new ArrayList<>();

    public Builder add(ProgramMethod method) {
      methods.add(method);
      return this;
    }

    /** Get the super method handle if this method overrides a parent method. */
    private SuperMethodReference superMethod(
        AppView<? extends AppInfoWithClassHierarchy> appView, MergeGroup group) {
      DexMethod template = methods.iterator().next().getReference();
      SingleResolutionResult<?> resolutionResult =
          appView
              .appInfo()
              .resolveMethodOnClassLegacy(group.getSuperType(), template)
              .asSingleResolution();
      if (resolutionResult == null || resolutionResult.getResolvedMethod().isAbstract()) {
        // If there is no super method or the method is abstract it should not be called.
        return null;
      }
      DexMethod reboundReference = resolutionResult.getResolvedMethod().getReference();
      DexMethod reference =
          resolutionResult.getResolvedHolder().isInterface()
              ? resolutionResult
                  .getResolvedMethod()
                  .getReference()
                  .withHolder(group.getSuperType(), appView.dexItemFactory())
              : reboundReference;
      return new SuperMethodReference(reference, reboundReference);
    }

    public VirtualMethodMerger build(
        AppView<? extends AppInfoWithClassHierarchy> appView, MergeGroup group) {
      // If not all the classes are in the merge group, find the fallback super method to call.
      SuperMethodReference superMethod =
          methods.size() < group.size() ? superMethod(appView, group) : null;
      return new VirtualMethodMerger(appView, group, methods, superMethod);
    }
  }

  public DexMethod getMethodReference() {
    return methods.iterator().next().getReference();
  }

  public int getArity() {
    return getMethodReference().getArity();
  }

  private DexMethod moveMethod(ClassMethodsBuilder classMethodsBuilder, ProgramMethod oldMethod) {
    DexMethod oldMethodReference = oldMethod.getReference();
    DexMethod method =
        dexItemFactory.createFreshMethodNameWithHolder(
            oldMethodReference.name.toSourceString(),
            oldMethod.getHolderType(),
            oldMethodReference.proto,
            group.getTarget().getType(),
            classMethodsBuilder::isFresh);

    DexEncodedMethod encodedMethod =
        oldMethod.getDefinition().toTypeSubstitutedMethodAsInlining(method, dexItemFactory);
    MethodAccessFlags flags = encodedMethod.getAccessFlags();
    flags.unsetProtected();
    flags.unsetPublic();
    flags.setPrivate();
    classMethodsBuilder.addDirectMethod(encodedMethod);

    return encodedMethod.getReference();
  }

  private MethodAccessFlags getAccessFlags() {
    Iterable<MethodAccessFlags> allFlags =
        Iterables.transform(methods, ProgramMethod::getAccessFlags);
    MethodAccessFlags result = allFlags.iterator().next().copy();
    assert Iterables.all(allFlags, flags -> !flags.isNative());
    assert !result.isStrict() || Iterables.all(allFlags, MethodAccessFlags::isStrict);
    assert !result.isSynchronized() || Iterables.all(allFlags, MethodAccessFlags::isSynchronized);
    if (result.isAbstract() && Iterables.any(allFlags, flags -> !flags.isAbstract())) {
      result.unsetAbstract();
    }
    if (result.isBridge() && Iterables.any(allFlags, flags -> !flags.isBridge())) {
      result.unsetBridge();
    }
    if (result.isFinal()) {
      if (methods.size() < group.size() || Iterables.any(allFlags, flags -> !flags.isFinal())) {
        result.unsetFinal();
      }
    }
    if (result.isSynthetic() && Iterables.any(allFlags, flags -> !flags.isSynthetic())) {
      result.unsetSynthetic();
    }
    if (result.isVarargs() && Iterables.any(allFlags, flags -> !flags.isVarargs())) {
      result.unsetVarargs();
    }
    result.unsetDeclaredSynchronized();
    return result;
  }

  private DexMethod getNewMethodReference() {
    return ListUtils.first(methods).getReference().withHolder(group.getTarget(), dexItemFactory);
  }

  /**
   * If there is a super method and all methods are abstract, then we can simply remove all abstract
   * methods.
   */
  private boolean isNop() {
    return superMethod != null
        && Iterables.all(methods, method -> method.getDefinition().isAbstract());
  }

  /**
   * If the method is present on all classes in the merge group, and there is at most one
   * non-abstract method, then we can simply move that method (or the first abstract method) to the
   * target class.
   */
  private boolean isTrivial() {
    if (superMethod != null) {
      return false;
    }
    if (methods.size() == 1) {
      return true;
    }
    int numberOfNonAbstractMethods =
        Iterables.size(Iterables.filter(methods, method -> !method.getDefinition().isAbstract()));
    return numberOfNonAbstractMethods <= 1;
  }

  boolean isNopOrTrivial() {
    return isNop() || isTrivial();
  }

  /**
   * If there is only a single method that does not override anything then it is safe to just move
   * it to the target type if it is not already in it.
   */
  @SuppressWarnings("ReferenceEquality")
  private void mergeTrivial(
      ClassMethodsBuilder classMethodsBuilder, HorizontalClassMergerGraphLens.Builder lensBuilder) {
    DexMethod newMethodReference = getNewMethodReference();

    // Find the first non-abstract method. If all are abstract, then select the first method.
    ProgramMethod representative =
        Iterables.find(methods, method -> !method.getDefinition().isAbstract(), null);
    if (representative == null) {
      representative = ListUtils.first(methods);
    }

    if (representative.getAccessFlags().isAbstract() && superMethod != null) {
      methods.forEach(method -> lensBuilder.mapMethod(method.getReference(), newMethodReference));
      return;
    }

    for (ProgramMethod method : methods) {
      if (method.getReference() == representative.getReference()) {
        lensBuilder.moveMethod(method.getReference(), newMethodReference);
      } else {
        lensBuilder.mapMethod(method.getReference(), newMethodReference);
      }
    }

    DexEncodedMethod newMethod;
    if (representative.getHolder() == group.getTarget()) {
      newMethod = representative.getDefinition();
    } else {
      // If the method is not in the target type, move it.
      OptionalBool isLibraryMethodOverride =
          representative.getDefinition().isLibraryMethodOverride();
      newMethod =
          representative
              .getDefinition()
              .toTypeSubstitutedMethodAsInlining(
                  newMethodReference,
                  dexItemFactory,
                  builder -> builder.setIsLibraryMethodOverrideIfKnown(isLibraryMethodOverride));
    }

    newMethod.getAccessFlags().unsetFinal();

    classMethodsBuilder.addVirtualMethod(newMethod);
  }

  public void merge(
      ProfileCollectionAdditions profileCollectionAdditions,
      ClassMethodsBuilder classMethodsBuilder,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      Reference2IntMap<DexType> classIdentifiers,
      Consumer<VirtuallyMergedMethodsKeepInfo> virtuallyMergedMethodsKeepInfoConsumer) {
    assert !methods.isEmpty();

    // Handle trivial merges.
    if (isNopOrTrivial()) {
      mergeTrivial(classMethodsBuilder, lensBuilder);
      return;
    }

    Int2ReferenceSortedMap<DexMethod> classIdToMethodMap = new Int2ReferenceAVLTreeMap<>();

    CfVersion classFileVersion = null;
    ProgramMethod representative = null;
    for (ProgramMethod method : methods) {
      if (method.getDefinition().isAbstract()) {
        continue;
      }
      if (method.getDefinition().hasClassFileVersion()) {
        CfVersion methodVersion = method.getDefinition().getClassFileVersion();
        classFileVersion = Ordered.maxIgnoreNull(classFileVersion, methodVersion);
      }
      DexMethod newMethod = moveMethod(classMethodsBuilder, method);
      lensBuilder.recordNewMethodSignature(method.getReference(), newMethod);
      classIdToMethodMap.put(
          classIdentifiers.getInt(method.getHolderType()), method.getReference());
      if (representative == null) {
        representative = method;
      }
    }

    assert representative != null;

    // Use the first of the original methods as the original method for the merged constructor.
    DexMethod originalMethodReference =
        appView.graphLens().getOriginalMethodSignature(representative.getReference());
    DexMethod bridgeMethodReference =
        dexItemFactory.createFreshMethodNameWithoutHolder(
            originalMethodReference.getName().toSourceString() + "$bridge",
            originalMethodReference.proto,
            originalMethodReference.getHolderType(),
            classMethodsBuilder::isFresh);
    DexEncodedMethod representativeMethod = representative.getDefinition();
    DexMethod newMethodReference = getNewMethodReference();
    IncompleteVirtuallyMergedMethodCode synthesizedCode =
        new IncompleteVirtuallyMergedMethodCode(
            group.getClassIdField(), classIdToMethodMap, originalMethodReference, superMethod);
    DexEncodedMethod newMethod =
        DexEncodedMethod.syntheticBuilder()
            .setMethod(newMethodReference)
            .setAccessFlags(getAccessFlags())
            .setCode(synthesizedCode)
            .setClassFileVersion(classFileVersion)
            .setApiLevelForDefinition(representativeMethod.getApiLevelForDefinition())
            .setApiLevelForCode(representativeMethod.getApiLevelForCode())
            .build();
    if (!representative.getDefinition().isLibraryMethodOverride().isUnknown()) {
      newMethod.setLibraryMethodOverride(representative.getDefinition().isLibraryMethodOverride());
    }

    // Map each old non-abstract method to the newly synthesized method in the graph lens.
    VirtuallyMergedMethodsKeepInfo virtuallyMergedMethodsKeepInfo =
        new VirtuallyMergedMethodsKeepInfo(representative.getReference());
    for (ProgramMethod oldMethod : methods) {
      lensBuilder.mapMethod(oldMethod.getReference(), newMethodReference);
      virtuallyMergedMethodsKeepInfo.amendKeepInfo(appView.getKeepInfo(oldMethod));
    }

    // The super method reference is not guaranteed to be rebound to a definition. To ensure correct
    // lens code rewriting we need to disable proto normalization until lens code rewriting no
    // longer relies on member rebinding (b/182129249).
    if (superMethod != null) {
      virtuallyMergedMethodsKeepInfo.getKeepInfo().disallowParameterReordering();
    }

    // Add a mapping from a synthetic name to the synthetic merged method.
    lensBuilder.recordNewMethodSignature(bridgeMethodReference, newMethodReference);

    // Amend the art profile collection.
    if (!profileCollectionAdditions.isNop()) {
      for (ProgramMethod oldMethod : methods) {
        profileCollectionAdditions.applyIfContextIsInProfile(
            oldMethod.getReference(),
            additionsBuilder -> additionsBuilder.addRule(representativeMethod.getReference()));
      }
    }

    classMethodsBuilder.addVirtualMethod(newMethod);

    if (!virtuallyMergedMethodsKeepInfo.getKeepInfo().isBottom()) {
      virtuallyMergedMethodsKeepInfoConsumer.accept(virtuallyMergedMethodsKeepInfo);
    }
  }
}
