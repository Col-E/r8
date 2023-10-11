// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.dex.Constants.TEMPORARY_INSTANCE_INITIALIZER_PREFIX;
import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.code.InvokeType.DIRECT;
import static com.android.tools.r8.ir.code.InvokeType.STATIC;
import static com.android.tools.r8.ir.code.InvokeType.VIRTUAL;
import static com.android.tools.r8.utils.AndroidApiLevelUtils.getApiReferenceLevelForMerging;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.features.FeatureSplitBoundaryOptimizationUtils;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DefaultInstanceInitializerCode;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassSignature.ClassSignatureBuilder;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignatureContextBuilder;
import com.android.tools.r8.graph.GenericSignatureContextBuilder.TypeParameterContext;
import com.android.tools.r8.graph.GenericSignatureCorrectnessHelper;
import com.android.tools.r8.graph.GenericSignaturePartialTypeArgumentApplier;
import com.android.tools.r8.graph.LookupResult.LookupResultSuccess;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.graph.TopDownClassHierarchyTraversal;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.UseRegistryWithResult;
import com.android.tools.r8.graph.classmerging.VerticallyMergedClasses;
import com.android.tools.r8.graph.fixup.TreeFixerBase;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.synthetic.AbstractSynthesizedCode;
import com.android.tools.r8.ir.synthetic.ForwardMethodSourceCode;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.FieldSignatureEquivalence;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.OptionalBool;
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
import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Merges Supertypes with a single implementation into their single subtype.
 *
 * <p>A common use-case for this is to merge an interface into its single implementation.
 *
 * <p>The class merger only fixes the structure of the graph but leaves the actual instructions
 * untouched. Fixup of instructions is deferred via a {@link GraphLens} to the IR building phase.
 */
public class VerticalClassMerger {

  private enum AbortReason {
    ALREADY_MERGED,
    ALWAYS_INLINE,
    CONFLICT,
    ILLEGAL_ACCESS,
    MAIN_DEX_ROOT_OUTSIDE_REFERENCE,
    MERGE_ACROSS_NESTS,
    NATIVE_METHOD,
    NO_SIDE_EFFECTS,
    PINNED_SOURCE,
    RESOLUTION_FOR_FIELDS_MAY_CHANGE,
    RESOLUTION_FOR_METHODS_MAY_CHANGE,
    SERVICE_LOADER,
    SOURCE_AND_TARGET_LOCK_CANDIDATES,
    STATIC_INITIALIZERS,
    UNHANDLED_INVOKE_DIRECT,
    UNHANDLED_INVOKE_SUPER,
    UNSAFE_INLINING,
    UNSUPPORTED_ATTRIBUTES,
    API_REFERENCE_LEVEL
  }

  private enum Rename {
    ALWAYS,
    IF_NEEDED,
    NEVER
  }

  private final DexApplication application;
  private final AppInfoWithLiveness appInfo;
  private final AppView<AppInfoWithLiveness> appView;
  private final InternalOptions options;
  private final SubtypingInfo subtypingInfo;
  private final ExecutorService executorService;
  private final Timing timing;
  private Collection<DexMethod> invokes;
  private final AndroidApiLevelCompute apiLevelCompute;

  private final OptimizationFeedback feedback = OptimizationFeedbackSimple.getInstance();

  // Set of merge candidates. Note that this must have a deterministic iteration order.
  private final Set<DexProgramClass> mergeCandidates = new LinkedHashSet<>();

  // Map from source class to target class.
  private final MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses =
      BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();

  private final MutableBidirectionalManyToOneMap<DexType, DexType> mergedInterfaces =
      BidirectionalManyToOneHashMap.newIdentityHashMap();

  // Set of types that must not be merged into their subtype.
  private final Set<DexType> pinnedTypes = Sets.newIdentityHashSet();

  // The resulting graph lens that should be used after class merging.
  private final VerticalClassMergerGraphLens.Builder lensBuilder;

  // All the bridge methods that have been synthesized during vertical class merging.
  private final List<SynthesizedBridgeCode> synthesizedBridges = new ArrayList<>();

  private final MainDexInfo mainDexInfo;

  public VerticalClassMerger(
      DexApplication application,
      AppView<AppInfoWithLiveness> appView,
      ExecutorService executorService,
      Timing timing) {
    this.application = application;
    this.appInfo = appView.appInfo();
    this.appView = appView;
    this.options = appView.options();
    this.mainDexInfo = appInfo.getMainDexInfo();
    this.subtypingInfo = appInfo.computeSubtypingInfo();
    this.executorService = executorService;
    this.lensBuilder = new VerticalClassMergerGraphLens.Builder(appView.dexItemFactory());
    this.apiLevelCompute = appView.apiLevelCompute();
    this.timing = timing;

    Iterable<DexProgramClass> classes = application.classesWithDeterministicOrder();
    initializePinnedTypes(classes); // Must be initialized prior to mergeCandidates.
    initializeMergeCandidates(classes);
  }

  private void initializeMergeCandidates(Iterable<DexProgramClass> classes) {
    for (DexProgramClass sourceClass : classes) {
      DexType singleSubtype = subtypingInfo.getSingleDirectSubtype(sourceClass.type);
      if (singleSubtype == null) {
        continue;
      }
      DexProgramClass targetClass = asProgramClassOrNull(appView.definitionFor(singleSubtype));
      if (targetClass == null) {
        continue;
      }
      if (!isMergeCandidate(sourceClass, targetClass, pinnedTypes)) {
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
  private void initializePinnedTypes(Iterable<DexProgramClass> classes) {
    // For all pinned fields, also pin the type of the field (because changing the type of the field
    // implicitly changes the signature of the field). Similarly, for all pinned methods, also pin
    // the return type and the parameter types of the method.
    // TODO(b/156715504): Compute referenced-by-pinned in the keep info objects.
    List<DexReference> pinnedItems = new ArrayList<>();
    appInfo.getKeepInfo().forEachPinnedType(pinnedItems::add, options);
    appInfo.getKeepInfo().forEachPinnedMethod(pinnedItems::add, options);
    appInfo.getKeepInfo().forEachPinnedField(pinnedItems::add, options);
    extractPinnedItems(pinnedItems, AbortReason.PINNED_SOURCE);

    for (DexProgramClass clazz : classes) {
      for (DexEncodedMethod method : clazz.methods()) {
        if (method.accessFlags.isNative()) {
          markTypeAsPinned(clazz.type, AbortReason.NATIVE_METHOD);
        }
      }
    }

    // It is valid to have an invoke-direct instruction in a default interface method that targets
    // another default method in the same interface (see InterfaceMethodDesugaringTests.testInvoke-
    // SpecialToDefaultMethod). However, in a class, that would lead to a verification error.
    // Therefore, we disallow merging such interfaces into their subtypes.
    for (DexMethod signature : appInfo.getVirtualMethodsTargetedByInvokeDirect()) {
      markTypeAsPinned(signature.holder, AbortReason.UNHANDLED_INVOKE_DIRECT);
    }

    // The set of targets that must remain for proper resolution error cases should not be merged.
    // TODO(b/192821424): Can be removed if handled.
    extractPinnedItems(
        appInfo.getFailedMethodResolutionTargets(), AbortReason.RESOLUTION_FOR_METHODS_MAY_CHANGE);
  }

  private <T extends DexReference> void extractPinnedItems(Iterable<T> items, AbortReason reason) {
    for (DexReference item : items) {
      if (item.isDexType()) {
        markTypeAsPinned(item.asDexType(), reason);
      } else if (item.isDexField()) {
        // Pin the holder and the type of the field.
        DexField field = item.asDexField();
        markTypeAsPinned(field.holder, reason);
        markTypeAsPinned(field.type, reason);
      } else {
        assert item.isDexMethod();
        // Pin the holder, the return type and the parameter types of the method. If we were to
        // merge any of these types into their sub classes, then we would implicitly change the
        // signature of this method.
        DexMethod method = item.asDexMethod();
        markTypeAsPinned(method.holder, reason);
        markTypeAsPinned(method.proto.returnType, reason);
        for (DexType parameterType : method.proto.parameters.values) {
          markTypeAsPinned(parameterType, reason);
        }
      }
    }
  }

  @SuppressWarnings("UnusedVariable")
  private void markTypeAsPinned(DexType type, AbortReason reason) {
    DexType baseType = type.toBaseType(appView.dexItemFactory());
    if (!baseType.isClassType() || appInfo.isPinnedWithDefinitionLookup(baseType)) {
      // We check for the case where the type is pinned according to appInfo.isPinned,
      // so we only need to add it here if it is not the case.
      return;
    }

    DexClass clazz = appInfo.definitionFor(baseType);
    if (clazz != null && clazz.isProgramClass()) {
      pinnedTypes.add(baseType);
    }
  }

  // Returns true if [clazz] is a merge candidate. Note that the result of the checks in this
  // method do not change in response to any class merges.
  @SuppressWarnings("ReferenceEquality")
  private boolean isMergeCandidate(
      DexProgramClass sourceClass, DexProgramClass targetClass, Set<DexType> pinnedTypes) {
    assert targetClass != null;
    ObjectAllocationInfoCollection allocationInfo = appInfo.getObjectAllocationInfoCollection();
    if (allocationInfo.isInstantiatedDirectly(sourceClass)
        || allocationInfo.isInterfaceWithUnknownSubtypeHierarchy(sourceClass)
        || allocationInfo.isImmediateInterfaceOfInstantiatedLambda(sourceClass)
        || appInfo.isPinned(sourceClass)
        || pinnedTypes.contains(sourceClass.type)
        || appInfo.isNoVerticalClassMergingOfType(sourceClass.type)) {
      return false;
    }

    assert Streams.stream(Iterables.concat(sourceClass.fields(), sourceClass.methods()))
        .noneMatch(appInfo::isPinned);

    if (!FeatureSplitBoundaryOptimizationUtils.isSafeForVerticalClassMerging(
        sourceClass, targetClass, appView)) {
      return false;
    }
    if (appView.appServices().allServiceTypes().contains(sourceClass.type)
        && appInfo.isPinned(targetClass)) {
      return false;
    }
    if (sourceClass.isAnnotation()) {
      return false;
    }
    if (!sourceClass.isInterface()
        && targetClass.isSerializable(appView)
        && !appInfo.isSerializable(sourceClass.type)) {
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
              method -> {
                AbortReason reason = disallowInlining(method, targetClass);
                if (reason != null) {
                  // Cannot guarantee that markForceInline() will work.
                  return TraversalContinuation.doBreak();
                }
                return TraversalContinuation.doContinue();
              });
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
    if (targetClass.getNestHost() != sourceClass.getNestHost()) {
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
  @SuppressWarnings("ReferenceEquality")
  private boolean isStillMergeCandidate(DexProgramClass sourceClass, DexProgramClass targetClass) {
    assert isMergeCandidate(sourceClass, targetClass, pinnedTypes);
    assert !mergedClasses.containsValue(sourceClass.getType());
    // For interface types, this is more complicated, see:
    // https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-5.html#jvms-5.5
    // We basically can't move the clinit, since it is not called when implementing classes have
    // their clinit called - except when the interface has a default method.
    if ((sourceClass.hasClassInitializer() && targetClass.hasClassInitializer())
        || targetClass.classInitializationMayHaveSideEffects(
            appView, type -> type == sourceClass.type)
        || (sourceClass.isInterface()
            && sourceClass.classInitializationMayHaveSideEffects(appView))) {
      // TODO(herhut): Handle class initializers.
      return false;
    }
    boolean sourceCanBeSynchronizedOn =
        appView.appInfo().isLockCandidate(sourceClass.type)
            || sourceClass.hasStaticSynchronizedMethods();
    boolean targetCanBeSynchronizedOn =
        appView.appInfo().isLockCandidate(targetClass.type)
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

  private Collection<DexMethod> getInvokes() {
    if (invokes == null) {
      invokes = new OverloadedMethodSignaturesRetriever().get();
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

    public OverloadedMethodSignaturesRetriever() {
      for (DexProgramClass mergeCandidate : mergeCandidates) {
        DexType singleSubtype = subtypingInfo.getSingleDirectSubtype(mergeCandidate.type);
        mergeeCandidates.add(singleSubtype);
      }
    }

    @SuppressWarnings("ReferenceEquality")
    public Collection<DexMethod> get() {
      Map<DexString, DexProto> overloadingInfo = new HashMap<>();

      // Find all signatures that may reference a type that could be the source or target of a
      // merge operation.
      Set<Wrapper<DexMethod>> filteredSignatures = new HashSet<>();
      for (DexProgramClass clazz : appInfo.classes()) {
        for (DexEncodedMethod encodedMethod : clazz.methods()) {
          DexMethod method = encodedMethod.getReference();
          DexClass definition = appInfo.definitionFor(method.holder);
          if (definition != null
              && definition.isProgramClass()
              && protoMayReferenceMergedSourceOrTarget(method.proto)) {
            filteredSignatures.add(equivalence.wrap(method));

            // Record that we have seen a method named [signature.name] with the proto
            // [signature.proto]. If at some point, we find a method with the same name, but a
            // different proto, it could be the case that a method with the given name is
            // overloaded.
            DexProto existing = overloadingInfo.computeIfAbsent(method.name, key -> method.proto);
            if (existing != DexProto.SENTINEL && !existing.equals(method.proto)) {
              // Mark that this signature is overloaded by mapping it to SENTINEL.
              overloadingInfo.put(method.name, DexProto.SENTINEL);
            }
          }
        }
      }

      List<DexMethod> result = new ArrayList<>();
      for (Wrapper<DexMethod> wrappedSignature : filteredSignatures) {
        DexMethod signature = wrappedSignature.get();

        // Ignore those method names that are definitely not overloaded since they cannot lead to
        // any collisions.
        if (overloadingInfo.get(signature.name) == DexProto.SENTINEL) {
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
        if (typeMayReferenceMergedSourceOrTarget(proto.returnType)) {
          result = true;
        } else {
          for (DexType type : proto.parameters.values) {
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
      type = type.toBaseType(appView.dexItemFactory());
      if (type.isClassType()) {
        if (mergeeCandidates.contains(type)) {
          return true;
        }
        DexClass clazz = appInfo.definitionFor(type);
        if (clazz != null && clazz.isProgramClass()) {
          return mergeCandidates.contains(clazz.asProgramClass());
        }
      }
      return false;
    }
  }

  public VerticalClassMergerGraphLens run() throws ExecutionException {
    timing.begin("merge");
    // Visit the program classes in a top-down order according to the class hierarchy.
    TopDownClassHierarchyTraversal.forProgramClasses(appView)
        .visit(mergeCandidates, this::mergeClassIfPossible);
    timing.end();

    VerticallyMergedClasses verticallyMergedClasses =
        new VerticallyMergedClasses(mergedClasses, mergedInterfaces);
    appView.setVerticallyMergedClasses(verticallyMergedClasses);
    if (verticallyMergedClasses.isEmpty()) {
      return null;
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
            lens.getPreviousMethodSignature(synthesizedBridge.method),
            additionsBuilder -> additionsBuilder.addRule(synthesizedBridge.method));
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
                asProgramMethodOrNull(appView.definitionFor(synthesizedBridge.method));
            ProgramMethod target =
                asProgramMethodOrNull(appView.definitionFor(synthesizedBridge.invocationTarget));
            if (bridge != null && target != null) {
              mutator.joinMethod(bridge, info -> info.merge(appView.getKeepInfo(target).joiner()));
              continue;
            }
            assert false;
          }
        });

    appView.notifyOptimizationFinishedForTesting();
    return lens;
  }

  @SuppressWarnings("ReferenceEquality")
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
    assert graphLens.assertPinnedNotModified(appInfo.getKeepInfo(), options);

    for (DexProgramClass clazz : appInfo.classes()) {
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
              ((SynthesizedBridgeCode) encodedMethod.getCode()).invocationTarget;
          DexMethod originalImplementationMethod =
              graphLens.getOriginalMethodSignature(implementationMethod);
          assert originalMethod == originalImplementationMethod;
          assert implementationMethod == renamedMethod;
        } else {
          assert method == renamedMethod;
        }

        // Verify that all types are up-to-date. After vertical class merging, there should be no
        // more references to types that have been merged into another type.
        assert !mergedClasses.containsKey(method.proto.returnType);
        assert Arrays.stream(method.proto.parameters.values).noneMatch(mergedClasses::containsKey);
      }
    }
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
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
            appInfo
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
                if (interfaceTarget.getDefinition() == method) {
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

  private void mergeClassIfPossible(DexProgramClass clazz) {
    if (!mergeCandidates.contains(clazz)) {
      return;
    }

    DexType singleSubtype = subtypingInfo.getSingleDirectSubtype(clazz.type);
    DexProgramClass targetClass = appView.definitionFor(singleSubtype).asProgramClass();
    assert !mergedClasses.containsKey(targetClass.type);
    if (mergedClasses.containsValue(clazz.type)) {
      return;
    }
    assert isMergeCandidate(clazz, targetClass, pinnedTypes);
    if (mergedClasses.containsValue(targetClass.type)) {
      if (!isStillMergeCandidate(clazz, targetClass)) {
        return;
      }
    } else {
      assert isStillMergeCandidate(clazz, targetClass);
    }

    // Guard against the case where we have two methods that may get the same signature
    // if we replace types. This is rare, so we approximate and err on the safe side here.
    if (new CollisionDetector(clazz.type, targetClass.type).mayCollide()) {
      return;
    }

    // Check with main dex classes to see if we are allowed to merge.
    if (!mainDexInfo.canMerge(clazz, targetClass, appView.getSyntheticItems())) {
      return;
    }

    ClassMerger merger = new ClassMerger(clazz, targetClass);
    boolean merged;
    try {
      merged = merger.merge();
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
    if (merged) {
      // Commit the changes to the graph lens.
      lensBuilder.merge(merger.getRenamings());
      synthesizedBridges.addAll(merger.getSynthesizedBridges());
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean fieldResolutionMayChange(DexClass source, DexClass target) {
    if (source.type == target.superType) {
      // If there is a "iget Target.f" or "iput Target.f" instruction in target, and the class
      // Target implements an interface that declares a static final field f, this should yield an
      // IncompatibleClassChangeError.
      // TODO(christofferqa): In the following we only check if a static field from an interface
      // shadows an instance field from [source]. We could actually check if there is an iget/iput
      // instruction whose resolution would be affected by the merge. The situation where a static
      // field shadows an instance field is probably not widespread in practice, though.
      FieldSignatureEquivalence equivalence = FieldSignatureEquivalence.get();
      Set<Wrapper<DexField>> staticFieldsInInterfacesOfTarget = new HashSet<>();
      for (DexType interfaceType : target.interfaces.values) {
        DexClass clazz = appInfo.definitionFor(interfaceType);
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

  private class ClassMerger {

    private final DexProgramClass source;
    private final DexProgramClass target;
    private final VerticalClassMergerGraphLens.Builder deferredRenamings =
        new VerticalClassMergerGraphLens.Builder(appView.dexItemFactory());
    private final List<SynthesizedBridgeCode> synthesizedBridges = new ArrayList<>();

    private boolean abortMerge = false;

    private ClassMerger(DexProgramClass source, DexProgramClass target) {
      this.source = source;
      this.target = target;
    }

    public boolean merge() throws ExecutionException {
      // Merge the class [clazz] into [targetClass] by adding all methods to
      // targetClass that are not currently contained.
      // Step 1: Merge methods
      Set<Wrapper<DexMethod>> existingMethods = new HashSet<>();
      addAll(existingMethods, target.methods(), MethodSignatureEquivalence.get());

      Map<Wrapper<DexMethod>, DexEncodedMethod> directMethods = new HashMap<>();
      Map<Wrapper<DexMethod>, DexEncodedMethod> virtualMethods = new HashMap<>();

      Predicate<DexMethod> availableMethodSignatures =
          (method) -> {
            Wrapper<DexMethod> wrapped = MethodSignatureEquivalence.get().wrap(method);
            return !existingMethods.contains(wrapped)
                && !directMethods.containsKey(wrapped)
                && !virtualMethods.containsKey(wrapped);
          };

      source.forEachProgramDirectMethod(
          directMethod -> {
            DexEncodedMethod definition = directMethod.getDefinition();
            if (definition.isInstanceInitializer()) {
              DexEncodedMethod resultingConstructor =
                  renameConstructor(
                      definition,
                      candidate ->
                          availableMethodSignatures.test(candidate)
                              && source.lookupVirtualMethod(candidate) == null);
              add(directMethods, resultingConstructor, MethodSignatureEquivalence.get());
              blockRedirectionOfSuperCalls(resultingConstructor.getReference());
            } else {
              DexEncodedMethod resultingDirectMethod =
                  renameMethod(
                      definition,
                      availableMethodSignatures,
                      definition.isClassInitializer() ? Rename.NEVER : Rename.IF_NEEDED);
              add(directMethods, resultingDirectMethod, MethodSignatureEquivalence.get());
              deferredRenamings.map(
                  directMethod.getReference(), resultingDirectMethod.getReference());
              deferredRenamings.recordMove(
                  directMethod.getReference(), resultingDirectMethod.getReference());
              blockRedirectionOfSuperCalls(resultingDirectMethod.getReference());

              // Private methods in the parent class may be targeted with invoke-super if the two
              // classes are in the same nest. Ensure such calls are mapped to invoke-direct.
              if (definition.isInstance()
                  && definition.isPrivate()
                  && AccessControl.isMemberAccessible(directMethod, source, target, appView)
                      .isTrue()) {
                deferredRenamings.mapVirtualMethodToDirectInType(
                    directMethod.getReference(),
                    prototypeChanges ->
                        new MethodLookupResult(
                            resultingDirectMethod.getReference(), null, DIRECT, prototypeChanges),
                    target.getType());
              }
            }
          });

      for (DexEncodedMethod virtualMethod : source.virtualMethods()) {
        DexEncodedMethod shadowedBy = findMethodInTarget(virtualMethod);
        if (shadowedBy != null) {
          if (virtualMethod.isAbstract()) {
            // Remove abstract/interface methods that are shadowed. The identity mapping below is
            // needed to ensure we correctly fixup the mapping in case the signature refers to
            // merged classes.
            deferredRenamings
                .map(virtualMethod.getReference(), shadowedBy.getReference())
                .map(shadowedBy.getReference(), shadowedBy.getReference())
                .recordMerge(virtualMethod.getReference(), shadowedBy.getReference());

            // The override now corresponds to the method in the parent, so unset its synthetic flag
            // if the method in the parent is not synthetic.
            if (!virtualMethod.isSyntheticMethod() && shadowedBy.isSyntheticMethod()) {
              shadowedBy.accessFlags.demoteFromSynthetic();
            }
            continue;
          }
        } else {
          if (abortMerge) {
            // If [virtualMethod] does not resolve to a single method in [target], abort.
            assert restoreDebuggingState(
                Streams.concat(directMethods.values().stream(), virtualMethods.values().stream()));
            return false;
          }

          // The method is not shadowed. If it is abstract, we can simply move it to the subclass.
          // Non-abstract methods are handled below (they cannot simply be moved to the subclass as
          // a virtual method, because they might be the target of an invoke-super instruction).
          if (virtualMethod.isAbstract()) {
            // Abort if target is non-abstract and does not override the abstract method.
            if (!target.isAbstract()) {
              assert appView.options().testing.allowNonAbstractClassesWithAbstractMethods;
              abortMerge = true;
              return false;
            }
            // Update the holder of [virtualMethod] using renameMethod().
            DexEncodedMethod resultingVirtualMethod =
                renameMethod(virtualMethod, availableMethodSignatures, Rename.NEVER);
            resultingVirtualMethod.setLibraryMethodOverride(
                virtualMethod.isLibraryMethodOverride());
            deferredRenamings.map(
                virtualMethod.getReference(), resultingVirtualMethod.getReference());
            deferredRenamings.recordMove(
                virtualMethod.getReference(), resultingVirtualMethod.getReference());
            add(virtualMethods, resultingVirtualMethod, MethodSignatureEquivalence.get());
            continue;
          }
        }

        DexEncodedMethod resultingMethod;
        if (source.accessFlags.isInterface()) {
          // Moving a default interface method into its subtype. This method could be hit directly
          // via an invoke-super instruction from any of the transitive subtypes of this interface,
          // due to the way invoke-super works on default interface methods. In order to be able
          // to hit this method directly after the merge, we need to make it public, and find a
          // method name that does not collide with one in the hierarchy of this class.
          DexItemFactory dexItemFactory = appView.dexItemFactory();
          String resultingMethodBaseName =
              virtualMethod.getName().toString() + '$' + source.getTypeName().replace('.', '$');
          DexMethod resultingMethodReference =
              dexItemFactory.createMethod(
                  target.getType(),
                  virtualMethod.getProto().prependParameter(source.getType(), dexItemFactory),
                  dexItemFactory.createGloballyFreshMemberString(resultingMethodBaseName));
          assert availableMethodSignatures.test(resultingMethodReference);
          resultingMethod = virtualMethod.toTypeSubstitutedMethod(resultingMethodReference);
          makeStatic(resultingMethod);
        } else {
          // This virtual method could be called directly from a sub class via an invoke-super in-
          // struction. Therefore, we translate this virtual method into an instance method with a
          // unique name, such that relevant invoke-super instructions can be rewritten to target
          // this method directly.
          resultingMethod = renameMethod(virtualMethod, availableMethodSignatures, Rename.ALWAYS);
          if (appView.options().getProguardConfiguration().isAccessModificationAllowed()) {
            makePublic(resultingMethod);
          } else {
            makePrivate(resultingMethod);
          }
        }

        add(
            resultingMethod.belongsToDirectPool() ? directMethods : virtualMethods,
            resultingMethod,
            MethodSignatureEquivalence.get());

        // Record that invoke-super instructions in the target class should be redirected to the
        // newly created direct method.
        redirectSuperCallsInTarget(virtualMethod, resultingMethod);
        blockRedirectionOfSuperCalls(resultingMethod.getReference());

        if (shadowedBy == null) {
          // In addition to the newly added direct method, create a virtual method such that we do
          // not accidentally remove the method from the interface of this class.
          // Note that this method is added independently of whether it will actually be used. If
          // it turns out that the method is never used, it will be removed by the final round
          // of tree shaking.
          shadowedBy = buildBridgeMethod(virtualMethod, resultingMethod);
          deferredRenamings.recordCreationOfBridgeMethod(
              virtualMethod.getReference(), shadowedBy.getReference());
          add(virtualMethods, shadowedBy, MethodSignatureEquivalence.get());
        }

        // Copy over any keep info from the original virtual method.
        ProgramMethod programMethod = new ProgramMethod(target, shadowedBy);
        appView
            .getKeepInfo()
            .mutate(
                mutableKeepInfoCollection ->
                    mutableKeepInfoCollection.joinMethod(
                        programMethod,
                        info ->
                            info.merge(
                                mutableKeepInfoCollection
                                    .getMethodInfo(virtualMethod, source)
                                    .joiner())));

        deferredRenamings.map(virtualMethod.getReference(), shadowedBy.getReference());
        deferredRenamings.recordMove(
            virtualMethod.getReference(),
            resultingMethod.getReference(),
            resultingMethod.isStatic());
      }

      if (abortMerge) {
        assert restoreDebuggingState(
            Streams.concat(directMethods.values().stream(), virtualMethods.values().stream()));
        return false;
      }

      // Rewrite generic signatures before we merge a base with a generic signature.
      rewriteGenericSignatures(target, source, directMethods.values(), virtualMethods.values());

      // Convert out of DefaultInstanceInitializerCode, since this piece of code will require lens
      // code rewriting.
      target.forEachProgramInstanceInitializerMatching(
          method -> method.getCode().isDefaultInstanceInitializerCode(),
          method -> DefaultInstanceInitializerCode.uncanonicalizeCode(appView, method));

      // Step 2: Merge fields
      Set<DexString> existingFieldNames = new HashSet<>();
      for (DexEncodedField field : target.fields()) {
        existingFieldNames.add(field.getReference().name);
      }

      // In principle, we could allow multiple fields with the same name, and then only rename the
      // field in the end when we are done merging all the classes, if it it turns out that the two
      // fields ended up having the same type. This would not be too expensive, since we visit the
      // entire program using VerticalClassMerger.TreeFixer anyway.
      //
      // For now, we conservatively report that a signature is already taken if there is a field
      // with the same name. If minification is used with -overloadaggressively, this is solved
      // later anyway.
      Predicate<DexField> availableFieldSignatures =
          field -> !existingFieldNames.contains(field.name);

      DexEncodedField[] mergedInstanceFields =
          mergeFields(
              source.instanceFields(),
              target.instanceFields(),
              availableFieldSignatures,
              existingFieldNames);

      DexEncodedField[] mergedStaticFields =
          mergeFields(
              source.staticFields(),
              target.staticFields(),
              availableFieldSignatures,
              existingFieldNames);

      // Step 3: Merge interfaces
      Set<DexType> interfaces = mergeArrays(target.interfaces.values, source.interfaces.values);
      // Now destructively update the class.
      // Step 1: Update supertype or fix interfaces.
      if (source.isInterface()) {
        interfaces.remove(source.type);
      } else {
        assert !target.isInterface();
        target.superType = source.superType;
      }
      target.interfaces =
          interfaces.isEmpty()
              ? DexTypeList.empty()
              : new DexTypeList(interfaces.toArray(DexType.EMPTY_ARRAY));
      // Step 2: ensure -if rules cannot target the members that were merged into the target class.
      directMethods.values().forEach(feedback::markMethodCannotBeKept);
      virtualMethods.values().forEach(feedback::markMethodCannotBeKept);
      for (int i = 0; i < source.instanceFields().size(); i++) {
        feedback.markFieldCannotBeKept(mergedInstanceFields[i]);
      }
      for (int i = 0; i < source.staticFields().size(); i++) {
        feedback.markFieldCannotBeKept(mergedStaticFields[i]);
      }
      // Step 3: replace fields and methods.
      target.addDirectMethods(directMethods.values());
      target.addVirtualMethods(virtualMethods.values());
      target.setInstanceFields(mergedInstanceFields);
      target.setStaticFields(mergedStaticFields);
      // Step 4: Clear the members of the source class since they have now been moved to the target.
      source.getMethodCollection().clearDirectMethods();
      source.getMethodCollection().clearVirtualMethods();
      source.clearInstanceFields();
      source.clearStaticFields();
      // Step 5: Record merging.
      mergedClasses.put(source.type, target.type);
      if (source.isInterface()) {
        mergedInterfaces.put(source.type, target.type);
      }
      assert !abortMerge;
      assert GenericSignatureCorrectnessHelper.createForVerification(
              appView, GenericSignatureContextBuilder.createForSingleClass(appView, target))
          .evaluateSignaturesForClass(target)
          .isValid();
      return true;
    }

    /**
     * The rewriting of generic signatures is pretty simple, but require some bookkeeping. We take
     * the arguments to the base type:
     *
     * <pre>
     *   class Sub<X> extends Base<X, String>
     * </pre>
     *
     * for
     *
     * <pre>
     *   class Base<T,R> extends OtherBase<T> implements I<R> {
     *     T t() { ... };
     *   }
     * </pre>
     *
     * and substitute T -> X and R -> String
     */
    private void rewriteGenericSignatures(
        DexProgramClass target,
        DexProgramClass source,
        Collection<DexEncodedMethod> directMethods,
        Collection<DexEncodedMethod> virtualMethods) {
      ClassSignature targetSignature = target.getClassSignature();
      if (targetSignature.hasNoSignature()) {
        // Null out all source signatures that is moved, but do not clear out the class since this
        // could be referred to by other generic signatures.
        // TODO(b/147504070): If merging classes with enclosing/innerclasses, this needs to be
        //  reconsidered.
        directMethods.forEach(DexEncodedMethod::clearGenericSignature);
        virtualMethods.forEach(DexEncodedMethod::clearGenericSignature);
        source.fields().forEach(DexEncodedMember::clearGenericSignature);
        return;
      }
      GenericSignaturePartialTypeArgumentApplier classApplier =
          getGenericSignatureArgumentApplier(target, source);
      if (classApplier == null) {
        target.clearClassSignature();
        target.members().forEach(DexEncodedMember::clearGenericSignature);
        return;
      }
      // We could generate a substitution map.
      ClassSignature rewrittenSource = classApplier.visitClassSignature(source.getClassSignature());
      // The variables in the class signature is now rewritten to use the targets argument.
      ClassSignatureBuilder builder = ClassSignature.builder();
      builder.addFormalTypeParameters(targetSignature.getFormalTypeParameters());
      if (!source.isInterface()) {
        if (rewrittenSource.hasSignature()) {
          builder.setSuperClassSignature(rewrittenSource.getSuperClassSignatureOrNull());
        } else {
          builder.setSuperClassSignature(new ClassTypeSignature(source.superType));
        }
      } else {
        builder.setSuperClassSignature(targetSignature.getSuperClassSignatureOrNull());
      }
      // Compute the seen set for interfaces to add. This is similar to the merging of interfaces
      // but allow us to maintain the type arguments.
      Set<DexType> seenInterfaces = new HashSet<>();
      if (source.isInterface()) {
        seenInterfaces.add(source.type);
      }
      for (ClassTypeSignature iFace : targetSignature.getSuperInterfaceSignatures()) {
        if (seenInterfaces.add(iFace.type())) {
          builder.addSuperInterfaceSignature(iFace);
        }
      }
      if (rewrittenSource.hasSignature()) {
        for (ClassTypeSignature iFace : rewrittenSource.getSuperInterfaceSignatures()) {
          if (!seenInterfaces.contains(iFace.type())) {
            builder.addSuperInterfaceSignature(iFace);
          }
        }
      } else {
        // Synthesize raw uses of interfaces to align with the actual class
        for (DexType iFace : source.interfaces) {
          if (!seenInterfaces.contains(iFace)) {
            builder.addSuperInterfaceSignature(new ClassTypeSignature(iFace));
          }
        }
      }
      target.setClassSignature(builder.build(appView.dexItemFactory()));

      // Go through all type-variable references for members and update them.
      CollectionUtils.forEach(
          method -> {
            MethodTypeSignature methodSignature = method.getGenericSignature();
            if (methodSignature.hasNoSignature()) {
              return;
            }
            method.setGenericSignature(
                classApplier
                    .buildForMethod(methodSignature.getFormalTypeParameters())
                    .visitMethodSignature(methodSignature));
          },
          directMethods,
          virtualMethods);

      source.forEachField(
          field -> {
            if (field.getGenericSignature().hasNoSignature()) {
              return;
            }
            field.setGenericSignature(
                classApplier.visitFieldTypeSignature(field.getGenericSignature()));
          });
    }

    private GenericSignaturePartialTypeArgumentApplier getGenericSignatureArgumentApplier(
        DexProgramClass target, DexProgramClass source) {
      assert target.getClassSignature().hasSignature();
      // We can assert proper structure below because the generic signature validator has run
      // before and pruned invalid signatures.
      List<FieldTypeSignature> genericArgumentsToSuperType =
          target
              .getClassSignature()
              .getGenericArgumentsToSuperType(source.type, appView.dexItemFactory());
      if (genericArgumentsToSuperType == null) {
        assert false : "Type should be present in generic signature";
        return null;
      }
      Map<String, FieldTypeSignature> substitutionMap = new HashMap<>();
      List<FormalTypeParameter> formals = source.getClassSignature().getFormalTypeParameters();
      if (genericArgumentsToSuperType.size() != formals.size()) {
        if (!genericArgumentsToSuperType.isEmpty()) {
          assert false : "Invalid argument count to formals";
          return null;
        }
      } else {
        for (int i = 0; i < formals.size(); i++) {
          // It is OK to override a generic type variable so we just use put.
          substitutionMap.put(formals.get(i).getName(), genericArgumentsToSuperType.get(i));
        }
      }
      return GenericSignaturePartialTypeArgumentApplier.build(
          appView,
          TypeParameterContext.empty().addPrunedSubstitutions(substitutionMap),
          (type1, type2) -> true,
          type -> true);
    }

    private boolean restoreDebuggingState(Stream<DexEncodedMethod> toBeDiscarded) {
      toBeDiscarded.forEach(
          method -> {
            assert !method.isObsolete();
            method.setObsolete();
          });
      source.forEachMethod(
          method -> {
            if (method.isObsolete()) {
              method.unsetObsolete();
            }
          });
      assert Streams.concat(Streams.stream(source.methods()), Streams.stream(target.methods()))
          .allMatch(method -> !method.isObsolete());
      return true;
    }

    public VerticalClassMergerGraphLens.Builder getRenamings() {
      return deferredRenamings;
    }

    public List<SynthesizedBridgeCode> getSynthesizedBridges() {
      return synthesizedBridges;
    }

    private void redirectSuperCallsInTarget(
        DexEncodedMethod oldTarget, DexEncodedMethod newTarget) {
      DexMethod oldTargetReference = oldTarget.getReference();
      DexMethod newTargetReference = newTarget.getReference();
      InvokeType newTargetType = newTarget.isNonPrivateVirtualMethod() ? VIRTUAL : DIRECT;
      if (source.accessFlags.isInterface()) {
        // If we merge a default interface method from interface I to its subtype C, then we need
        // to rewrite invocations on the form "invoke-super I.m()" to "invoke-direct C.m$I()".
        //
        // Unlike when we merge a class into its subclass (the else-branch below), we should *not*
        // rewrite any invocations on the form "invoke-super J.m()" to "invoke-direct C.m$I()",
        // if I has a supertype J. This is due to the fact that invoke-super instructions that
        // resolve to a method on an interface never hit an implementation below that interface.
        deferredRenamings.mapVirtualMethodToDirectInType(
            oldTargetReference,
            prototypeChanges ->
                new MethodLookupResult(newTargetReference, null, STATIC, prototypeChanges),
            target.type);
      } else {
        // If we merge class B into class C, and class C contains an invocation super.m(), then it
        // is insufficient to rewrite "invoke-super B.m()" to "invoke-{direct,virtual} C.m$B()" (the
        // method C.m$B denotes the direct/virtual method that has been created in C for B.m). In
        // particular, there might be an instruction "invoke-super A.m()" in C that resolves to B.m
        // at runtime (A is a superclass of B), which also needs to be rewritten to
        // "invoke-{direct,virtual} C.m$B()".
        //
        // We handle this by adding a mapping for [target] and all of its supertypes.
        DexProgramClass holder = target;
        while (holder != null && holder.isProgramClass()) {
          DexMethod signatureInHolder =
              oldTargetReference.withHolder(holder, appView.dexItemFactory());
          // Only rewrite the invoke-super call if it does not lead to a NoSuchMethodError.
          boolean resolutionSucceeds =
              holder.lookupVirtualMethod(signatureInHolder) != null
                  || appInfo.lookupSuperTarget(signatureInHolder, holder, appView) != null;
          if (resolutionSucceeds) {
            deferredRenamings.mapVirtualMethodToDirectInType(
                signatureInHolder,
                prototypeChanges ->
                    new MethodLookupResult(
                        newTargetReference, null, newTargetType, prototypeChanges),
                target.type);
          } else {
            break;
          }

          // Consider that A gets merged into B and B's subclass C gets merged into D. Instructions
          // on the form "invoke-super {B,C,D}.m()" in D are changed into "invoke-direct D.m$C()" by
          // the code above. However, instructions on the form "invoke-super A.m()" should also be
          // changed into "invoke-direct D.m$C()". This is achieved by also considering the classes
          // that have been merged into [holder].
          Set<DexType> mergedTypes = mergedClasses.getKeys(holder.type);
          for (DexType type : mergedTypes) {
            DexMethod signatureInType =
                oldTargetReference.withHolder(type, appView.dexItemFactory());
            // Resolution would have succeeded if the method used to be in [type], or if one of
            // its super classes declared the method.
            boolean resolutionSucceededBeforeMerge =
                lensBuilder.hasMappingForSignatureInContext(holder, signatureInType)
                    || appInfo.lookupSuperTarget(signatureInHolder, holder, appView) != null;
            if (resolutionSucceededBeforeMerge) {
              deferredRenamings.mapVirtualMethodToDirectInType(
                  signatureInType,
                  prototypeChanges ->
                      new MethodLookupResult(
                          newTargetReference, null, newTargetType, prototypeChanges),
                  target.type);
            }
          }
          holder =
              holder.superType != null
                  ? asProgramClassOrNull(appInfo.definitionFor(holder.superType))
                  : null;
        }
      }
    }

    private void blockRedirectionOfSuperCalls(DexMethod method) {
      // We are merging a class B into C. The methods from B are being moved into C, and then we
      // subsequently rewrite the invoke-super instructions in C that hit a method in B, such that
      // they use an invoke-direct instruction instead. In this process, we need to avoid rewriting
      // the invoke-super instructions that originally was in the superclass B.
      //
      // Example:
      //   class A {
      //     public void m() {}
      //   }
      //   class B extends A {
      //     public void m() { super.m(); } <- invoke must not be rewritten to invoke-direct
      //                                       (this would lead to an infinite loop)
      //   }
      //   class C extends B {
      //     public void m() { super.m(); } <- invoke needs to be rewritten to invoke-direct
      //   }
      deferredRenamings.markMethodAsMerged(method);
    }

    private DexEncodedMethod buildBridgeMethod(
        DexEncodedMethod method, DexEncodedMethod invocationTarget) {
      DexType holder = target.type;
      DexProto proto = method.getReference().proto;
      DexString name = method.getReference().name;
      DexMethod newMethod = application.dexItemFactory.createMethod(holder, proto, name);
      MethodAccessFlags accessFlags = method.accessFlags.copy();
      accessFlags.setBridge();
      accessFlags.setSynthetic();
      accessFlags.unsetAbstract();

      assert invocationTarget.isStatic()
          || invocationTarget.isNonPrivateVirtualMethod()
          || invocationTarget.isNonStaticPrivateMethod();
      SynthesizedBridgeCode code =
          new SynthesizedBridgeCode(
              newMethod,
              appView.graphLens().getOriginalMethodSignature(method.getReference()),
              invocationTarget.getReference(),
              invocationTarget.isStatic()
                  ? STATIC
                  : (invocationTarget.isNonPrivateVirtualMethod() ? VIRTUAL : DIRECT),
              target.isInterface());

      // Add the bridge to the list of synthesized bridges such that the method signatures will
      // be updated by the end of vertical class merging.
      synthesizedBridges.add(code);

      CfVersion classFileVersion =
          method.hasClassFileVersion() ? method.getClassFileVersion() : null;
      DexEncodedMethod bridge =
          DexEncodedMethod.syntheticBuilder()
              .setMethod(newMethod)
              .setAccessFlags(accessFlags)
              .setCode(code)
              .setClassFileVersion(classFileVersion)
              .setApiLevelForDefinition(method.getApiLevelForDefinition())
              .setApiLevelForCode(method.getApiLevelForDefinition())
              .setIsLibraryMethodOverride(method.isLibraryMethodOverride())
              .setGenericSignature(method.getGenericSignature())
              .build();
      if (method.accessFlags.isPromotedToPublic()) {
        // The bridge is now the public method serving the role of the original method, and should
        // reflect that this method was publicized.
        assert bridge.accessFlags.isPromotedToPublic();
      }
      return bridge;
    }

    @SuppressWarnings("ReferenceEquality")
    // Returns the method that shadows the given method, or null if method is not shadowed.
    private DexEncodedMethod findMethodInTarget(DexEncodedMethod method) {
      MethodResolutionResult resolutionResult =
          appInfo.resolveMethodOnLegacy(target, method.getReference());
      if (!resolutionResult.isSingleResolution()) {
        // May happen in case of missing classes, or if multiple implementations were found.
        abortMerge = true;
        return null;
      }
      DexEncodedMethod actual = resolutionResult.getSingleTarget();
      if (actual != method) {
        assert actual.isVirtualMethod() == method.isVirtualMethod();
        return actual;
      }
      // The method is not actually overridden. This means that we will move `method` to the
      // subtype. If `method` is abstract, then so should the subtype be.
      return null;
    }

    private <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>> void add(
        Map<Wrapper<R>, D> map, D item, Equivalence<R> equivalence) {
      map.put(equivalence.wrap(item.getReference()), item);
    }

    private <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>> void addAll(
        Collection<Wrapper<R>> collection, Iterable<D> items, Equivalence<R> equivalence) {
      for (D item : items) {
        collection.add(equivalence.wrap(item.getReference()));
      }
    }

    private <T> Set<T> mergeArrays(T[] one, T[] other) {
      Set<T> merged = new LinkedHashSet<>();
      Collections.addAll(merged, one);
      Collections.addAll(merged, other);
      return merged;
    }

    private DexEncodedField[] mergeFields(
        Collection<DexEncodedField> sourceFields,
        Collection<DexEncodedField> targetFields,
        Predicate<DexField> availableFieldSignatures,
        Set<DexString> existingFieldNames) {
      DexEncodedField[] result = new DexEncodedField[sourceFields.size() + targetFields.size()];
      // Add fields from source
      int i = 0;
      for (DexEncodedField field : sourceFields) {
        DexEncodedField resultingField = renameFieldIfNeeded(field, availableFieldSignatures);
        existingFieldNames.add(resultingField.getReference().name);
        deferredRenamings.map(field.getReference(), resultingField.getReference());
        result[i] = resultingField;
        i++;
      }
      // Add fields from target.
      for (DexEncodedField field : targetFields) {
        result[i] = field;
        i++;
      }
      return result;
    }

    // Note that names returned by this function are not necessarily unique. Clients should
    // repeatedly try to generate a fresh name until it is unique.
    private DexString getFreshName(String nameString, int index, DexType holder) {
      String freshName = nameString + "$" + holder.toSourceString().replace('.', '$');
      if (index > 1) {
        freshName += index;
      }
      return application.dexItemFactory.createString(freshName);
    }

    private DexEncodedMethod renameConstructor(
        DexEncodedMethod method, Predicate<DexMethod> availableMethodSignatures) {
      assert method.isInstanceInitializer();
      DexType oldHolder = method.getHolderType();

      DexMethod newSignature;
      int count = 1;
      do {
        DexString newName = getFreshName(TEMPORARY_INSTANCE_INITIALIZER_PREFIX, count, oldHolder);
        newSignature =
            application.dexItemFactory.createMethod(
                target.type, method.getReference().proto, newName);
        count++;
      } while (!availableMethodSignatures.test(newSignature));

      DexEncodedMethod result = method.toTypeSubstitutedMethod(newSignature);
      result.getMutableOptimizationInfo().markForceInline();
      deferredRenamings.map(method.getReference(), result.getReference());
      deferredRenamings.recordMove(method.getReference(), result.getReference());
      // Renamed constructors turn into ordinary private functions. They can be private, as
      // they are only references from their direct subclass, which they were merged into.
      result.accessFlags.unsetConstructor();
      makePrivate(result);
      return result;
    }

    private DexEncodedMethod renameMethod(
        DexEncodedMethod method, Predicate<DexMethod> availableMethodSignatures, Rename strategy) {
      return renameMethod(method, availableMethodSignatures, strategy, method.getReference().proto);
    }

    private DexEncodedMethod renameMethod(
        DexEncodedMethod method,
        Predicate<DexMethod> availableMethodSignatures,
        Rename strategy,
        DexProto newProto) {
      // We cannot handle renaming static initializers yet and constructors should have been
      // renamed already.
      assert !method.accessFlags.isConstructor() || strategy == Rename.NEVER;
      DexString oldName = method.getReference().name;
      DexType oldHolder = method.getHolderType();

      DexMethod newSignature;
      switch (strategy) {
        case IF_NEEDED:
          newSignature = application.dexItemFactory.createMethod(target.type, newProto, oldName);
          if (availableMethodSignatures.test(newSignature)) {
            break;
          }
          // Fall-through to ALWAYS so that we assign a new name.

        case ALWAYS:
          int count = 1;
          do {
            DexString newName = getFreshName(oldName.toSourceString(), count, oldHolder);
            newSignature = application.dexItemFactory.createMethod(target.type, newProto, newName);
            count++;
          } while (!availableMethodSignatures.test(newSignature));
          break;

        case NEVER:
          newSignature = application.dexItemFactory.createMethod(target.type, newProto, oldName);
          assert availableMethodSignatures.test(newSignature);
          break;

        default:
          throw new Unreachable();
      }

      return method.toTypeSubstitutedMethod(newSignature);
    }

    private DexEncodedField renameFieldIfNeeded(
        DexEncodedField field, Predicate<DexField> availableFieldSignatures) {
      DexString oldName = field.getReference().name;
      DexType oldHolder = field.getHolderType();

      DexField newSignature =
          application.dexItemFactory.createField(target.type, field.getReference().type, oldName);
      if (!availableFieldSignatures.test(newSignature)) {
        int count = 1;
        do {
          DexString newName = getFreshName(oldName.toSourceString(), count, oldHolder);
          newSignature =
              application.dexItemFactory.createField(
                  target.type, field.getReference().type, newName);
          count++;
        } while (!availableFieldSignatures.test(newSignature));
      }

      return field.toTypeSubstitutedField(appView, newSignature);
    }

    private void makeStatic(DexEncodedMethod method) {
      method.accessFlags.setStatic();
      if (!method.getCode().isCfCode()) {
        // Due to member rebinding we may have inserted bridge methods with synthesized code.
        // Currently, there is no easy way to make such code static.
        abortMerge = true;
      }
    }
  }

  private static void makePrivate(DexEncodedMethod method) {
    assert !method.accessFlags.isAbstract();
    method.accessFlags.unsetPublic();
    method.accessFlags.unsetProtected();
    method.accessFlags.setPrivate();
  }

  private static void makePublic(DexEncodedMethod method) {
    MethodAccessFlags accessFlags = method.getAccessFlags();
    assert !accessFlags.isAbstract();
    accessFlags.unsetPrivate();
    accessFlags.unsetProtected();
    accessFlags.setPublic();
  }

  private static class VerticalClassMergerTreeFixer extends TreeFixerBase {

    private final AppView<AppInfoWithLiveness> appView;
    private final VerticalClassMergerGraphLens.Builder lensBuilder;
    private final VerticallyMergedClasses mergedClasses;
    private final List<SynthesizedBridgeCode> synthesizedBridges;

    VerticalClassMergerTreeFixer(
        AppView<AppInfoWithLiveness> appView,
        VerticalClassMergerGraphLens.Builder lensBuilder,
        VerticallyMergedClasses mergedClasses,
        List<SynthesizedBridgeCode> synthesizedBridges) {
      super(appView);
      this.appView = appView;
      this.lensBuilder =
          VerticalClassMergerGraphLens.Builder.createBuilderForFixup(lensBuilder, mergedClasses);
      this.mergedClasses = mergedClasses;
      this.synthesizedBridges = synthesizedBridges;
    }

    private VerticalClassMergerGraphLens fixupTypeReferences() {
      // Globally substitute merged class types in protos and holders.
      for (DexProgramClass clazz : appView.appInfo().classes()) {
        clazz.getMethodCollection().replaceMethods(this::fixupMethod);
        clazz.setStaticFields(fixupFields(clazz.staticFields()));
        clazz.setInstanceFields(fixupFields(clazz.instanceFields()));
        clazz.setPermittedSubclassAttributes(
            fixupPermittedSubclassAttribute(clazz.getPermittedSubclassAttributes()));
      }
      for (SynthesizedBridgeCode synthesizedBridge : synthesizedBridges) {
        synthesizedBridge.updateMethodSignatures(this::fixupMethodReference);
      }
      VerticalClassMergerGraphLens lens = lensBuilder.build(appView, mergedClasses);
      if (lens != null) {
        new AnnotationFixer(lens, appView.graphLens()).run(appView.appInfo().classes());
      }
      return lens;
    }

    @Override
    public DexType mapClassType(DexType type) {
      while (mergedClasses.hasBeenMergedIntoSubtype(type)) {
        type = mergedClasses.getTargetFor(type);
      }
      return type;
    }

    @Override
    public void recordClassChange(DexType from, DexType to) {
      // Fixup of classes is not used so no class type should change.
      throw new Unreachable();
    }

    @Override
    public void recordFieldChange(DexField from, DexField to) {
      if (!lensBuilder.hasOriginalSignatureMappingFor(to)) {
        lensBuilder.map(from, to);
      }
    }

    @Override
    public void recordMethodChange(DexMethod from, DexMethod to) {
      if (!lensBuilder.hasOriginalSignatureMappingFor(to)) {
        lensBuilder.map(from, to).recordMove(from, to);
      }
    }

    @Override
    public DexEncodedMethod recordMethodChange(
        DexEncodedMethod method, DexEncodedMethod newMethod) {
      recordMethodChange(method.getReference(), newMethod.getReference());
      if (newMethod.isNonPrivateVirtualMethod()) {
        // Since we changed the return type or one of the parameters, this method cannot be a
        // classpath or library method override, since we only class merge program classes.
        assert !method.isLibraryMethodOverride().isTrue();
        newMethod.setLibraryMethodOverride(OptionalBool.FALSE);
      }
      return newMethod;
    }
  }

  private class CollisionDetector {

    private static final int NOT_FOUND = Integer.MIN_VALUE;

    // TODO(herhut): Maybe cache seenPositions for target classes.
    private final Map<DexString, Int2IntMap> seenPositions = new IdentityHashMap<>();
    private final Reference2IntMap<DexProto> targetProtoCache;
    private final Reference2IntMap<DexProto> sourceProtoCache;
    private final DexType source, target;
    private final Collection<DexMethod> invokes = getInvokes();

    private CollisionDetector(DexType source, DexType target) {
      this.source = source;
      this.target = target;
      this.targetProtoCache = new Reference2IntOpenHashMap<>(invokes.size() / 2);
      this.targetProtoCache.defaultReturnValue(NOT_FOUND);
      this.sourceProtoCache = new Reference2IntOpenHashMap<>(invokes.size() / 2);
      this.sourceProtoCache.defaultReturnValue(NOT_FOUND);
    }

    boolean mayCollide() {
      timing.begin("collision detection");
      fillSeenPositions();
      boolean result = false;
      // If the type is not used in methods at all, there cannot be any conflict.
      if (!seenPositions.isEmpty()) {
        for (DexMethod method : invokes) {
          Int2IntMap positionsMap = seenPositions.get(method.name);
          if (positionsMap != null) {
            int arity = method.getArity();
            int previous = positionsMap.get(arity);
            if (previous != NOT_FOUND) {
              assert previous != 0;
              int positions = computePositionsFor(method.proto, source, sourceProtoCache);
              if ((positions & previous) != 0) {
                result = true;
                break;
              }
            }
          }
        }
      }
      timing.end();
      return result;
    }

    private void fillSeenPositions() {
      for (DexMethod method : invokes) {
        DexType[] parameters = method.proto.parameters.values;
        int arity = parameters.length;
        int positions = computePositionsFor(method.proto, target, targetProtoCache);
        if (positions != 0) {
          Int2IntMap positionsMap =
              seenPositions.computeIfAbsent(method.name, k -> {
                Int2IntMap result = new Int2IntOpenHashMap();
                result.defaultReturnValue(NOT_FOUND);
                return result;
              });
          int value = 0;
          int previous = positionsMap.get(arity);
          if (previous != NOT_FOUND) {
            value = previous;
          }
          value |= positions;
          positionsMap.put(arity, value);
        }
      }

    }

    @SuppressWarnings("ReferenceEquality")
    // Given a method signature and a type, this method computes a bit vector that denotes the
    // positions at which the given type is used in the method signature.
    private int computePositionsFor(
        DexProto proto, DexType type, Reference2IntMap<DexProto> cache) {
      int result = cache.getInt(proto);
      if (result != NOT_FOUND) {
        return result;
      }
      result = 0;
      int bitsUsed = 0;
      int accumulator = 0;
      for (DexType parameterType : proto.parameters.values) {
        DexType parameterBaseType = parameterType.toBaseType(appView.dexItemFactory());
        // Substitute the type with the already merged class to estimate what it will look like.
        DexType mappedType = mergedClasses.getOrDefault(parameterBaseType, parameterBaseType);
        accumulator <<= 1;
        bitsUsed++;
        if (mappedType == type) {
          accumulator |= 1;
        }
        // Handle overflow on 31 bit boundary.
        if (bitsUsed == Integer.SIZE - 1) {
          result |= accumulator;
          accumulator = 0;
          bitsUsed = 0;
        }
      }
      // We also take the return type into account for potential conflicts.
      DexType returnBaseType = proto.returnType.toBaseType(appView.dexItemFactory());
      DexType mappedReturnType = mergedClasses.getOrDefault(returnBaseType, returnBaseType);
      accumulator <<= 1;
      if (mappedReturnType == type) {
        accumulator |= 1;
      }
      result |= accumulator;
      cache.put(proto, result);
      return result;
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private AbortReason disallowInlining(ProgramMethod method, DexProgramClass context) {
    if (appView.options().inlinerOptions().enableInlining) {
      Code code = method.getDefinition().getCode();
      if (code.isCfCode()) {
        CfCode cfCode = code.asCfCode();
        ConstraintWithTarget constraint =
            cfCode.computeInliningConstraint(
                method,
                appView,
                new SingleTypeMapperGraphLens(method.getHolderType(), context),
                context.programInstanceInitializers().iterator().next());
        if (constraint == ConstraintWithTarget.NEVER) {
          return AbortReason.UNSAFE_INLINING;
        }
        // Constructors can have references beyond the root main dex classes. This can increase the
        // size of the main dex dependent classes and we should bail out.
        if (mainDexInfo.disallowInliningIntoContext(
            appView, context, method, appView.getSyntheticItems())) {
          return AbortReason.MAIN_DEX_ROOT_OUTSIDE_REFERENCE;
        }
        return null;
      } else if (code.isDefaultInstanceInitializerCode()) {
        return null;
      }
      // For non-jar/cf code we currently cannot guarantee that markForceInline() will succeed.
    }
    return AbortReason.UNSAFE_INLINING;
  }

  public class SingleTypeMapperGraphLens extends NonIdentityGraphLens {

    private final DexType source;
    private final DexProgramClass target;

    public SingleTypeMapperGraphLens(DexType source, DexProgramClass target) {
      super(appView.dexItemFactory(), GraphLens.getIdentityLens());
      this.source = source;
      this.target = target;
    }

    @Override
    public Iterable<DexType> getOriginalTypes(DexType type) {
      throw new Unreachable();
    }

    @Override
    public DexType getPreviousClassType(DexType type) {
      throw new Unreachable();
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public final DexType getNextClassType(DexType type) {
      return type == source ? target.type : mergedClasses.getOrDefault(type, type);
    }

    @Override
    public DexField getPreviousFieldSignature(DexField field) {
      throw new Unreachable();
    }

    @Override
    public DexField getNextFieldSignature(DexField field) {
      throw new Unreachable();
    }

    @Override
    public DexMethod getPreviousMethodSignature(DexMethod method) {
      throw new Unreachable();
    }

    @Override
    public DexMethod getNextMethodSignature(DexMethod method) {
      throw new Unreachable();
    }

    @Override
    public MethodLookupResult lookupMethod(
        DexMethod method, DexMethod context, InvokeType type, GraphLens codeLens) {
      // First look up the method using the existing graph lens (for example, the type will have
      // changed if the method was publicized by ClassAndMemberPublicizer).
      MethodLookupResult lookup = appView.graphLens().lookupMethod(method, context, type, codeLens);
      // Then check if there is a renaming due to the vertical class merger.
      DexMethod newMethod = lensBuilder.methodMap.get(lookup.getReference());
      if (newMethod == null) {
        return lookup;
      }
      MethodLookupResult.Builder methodLookupResultBuilder =
          MethodLookupResult.builder(this)
              .setReference(newMethod)
              .setPrototypeChanges(lookup.getPrototypeChanges())
              .setType(lookup.getType());
      if (lookup.getType() == InvokeType.INTERFACE) {
        // If an interface has been merged into a class, invoke-interface needs to be translated
        // to invoke-virtual.
        DexClass clazz = appInfo.definitionFor(newMethod.holder);
        if (clazz != null && !clazz.accessFlags.isInterface()) {
          assert appInfo.definitionFor(method.holder).accessFlags.isInterface();
          methodLookupResultBuilder.setType(VIRTUAL);
        }
      }
      return methodLookupResultBuilder.build();
    }

    @Override
    protected MethodLookupResult internalDescribeLookupMethod(
        MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
      // This is unreachable since we override the implementation of lookupMethod() above.
      throw new Unreachable();
    }

    @Override
    public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
        DexMethod method, GraphLens codeLens) {
      throw new Unreachable();
    }

    @Override
    public DexField lookupField(DexField field, GraphLens codeLens) {
      return lensBuilder.fieldMap.getOrDefault(field, field);
    }

    @Override
    protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
      // This is unreachable since we override the implementation of lookupField() above.
      throw new Unreachable();
    }

    @Override
    @SuppressWarnings("HidingField")
    public boolean isContextFreeForMethods(GraphLens codeLens) {
      return true;
    }
  }

  // Searches for a reference to a non-private, non-public class, field or method declared in the
  // same package as [source].
  public static class IllegalAccessDetector extends UseRegistryWithResult<Boolean, ProgramMethod> {

    private final AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy;

    public IllegalAccessDetector(
        AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy,
        ProgramMethod context) {
      super(appViewWithClassHierarchy, context, false);
      this.appViewWithClassHierarchy = appViewWithClassHierarchy;
    }

    protected boolean checkFoundPackagePrivateAccess() {
      assert getResult();
      return true;
    }

    protected boolean setFoundPackagePrivateAccess() {
      setResult(true);
      return true;
    }

    protected static boolean continueSearchForPackagePrivateAccess() {
      return false;
    }

    private boolean checkFieldReference(DexField field) {
      return checkRewrittenFieldReference(appViewWithClassHierarchy.graphLens().lookupField(field));
    }

    private boolean checkRewrittenFieldReference(DexField field) {
      assert field.getHolderType().isClassType();
      DexType fieldHolder = field.getHolderType();
      if (fieldHolder.isSamePackage(getContext().getHolderType())) {
        if (checkRewrittenTypeReference(fieldHolder)) {
          return checkFoundPackagePrivateAccess();
        }
        DexClassAndField resolvedField =
            appViewWithClassHierarchy.appInfo().resolveField(field).getResolutionPair();
        if (resolvedField == null) {
          return setFoundPackagePrivateAccess();
        }
        if (resolvedField.getHolder() != getContext().getHolder()
            && !resolvedField.getAccessFlags().isPublic()) {
          return setFoundPackagePrivateAccess();
        }
        if (checkRewrittenFieldType(resolvedField)) {
          return checkFoundPackagePrivateAccess();
        }
      }
      return continueSearchForPackagePrivateAccess();
    }

    protected boolean checkRewrittenFieldType(DexClassAndField field) {
      return continueSearchForPackagePrivateAccess();
    }

    private boolean checkRewrittenMethodReference(
        DexMethod rewrittenMethod, OptionalBool isInterface) {
      DexType baseType =
          rewrittenMethod.getHolderType().toBaseType(appViewWithClassHierarchy.dexItemFactory());
      if (baseType.isClassType() && baseType.isSamePackage(getContext().getHolderType())) {
        if (checkTypeReference(rewrittenMethod.getHolderType())) {
          return checkFoundPackagePrivateAccess();
        }
        MethodResolutionResult resolutionResult =
            isInterface.isUnknown()
                ? appViewWithClassHierarchy
                    .appInfo()
                    .unsafeResolveMethodDueToDexFormat(rewrittenMethod)
                : appViewWithClassHierarchy
                    .appInfo()
                    .resolveMethod(rewrittenMethod, isInterface.isTrue());
        if (!resolutionResult.isSingleResolution()) {
          return setFoundPackagePrivateAccess();
        }
        DexClassAndMethod resolvedMethod =
            resolutionResult.asSingleResolution().getResolutionPair();
        if (resolvedMethod.getHolder() != getContext().getHolder()
            && !resolvedMethod.getAccessFlags().isPublic()) {
          return setFoundPackagePrivateAccess();
        }
      }
      return continueSearchForPackagePrivateAccess();
    }

    private boolean checkTypeReference(DexType type) {
      return internalCheckTypeReference(type, appViewWithClassHierarchy.graphLens());
    }

    private boolean checkRewrittenTypeReference(DexType type) {
      return internalCheckTypeReference(type, GraphLens.getIdentityLens());
    }

    private boolean internalCheckTypeReference(DexType type, GraphLens graphLens) {
      DexType baseType =
          graphLens.lookupType(type.toBaseType(appViewWithClassHierarchy.dexItemFactory()));
      if (baseType.isClassType() && baseType.isSamePackage(getContext().getHolderType())) {
        DexClass clazz = appViewWithClassHierarchy.definitionFor(baseType);
        if (clazz == null || !clazz.isPublic()) {
          return setFoundPackagePrivateAccess();
        }
      }
      return continueSearchForPackagePrivateAccess();
    }

    @Override
    public void registerInitClass(DexType clazz) {
      if (appViewWithClassHierarchy.initClassLens().isFinal()) {
        // The InitClass lens is always rewritten up until the most recent graph lens, so first map
        // the class type to the most recent graph lens.
        DexType rewrittenType = appViewWithClassHierarchy.graphLens().lookupType(clazz);
        DexField initClassField =
            appViewWithClassHierarchy.initClassLens().getInitClassField(rewrittenType);
        checkRewrittenFieldReference(initClassField);
      } else {
        checkTypeReference(clazz);
      }
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      MethodLookupResult lookup =
          appViewWithClassHierarchy.graphLens().lookupInvokeVirtual(method, getContext());
      checkRewrittenMethodReference(lookup.getReference(), OptionalBool.FALSE);
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      MethodLookupResult lookup =
          appViewWithClassHierarchy.graphLens().lookupInvokeDirect(method, getContext());
      checkRewrittenMethodReference(lookup.getReference(), OptionalBool.UNKNOWN);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      MethodLookupResult lookup =
          appViewWithClassHierarchy.graphLens().lookupInvokeStatic(method, getContext());
      checkRewrittenMethodReference(lookup.getReference(), OptionalBool.UNKNOWN);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      MethodLookupResult lookup =
          appViewWithClassHierarchy.graphLens().lookupInvokeInterface(method, getContext());
      checkRewrittenMethodReference(lookup.getReference(), OptionalBool.TRUE);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      MethodLookupResult lookup =
          appViewWithClassHierarchy.graphLens().lookupInvokeSuper(method, getContext());
      checkRewrittenMethodReference(lookup.getReference(), OptionalBool.UNKNOWN);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      checkFieldReference(field);
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      checkFieldReference(field);
    }

    @Override
    public void registerNewInstance(DexType type) {
      checkTypeReference(type);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      checkFieldReference(field);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      checkFieldReference(field);
    }

    @Override
    public void registerTypeReference(DexType type) {
      checkTypeReference(type);
    }

    @Override
    public void registerInstanceOf(DexType type) {
      checkTypeReference(type);
    }
  }

  public static class InvokeSpecialToDefaultLibraryMethodUseRegistry
      extends UseRegistryWithResult<Boolean, ProgramMethod> {

    InvokeSpecialToDefaultLibraryMethodUseRegistry(
        AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
      super(appView, context, false);
      assert context.getHolder().isInterface();
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public void registerInvokeSpecial(DexMethod method) {
      ProgramMethod context = getContext();
      if (method.getHolderType() != context.getHolderType()) {
        return;
      }

      DexEncodedMethod definition = context.getHolder().lookupMethod(method);
      if (definition != null && definition.belongsToVirtualPool()) {
        setResult(true);
      }
    }

    @Override
    public void registerInitClass(DexType type) {}

    @Override
    public void registerInvokeDirect(DexMethod method) {}

    @Override
    public void registerInvokeInterface(DexMethod method) {}

    @Override
    public void registerInvokeStatic(DexMethod method) {}

    @Override
    public void registerInvokeSuper(DexMethod method) {}

    @Override
    public void registerInvokeVirtual(DexMethod method) {}

    @Override
    public void registerInstanceFieldRead(DexField field) {}

    @Override
    public void registerInstanceFieldWrite(DexField field) {}

    @Override
    public void registerStaticFieldRead(DexField field) {}

    @Override
    public void registerStaticFieldWrite(DexField field) {}

    @Override
    public void registerTypeReference(DexType type) {}
  }

  protected static class SynthesizedBridgeCode extends AbstractSynthesizedCode {

    private DexMethod method;
    private DexMethod originalMethod;
    private DexMethod invocationTarget;
    private InvokeType type;
    private final boolean isInterface;

    public SynthesizedBridgeCode(
        DexMethod method,
        DexMethod originalMethod,
        DexMethod invocationTarget,
        InvokeType type,
        boolean isInterface) {
      this.method = method;
      this.originalMethod = originalMethod;
      this.invocationTarget = invocationTarget;
      this.type = type;
      this.isInterface = isInterface;
    }

    // By the time the synthesized code object is created, vertical class merging still has not
    // finished. Therefore it is possible that the method signatures `method` and `invocationTarget`
    // will change as a result of additional class merging operations. To deal with this, the
    // vertical class merger explicitly invokes this method to update `method` and `invocation-
    // Target` when vertical class merging has finished.
    //
    // Note that, without this step, these method signatures might refer to intermediate signatures
    // that are only present in the middle of vertical class merging, which means that the graph
    // lens will not work properly (since the graph lens generated by vertical class merging only
    // expects to be applied to method signatures from *before* vertical class merging or *after*
    // vertical class merging).
    public void updateMethodSignatures(Function<DexMethod, DexMethod> transformer) {
      method = transformer.apply(method);
      invocationTarget = transformer.apply(invocationTarget);
    }

    @Override
    public SourceCodeProvider getSourceCodeProvider() {
      ForwardMethodSourceCode.Builder forwardSourceCodeBuilder =
          ForwardMethodSourceCode.builder(method);
      forwardSourceCodeBuilder
          .setReceiver(method.holder)
          .setOriginalMethod(originalMethod)
          .setTargetReceiver(type.isStatic() ? null : method.holder)
          .setTarget(invocationTarget)
          .setInvokeType(type)
          .setIsInterface(isInterface);
      return (context, callerPosition) -> {
        SyntheticPosition caller =
            SyntheticPosition.builder()
                .setLine(0)
                .setMethod(method)
                .setIsD8R8Synthesized(true)
                .setCallerPosition(callerPosition)
                .build();
        return forwardSourceCodeBuilder.build(context, caller);
      };
    }

    @Override
    public Consumer<UseRegistry> getRegistryCallback(DexClassAndMethod method) {
      return registry -> {
        assert registry.getTraversalContinuation().shouldContinue();
        switch (type) {
          case DIRECT:
            registry.registerInvokeDirect(invocationTarget);
            break;
          case STATIC:
            registry.registerInvokeStatic(invocationTarget);
            break;
          case VIRTUAL:
            registry.registerInvokeVirtual(invocationTarget);
            break;
          default:
            throw new Unreachable("Unexpected invocation type: " + type);
        }
      };
    }
  }

  public Collection<DexType> getRemovedClasses() {
    return Collections.unmodifiableCollection(mergedClasses.keySet());
  }
}
