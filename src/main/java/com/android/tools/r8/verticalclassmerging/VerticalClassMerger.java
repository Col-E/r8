// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.AndroidApiLevelUtils.getApiReferenceLevelForMerging;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.features.FeatureSplitBoundaryOptimizationUtils;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.LookupResult.LookupResultSuccess;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.TopDownClassHierarchyTraversal;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.profile.art.ArtProfileCompletenessChecker;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.FieldSignatureEquivalence;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Merges Supertypes with a single implementation into their single subtype.
 *
 * <p>A common use-case for this is to merge an interface into its single implementation.
 *
 * <p>The class merger only fixes the structure of the graph but leaves the actual instructions
 * untouched. Fixup of instructions is deferred via a {@link GraphLens} to the IR building phase.
 */
public class VerticalClassMerger {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;
  private Collection<DexMethod> invokes;

  // Set of merge candidates. Note that this must have a deterministic iteration order.
  private final Set<DexProgramClass> mergeCandidates = new LinkedHashSet<>();

  // Map from source class to target class.
  private final MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses =
      BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();

  private final MutableBidirectionalManyToOneMap<DexType, DexType> mergedInterfaces =
      BidirectionalManyToOneHashMap.newIdentityHashMap();

  // Set of types that must not be merged into their subtype.
  private final Set<DexProgramClass> pinnedClasses = Sets.newIdentityHashSet();

  // The resulting graph lens that should be used after class merging.
  private final VerticalClassMergerGraphLens.Builder lensBuilder;

  // All the bridge methods that have been synthesized during vertical class merging.
  private final List<SynthesizedBridgeCode> synthesizedBridges = new ArrayList<>();

  private final MainDexInfo mainDexInfo;

  public VerticalClassMerger(AppView<AppInfoWithLiveness> appView) {
    AppInfoWithLiveness appInfo = appView.appInfo();
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options();
    this.mainDexInfo = appInfo.getMainDexInfo();
    this.lensBuilder = new VerticalClassMergerGraphLens.Builder(dexItemFactory);
  }

  private void initializeMergeCandidates(ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    for (DexProgramClass sourceClass : appView.appInfo().classesWithDeterministicOrder()) {
      List<DexProgramClass> subclasses = immediateSubtypingInfo.getSubclasses(sourceClass);
      if (subclasses.size() != 1) {
        continue;
      }
      DexProgramClass targetClass = ListUtils.first(subclasses);
      if (!isMergeCandidate(sourceClass, targetClass)) {
        continue;
      }
      if (!isStillMergeCandidate(sourceClass, targetClass)) {
        continue;
      }
      if (mergeMayLeadToIllegalAccesses(sourceClass, targetClass)) {
        continue;
      }
      mergeCandidates.add(sourceClass);
    }
  }

  // Returns a set of types that must not be merged into other types.
  private void initializePinnedTypes() {
    // For all pinned fields, also pin the type of the field (because changing the type of the field
    // implicitly changes the signature of the field). Similarly, for all pinned methods, also pin
    // the return type and the parameter types of the method.
    // TODO(b/156715504): Compute referenced-by-pinned in the keep info objects.
    List<DexReference> pinnedItems = new ArrayList<>();
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    keepInfo.forEachPinnedType(pinnedItems::add, options);
    keepInfo.forEachPinnedMethod(pinnedItems::add, options);
    keepInfo.forEachPinnedField(pinnedItems::add, options);
    extractPinnedItems(pinnedItems);

    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (Iterables.any(clazz.methods(), method -> method.getAccessFlags().isNative())) {
        markClassAsPinned(clazz);
      }
    }

    // It is valid to have an invoke-direct instruction in a default interface method that targets
    // another default method in the same interface (see InterfaceMethodDesugaringTests.testInvoke-
    // SpecialToDefaultMethod). However, in a class, that would lead to a verification error.
    // Therefore, we disallow merging such interfaces into their subtypes.
    for (DexMethod signature : appView.appInfo().getVirtualMethodsTargetedByInvokeDirect()) {
      markTypeAsPinned(signature.getHolderType());
    }

    // The set of targets that must remain for proper resolution error cases should not be merged.
    // TODO(b/192821424): Can be removed if handled.
    extractPinnedItems(appView.appInfo().getFailedMethodResolutionTargets());
  }

  private <T extends DexReference> void extractPinnedItems(Iterable<T> items) {
    for (DexReference item : items) {
      if (item.isDexType()) {
        markTypeAsPinned(item.asDexType());
      } else if (item.isDexField()) {
        // Pin the holder and the type of the field.
        DexField field = item.asDexField();
        markTypeAsPinned(field.getHolderType());
        markTypeAsPinned(field.getType());
      } else {
        assert item.isDexMethod();
        // Pin the holder, the return type and the parameter types of the method. If we were to
        // merge any of these types into their sub classes, then we would implicitly change the
        // signature of this method.
        DexMethod method = item.asDexMethod();
        markTypeAsPinned(method.getHolderType());
        markTypeAsPinned(method.getReturnType());
        for (DexType parameterType : method.getParameters()) {
          markTypeAsPinned(parameterType);
        }
      }
    }
  }

  private void markTypeAsPinned(DexType type) {
    DexType baseType = type.toBaseType(dexItemFactory);
    if (!baseType.isClassType() || appView.appInfo().isPinnedWithDefinitionLookup(baseType)) {
      // We check for the case where the type is pinned according to appInfo.isPinned,
      // so we only need to add it here if it is not the case.
      return;
    }

    DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(baseType));
    if (clazz != null) {
      markClassAsPinned(clazz);
    }
  }

  private void markClassAsPinned(DexProgramClass clazz) {
    pinnedClasses.add(clazz);
  }

  // Returns true if [clazz] is a merge candidate. Note that the result of the checks in this
  // method do not change in response to any class merges.
  private boolean isMergeCandidate(DexProgramClass sourceClass, DexProgramClass targetClass) {
    assert targetClass != null;
    ObjectAllocationInfoCollection allocationInfo =
        appView.appInfo().getObjectAllocationInfoCollection();
    if (allocationInfo.isInstantiatedDirectly(sourceClass)
        || allocationInfo.isInterfaceWithUnknownSubtypeHierarchy(sourceClass)
        || allocationInfo.isImmediateInterfaceOfInstantiatedLambda(sourceClass)
        || appView.getKeepInfo(sourceClass).isPinned(options)
        || pinnedClasses.contains(sourceClass)
        || appView.appInfo().isNoVerticalClassMergingOfType(sourceClass)) {
      return false;
    }

    assert sourceClass
        .traverseProgramMembers(
            member -> {
              assert !appView.getKeepInfo(member).isPinned(options);
              return TraversalContinuation.doContinue();
            })
        .shouldContinue();

    if (!FeatureSplitBoundaryOptimizationUtils.isSafeForVerticalClassMerging(
        sourceClass, targetClass, appView)) {
      return false;
    }
    if (appView.appServices().allServiceTypes().contains(sourceClass.getType())
        && appView.getKeepInfo(targetClass).isPinned(options)) {
      return false;
    }
    if (sourceClass.isAnnotation()) {
      return false;
    }
    if (!sourceClass.isInterface()
        && targetClass.isSerializable(appView)
        && !appView.appInfo().isSerializable(sourceClass.getType())) {
      // https://docs.oracle.com/javase/8/docs/platform/serialization/spec/serial-arch.html
      //   1.10 The Serializable Interface
      //   ...
      //   A Serializable class must do the following:
      //   ...
      //     * Have access to the no-arg constructor of its first non-serializable superclass
      return false;
    }

    // If there is a constructor in the target, make sure that all source constructors can be
    // inlined.
    if (!Iterables.isEmpty(targetClass.programInstanceInitializers())) {
      TraversalContinuation<?, ?> result =
          sourceClass.traverseProgramInstanceInitializers(
              method -> TraversalContinuation.breakIf(disallowInlining(method, targetClass)));
      if (result.shouldBreak()) {
        return false;
      }
    }
    if (sourceClass.getEnclosingMethodAttribute() != null
        || !sourceClass.getInnerClasses().isEmpty()) {
      // TODO(b/147504070): Consider merging of enclosing-method and inner-class attributes.
      return false;
    }
    // We abort class merging when merging across nests or from a nest to non-nest.
    // Without nest this checks null == null.
    if (ObjectUtils.notIdentical(targetClass.getNestHost(), sourceClass.getNestHost())) {
      return false;
    }

    // If there is an invoke-special to a default interface method and we are not merging into an
    // interface, then abort, since invoke-special to a virtual class method requires desugaring.
    if (sourceClass.isInterface() && !targetClass.isInterface()) {
      TraversalContinuation<?, ?> result =
          sourceClass.traverseProgramMethods(
              method -> {
                boolean foundInvokeSpecialToDefaultLibraryMethod =
                    method.registerCodeReferencesWithResult(
                        new InvokeSpecialToDefaultLibraryMethodUseRegistry(appView, method));
                return TraversalContinuation.breakIf(foundInvokeSpecialToDefaultLibraryMethod);
              });
      if (result.shouldBreak()) {
        return false;
      }
    }
    return true;
  }

  // Returns true if [clazz] is a merge candidate. Note that the result of the checks in this
  // method may change in response to class merges. Therefore, this method should always be called
  // before merging [clazz] into its subtype.
  private boolean isStillMergeCandidate(DexProgramClass sourceClass, DexProgramClass targetClass) {
    assert isMergeCandidate(sourceClass, targetClass);
    assert !mergedClasses.containsValue(sourceClass.getType());
    // For interface types, this is more complicated, see:
    // https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-5.html#jvms-5.5
    // We basically can't move the clinit, since it is not called when implementing classes have
    // their clinit called - except when the interface has a default method.
    if ((sourceClass.hasClassInitializer() && targetClass.hasClassInitializer())
        || targetClass.classInitializationMayHaveSideEffects(
            appView, type -> type.isIdenticalTo(sourceClass.getType()))
        || (sourceClass.isInterface()
            && sourceClass.classInitializationMayHaveSideEffects(appView))) {
      // TODO(herhut): Handle class initializers.
      return false;
    }
    boolean sourceCanBeSynchronizedOn =
        appView.appInfo().isLockCandidate(sourceClass)
            || sourceClass.hasStaticSynchronizedMethods();
    boolean targetCanBeSynchronizedOn =
        appView.appInfo().isLockCandidate(targetClass)
            || targetClass.hasStaticSynchronizedMethods();
    if (sourceCanBeSynchronizedOn && targetCanBeSynchronizedOn) {
      return false;
    }
    if (targetClass.getEnclosingMethodAttribute() != null
        || !targetClass.getInnerClasses().isEmpty()) {
      // TODO(b/147504070): Consider merging of enclosing-method and inner-class attributes.
      return false;
    }
    if (methodResolutionMayChange(sourceClass, targetClass)) {
      return false;
    }
    // Field resolution first considers the direct interfaces of [targetClass] before it proceeds
    // to the super class.
    if (fieldResolutionMayChange(sourceClass, targetClass)) {
      return false;
    }
    // Only merge if api reference level of source class is equal to target class. The check is
    // somewhat expensive.
    if (appView.options().apiModelingOptions().isApiCallerIdentificationEnabled()) {
      AndroidApiLevelCompute apiLevelCompute = appView.apiLevelCompute();
      ComputedApiLevel sourceApiLevel =
          getApiReferenceLevelForMerging(apiLevelCompute, sourceClass);
      ComputedApiLevel targetApiLevel =
          getApiReferenceLevelForMerging(apiLevelCompute, targetClass);
      if (!sourceApiLevel.equals(targetApiLevel)) {
        return false;
      }
    }
    return true;
  }

  private boolean mergeMayLeadToIllegalAccesses(DexProgramClass source, DexProgramClass target) {
    if (source.isSamePackage(target)) {
      // When merging two classes from the same package, we only need to make sure that [source]
      // does not get less visible, since that could make a valid access to [source] from another
      // package illegal after [source] has been merged into [target].
      assert source.getAccessFlags().isPackagePrivateOrPublic();
      assert target.getAccessFlags().isPackagePrivateOrPublic();
      // TODO(b/287891322): Allow merging if `source` is only accessed from inside its own package.
      return source.getAccessFlags().isPublic() && target.getAccessFlags().isPackagePrivate();
    }

    // Check that all accesses to [source] and its members from inside the current package of
    // [source] will continue to work. This is guaranteed if [target] is public and all members of
    // [source] are either private or public.
    //
    // (Deliberately not checking all accesses to [source] since that would be expensive.)
    if (!target.isPublic()) {
      return true;
    }
    for (DexType sourceInterface : source.getInterfaces()) {
      DexClass sourceInterfaceClass = appView.definitionFor(sourceInterface);
      if (sourceInterfaceClass != null && !sourceInterfaceClass.isPublic()) {
        return true;
      }
    }
    for (DexEncodedField field : source.fields()) {
      if (!(field.isPublic() || field.isPrivate())) {
        return true;
      }
    }
    for (DexEncodedMethod method : source.methods()) {
      if (!(method.isPublic() || method.isPrivate())) {
        return true;
      }
      // Check if the target is overriding and narrowing the access.
      if (method.isPublic()) {
        DexEncodedMethod targetOverride = target.lookupVirtualMethod(method.getReference());
        if (targetOverride != null && !targetOverride.isPublic()) {
          return true;
        }
      }
    }
    // Check that all accesses from [source] to classes or members from the current package of
    // [source] will continue to work. This is guaranteed if the methods of [source] do not access
    // any private or protected classes or members from the current package of [source].
    TraversalContinuation<?, ?> result =
        source.traverseProgramMethods(
            method -> {
              boolean foundIllegalAccess =
                  method.registerCodeReferencesWithResult(
                      new IllegalAccessDetector(appView, method));
              if (foundIllegalAccess) {
                return TraversalContinuation.doBreak();
              }
              return TraversalContinuation.doContinue();
            });
    return result.shouldBreak();
  }

  private Collection<DexMethod> getInvokes(ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    if (invokes == null) {
      invokes = new OverloadedMethodSignaturesRetriever(immediateSubtypingInfo).get();
    }
    return invokes;
  }

  // Collects all potentially overloaded method signatures that reference at least one type that
  // may be the source or target of a merge operation.
  private class OverloadedMethodSignaturesRetriever {
    private final Reference2BooleanOpenHashMap<DexProto> cache =
        new Reference2BooleanOpenHashMap<>();
    private final Equivalence<DexMethod> equivalence = MethodSignatureEquivalence.get();
    private final Set<DexType> mergeeCandidates = new HashSet<>();

    public OverloadedMethodSignaturesRetriever(
        ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
      for (DexProgramClass mergeCandidate : mergeCandidates) {
        List<DexProgramClass> subclasses = immediateSubtypingInfo.getSubclasses(mergeCandidate);
        if (subclasses.size() == 1) {
          mergeeCandidates.add(ListUtils.first(subclasses).getType());
        }
      }
    }

    public Collection<DexMethod> get() {
      Map<DexString, DexProto> overloadingInfo = new HashMap<>();

      // Find all signatures that may reference a type that could be the source or target of a
      // merge operation.
      Set<Wrapper<DexMethod>> filteredSignatures = new HashSet<>();
      for (DexProgramClass clazz : appView.appInfo().classes()) {
        for (DexEncodedMethod encodedMethod : clazz.methods()) {
          DexMethod method = encodedMethod.getReference();
          DexClass definition = appView.definitionFor(method.getHolderType());
          if (definition != null
              && definition.isProgramClass()
              && protoMayReferenceMergedSourceOrTarget(method.getProto())) {
            filteredSignatures.add(equivalence.wrap(method));

            // Record that we have seen a method named [signature.name] with the proto
            // [signature.proto]. If at some point, we find a method with the same name, but a
            // different proto, it could be the case that a method with the given name is
            // overloaded.
            DexProto existing =
                overloadingInfo.computeIfAbsent(method.getName(), key -> method.getProto());
            if (existing.isNotIdenticalTo(DexProto.SENTINEL)
                && !existing.equals(method.getProto())) {
              // Mark that this signature is overloaded by mapping it to SENTINEL.
              overloadingInfo.put(method.getName(), DexProto.SENTINEL);
            }
          }
        }
      }

      List<DexMethod> result = new ArrayList<>();
      for (Wrapper<DexMethod> wrappedSignature : filteredSignatures) {
        DexMethod signature = wrappedSignature.get();

        // Ignore those method names that are definitely not overloaded since they cannot lead to
        // any collisions.
        if (overloadingInfo.get(signature.getName()).isIdenticalTo(DexProto.SENTINEL)) {
          result.add(signature);
        }
      }
      return result;
    }

    private boolean protoMayReferenceMergedSourceOrTarget(DexProto proto) {
      boolean result;
      if (cache.containsKey(proto)) {
        result = cache.getBoolean(proto);
      } else {
        result = false;
        if (typeMayReferenceMergedSourceOrTarget(proto.getReturnType())) {
          result = true;
        } else {
          for (DexType type : proto.getParameters()) {
            if (typeMayReferenceMergedSourceOrTarget(type)) {
              result = true;
              break;
            }
          }
        }
        cache.put(proto, result);
      }
      return result;
    }

    private boolean typeMayReferenceMergedSourceOrTarget(DexType type) {
      type = type.toBaseType(dexItemFactory);
      if (type.isClassType()) {
        if (mergeeCandidates.contains(type)) {
          return true;
        }
        DexClass clazz = appView.definitionFor(type);
        if (clazz != null && clazz.isProgramClass()) {
          return mergeCandidates.contains(clazz.asProgramClass());
        }
      }
      return false;
    }
  }

  public static void runIfNecessary(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.begin("VerticalClassMerger");
    if (shouldRun(appView)) {
      new VerticalClassMerger(appView).run(executorService, timing);
    } else {
      appView.setVerticallyMergedClasses(VerticallyMergedClasses.empty());
    }
    assert appView.hasVerticallyMergedClasses();
    assert ArtProfileCompletenessChecker.verify(appView);
    timing.end();
  }

  private static boolean shouldRun(AppView<AppInfoWithLiveness> appView) {
    return appView.options().getVerticalClassMergerOptions().isEnabled()
        && !appView.hasCfByteCodePassThroughMethods();
  }

  private void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);

    initializePinnedTypes(); // Must be initialized prior to mergeCandidates.
    initializeMergeCandidates(immediateSubtypingInfo);

    timing.begin("merge");
    // Visit the program classes in a top-down order according to the class hierarchy.
    TopDownClassHierarchyTraversal.forProgramClasses(appView)
        .visit(
            mergeCandidates, clazz -> mergeClassIfPossible(clazz, immediateSubtypingInfo, timing));
    timing.end();

    VerticallyMergedClasses verticallyMergedClasses =
        new VerticallyMergedClasses(mergedClasses, mergedInterfaces);
    appView.setVerticallyMergedClasses(verticallyMergedClasses);
    if (verticallyMergedClasses.isEmpty()) {
      return;
    }

    timing.begin("fixup");
    VerticalClassMergerGraphLens lens =
        new VerticalClassMergerTreeFixer(
                appView, lensBuilder, verticallyMergedClasses, synthesizedBridges)
            .fixupTypeReferences();
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    keepInfo.mutate(
        mutator ->
            mutator.removeKeepInfoForMergedClasses(
                PrunedItems.builder().setRemovedClasses(mergedClasses.keySet()).build()));
    timing.end();

    assert lens != null;
    assert verifyGraphLens(lens);

    // Include bridges in art profiles.
    ProfileCollectionAdditions profileCollectionAdditions =
        ProfileCollectionAdditions.create(appView);
    if (!profileCollectionAdditions.isNop()) {
      for (SynthesizedBridgeCode synthesizedBridge : synthesizedBridges) {
        profileCollectionAdditions.applyIfContextIsInProfile(
            lens.getPreviousMethodSignature(synthesizedBridge.getMethod()),
            additionsBuilder -> additionsBuilder.addRule(synthesizedBridge.getMethod()));
      }
    }
    profileCollectionAdditions.commit(appView);

    // Rewrite collections using the lens.
    appView.rewriteWithLens(lens, executorService, timing);

    // Copy keep info to newly synthesized methods.
    keepInfo.mutate(
        mutator -> {
          for (SynthesizedBridgeCode synthesizedBridge : synthesizedBridges) {
            ProgramMethod bridge =
                asProgramMethodOrNull(appView.definitionFor(synthesizedBridge.getMethod()));
            ProgramMethod target =
                asProgramMethodOrNull(appView.definitionFor(synthesizedBridge.getTarget()));
            if (bridge != null && target != null) {
              mutator.joinMethod(bridge, info -> info.merge(appView.getKeepInfo(target).joiner()));
              continue;
            }
            assert false;
          }
        });

    appView.notifyOptimizationFinishedForTesting();
  }

  private boolean verifyGraphLens(VerticalClassMergerGraphLens graphLens) {
    // Note that the method assertReferencesNotModified() relies on getRenamedFieldSignature() and
    // getRenamedMethodSignature() instead of lookupField() and lookupMethod(). This is important
    // for this check to succeed, since it is not guaranteed that calling lookupMethod() with a
    // pinned method will return the method itself.
    //
    // Consider the following example.
    //
    //   class A {
    //     public void method() {}
    //   }
    //   class B extends A {
    //     @Override
    //     public void method() {}
    //   }
    //   class C extends B {
    //     @Override
    //     public void method() {}
    //   }
    //
    // If A.method() is pinned, then A cannot be merged into B, but B can still be merged into C.
    // Now, if there is an invoke-super instruction in C that hits B.method(), then this needs to
    // be rewritten into an invoke-direct instruction. In particular, there could be an instruction
    // `invoke-super A.method` in C. This would hit B.method(). Therefore, the graph lens records
    // that `invoke-super A.method` instructions, which are in one of the methods from C, needs to
    // be rewritten to `invoke-direct C.method$B`. This is valid even though A.method() is actually
    // pinned, because this rewriting does not affect A.method() in any way.
    assert graphLens.assertPinnedNotModified(appView);

    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedMethod encodedMethod : clazz.methods()) {
        DexMethod method = encodedMethod.getReference();
        DexMethod originalMethod = graphLens.getOriginalMethodSignature(method);
        DexMethod renamedMethod = graphLens.getRenamedMethodSignature(originalMethod);

        // Must be able to map back and forth.
        if (encodedMethod.hasCode() && encodedMethod.getCode() instanceof SynthesizedBridgeCode) {
          // For virtual methods, the vertical class merger creates two methods in the sub class
          // in order to deal with invoke-super instructions (one that is private and one that is
          // virtual). Therefore, it is not possible to go back and forth. Instead, we check that
          // the two methods map back to the same original method, and that the original method
          // can be mapped to the implementation method.
          DexMethod implementationMethod =
              ((SynthesizedBridgeCode) encodedMethod.getCode()).getTarget();
          DexMethod originalImplementationMethod =
              graphLens.getOriginalMethodSignature(implementationMethod);
          assert originalMethod.isIdenticalTo(originalImplementationMethod);
          assert implementationMethod.isIdenticalTo(renamedMethod);
        } else {
          assert method.isIdenticalTo(renamedMethod);
        }

        // Verify that all types are up-to-date. After vertical class merging, there should be no
        // more references to types that have been merged into another type.
        assert !mergedClasses.containsKey(method.getReturnType());
        assert Arrays.stream(method.getParameters().getBacking())
            .noneMatch(mergedClasses::containsKey);
      }
    }
    return true;
  }

  private boolean methodResolutionMayChange(DexProgramClass source, DexProgramClass target) {
    for (DexEncodedMethod virtualSourceMethod : source.virtualMethods()) {
      DexEncodedMethod directTargetMethod =
          target.lookupDirectMethod(virtualSourceMethod.getReference());
      if (directTargetMethod != null) {
        // A private method shadows a virtual method. This situation is rare, since it is not
        // allowed by javac. Therefore, we just give up in this case. (In principle, it would be
        // possible to rename the private method in the subclass, and then move the virtual method
        // to the subclass without changing its name.)
        return true;
      }
    }

    // When merging an interface into a class, all instructions on the form "invoke-interface
    // [source].m" are changed into "invoke-virtual [target].m". We need to abort the merge if this
    // transformation could hide IncompatibleClassChangeErrors.
    if (source.isInterface() && !target.isInterface()) {
      List<DexEncodedMethod> defaultMethods = new ArrayList<>();
      for (DexEncodedMethod virtualMethod : source.virtualMethods()) {
        if (!virtualMethod.accessFlags.isAbstract()) {
          defaultMethods.add(virtualMethod);
        }
      }

      // For each of the default methods, the subclass [target] could inherit another default method
      // with the same signature from another interface (i.e., there is a conflict). In such cases,
      // instructions on the form "invoke-interface [source].foo()" will fail with an Incompatible-
      // ClassChangeError.
      //
      // Example:
      //   interface I1 { default void m() {} }
      //   interface I2 { default void m() {} }
      //   class C implements I1, I2 {
      //     ... invoke-interface I1.m ... <- IncompatibleClassChangeError
      //   }
      for (DexEncodedMethod method : defaultMethods) {
        // Conservatively find all possible targets for this method.
        LookupResultSuccess lookupResult =
            appView
                .appInfo()
                .resolveMethodOnInterfaceLegacy(method.getHolderType(), method.getReference())
                .lookupVirtualDispatchTargets(target, appView)
                .asLookupResultSuccess();
        assert lookupResult != null;
        if (lookupResult == null) {
          return true;
        }
        if (lookupResult.contains(method)) {
          Box<Boolean> found = new Box<>(false);
          lookupResult.forEach(
              interfaceTarget -> {
                if (ObjectUtils.identical(interfaceTarget.getDefinition(), method)) {
                  return;
                }
                DexClass enclosingClass = interfaceTarget.getHolder();
                if (enclosingClass != null && enclosingClass.isInterface()) {
                  // Found a default method that is different from the one in [source], aborting.
                  found.set(true);
                }
              },
              lambdaTarget -> {
                // The merger should already have excluded lambda implemented interfaces.
                assert false;
              });
          if (found.get()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void mergeClassIfPossible(
      DexProgramClass clazz, ImmediateProgramSubtypingInfo immediateSubtypingInfo, Timing timing)
      throws ExecutionException {
    if (!mergeCandidates.contains(clazz)) {
      return;
    }
    List<DexProgramClass> subclasses = immediateSubtypingInfo.getSubclasses(clazz);
    if (subclasses.size() != 1) {
      return;
    }
    DexProgramClass targetClass = ListUtils.first(subclasses);
    assert !mergedClasses.containsKey(targetClass.getType());
    if (mergedClasses.containsValue(clazz.getType())) {
      return;
    }
    assert isMergeCandidate(clazz, targetClass);
    if (mergedClasses.containsValue(targetClass.getType())) {
      if (!isStillMergeCandidate(clazz, targetClass)) {
        return;
      }
    } else {
      assert isStillMergeCandidate(clazz, targetClass);
    }

    // Guard against the case where we have two methods that may get the same signature
    // if we replace types. This is rare, so we approximate and err on the safe side here.
    CollisionDetector collisionDetector =
        new CollisionDetector(
            appView,
            getInvokes(immediateSubtypingInfo),
            mergedClasses,
            clazz.getType(),
            targetClass.getType());
    if (collisionDetector.mayCollide(timing)) {
      return;
    }

    // Check with main dex classes to see if we are allowed to merge.
    if (!mainDexInfo.canMerge(clazz, targetClass, appView.getSyntheticItems())) {
      return;
    }

    ClassMerger merger = new ClassMerger(appView, lensBuilder, mergedClasses, clazz, targetClass);
    if (merger.merge()) {
      mergedClasses.put(clazz.getType(), targetClass.getType());
      if (clazz.isInterface()) {
        mergedInterfaces.put(clazz.getType(), targetClass.getType());
      }
      // Commit the changes to the graph lens.
      lensBuilder.merge(merger.getRenamings());
      synthesizedBridges.addAll(merger.getSynthesizedBridges());
    }
  }

  private boolean fieldResolutionMayChange(DexClass source, DexClass target) {
    if (source.getType().isIdenticalTo(target.getSuperType())) {
      // If there is a "iget Target.f" or "iput Target.f" instruction in target, and the class
      // Target implements an interface that declares a static final field f, this should yield an
      // IncompatibleClassChangeError.
      // TODO(christofferqa): In the following we only check if a static field from an interface
      // shadows an instance field from [source]. We could actually check if there is an iget/iput
      // instruction whose resolution would be affected by the merge. The situation where a static
      // field shadows an instance field is probably not widespread in practice, though.
      FieldSignatureEquivalence equivalence = FieldSignatureEquivalence.get();
      Set<Wrapper<DexField>> staticFieldsInInterfacesOfTarget = new HashSet<>();
      for (DexType interfaceType : target.getInterfaces()) {
        DexClass clazz = appView.definitionFor(interfaceType);
        for (DexEncodedField staticField : clazz.staticFields()) {
          staticFieldsInInterfacesOfTarget.add(equivalence.wrap(staticField.getReference()));
        }
      }
      for (DexEncodedField instanceField : source.instanceFields()) {
        if (staticFieldsInInterfacesOfTarget.contains(
            equivalence.wrap(instanceField.getReference()))) {
          // An instruction "iget Target.f" or "iput Target.f" that used to hit a static field in an
          // interface would now hit an instance field from [source], so that an IncompatibleClass-
          // ChangeError would no longer be thrown. Abort merge.
          return true;
        }
      }
    }
    return false;
  }

  private boolean disallowInlining(ProgramMethod method, DexProgramClass context) {
    if (appView.options().inlinerOptions().enableInlining) {
      Code code = method.getDefinition().getCode();
      if (code.isCfCode()) {
        CfCode cfCode = code.asCfCode();
        SingleTypeMapperGraphLens lens =
            new SingleTypeMapperGraphLens(
                appView, lensBuilder, mergedClasses, method.getHolder(), context);
        ConstraintWithTarget constraint =
            cfCode.computeInliningConstraint(
                method, appView, lens, context.programInstanceInitializers().iterator().next());
        if (constraint.isNever()) {
          return true;
        }
        // Constructors can have references beyond the root main dex classes. This can increase the
        // size of the main dex dependent classes and we should bail out.
        if (mainDexInfo.disallowInliningIntoContext(
            appView, context, method, appView.getSyntheticItems())) {
          return true;
        }
        return false;
      } else if (code.isDefaultInstanceInitializerCode()) {
        return false;
      }
      // For non-jar/cf code we currently cannot guarantee that markForceInline() will succeed.
    }
    return true;
  }

}
