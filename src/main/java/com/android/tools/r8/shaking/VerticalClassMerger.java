// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.code.Invoke.Type.DIRECT;
import static com.android.tools.r8.ir.code.Invoke.Type.STATIC;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClass.FieldSetter;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.MethodLookupResult;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.LookupResult.LookupResultSuccess;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.graph.TopDownClassHierarchyTraversal;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.classmerging.VerticallyMergedClasses;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.MemberPoolCollection.MemberPool;
import com.android.tools.r8.ir.optimize.MethodPoolCollection;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.synthetic.AbstractSynthesizedCode;
import com.android.tools.r8.ir.synthetic.ForwardMethodSourceCode;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.FieldSignatureEquivalence;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TraversalContinuation;
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
    UNSUPPORTED_ATTRIBUTES;

    public void printLogMessageForClass(DexClass clazz) {
      Log.info(VerticalClassMerger.class, getMessageForClass(clazz));
    }

    private String getMessageForClass(DexClass clazz) {
      String message = null;
      switch (this) {
        case ALREADY_MERGED:
          message = "it has already been merged with its superclass";
          break;
        case ALWAYS_INLINE:
          message = "it is mentioned in appInfo.alwaysInline";
          break;
        case CONFLICT:
          message = "it is conflicting with its subclass";
          break;
        case ILLEGAL_ACCESS:
          message = "it could lead to illegal accesses";
          break;
        case MAIN_DEX_ROOT_OUTSIDE_REFERENCE:
          message = "contains a constructor with a reference outside the main dex classes";
          break;
        case MERGE_ACROSS_NESTS:
          message = "cannot merge across nests, or from nest to non-nest";
          break;
        case NATIVE_METHOD:
          message = "it has a native method";
          break;
        case NO_SIDE_EFFECTS:
          message = "it is mentioned in appInfo.noSideEffects";
          break;
        case PINNED_SOURCE:
          message = "it should be kept";
          break;
        case RESOLUTION_FOR_FIELDS_MAY_CHANGE:
          message = "it could affect field resolution";
          break;
        case RESOLUTION_FOR_METHODS_MAY_CHANGE:
          message = "it could affect method resolution";
          break;
        case SERVICE_LOADER:
          message = "it is used by a service loader";
          break;
        case SOURCE_AND_TARGET_LOCK_CANDIDATES:
          message = "source and target are both lock-candidates";
          break;
        case STATIC_INITIALIZERS:
          message = "merging of static initializers are not supported";
          break;
        case UNHANDLED_INVOKE_DIRECT:
          message = "a virtual method is targeted by an invoke-direct instruction";
          break;
        case UNHANDLED_INVOKE_SUPER:
          message = "it may change the semantics of an invoke-super instruction";
          break;
        case UNSAFE_INLINING:
          message = "force-inlining might fail";
          break;
        case UNSUPPORTED_ATTRIBUTES:
          message = "since inner-class attributes are not supported";
          break;
        default:
          assert false;
      }
      return String.format("Cannot merge %s since %s.", clazz.toSourceString(), message);
    }
  }

  private enum Rename {
    ALWAYS,
    IF_NEEDED,
    NEVER
  }

  private final DexApplication application;
  private final AppInfoWithLiveness appInfo;
  private final AppView<AppInfoWithLiveness> appView;
  private final SubtypingInfo subtypingInfo;
  private final ExecutorService executorService;
  private final MethodPoolCollection methodPoolCollection;
  private final Timing timing;
  private Collection<DexMethod> invokes;

  private final OptimizationFeedback feedback = OptimizationFeedbackSimple.getInstance();

  // Set of merge candidates. Note that this must have a deterministic iteration order.
  private final Set<DexProgramClass> mergeCandidates = new LinkedHashSet<>();

  // Map from source class to target class.
  private final Map<DexType, DexType> mergedClasses = new IdentityHashMap<>();

  // Map from target class to the super classes that have been merged into the target class.
  private final Map<DexType, Set<DexType>> mergedClassesInverse = new IdentityHashMap<>();

  // Set of types that must not be merged into their subtype.
  private final Set<DexType> pinnedTypes = Sets.newIdentityHashSet();

  // The resulting graph lens that should be used after class merging.
  private final VerticalClassMergerGraphLens.Builder renamedMembersLens;

  // All the bridge methods that have been synthesized during vertical class merging.
  private final List<SynthesizedBridgeCode> synthesizedBridges = new ArrayList<>();

  private final MainDexTracingResult mainDexClasses;

  public VerticalClassMerger(
      DexApplication application,
      AppView<AppInfoWithLiveness> appView,
      ExecutorService executorService,
      Timing timing,
      MainDexTracingResult mainDexClasses) {
    this.application = application;
    this.appInfo = appView.appInfo();
    this.appView = appView;
    this.subtypingInfo = appInfo.computeSubtypingInfo();
    this.executorService = executorService;
    this.methodPoolCollection = new MethodPoolCollection(appView, subtypingInfo);
    this.renamedMembersLens = new VerticalClassMergerGraphLens.Builder(appView.dexItemFactory());
    this.timing = timing;
    this.mainDexClasses = mainDexClasses;

    Iterable<DexProgramClass> classes = application.classesWithDeterministicOrder();
    initializePinnedTypes(classes); // Must be initialized prior to mergeCandidates.
    initializeMergeCandidates(classes);
  }

  private VerticallyMergedClasses getMergedClasses() {
    return new VerticallyMergedClasses(mergedClasses, mergedClassesInverse);
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
    appInfo.getKeepInfo().forEachPinnedType(pinnedItems::add);
    appInfo.getKeepInfo().forEachPinnedMethod(pinnedItems::add);
    appInfo.getKeepInfo().forEachPinnedField(pinnedItems::add);
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
    for (DexMethod method : appInfo.getFailedResolutionTargets()) {
      markTypeAsPinned(method.holder, AbortReason.RESOLUTION_FOR_METHODS_MAY_CHANGE);
    }
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

  private void markTypeAsPinned(DexType type, AbortReason reason) {
    DexType baseType = type.toBaseType(appView.dexItemFactory());
    if (!baseType.isClassType() || appInfo.isPinned(baseType)) {
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
  private boolean isMergeCandidate(
      DexProgramClass sourceClass, DexProgramClass targetClass, Set<DexType> pinnedTypes) {
    assert targetClass != null;
    ObjectAllocationInfoCollection allocationInfo = appInfo.getObjectAllocationInfoCollection();
    if (allocationInfo.isInstantiatedDirectly(sourceClass)
        || allocationInfo.isInterfaceWithUnknownSubtypeHierarchy(sourceClass)
        || allocationInfo.isImmediateInterfaceOfInstantiatedLambda(sourceClass)
        || appInfo.isPinned(sourceClass.type)
        || pinnedTypes.contains(sourceClass.type)
        || appInfo.getNoVerticalClassMergingSet().contains(sourceClass.type)) {
      return false;
    }

    assert Streams.stream(Iterables.concat(sourceClass.fields(), sourceClass.methods()))
        .map(DexEncodedMember::getReference)
        .noneMatch(appInfo::isPinned);

    if (!appInfo
        .getClassToFeatureSplitMap()
        .isInSameFeatureOrBothInBase(sourceClass, targetClass)) {
      return false;
    }
    if (appView.appServices().allServiceTypes().contains(sourceClass.type)
        && appInfo.isPinned(targetClass.type)) {
      if (Log.ENABLED) {
        AbortReason.SERVICE_LOADER.printLogMessageForClass(sourceClass);
      }
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
      TraversalContinuation result =
          sourceClass.traverseProgramInstanceInitializers(
              method -> {
                AbortReason reason = disallowInlining(method, targetClass);
                if (reason != null) {
                  // Cannot guarantee that markForceInline() will work.
                  if (Log.ENABLED) {
                    reason.printLogMessageForClass(sourceClass);
                  }
                  return TraversalContinuation.BREAK;
                }
                return TraversalContinuation.CONTINUE;
              });
      if (result.shouldBreak()) {
        return false;
      }
    }
    if (sourceClass.getEnclosingMethodAttribute() != null
        || !sourceClass.getInnerClasses().isEmpty()) {
      // TODO(b/147504070): Consider merging of enclosing-method and inner-class attributes.
      if (Log.ENABLED) {
        AbortReason.UNSUPPORTED_ATTRIBUTES.printLogMessageForClass(sourceClass);
      }
      return false;
    }
    // We abort class merging when merging across nests or from a nest to non-nest.
    // Without nest this checks null == null.
    if (targetClass.getNestHost() != sourceClass.getNestHost()) {
      if (Log.ENABLED) {
        AbortReason.MERGE_ACROSS_NESTS.printLogMessageForClass(sourceClass);
      }
      return false;
    }
    return true;
  }

  // Returns true if [clazz] is a merge candidate. Note that the result of the checks in this
  // method may change in response to class merges. Therefore, this method should always be called
  // before merging [clazz] into its subtype.
  private boolean isStillMergeCandidate(DexProgramClass sourceClass, DexProgramClass targetClass) {
    assert isMergeCandidate(sourceClass, targetClass, pinnedTypes);
    if (mergedClassesInverse.containsKey(sourceClass.type)) {
      // Do not allow merging the resulting class into its subclass.
      // TODO(christofferqa): Get rid of this limitation.
      if (Log.ENABLED) {
        AbortReason.ALREADY_MERGED.printLogMessageForClass(sourceClass);
      }
      return false;
    }
    // For interface types, this is more complicated, see:
    // https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-5.html#jvms-5.5
    // We basically can't move the clinit, since it is not called when implementing classes have
    // their clinit called - except when the interface has a default method.
    if ((sourceClass.hasClassInitializer() && targetClass.hasClassInitializer())
        || targetClass.classInitializationMayHaveSideEffects(
            appView, type -> type == sourceClass.type, Sets.newIdentityHashSet())
        || (sourceClass.isInterface()
            && sourceClass.classInitializationMayHaveSideEffects(appView))) {
      // TODO(herhut): Handle class initializers.
      if (Log.ENABLED) {
        AbortReason.STATIC_INITIALIZERS.printLogMessageForClass(sourceClass);
      }
      return false;
    }
    boolean sourceCanBeSynchronizedOn =
        appView.appInfo().isLockCandidate(sourceClass.type)
            || sourceClass.hasStaticSynchronizedMethods();
    boolean targetCanBeSynchronizedOn =
        appView.appInfo().isLockCandidate(targetClass.type)
            || targetClass.hasStaticSynchronizedMethods();
    if (sourceCanBeSynchronizedOn && targetCanBeSynchronizedOn) {
      if (Log.ENABLED) {
        AbortReason.SOURCE_AND_TARGET_LOCK_CANDIDATES.printLogMessageForClass(sourceClass);
      }
      return false;
    }
    if (targetClass.getEnclosingMethodAttribute() != null
        || !targetClass.getInnerClasses().isEmpty()) {
      // TODO(b/147504070): Consider merging of enclosing-method and inner-class attributes.
      if (Log.ENABLED) {
        AbortReason.UNSUPPORTED_ATTRIBUTES.printLogMessageForClass(sourceClass);
      }
      return false;
    }
    if (methodResolutionMayChange(sourceClass, targetClass)) {
      if (Log.ENABLED) {
        AbortReason.RESOLUTION_FOR_METHODS_MAY_CHANGE.printLogMessageForClass(sourceClass);
      }
      return false;
    }
    // Field resolution first considers the direct interfaces of [targetClass] before it proceeds
    // to the super class.
    if (fieldResolutionMayChange(sourceClass, targetClass)) {
      if (Log.ENABLED) {
        AbortReason.RESOLUTION_FOR_FIELDS_MAY_CHANGE.printLogMessageForClass(sourceClass);
      }
      return false;
    }
    return true;
  }

  private boolean mergeMayLeadToIllegalAccesses(DexProgramClass source, DexProgramClass target) {
    if (source.type.isSamePackage(target.type)) {
      // When merging two classes from the same package, we only need to make sure that [source]
      // does not get less visible, since that could make a valid access to [source] from another
      // package illegal after [source] has been merged into [target].
      int accessLevel = source.isPrivate() ? 0 : (source.isPublic() ? 2 : 1);
      int otherAccessLevel = target.isPrivate() ? 0 : (target.isPublic() ? 2 : 1);
      return accessLevel > otherAccessLevel;
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
        DexEncodedMethod targetOverride = target.lookupVirtualMethod(method.method);
        if (targetOverride != null && !targetOverride.isPublic()) {
          return true;
        }
      }
    }
    // Check that all accesses from [source] to classes or members from the current package of
    // [source] will continue to work. This is guaranteed if the methods of [source] do not access
    // any private or protected classes or members from the current package of [source].
    IllegalAccessDetector registry = new IllegalAccessDetector(appView, source);
    TraversalContinuation result =
        source.traverseProgramMethods(
            method -> {
              registry.setContext(method);
              method.registerCodeReferences(registry);
              if (registry.foundIllegalAccess()) {
                return TraversalContinuation.BREAK;
              }
              return TraversalContinuation.CONTINUE;
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

    public Collection<DexMethod> get() {
      Map<DexString, DexProto> overloadingInfo = new HashMap<>();

      // Find all signatures that may reference a type that could be the source or target of a
      // merge operation.
      Set<Wrapper<DexMethod>> filteredSignatures = new HashSet<>();
      for (DexProgramClass clazz : appInfo.classes()) {
        for (DexEncodedMethod encodedMethod : clazz.methods()) {
          DexMethod method = encodedMethod.method;
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

  public VerticalClassMergerGraphLens run() {
    timing.begin("merge");
    // Visit the program classes in a top-down order according to the class hierarchy.
    TopDownClassHierarchyTraversal.forProgramClasses(appView)
        .visit(mergeCandidates, this::mergeClassIfPossible);
    if (Log.ENABLED) {
      Log.debug(getClass(), "Merged %d classes.", mergedClasses.size());
    }
    timing.end();

    if (mergedClasses.isEmpty()) {
      return null;
    }

    timing.begin("fixup");
    VerticalClassMergerGraphLens lens = new TreeFixer().fixupTypeReferences();
    KeepInfoCollection keepInfo = appView.appInfo().getKeepInfo();
    keepInfo.mutate(mutator -> mutator.removeKeepInfoForPrunedItems(mergedClasses.keySet()));
    timing.end();

    assert lens != null;
    assert verifyGraphLens(lens);
    return lens;
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
    assert graphLens.assertPinnedNotModified(appInfo.getKeepInfo());

    for (DexProgramClass clazz : appInfo.classes()) {
      for (DexEncodedMethod encodedMethod : clazz.methods()) {
        DexMethod method = encodedMethod.method;
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

  private boolean methodResolutionMayChange(DexProgramClass source, DexProgramClass target) {
    for (DexEncodedMethod virtualSourceMethod : source.virtualMethods()) {
      DexEncodedMethod directTargetMethod = target.lookupDirectMethod(virtualSourceMethod.method);
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
                .resolveMethodOnInterface(method.holder(), method.method)
                .lookupVirtualDispatchTargets(target, appInfo)
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
    assert isMergeCandidate(clazz, targetClass, pinnedTypes);
    assert !mergedClasses.containsKey(targetClass.type);

    boolean clazzOrTargetClassHasBeenMerged =
        mergedClassesInverse.containsKey(clazz.type)
            || mergedClassesInverse.containsKey(targetClass.type);
    if (clazzOrTargetClassHasBeenMerged) {
      if (!isStillMergeCandidate(clazz, targetClass)) {
        return;
      }
    } else {
      assert isStillMergeCandidate(clazz, targetClass);
    }

    // Guard against the case where we have two methods that may get the same signature
    // if we replace types. This is rare, so we approximate and err on the safe side here.
    if (new CollisionDetector(clazz.type, targetClass.type).mayCollide()) {
      if (Log.ENABLED) {
        AbortReason.CONFLICT.printLogMessageForClass(clazz);
      }
      return;
    }

    // For a main dex class in the dependent set only merge with other classes in either main dex
    // set.
    if ((mainDexClasses.getDependencies().contains(clazz.type)
        || mainDexClasses.getDependencies().contains(targetClass.type))
        && !(mainDexClasses.getClasses().contains(clazz.type)
        && mainDexClasses.getClasses().contains(targetClass.type))) {
      return;
    }

    // For a main dex class in the root set only merge with other classes in main dex root set.
    if ((mainDexClasses.getRoots().contains(clazz.type)
        || mainDexClasses.getRoots().contains(targetClass.type))
        && !(mainDexClasses.getRoots().contains(clazz.type)
        && mainDexClasses.getRoots().contains(targetClass.type))) {
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
      renamedMembersLens.merge(merger.getRenamings());
      synthesizedBridges.addAll(merger.getSynthesizedBridges());
    }
    if (Log.ENABLED) {
      if (merged) {
        Log.info(
            getClass(),
            "Merged class %s into %s.",
            clazz.toSourceString(),
            targetClass.toSourceString());
      } else {
        Log.info(
            getClass(),
            "Aborted merge for class %s into %s.",
            clazz.toSourceString(),
            targetClass.toSourceString());
      }
    }
  }

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
          staticFieldsInInterfacesOfTarget.add(equivalence.wrap(staticField.field));
        }
      }
      for (DexEncodedField instanceField : source.instanceFields()) {
        if (staticFieldsInInterfacesOfTarget.contains(equivalence.wrap(instanceField.field))) {
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

    private static final String CONSTRUCTOR_NAME = "constructor";

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

      for (DexEncodedMethod directMethod : source.directMethods()) {
        if (directMethod.isInstanceInitializer()) {
          DexEncodedMethod resultingConstructor =
              renameConstructor(directMethod, availableMethodSignatures);
          add(directMethods, resultingConstructor, MethodSignatureEquivalence.get());
          blockRedirectionOfSuperCalls(resultingConstructor.method);
        } else {
          DexEncodedMethod resultingDirectMethod =
              renameMethod(
                  directMethod,
                  availableMethodSignatures,
                  directMethod.isClassInitializer() ? Rename.NEVER : Rename.IF_NEEDED);
          add(directMethods, resultingDirectMethod, MethodSignatureEquivalence.get());
          deferredRenamings.map(directMethod.method, resultingDirectMethod.method);
          deferredRenamings.recordMove(directMethod.method, resultingDirectMethod.method);
          blockRedirectionOfSuperCalls(resultingDirectMethod.method);
        }
      }

      for (DexEncodedMethod virtualMethod : source.virtualMethods()) {
        DexEncodedMethod shadowedBy = findMethodInTarget(virtualMethod);
        if (shadowedBy != null) {
          if (virtualMethod.isAbstract()) {
            // Remove abstract/interface methods that are shadowed.
            deferredRenamings.map(virtualMethod.method, shadowedBy.method);

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
            deferredRenamings.map(virtualMethod.method, resultingVirtualMethod.method);
            deferredRenamings.recordMove(virtualMethod.method, resultingVirtualMethod.method);
            add(virtualMethods, resultingVirtualMethod, MethodSignatureEquivalence.get());
            continue;
          }
        }

        DexEncodedMethod resultingDirectMethod;
        if (source.accessFlags.isInterface()) {
          // Moving a default interface method into its subtype. This method could be hit directly
          // via an invoke-super instruction from any of the transitive subtypes of this interface,
          // due to the way invoke-super works on default interface methods. In order to be able
          // to hit this method directly after the merge, we need to make it public, and find a
          // method name that does not collide with one in the hierarchy of this class.
          MemberPool<DexMethod> methodPoolForTarget =
              methodPoolCollection.buildForHierarchy(target, executorService, timing);
          resultingDirectMethod =
              renameMethod(
                  virtualMethod,
                  method ->
                      availableMethodSignatures.test(method)
                          && !methodPoolForTarget.hasSeen(
                              MethodSignatureEquivalence.get().wrap(method)),
                  Rename.ALWAYS,
                  appView.dexItemFactory().prependHolderToProto(virtualMethod.getReference()));
          makeStatic(resultingDirectMethod);

          // Update method pool collection now that we are adding a new public method.
          methodPoolForTarget.seen(resultingDirectMethod.method);
        } else {
          // This virtual method could be called directly from a sub class via an invoke-super in-
          // struction. Therefore, we translate this virtual method into a direct method, such that
          // relevant invoke-super instructions can be rewritten into invoke-direct instructions.
          resultingDirectMethod =
              renameMethod(virtualMethod, availableMethodSignatures, Rename.ALWAYS);
          makePrivate(resultingDirectMethod);
        }

        add(directMethods, resultingDirectMethod, MethodSignatureEquivalence.get());

        // Record that invoke-super instructions in the target class should be redirected to the
        // newly created direct method.
        redirectSuperCallsInTarget(virtualMethod.method, resultingDirectMethod.method);
        blockRedirectionOfSuperCalls(resultingDirectMethod.method);

        if (shadowedBy == null) {
          // In addition to the newly added direct method, create a virtual method such that we do
          // not accidentally remove the method from the interface of this class.
          // Note that this method is added independently of whether it will actually be used. If
          // it turns out that the method is never used, it will be removed by the final round
          // of tree shaking.
          shadowedBy = buildBridgeMethod(virtualMethod, resultingDirectMethod);
          deferredRenamings.recordCreationOfBridgeMethod(virtualMethod.method, shadowedBy.method);
          add(virtualMethods, shadowedBy, MethodSignatureEquivalence.get());
        }

        deferredRenamings.map(virtualMethod.method, shadowedBy.method);
        deferredRenamings.recordMove(virtualMethod.method, resultingDirectMethod.method);
      }

      if (abortMerge) {
        assert restoreDebuggingState(
            Streams.concat(directMethods.values().stream(), virtualMethods.values().stream()));
        return false;
      }

      // Step 2: Merge fields
      Set<DexString> existingFieldNames = new HashSet<>();
      for (DexEncodedField field : target.fields()) {
        existingFieldNames.add(field.field.name);
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
      // Step 2: replace fields and methods.
      target.addDirectMethods(directMethods.values());
      target.addVirtualMethods(virtualMethods.values());
      target.setInstanceFields(mergedInstanceFields);
      target.setStaticFields(mergedStaticFields);
      target.forEachField(feedback::markFieldCannotBeKept);
      target.forEachMethod(feedback::markMethodCannotBeKept);
      // Step 3: Clear the members of the source class since they have now been moved to the target.
      source.getMethodCollection().clearDirectMethods();
      source.getMethodCollection().clearVirtualMethods();
      source.setInstanceFields(null);
      source.setStaticFields(null);
      // Step 4: Record merging.
      mergedClasses.put(source.type, target.type);
      mergedClassesInverse.computeIfAbsent(target.type, key -> new HashSet<>()).add(source.type);
      assert !abortMerge;
      return true;
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

    private void redirectSuperCallsInTarget(DexMethod oldTarget, DexMethod newTarget) {
      if (source.accessFlags.isInterface()) {
        // If we merge a default interface method from interface I to its subtype C, then we need
        // to rewrite invocations on the form "invoke-super I.m()" to "invoke-direct C.m$I()".
        //
        // Unlike when we merge a class into its subclass (the else-branch below), we should *not*
        // rewrite any invocations on the form "invoke-super J.m()" to "invoke-direct C.m$I()",
        // if I has a supertype J. This is due to the fact that invoke-super instructions that
        // resolve to a method on an interface never hit an implementation below that interface.
        deferredRenamings.mapVirtualMethodToDirectInType(
            oldTarget,
            prototypeChanges -> new MethodLookupResult(newTarget, null, STATIC, prototypeChanges),
            target.type);
      } else {
        // If we merge class B into class C, and class C contains an invocation super.m(), then it
        // is insufficient to rewrite "invoke-super B.m()" to "invoke-direct C.m$B()" (the method
        // C.m$B denotes the direct method that has been created in C for B.m). In particular, there
        // might be an instruction "invoke-super A.m()" in C that resolves to B.m at runtime (A is
        // a superclass of B), which also needs to be rewritten to "invoke-direct C.m$B()".
        //
        // We handle this by adding a mapping for [target] and all of its supertypes.
        DexProgramClass holder = target;
        while (holder != null && holder.isProgramClass()) {
          DexMethod signatureInHolder =
              application.dexItemFactory.createMethod(holder.type, oldTarget.proto, oldTarget.name);
          // Only rewrite the invoke-super call if it does not lead to a NoSuchMethodError.
          boolean resolutionSucceeds =
              holder.lookupVirtualMethod(signatureInHolder) != null
                  || appInfo.lookupSuperTarget(signatureInHolder, holder) != null;
          if (resolutionSucceeds) {
            deferredRenamings.mapVirtualMethodToDirectInType(
                signatureInHolder,
                prototypeChanges ->
                    new MethodLookupResult(newTarget, null, DIRECT, prototypeChanges),
                target.type);
          } else {
            break;
          }

          // Consider that A gets merged into B and B's subclass C gets merged into D. Instructions
          // on the form "invoke-super {B,C,D}.m()" in D are changed into "invoke-direct D.m$C()" by
          // the code above. However, instructions on the form "invoke-super A.m()" should also be
          // changed into "invoke-direct D.m$C()". This is achieved by also considering the classes
          // that have been merged into [holder].
          Set<DexType> mergedTypes = mergedClassesInverse.get(holder.type);
          if (mergedTypes != null) {
            for (DexType type : mergedTypes) {
              DexMethod signatureInType =
                  application.dexItemFactory.createMethod(type, oldTarget.proto, oldTarget.name);
              // Resolution would have succeeded if the method used to be in [type], or if one of
              // its super classes declared the method.
              boolean resolutionSucceededBeforeMerge =
                  renamedMembersLens.hasMappingForSignatureInContext(holder, signatureInType)
                      || appInfo.lookupSuperTarget(signatureInHolder, holder) != null;
              if (resolutionSucceededBeforeMerge) {
                deferredRenamings.mapVirtualMethodToDirectInType(
                    signatureInType,
                    prototypeChanges ->
                        new MethodLookupResult(newTarget, null, DIRECT, prototypeChanges),
                    target.type);
              }
            }
          }
          holder =
              holder.superType != null
                  ? appInfo.definitionFor(holder.superType).asProgramClass()
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
      DexProto proto = method.method.proto;
      DexString name = method.method.name;
      DexMethod newMethod = application.dexItemFactory.createMethod(holder, proto, name);
      MethodAccessFlags accessFlags = method.accessFlags.copy();
      accessFlags.setBridge();
      accessFlags.setSynthetic();
      accessFlags.unsetAbstract();

      assert invocationTarget.isPrivateMethod() == !invocationTarget.isStatic();
      SynthesizedBridgeCode code =
          new SynthesizedBridgeCode(
              newMethod,
              appView.graphLens().getOriginalMethodSignature(method.method),
              invocationTarget.method,
              invocationTarget.isPrivateMethod() ? DIRECT : STATIC,
              target.isInterface());

      // Add the bridge to the list of synthesized bridges such that the method signatures will
      // be updated by the end of vertical class merging.
      synthesizedBridges.add(code);

      DexEncodedMethod bridge =
          new DexEncodedMethod(
              newMethod,
              accessFlags,
              MethodTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              ParameterAnnotationsList.empty(),
              code,
              true,
              method.hasClassFileVersion() ? method.getClassFileVersion() : null);
      bridge.setLibraryMethodOverride(method.isLibraryMethodOverride());
      if (method.accessFlags.isPromotedToPublic()) {
        // The bridge is now the public method serving the role of the original method, and should
        // reflect that this method was publicized.
        assert bridge.accessFlags.isPromotedToPublic();
      }
      return bridge;
    }

    // Returns the method that shadows the given method, or null if method is not shadowed.
    private DexEncodedMethod findMethodInTarget(DexEncodedMethod method) {
      ResolutionResult resolutionResult = appInfo.resolveMethodOn(target, method.method);
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
      if (Log.ENABLED) {
        if (method.accessFlags.isAbstract() && !target.accessFlags.isAbstract()) {
          Log.warn(
              VerticalClassMerger.class,
              "The non-abstract type `"
                  + target.type.toSourceString()
                  + "` does not implement the method `"
                  + method.method.toSourceString()
                  + "`.");
        }
      }
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
        existingFieldNames.add(resultingField.field.name);
        deferredRenamings.map(field.field, resultingField.field);
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
      DexType oldHolder = method.holder();

      DexMethod newSignature;
      int count = 1;
      do {
        DexString newName = getFreshName(CONSTRUCTOR_NAME, count, oldHolder);
        newSignature =
            application.dexItemFactory.createMethod(target.type, method.method.proto, newName);
        count++;
      } while (!availableMethodSignatures.test(newSignature));

      DexEncodedMethod result = method.toTypeSubstitutedMethod(newSignature);
      result.getMutableOptimizationInfo().markForceInline();
      deferredRenamings.map(method.method, result.method);
      deferredRenamings.recordMove(method.method, result.method);
      // Renamed constructors turn into ordinary private functions. They can be private, as
      // they are only references from their direct subclass, which they were merged into.
      result.accessFlags.unsetConstructor();
      makePrivate(result);
      return result;
    }

    private DexEncodedMethod renameMethod(
        DexEncodedMethod method, Predicate<DexMethod> availableMethodSignatures, Rename strategy) {
      return renameMethod(method, availableMethodSignatures, strategy, method.method.proto);
    }

    private DexEncodedMethod renameMethod(
        DexEncodedMethod method,
        Predicate<DexMethod> availableMethodSignatures,
        Rename strategy,
        DexProto newProto) {
      // We cannot handle renaming static initializers yet and constructors should have been
      // renamed already.
      assert !method.accessFlags.isConstructor() || strategy == Rename.NEVER;
      DexString oldName = method.method.name;
      DexType oldHolder = method.holder();

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
      DexString oldName = field.field.name;
      DexType oldHolder = field.holder();

      DexField newSignature =
          application.dexItemFactory.createField(target.type, field.field.type, oldName);
      if (!availableFieldSignatures.test(newSignature)) {
        int count = 1;
        do {
          DexString newName = getFreshName(oldName.toSourceString(), count, oldHolder);
          newSignature =
              application.dexItemFactory.createField(target.type, field.field.type, newName);
          count++;
        } while (!availableFieldSignatures.test(newSignature));
      }

      return field.toTypeSubstitutedField(newSignature);
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

  private class TreeFixer {

    private final VerticalClassMergerGraphLens.Builder lensBuilder =
        VerticalClassMergerGraphLens.Builder.createBuilderForFixup(
            renamedMembersLens, mergedClasses);
    private final Map<DexProto, DexProto> protoFixupCache = new IdentityHashMap<>();

    private VerticalClassMergerGraphLens fixupTypeReferences() {
      // Globally substitute merged class types in protos and holders.
      for (DexProgramClass clazz : appInfo.classes()) {
        clazz.getMethodCollection().replaceMethods(this::fixupMethod);
        fixupFields(clazz.staticFields(), clazz::setStaticField);
        fixupFields(clazz.instanceFields(), clazz::setInstanceField);
      }
      for (SynthesizedBridgeCode synthesizedBridge : synthesizedBridges) {
        synthesizedBridge.updateMethodSignatures(this::fixupMethod);
      }
      VerticalClassMergerGraphLens lens = lensBuilder.build(appView, getMergedClasses());
      if (lens != null) {
        new AnnotationFixer(lens).run(appView.appInfo().classes());
      }
      return lens;
    }

    private DexEncodedMethod fixupMethod(DexEncodedMethod method) {
      DexMethod methodReference = method.method;
      DexMethod newMethodReference = fixupMethod(methodReference);
      if (newMethodReference != methodReference) {
        if (!lensBuilder.hasOriginalSignatureMappingFor(newMethodReference)) {
          lensBuilder
              .map(methodReference, newMethodReference)
              .recordMove(methodReference, newMethodReference);
        }
        DexEncodedMethod newMethod = method.toTypeSubstitutedMethod(newMethodReference);
        if (newMethod.isNonPrivateVirtualMethod()) {
          // Since we changed the return type or one of the parameters, this method cannot be a
          // classpath or library method override, since we only class merge program classes.
          assert !method.isLibraryMethodOverride().isTrue();
          newMethod.setLibraryMethodOverride(OptionalBool.FALSE);
        }
        return newMethod;
      }
      return method;
    }

    private void fixupFields(List<DexEncodedField> fields, FieldSetter setter) {
      if (fields == null) {
        return;
      }
      for (int i = 0; i < fields.size(); i++) {
        DexEncodedField encodedField = fields.get(i);
        DexField field = encodedField.field;
        DexType newType = fixupType(field.type);
        DexType newHolder = fixupType(field.holder);
        DexField newField = application.dexItemFactory.createField(newHolder, newType, field.name);
        if (newField != encodedField.field) {
          if (!lensBuilder.hasOriginalSignatureMappingFor(newField)) {
            lensBuilder.map(field, newField);
          }
          setter.setField(i, encodedField.toTypeSubstitutedField(newField));
        }
      }
    }

    private DexMethod fixupMethod(DexMethod method) {
      return application.dexItemFactory.createMethod(
          fixupType(method.holder), fixupProto(method.proto), method.name);
    }

    private DexProto fixupProto(DexProto proto) {
      DexProto result = protoFixupCache.get(proto);
      if (result == null) {
        DexType returnType = fixupType(proto.returnType);
        DexType[] arguments = fixupTypes(proto.parameters.values);
        result = application.dexItemFactory.createProto(returnType, arguments);
        protoFixupCache.put(proto, result);
      }
      return result;
    }

    private DexType fixupType(DexType type) {
      if (type.isArrayType()) {
        DexType base = type.toBaseType(application.dexItemFactory);
        DexType fixed = fixupType(base);
        if (base == fixed) {
          return type;
        }
        return type.replaceBaseType(fixed, application.dexItemFactory);
      }
      if (type.isClassType()) {
        while (mergedClasses.containsKey(type)) {
          type = mergedClasses.get(type);
        }
      }
      return type;
    }

    private DexType[] fixupTypes(DexType[] types) {
      DexType[] result = new DexType[types.length];
      for (int i = 0; i < result.length; i++) {
        result[i] = fixupType(types[i]);
      }
      return result;
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

  private AbortReason disallowInlining(ProgramMethod method, DexProgramClass context) {
    if (appView.options().enableInlining) {
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
        if (mainDexClasses.getRoots().contains(context.type)
            && MainDexDirectReferenceTracer.hasReferencesOutsideFromCode(
                appView.appInfo(), method, mainDexClasses.getRoots())) {
          return AbortReason.MAIN_DEX_ROOT_OUTSIDE_REFERENCE;
        }
        return null;
      }
      // For non-jar/cf code we currently cannot guarantee that markForceInline() will succeed.
    }
    return AbortReason.UNSAFE_INLINING;
  }

  private class SingleTypeMapperGraphLens extends NonIdentityGraphLens {

    private final DexType source;
    private final DexProgramClass target;

    public SingleTypeMapperGraphLens(DexType source, DexProgramClass target) {
      super(appView.dexItemFactory(), GraphLens.getIdentityLens());
      this.source = source;
      this.target = target;
    }

    @Override
    public DexType getOriginalType(DexType type) {
      throw new Unreachable();
    }

    @Override
    public Iterable<DexType> getOriginalTypes(DexType type) {
      throw new Unreachable();
    }

    @Override
    public DexField getOriginalFieldSignature(DexField field) {
      throw new Unreachable();
    }

    @Override
    public DexMethod getOriginalMethodSignature(DexMethod method) {
      throw new Unreachable();
    }

    @Override
    public DexField getRenamedFieldSignature(DexField originalField) {
      throw new Unreachable();
    }

    @Override
    public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
      throw new Unreachable();
    }

    @Override
    public final DexType internalDescribeLookupClassType(DexType previous) {
      return previous == source ? target.type : mergedClasses.getOrDefault(previous, previous);
    }

    @Override
    protected DexMethod internalGetPreviousMethodSignature(DexMethod method) {
      throw new Unreachable();
    }

    @Override
    public MethodLookupResult lookupMethod(DexMethod method, DexMethod context, Type type) {
      // First look up the method using the existing graph lens (for example, the type will have
      // changed if the method was publicized by ClassAndMemberPublicizer).
      MethodLookupResult lookup = appView.graphLens().lookupMethod(method, context, type);
      // Then check if there is a renaming due to the vertical class merger.
      DexMethod newMethod = renamedMembersLens.methodMap.get(lookup.getReference());
      if (newMethod == null) {
        return lookup;
      }
      MethodLookupResult.Builder methodLookupResultBuilder =
          MethodLookupResult.builder(this)
              .setReference(newMethod)
              .setPrototypeChanges(lookup.getPrototypeChanges())
              .setType(lookup.getType());
      if (lookup.getType() == Type.INTERFACE) {
        // If an interface has been merged into a class, invoke-interface needs to be translated
        // to invoke-virtual.
        DexClass clazz = appInfo.definitionFor(newMethod.holder);
        if (clazz != null && !clazz.accessFlags.isInterface()) {
          assert appInfo.definitionFor(method.holder).accessFlags.isInterface();
          methodLookupResultBuilder.setType(Type.VIRTUAL);
        }
      }
      return methodLookupResultBuilder.build();
    }

    @Override
    protected MethodLookupResult internalDescribeLookupMethod(
        MethodLookupResult previous, DexMethod context) {
      // This is unreachable since we override the implementation of lookupMethod() above.
      throw new Unreachable();
    }

    @Override
    public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
        DexMethod method) {
      throw new Unreachable();
    }

    @Override
    public DexField lookupField(DexField field) {
      return renamedMembersLens.fieldMap.getOrDefault(field, field);
    }

    @Override
    protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
      // This is unreachable since we override the implementation of lookupField() above.
      throw new Unreachable();
    }

    @Override
    public boolean isContextFreeForMethods() {
      return true;
    }
  }

  // Searches for a reference to a non-public class, field or method declared in the same package
  // as [source].
  public static class IllegalAccessDetector extends UseRegistry {

    private boolean foundIllegalAccess;
    private ProgramMethod context;

    private final AppView<AppInfoWithLiveness> appView;
    private final DexClass source;

    public IllegalAccessDetector(AppView<AppInfoWithLiveness> appView, DexClass source) {
      super(appView.dexItemFactory());
      this.appView = appView;
      this.source = source;
    }

    public boolean foundIllegalAccess() {
      return foundIllegalAccess;
    }

    public void setContext(ProgramMethod context) {
      this.context = context;
    }

    private void checkFieldReference(DexField field) {
      if (!foundIllegalAccess) {
        DexType baseType =
            appView.graphLens().lookupType(field.holder.toBaseType(appView.dexItemFactory()));
        if (baseType.isClassType() && baseType.isSamePackage(source.type)) {
          checkTypeReference(field.holder);
          checkTypeReference(field.type);

          DexEncodedField definition = appView.appInfo().resolveField(field).getResolvedField();
          if (definition == null || !definition.accessFlags.isPublic()) {
            foundIllegalAccess = true;
          }
        }
      }
    }

    private void checkMethodReference(DexMethod method, OptionalBool isInterface) {
      if (!foundIllegalAccess) {
        DexType baseType =
            appView.graphLens().lookupType(method.holder.toBaseType(appView.dexItemFactory()));
        if (baseType.isClassType() && baseType.isSamePackage(source.type)) {
          checkTypeReference(method.holder);
          checkTypeReference(method.proto.returnType);
          for (DexType type : method.proto.parameters.values) {
            checkTypeReference(type);
          }
          ResolutionResult resolutionResult =
              isInterface.isUnknown()
                  ? appView.appInfo().unsafeResolveMethodDueToDexFormat(method)
                  : appView.appInfo().resolveMethod(method, isInterface.isTrue());
          if (!resolutionResult.isSingleResolution()
              || !resolutionResult.asSingleResolution().getResolvedMethod().isPublic()) {
            foundIllegalAccess = true;
          }
        }
      }
    }

    private void checkTypeReference(DexType type) {
      if (!foundIllegalAccess) {
        DexType baseType =
            appView.graphLens().lookupType(type.toBaseType(appView.dexItemFactory()));
        if (baseType.isClassType() && baseType.isSamePackage(source.type)) {
          DexClass clazz = appView.definitionFor(baseType);
          if (clazz == null || !clazz.accessFlags.isPublic()) {
            foundIllegalAccess = true;
          }
        }
      }
    }

    @Override
    public void registerInitClass(DexType clazz) {
      checkTypeReference(clazz);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      assert context != null;
      MethodLookupResult lookup =
          appView.graphLens().lookupMethod(method, context.getReference(), Type.VIRTUAL);
      checkMethodReference(lookup.getReference(), OptionalBool.FALSE);
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      assert context != null;
      MethodLookupResult lookup =
          appView.graphLens().lookupMethod(method, context.getReference(), Type.DIRECT);
      checkMethodReference(lookup.getReference(), OptionalBool.UNKNOWN);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      assert context != null;
      MethodLookupResult lookup =
          appView.graphLens().lookupMethod(method, context.getReference(), Type.STATIC);
      checkMethodReference(lookup.getReference(), OptionalBool.UNKNOWN);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      assert context != null;
      MethodLookupResult lookup =
          appView.graphLens().lookupMethod(method, context.getReference(), Type.INTERFACE);
      checkMethodReference(lookup.getReference(), OptionalBool.TRUE);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      assert context != null;
      MethodLookupResult lookup =
          appView.graphLens().lookupMethod(method, context.getReference(), Type.SUPER);
      checkMethodReference(lookup.getReference(), OptionalBool.UNKNOWN);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      checkFieldReference(appView.graphLens().lookupField(field));
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      checkFieldReference(appView.graphLens().lookupField(field));
    }

    @Override
    public void registerNewInstance(DexType type) {
      checkTypeReference(type);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      checkFieldReference(appView.graphLens().lookupField(field));
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      checkFieldReference(appView.graphLens().lookupField(field));
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

  protected static class SynthesizedBridgeCode extends AbstractSynthesizedCode {

    private DexMethod method;
    private DexMethod originalMethod;
    private DexMethod invocationTarget;
    private Type type;
    private final boolean isInterface;

    public SynthesizedBridgeCode(
        DexMethod method,
        DexMethod originalMethod,
        DexMethod invocationTarget,
        Type type,
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
          .setTargetReceiver(type == DIRECT ? method.holder : null)
          .setTarget(invocationTarget)
          .setInvokeType(type)
          .setIsInterface(isInterface);
      return forwardSourceCodeBuilder::build;
    }

    @Override
    public Consumer<UseRegistry> getRegistryCallback() {
      return registry -> {
        switch (type) {
          case DIRECT:
            registry.registerInvokeDirect(invocationTarget);
            break;

          case STATIC:
            registry.registerInvokeStatic(invocationTarget);
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
