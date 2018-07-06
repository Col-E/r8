// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo.ResolutionResult;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.Builder;
import com.android.tools.r8.graph.JarCode;
import com.android.tools.r8.graph.KeyedDexItem;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.PresortedComparable;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.ir.synthetic.ForwardMethodSourceCode;
import com.android.tools.r8.ir.synthetic.SynthesizedCode;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.FieldSignatureEquivalence;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Merges Supertypes with a single implementation into their single subtype.
 *
 * <p>A common use-case for this is to merge an interface into its single implementation.
 *
 * <p>The class merger only fixes the structure of the graph but leaves the actual instructions
 * untouched. Fixup of instructions is deferred via a {@link GraphLense} to the IR building phase.
 */
public class VerticalClassMerger {

  private enum AbortReason {
    ALREADY_MERGED,
    ALWAYS_INLINE,
    CONFLICT,
    ILLEGAL_ACCESS,
    NATIVE_METHOD,
    NO_SIDE_EFFECTS,
    PINNED_SOURCE,
    RESOLUTION_FOR_FIELDS_MAY_CHANGE,
    RESOLUTION_FOR_METHODS_MAY_CHANGE,
    STATIC_INITIALIZERS,
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
        case STATIC_INITIALIZERS:
          message = "merging of static initializers are not supported";
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
  private final GraphLense graphLense;
  private final Timing timing;
  private Collection<DexMethod> invokes;

  // Set of merge candidates. Note that this must have a deterministic iteration order.
  private final Set<DexProgramClass> mergeCandidates = new LinkedHashSet<>();

  // Map from source class to target class.
  private final Map<DexType, DexType> mergedClasses = new HashMap<>();

  // Map from target class to the super classes that have been merged into the target class.
  private final Map<DexType, Set<DexType>> mergedClassesInverse = new HashMap<>();

  // Set of types that must not be merged into their subtype.
  private final Set<DexType> pinnedTypes = new HashSet<>();

  // The resulting graph lense that should be used after class merging.
  private final VerticalClassMergerGraphLense.Builder renamedMembersLense;

  public VerticalClassMerger(
      DexApplication application, AppView<AppInfoWithLiveness> appView, Timing timing) {
    this.application = application;
    this.appInfo = appView.getAppInfo();
    this.graphLense = appView.getGraphLense();
    this.renamedMembersLense = VerticalClassMergerGraphLense.builder(appInfo);
    this.timing = timing;

    Iterable<DexProgramClass> classes = application.classesWithDeterministicOrder();
    initializePinnedTypes(classes); // Must be initialized prior to mergeCandidates.
    initializeMergeCandidates(classes);
  }

  private void initializeMergeCandidates(Iterable<DexProgramClass> classes) {
    for (DexProgramClass clazz : classes) {
      if (isMergeCandidate(clazz, pinnedTypes) && isStillMergeCandidate(clazz)) {
        mergeCandidates.add(clazz);
      }
    }
  }

  // Returns a set of types that must not be merged into other types.
  private void initializePinnedTypes(Iterable<DexProgramClass> classes) {
    // For all pinned fields, also pin the type of the field (because changing the type of the field
    // implicitly changes the signature of the field). Similarly, for all pinned methods, also pin
    // the return type and the parameter types of the method.
    extractPinnedItems(appInfo.pinnedItems, AbortReason.PINNED_SOURCE);

    // TODO(christofferqa): Remove the invariant that the graph lense should not modify any
    // methods from the sets alwaysInline and noSideEffects (see use of assertNotModified).
    extractPinnedItems(appInfo.alwaysInline, AbortReason.ALWAYS_INLINE);
    extractPinnedItems(appInfo.noSideEffects.keySet(), AbortReason.NO_SIDE_EFFECTS);

    for (DexProgramClass clazz : classes) {
      for (DexEncodedMethod method : clazz.methods()) {
        if (method.accessFlags.isNative()) {
          markTypeAsPinned(clazz.type, AbortReason.NATIVE_METHOD);
        }
      }
    }

    // Avoid merging two types if this could remove a NoSuchMethodError, as illustrated by the
    // following example. (Alternatively, it would be possible to merge A and B and rewrite the
    // "invoke-super A.m" instruction into "invoke-super Object.m" to preserve the error. This
    // situation should generally not occur in practice, though.)
    //
    //   class A {}
    //   class B extends A {
    //     public void m() {}
    //   }
    //   class C extends A {
    //     public void m() {
    //       invoke-super "A.m" <- should yield NoSuchMethodError, cannot merge A and B
    //     }
    //   }
    for (DexMethod signature : appInfo.brokenSuperInvokes) {
      DexClass targetClass = appInfo.definitionFor(signature.holder);
      if (targetClass != null && targetClass.isProgramClass()) {
        pinnedTypes.add(signature.holder);
      }
    }
  }

  private void extractPinnedItems(Iterable<DexItem> items, AbortReason reason) {
    for (DexItem item : items) {
      if (item instanceof DexType || item instanceof DexClass) {
        DexType type = item instanceof DexType ? (DexType) item : ((DexClass) item).type;
        markTypeAsPinned(type, reason);
      } else if (item instanceof DexField || item instanceof DexEncodedField) {
        // Pin the holder and the type of the field.
        DexField field =
            item instanceof DexField ? (DexField) item : ((DexEncodedField) item).field;
        markTypeAsPinned(field.clazz, reason);
        markTypeAsPinned(field.type, reason);
      } else if (item instanceof DexMethod || item instanceof DexEncodedMethod) {
        // Pin the holder, the return type and the parameter types of the method. If we were to
        // merge any of these types into their sub classes, then we would implicitly change the
        // signature of this method.
        DexMethod method =
            item instanceof DexMethod ? (DexMethod) item : ((DexEncodedMethod) item).method;
        markTypeAsPinned(method.holder, reason);
        markTypeAsPinned(method.proto.returnType, reason);
        for (DexType parameterType : method.proto.parameters.values) {
          markTypeAsPinned(parameterType, reason);
        }
      }
    }
  }

  private void markTypeAsPinned(DexType type, AbortReason reason) {
    if (appInfo.isPinned(type)) {
      // We check for the case where the type is pinned according to appInfo.isPinned,
      // so we only need to add it here if it is not the case.
      return;
    }

    DexClass clazz = appInfo.definitionFor(type);
    if (clazz != null && clazz.isProgramClass()) {
      boolean changed = pinnedTypes.add(type);

      if (Log.ENABLED) {
        if (changed && isMergeCandidate(clazz.asProgramClass(), ImmutableSet.of())) {
          reason.printLogMessageForClass(clazz);
        }
      }
    }
  }

  // Returns true if [clazz] is a merge candidate. Note that the result of the checks in this
  // method do not change in response to any class merges.
  private boolean isMergeCandidate(DexProgramClass clazz, Set<DexType> pinnedTypes) {
    if (appInfo.instantiatedTypes.contains(clazz.type)
        || appInfo.instantiatedLambdas.contains(clazz.type)
        || appInfo.isPinned(clazz.type)
        || pinnedTypes.contains(clazz.type)) {
      return false;
    }
    // Note that the property "singleSubtype == null" cannot change during merging, since we visit
    // classes in a top-down order.
    DexType singleSubtype = clazz.type.getSingleSubtype();
    if (singleSubtype == null) {
      // TODO(christofferqa): Even if [clazz] has multiple subtypes, we could still merge it into
      // its subclass if [clazz] is not live. This should only be done, though, if it does not
      // lead to members being duplicated.
      return false;
    }
    for (DexEncodedField field : clazz.fields()) {
      if (appInfo.isPinned(field.field)) {
        return false;
      }
    }
    for (DexEncodedMethod method : clazz.methods()) {
      if (appInfo.isPinned(method.method)) {
        return false;
      }
      if (method.isInstanceInitializer() && disallowInlining(method, singleSubtype)) {
        // Cannot guarantee that markForceInline() will work.
        if (Log.ENABLED) {
          AbortReason.UNSAFE_INLINING.printLogMessageForClass(clazz);
        }
        return false;
      }
    }
    if (clazz.getEnclosingMethod() != null || !clazz.getInnerClasses().isEmpty()) {
      // TODO(herhut): Consider supporting merging of enclosing-method and inner-class attributes.
      if (Log.ENABLED) {
        AbortReason.UNSUPPORTED_ATTRIBUTES.printLogMessageForClass(clazz);
      }
      return false;
    }
    return true;
  }

  // Returns true if [clazz] is a merge candidate. Note that the result of the checks in this
  // method may change in response to class merges. Therefore, this method should always be called
  // before merging [clazz] into its subtype.
  private boolean isStillMergeCandidate(DexProgramClass clazz) {
    assert isMergeCandidate(clazz, pinnedTypes);
    if (mergedClassesInverse.containsKey(clazz.type)) {
      // Do not allow merging the resulting class into its subclass.
      // TODO(christofferqa): Get rid of this limitation.
      if (Log.ENABLED) {
        AbortReason.ALREADY_MERGED.printLogMessageForClass(clazz);
      }
      return false;
    }
    DexClass targetClass = appInfo.definitionFor(clazz.type.getSingleSubtype());
    if (clazz.hasClassInitializer() && targetClass.hasClassInitializer()) {
      // TODO(herhut): Handle class initializers.
      if (Log.ENABLED) {
        AbortReason.STATIC_INITIALIZERS.printLogMessageForClass(clazz);
      }
      return false;
    }
    if (targetClass.getEnclosingMethod() != null || !targetClass.getInnerClasses().isEmpty()) {
      // TODO(herhut): Consider supporting merging of enclosing-method and inner-class attributes.
      if (Log.ENABLED) {
        AbortReason.UNSUPPORTED_ATTRIBUTES.printLogMessageForClass(clazz);
      }
      return false;
    }
    if (mergeMayLeadToIllegalAccesses(clazz, targetClass)) {
      if (Log.ENABLED) {
        AbortReason.ILLEGAL_ACCESS.printLogMessageForClass(clazz);
      }
      return false;
    }
    if (methodResolutionMayChange(clazz, targetClass)) {
      if (Log.ENABLED) {
        AbortReason.RESOLUTION_FOR_METHODS_MAY_CHANGE.printLogMessageForClass(clazz);
      }
      return false;
    }
    // Field resolution first considers the direct interfaces of [targetClass] before it proceeds
    // to the super class.
    if (fieldResolutionMayChange(clazz, targetClass)) {
      if (Log.ENABLED) {
        AbortReason.RESOLUTION_FOR_FIELDS_MAY_CHANGE.printLogMessageForClass(clazz);
      }
      return false;
    }
    return true;
  }

  private boolean mergeMayLeadToIllegalAccesses(DexClass source, DexClass target) {
    if (source.type.isSamePackage(target.type)) {
      // When merging two classes from the same package, we only need to make sure that [source]
      // does not get less visible, since that could make a valid access to [source] from another
      // package illegal after [source] has been merged into [target].
      int accessLevel =
          source.accessFlags.isPrivate() ? 0 : (source.accessFlags.isPublic() ? 2 : 1);
      int otherAccessLevel =
          target.accessFlags.isPrivate() ? 0 : (target.accessFlags.isPublic() ? 2 : 1);
      return accessLevel > otherAccessLevel;
    }

    // Check that all accesses to [source] and its members from inside the current package of
    // [source] will continue to work. This is guaranteed if [target] is public and all members of
    // [source] are either private or public.
    //
    // (Deliberately not checking all accesses to [source] since that would be expensive.)
    if (!target.accessFlags.isPublic()) {
      return true;
    }
    for (DexEncodedField field : source.fields()) {
      if (!(field.accessFlags.isPublic() || field.accessFlags.isPrivate())) {
        return true;
      }
    }
    for (DexEncodedMethod method : source.methods()) {
      if (!(method.accessFlags.isPublic() || method.accessFlags.isPrivate())) {
        return true;
      }
    }

    // Check that all accesses from [source] to classes or members from the current package of
    // [source] will continue to work. This is guaranteed if the methods of [source] do not access
    // any private or protected classes or members from the current package of [source].
    IllegalAccessDetector registry = new IllegalAccessDetector(source);
    for (DexEncodedMethod method : source.methods()) {
      method.registerCodeReferences(registry);
      if (registry.foundIllegalAccess()) {
        return true;
      }
    }

    return false;
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
        mergeeCandidates.add(mergeCandidate.type.getSingleSubtype());
      }
    }

    public Collection<DexMethod> get() {
      Map<DexString, DexProto> overloadingInfo = new HashMap<>();

      // Find all signatures that may reference a type that could be the source or target of a
      // merge operation.
      Set<Wrapper<DexMethod>> filteredSignatures = new HashSet<>();
      for (DexMethod signature : appInfo.targetedMethods) {
        DexClass definition = appInfo.definitionFor(signature.holder);
        if (definition != null
            && definition.isProgramClass()
            && protoMayReferenceMergedSourceOrTarget(signature.proto)) {
          filteredSignatures.add(equivalence.wrap(signature));

          // Record that we have seen a method named [signature.name] with the proto
          // [signature.proto]. If at some point, we find a method with the same name, but a
          // different proto, it could be the case that a method with the given name is overloaded.
          DexProto existing =
              overloadingInfo.computeIfAbsent(signature.name, key -> signature.proto);
          if (existing != DexProto.SENTINEL && !existing.equals(signature.proto)) {
            // Mark that this signature is overloaded by mapping it to SENTINEL.
            overloadingInfo.put(signature.name, DexProto.SENTINEL);
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
      if (type.isArrayType()) {
        type = type.toBaseType(appInfo.dexItemFactory);
      }
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

  public GraphLense run() {
    timing.begin("merge");
    GraphLense mergingGraphLense = mergeClasses(graphLense);
    timing.end();
    timing.begin("fixup");
    GraphLense result = new TreeFixer().fixupTypeReferences(mergingGraphLense);
    timing.end();
    assert result.assertNotModified(appInfo.alwaysInline);
    assert result.assertNotModified(appInfo.noSideEffects.keySet());
    // TODO(christofferqa): Enable this assert.
    // assert result.assertNotModified(appInfo.pinnedItems);
    return result;
  }

  private void addCandidateAncestorsToWorklist(
      DexProgramClass clazz, Deque<DexProgramClass> worklist, Set<DexProgramClass> seenBefore) {
    if (seenBefore.contains(clazz)) {
      return;
    }

    if (mergeCandidates.contains(clazz)) {
      worklist.addFirst(clazz);
    }

    // Add super classes to worklist.
    if (clazz.superType != null) {
      DexClass definition = appInfo.definitionFor(clazz.superType);
      if (definition != null && definition.isProgramClass()) {
        addCandidateAncestorsToWorklist(definition.asProgramClass(), worklist, seenBefore);
      }
    }

    // Add super interfaces to worklist.
    for (DexType interfaceType : clazz.interfaces.values) {
      DexClass definition = appInfo.definitionFor(interfaceType);
      if (definition != null && definition.isProgramClass()) {
        addCandidateAncestorsToWorklist(definition.asProgramClass(), worklist, seenBefore);
      }
    }
  }

  private GraphLense mergeClasses(GraphLense graphLense) {
    Deque<DexProgramClass> worklist = new ArrayDeque<>();
    Set<DexProgramClass> seenBefore = new HashSet<>();

    int numberOfMerges = 0;

    Iterator<DexProgramClass> candidatesIterator = mergeCandidates.iterator();

    // Visit the program classes in a top-down order according to the class hierarchy.
    while (candidatesIterator.hasNext() || !worklist.isEmpty()) {
      if (worklist.isEmpty()) {
        // Add the ancestors of this class (including the class itself) to the worklist in such a
        // way that all super types of the class come before the class itself.
        addCandidateAncestorsToWorklist(candidatesIterator.next(), worklist, seenBefore);
        if (worklist.isEmpty()) {
          continue;
        }
      }

      DexProgramClass clazz = worklist.removeFirst();
      assert isMergeCandidate(clazz, pinnedTypes);
      if (!seenBefore.add(clazz)) {
        continue;
      }

      DexProgramClass targetClass =
          appInfo.definitionFor(clazz.type.getSingleSubtype()).asProgramClass();
      assert !mergedClasses.containsKey(targetClass.type);

      boolean clazzOrTargetClassHasBeenMerged =
          mergedClassesInverse.containsKey(clazz.type)
              || mergedClassesInverse.containsKey(targetClass.type);
      if (clazzOrTargetClassHasBeenMerged) {
        if (!isStillMergeCandidate(clazz)) {
          continue;
        }
      } else {
        assert isStillMergeCandidate(clazz);
      }

      // Guard against the case where we have two methods that may get the same signature
      // if we replace types. This is rare, so we approximate and err on the safe side here.
      if (new CollisionDetector(clazz.type, targetClass.type).mayCollide()) {
        if (Log.ENABLED) {
          AbortReason.CONFLICT.printLogMessageForClass(clazz);
        }
        continue;
      }

      ClassMerger merger = new ClassMerger(clazz, targetClass);
      boolean merged = merger.merge();
      if (merged) {
        // Commit the changes to the graph lense.
        renamedMembersLense.merge(merger.getRenamings());
      }
      if (Log.ENABLED) {
        if (merged) {
          numberOfMerges++;
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
    if (Log.ENABLED) {
      Log.debug(getClass(), "Merged %d classes.", numberOfMerges);
    }
    return renamedMembersLense.build(graphLense, mergedClasses, application.dexItemFactory);
  }

  private boolean methodResolutionMayChange(DexClass source, DexClass target) {
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
        Set<DexEncodedMethod> interfaceTargets = appInfo.lookupInterfaceTargets(method.method);

        // If [method] is not even an interface-target, then we can safely merge it. Otherwise we
        // need to check for a conflict.
        if (interfaceTargets.remove(method)) {
          for (DexEncodedMethod interfaceTarget : interfaceTargets) {
            DexClass enclosingClass = appInfo.definitionFor(interfaceTarget.method.holder);
            if (enclosingClass != null && enclosingClass.isInterface()) {
              // Found another default method that is different from the one in [source], aborting.
              return true;
            }
          }
        }
      }
    }
    return false;
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

    private final DexClass source;
    private final DexClass target;
    private final VerticalClassMergerGraphLense.Builder deferredRenamings =
        VerticalClassMergerGraphLense.builder(appInfo);
    private boolean abortMerge = false;

    private ClassMerger(DexClass source, DexClass target) {
      this.source = source;
      this.target = target;
    }

    public boolean merge() {
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
          add(
              directMethods,
              renameConstructor(directMethod, availableMethodSignatures),
              MethodSignatureEquivalence.get());
        } else {
          DexEncodedMethod resultingDirectMethod =
              renameMethod(
                  directMethod,
                  availableMethodSignatures,
                  directMethod.isClassInitializer() ? Rename.NEVER : Rename.IF_NEEDED);
          add(directMethods, resultingDirectMethod, MethodSignatureEquivalence.get());
          deferredRenamings.map(directMethod.method, resultingDirectMethod.method);

          if (!directMethod.isStaticMethod()) {
            blockRedirectionOfSuperCalls(resultingDirectMethod.method);
          }
        }
      }

      for (DexEncodedMethod virtualMethod : source.virtualMethods()) {
        DexEncodedMethod shadowedBy = findMethodInTarget(virtualMethod);
        if (shadowedBy != null) {
          if (virtualMethod.accessFlags.isAbstract()) {
            // Remove abstract/interface methods that are shadowed.
            deferredRenamings.map(virtualMethod.method, shadowedBy.method);
            continue;
          }
        } else {
          if (abortMerge) {
            // If [virtualMethod] does not resolve to a single method in [target], abort.
            return false;
          }

          // The method is not shadowed. If it is abstract, we can simply move it to the subclass.
          // Non-abstract methods are handled below (they cannot simply be moved to the subclass as
          // a virtual method, because they might be the target of an invoke-super instruction).
          if (virtualMethod.accessFlags.isAbstract()) {
            // Update the holder of [virtualMethod] using renameMethod().
            DexEncodedMethod resultingVirtualMethod =
                renameMethod(virtualMethod, availableMethodSignatures, Rename.NEVER);
            deferredRenamings.map(virtualMethod.method, resultingVirtualMethod.method);
            add(virtualMethods, resultingVirtualMethod, MethodSignatureEquivalence.get());
            continue;
          }
        }

        // This virtual method could be called directly from a sub class via an invoke-super
        // instruction. Therefore, we translate this virtual method into a direct method, such that
        // relevant invoke-super instructions can be rewritten into invoke-direct instructions.
        DexEncodedMethod resultingDirectMethod =
            renameMethod(virtualMethod, availableMethodSignatures, Rename.ALWAYS);
        makePrivate(resultingDirectMethod);
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
          shadowedBy = buildBridgeMethod(virtualMethod, resultingDirectMethod.method);
          add(virtualMethods, shadowedBy, MethodSignatureEquivalence.get());
        }

        deferredRenamings.map(virtualMethod.method, shadowedBy.method);
      }

      if (abortMerge) {
        return false;
      }

      DexEncodedMethod[] mergedDirectMethods =
          mergeMethods(directMethods.values(), target.directMethods());
      DexEncodedMethod[] mergedVirtualMethods =
          mergeMethods(virtualMethods.values(), target.virtualMethods());

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
      target.interfaces = interfaces.isEmpty()
          ? DexTypeList.empty()
          : new DexTypeList(interfaces.toArray(new DexType[interfaces.size()]));
      // Step 2: replace fields and methods.
      target.setDirectMethods(mergedDirectMethods);
      target.setVirtualMethods(mergedVirtualMethods);
      target.setInstanceFields(mergedInstanceFields);
      target.setStaticFields(mergedStaticFields);
      // Step 3: Unlink old class to ease tree shaking.
      source.superType = application.dexItemFactory.objectType;
      source.setDirectMethods(null);
      source.setVirtualMethods(null);
      source.setInstanceFields(null);
      source.setStaticFields(null);
      source.interfaces = DexTypeList.empty();
      // Step 4: Record merging.
      mergedClasses.put(source.type, target.type);
      mergedClassesInverse.computeIfAbsent(target.type, key -> new HashSet<>()).add(source.type);
      assert !abortMerge;
      return true;
    }

    public VerticalClassMergerGraphLense.Builder getRenamings() {
      return deferredRenamings;
    }

    private void redirectSuperCallsInTarget(DexMethod oldTarget, DexMethod newTarget) {
      // If we merge class B into class C, and class C contains an invocation super.m(), then it
      // is insufficient to rewrite "invoke-super B.m()" to "invoke-direct C.m$B()" (the method
      // C.m$B denotes the direct method that has been created in C for B.m). In particular, there
      // might be an instruction "invoke-super A.m()" in C that resolves to B.m at runtime (A is
      // a superclass of B), which also needs to be rewritten to "invoke-direct C.m$B()".
      //
      // We handle this by adding a mapping for [target] and all of its supertypes.
      DexClass holder = target;
      while (holder != null && holder.isProgramClass()) {
        DexMethod signatureInHolder =
            application.dexItemFactory.createMethod(holder.type, oldTarget.proto, oldTarget.name);
        // Only rewrite the invoke-super call if it does not lead to a NoSuchMethodError.
        boolean resolutionSucceeds =
            holder.lookupVirtualMethod(signatureInHolder) != null
                || appInfo.lookupSuperTarget(signatureInHolder, holder.type) != null;
        if (resolutionSucceeds) {
          deferredRenamings.mapVirtualMethodToDirectInType(
              signatureInHolder, newTarget, target.type);
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
                renamedMembersLense.hasMappingForSignatureInContext(holder.type, signatureInType)
                    || appInfo.lookupSuperTarget(signatureInHolder, holder.type) != null;
            if (resolutionSucceededBeforeMerge) {
              deferredRenamings.mapVirtualMethodToDirectInType(
                  signatureInType, newTarget, target.type);
            }
          }
        }
        holder = holder.superType != null ? appInfo.definitionFor(holder.superType) : null;
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
        DexEncodedMethod method, DexMethod invocationTarget) {
      DexType holder = target.type;
      DexProto proto = invocationTarget.proto;
      DexString name = method.method.name;
      MethodAccessFlags accessFlags = method.accessFlags.copy();
      accessFlags.setBridge();
      accessFlags.setSynthetic();
      accessFlags.unsetAbstract();
      DexEncodedMethod bridge = new DexEncodedMethod(
          application.dexItemFactory.createMethod(holder, proto, name),
          accessFlags,
          DexAnnotationSet.empty(),
          ParameterAnnotationsList.empty(),
          new SynthesizedCode(
              new ForwardMethodSourceCode(holder, proto, holder, invocationTarget, Type.DIRECT),
              registry -> registry.registerInvokeDirect(invocationTarget)),
          method.hasClassFileVersion() ? method.getClassFileVersion() : -1);
      if (method.getOptimizationInfo().isPublicized()) {
        // The bridge is now the public method serving the role of the original method, and should
        // reflect that this method was publicized.
        bridge.markPublicized();
        method.unsetPublicized();
      }
      return bridge;
    }

    // Returns the method that shadows the given method, or null if method is not shadowed.
    private DexEncodedMethod findMethodInTarget(DexEncodedMethod method) {
      ResolutionResult resolutionResult = appInfo.resolveMethod(target.type, method.method);
      if (!resolutionResult.hasSingleTarget()) {
        // May happen in case of missing classes, or if multiple implementations were found.
        abortMerge = true;
        return null;
      }
      DexEncodedMethod actual = resolutionResult.asSingleTarget();
      if (actual != method) {
        return actual;
      }
      // We will keep the method, so the class better be abstract if there is no implementation.
      assert !method.accessFlags.isAbstract() || target.accessFlags.isAbstract();
      return null;
    }

    private <T extends KeyedDexItem<S>, S extends PresortedComparable<S>> void add(
        Map<Wrapper<S>, T> map, T item, Equivalence<S> equivalence) {
      map.put(equivalence.wrap(item.getKey()), item);
    }

    private <T extends KeyedDexItem<S>, S extends PresortedComparable<S>> void addAll(
        Collection<Wrapper<S>> collection, Iterable<T> items, Equivalence<S> equivalence) {
      for (T item : items) {
        collection.add(equivalence.wrap(item.getKey()));
      }
    }

    private <T> Set<T> mergeArrays(T[] one, T[] other) {
      Set<T> merged = new LinkedHashSet<>();
      Collections.addAll(merged, one);
      Collections.addAll(merged, other);
      return merged;
    }

    private DexEncodedField[] mergeFields(
        DexEncodedField[] sourceFields,
        DexEncodedField[] targetFields,
        Predicate<DexField> availableFieldSignatures,
        Set<DexString> existingFieldNames) {
      DexEncodedField[] result = new DexEncodedField[sourceFields.length + targetFields.length];
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
      System.arraycopy(targetFields, 0, result, i, targetFields.length);
      return result;
    }

    private DexEncodedMethod[] mergeMethods(
        Collection<DexEncodedMethod> sourceMethods, DexEncodedMethod[] targetMethods) {
      DexEncodedMethod[] result = new DexEncodedMethod[sourceMethods.size() + targetMethods.length];
      // Add methods from source.
      int i = 0;
      for (DexEncodedMethod method : sourceMethods) {
        result[i] = method;
        i++;
      }
      // Add methods from target.
      System.arraycopy(targetMethods, 0, result, i, targetMethods.length);
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
      DexType oldHolder = method.method.holder;

      DexMethod newSignature;
      int count = 1;
      do {
        DexString newName = getFreshName(CONSTRUCTOR_NAME, count, oldHolder);
        newSignature =
            application.dexItemFactory.createMethod(target.type, method.method.proto, newName);
        count++;
      } while (!availableMethodSignatures.test(newSignature));

      DexEncodedMethod result = method.toTypeSubstitutedMethod(newSignature);
      result.markForceInline();
      deferredRenamings.map(method.method, result.method);
      // Renamed constructors turn into ordinary private functions. They can be private, as
      // they are only references from their direct subclass, which they were merged into.
      result.accessFlags.unsetConstructor();
      makePrivate(result);
      return result;
    }

    private DexEncodedMethod renameMethod(
        DexEncodedMethod method, Predicate<DexMethod> availableMethodSignatures, Rename strategy) {
      // We cannot handle renaming static initializers yet and constructors should have been
      // renamed already.
      assert !method.accessFlags.isConstructor() || strategy == Rename.NEVER;
      DexString oldName = method.method.name;
      DexType oldHolder = method.method.holder;

      DexMethod newSignature;
      switch (strategy) {
        case IF_NEEDED:
          newSignature =
              application.dexItemFactory.createMethod(target.type, method.method.proto, oldName);
          if (availableMethodSignatures.test(newSignature)) {
            break;
          }
          // Fall-through to ALWAYS so that we assign a new name.

        case ALWAYS:
          int count = 1;
          do {
            DexString newName = getFreshName(oldName.toSourceString(), count, oldHolder);
            newSignature =
                application.dexItemFactory.createMethod(target.type, method.method.proto, newName);
            count++;
          } while (!availableMethodSignatures.test(newSignature));
          break;

        case NEVER:
          newSignature =
              application.dexItemFactory.createMethod(target.type, method.method.proto, oldName);
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
      DexType oldHolder = field.field.clazz;

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
  }

  private static void makePrivate(DexEncodedMethod method) {
    assert !method.accessFlags.isAbstract();
    method.accessFlags.unsetPublic();
    method.accessFlags.unsetProtected();
    method.accessFlags.setPrivate();
  }

  private class TreeFixer {

    private final Builder lense = GraphLense.builder();
    Map<DexProto, DexProto> protoFixupCache = new IdentityHashMap<>();

    private GraphLense fixupTypeReferences(GraphLense graphLense) {
      // Globally substitute merged class types in protos and holders.
      for (DexProgramClass clazz : appInfo.classes()) {
        clazz.setDirectMethods(substituteTypesIn(clazz.directMethods()));
        clazz.setVirtualMethods(substituteTypesIn(clazz.virtualMethods()));
        clazz.setVirtualMethods(removeDupes(clazz.virtualMethods()));
        clazz.setStaticFields(substituteTypesIn(clazz.staticFields()));
        clazz.setInstanceFields(substituteTypesIn(clazz.instanceFields()));
      }
      // Record type renamings so instanceof and checkcast checks are also fixed.
      for (DexType type : mergedClasses.keySet()) {
        DexType fixed = fixupType(type);
        lense.map(type, fixed);
      }
      return lense.build(application.dexItemFactory, graphLense);
    }

    private DexEncodedMethod[] removeDupes(DexEncodedMethod[] methods) {
      if (methods == null) {
        return null;
      }
      Map<DexMethod, DexEncodedMethod> filtered = new IdentityHashMap<>();
      for (DexEncodedMethod method : methods) {
        DexEncodedMethod previous = filtered.put(method.method, method);
        if (previous != null) {
          if (!previous.accessFlags.isBridge()) {
            if (!method.accessFlags.isBridge()) {
              throw new CompilationError(
                  "Class merging produced invalid result on: " + previous.toSourceString());
            } else {
              filtered.put(previous.method, previous);
            }
          }
        }
      }
      if (filtered.size() == methods.length) {
        return methods;
      }
      return filtered.values().toArray(new DexEncodedMethod[filtered.size()]);
    }

    private DexEncodedMethod[] substituteTypesIn(DexEncodedMethod[] methods) {
      if (methods == null) {
        return null;
      }
      for (int i = 0; i < methods.length; i++) {
        DexEncodedMethod encodedMethod = methods[i];
        DexMethod method = encodedMethod.method;
        DexProto newProto = getUpdatedProto(method.proto);
        DexType newHolder = fixupType(method.holder);
        DexMethod newMethod = application.dexItemFactory.createMethod(newHolder, newProto,
            method.name);
        if (newMethod != encodedMethod.method) {
          lense.map(encodedMethod.method, newMethod);
          methods[i] = encodedMethod.toTypeSubstitutedMethod(newMethod);
        }
      }
      return methods;
    }

    private DexEncodedField[] substituteTypesIn(DexEncodedField[] fields) {
      if (fields == null) {
        return null;
      }
      for (int i = 0; i < fields.length; i++) {
        DexEncodedField encodedField = fields[i];
        DexField field = encodedField.field;
        DexType newType = fixupType(field.type);
        DexType newHolder = fixupType(field.clazz);
        DexField newField = application.dexItemFactory.createField(newHolder, newType, field.name);
        if (newField != encodedField.field) {
          lense.map(encodedField.field, newField);
          fields[i] = encodedField.toTypeSubstitutedField(newField);
        }
      }
      return fields;
    }

    private DexProto getUpdatedProto(DexProto proto) {
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
        } else {
          return type.replaceBaseType(fixed, application.dexItemFactory);
        }
      }
      while (mergedClasses.containsKey(type)) {
        type = mergedClasses.get(type);
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
      for (DexType aType : proto.parameters.values) {
        // Substitute the type with the already merged class to estimate what it will look like.
        aType = mergedClasses.getOrDefault(aType, aType);
        accumulator <<= 1;
        bitsUsed++;
        if (aType == type) {
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
      DexType returnType = mergedClasses.getOrDefault(proto.returnType, proto.returnType);
      accumulator <<= 1;
      if (returnType == type) {
        accumulator |= 1;
      }
      result |= accumulator;
      cache.put(proto, result);
      return result;
    }
  }

  private boolean disallowInlining(DexEncodedMethod method, DexType invocationContext) {
    // TODO(christofferqa): Determine the situations where markForceInline() may fail, and ensure
    // that we always return true here in these cases.
    if (method.getCode().isJarCode()) {
      JarCode jarCode = method.getCode().asJarCode();
      Constraint constraint =
          jarCode.computeInliningConstraint(
              method,
              appInfo,
              new SingleTypeMapperGraphLense(method.method.holder, invocationContext),
              invocationContext);
      return constraint == Constraint.NEVER;
    }
    // TODO(christofferqa): For non-jar code we currently cannot guarantee that markForceInline()
    // will succeed.
    return true;
  }

  private class SingleTypeMapperGraphLense extends GraphLense {

    private final DexType source;
    private final DexType target;

    public SingleTypeMapperGraphLense(DexType source, DexType target) {
      this.source = source;
      this.target = target;
    }

    @Override
    public DexType lookupType(DexType type) {
      return type == source ? target : type;
    }

    @Override
    public GraphLenseLookupResult lookupMethod(
        DexMethod method, DexEncodedMethod context, Type type) {
      return new GraphLenseLookupResult(
          renamedMembersLense.methodMap.getOrDefault(method, method), type);
    }

    @Override
    public DexField lookupField(DexField field) {
      return renamedMembersLense.fieldMap.getOrDefault(field, field);
    }

    @Override
    public boolean isContextFreeForMethods() {
      return true;
    }
  }

  // Searches for a reference to a non-public class, field or method declared in the same package
  // as [source].
  private class IllegalAccessDetector extends UseRegistry {
    private boolean foundIllegalAccess = false;
    private DexClass source;

    public IllegalAccessDetector(DexClass source) {
      this.source = source;
    }

    public boolean foundIllegalAccess() {
      return foundIllegalAccess;
    }

    private boolean checkFieldReference(DexField field) {
      if (!foundIllegalAccess) {
        if (field.clazz.isSamePackage(source.type)) {
          checkTypeReference(field.clazz);
          checkTypeReference(field.type);

          DexEncodedField definition = appInfo.definitionFor(field);
          if (definition == null || !definition.accessFlags.isPublic()) {
            foundIllegalAccess = true;
          }
        }
      }
      return true;
    }

    private boolean checkMethodReference(DexMethod method) {
      if (!foundIllegalAccess) {
        if (method.holder.isSamePackage(source.type)) {
          checkTypeReference(method.holder);
          checkTypeReference(method.proto.returnType);
          for (DexType type : method.proto.parameters.values) {
            checkTypeReference(type);
          }
          DexEncodedMethod definition = appInfo.definitionFor(method);
          if (definition == null || !definition.accessFlags.isPublic()) {
            foundIllegalAccess = true;
          }
        }
      }
      return true;
    }

    private boolean checkTypeReference(DexType type) {
      if (!foundIllegalAccess) {
        if (type.isClassType() && type.isSamePackage(source.type)) {
          DexClass clazz = appInfo.definitionFor(type);
          if (clazz == null || !clazz.accessFlags.isPublic()) {
            foundIllegalAccess = true;
          }
        }
      }
      return true;
    }

    @Override
    public boolean registerInvokeVirtual(DexMethod method) {
      return checkMethodReference(method);
    }

    @Override
    public boolean registerInvokeDirect(DexMethod method) {
      return checkMethodReference(method);
    }

    @Override
    public boolean registerInvokeStatic(DexMethod method) {
      return checkMethodReference(method);
    }

    @Override
    public boolean registerInvokeInterface(DexMethod method) {
      return checkMethodReference(method);
    }

    @Override
    public boolean registerInvokeSuper(DexMethod method) {
      return checkMethodReference(method);
    }

    @Override
    public boolean registerInstanceFieldWrite(DexField field) {
      return checkFieldReference(field);
    }

    @Override
    public boolean registerInstanceFieldRead(DexField field) {
      return checkFieldReference(field);
    }

    @Override
    public boolean registerNewInstance(DexType type) {
      return checkTypeReference(type);
    }

    @Override
    public boolean registerStaticFieldRead(DexField field) {
      return checkFieldReference(field);
    }

    @Override
    public boolean registerStaticFieldWrite(DexField field) {
      return checkFieldReference(field);
    }

    @Override
    public boolean registerTypeReference(DexType type) {
      return checkTypeReference(type);
    }
  }

  public Collection<DexType> getRemovedClasses() {
    return Collections.unmodifiableCollection(mergedClasses.keySet());
  }
}
