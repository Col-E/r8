// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfo.ResolutionResult;
import com.android.tools.r8.graph.DefaultUseRegistry;
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
import com.android.tools.r8.graph.KeyedDexItem;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.PresortedComparable;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.synthetic.ForwardMethodSourceCode;
import com.android.tools.r8.ir.synthetic.SynthesizedCode;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.optimize.InvokeSingleTargetExtractor;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.FieldSignatureEquivalence;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
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
import java.util.function.BiFunction;
import java.util.stream.Collectors;

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
    NO_SIDE_EFFECTS,
    PINNED_SOURCE,
    PINNED_TARGET,
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
        case NO_SIDE_EFFECTS:
          message = "it is mentioned in appInfo.noSideEffects";
          break;
        case PINNED_SOURCE:
          message = "it should be kept";
          break;
        case PINNED_TARGET:
          message = "its target should be kept";
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

  private final DexApplication application;
  private final AppInfoWithLiveness appInfo;
  private final GraphLense graphLense;
  private final Timing timing;
  private Collection<DexMethod> invokes;

  // Map from source class to target class.
  private final Map<DexType, DexType> mergedClasses = new HashMap<>();

  // Map from target class to the super classes that have been merged into the target class.
  private final Map<DexType, Set<DexType>> mergedClassesInverse = new HashMap<>();

  // The resulting graph lense that should be used after class merging.
  private final VerticalClassMergerGraphLense.Builder renamedMembersLense;

  public VerticalClassMerger(
      DexApplication application,
      AppInfoWithLiveness appInfo,
      GraphLense graphLense,
      Timing timing) {
    this.application = application;
    this.appInfo = appInfo;
    this.graphLense = graphLense;
    this.renamedMembersLense = VerticalClassMergerGraphLense.builder(appInfo);
    this.timing = timing;
  }

  // Returns a set of types that must not be merged into other types.
  private Set<DexType> getPinnedTypes(Iterable<DexProgramClass> classes) {
    Set<DexType> pinnedTypes = new HashSet<>();

    // For all pinned fields, also pin the type of the field (because changing the type of the field
    // implicitly changes the signature of the field). Similarly, for all pinned methods, also pin
    // the return type and the parameter types of the method.
    extractPinnedItems(appInfo.pinnedItems, pinnedTypes, AbortReason.PINNED_SOURCE);

    // TODO(christofferqa): Remove the invariant that the graph lense should not modify any
    // methods from the sets alwaysInline and noSideEffects (see use of assertNotModified).
    extractPinnedItems(appInfo.alwaysInline, pinnedTypes, AbortReason.ALWAYS_INLINE);
    extractPinnedItems(appInfo.noSideEffects.keySet(), pinnedTypes, AbortReason.NO_SIDE_EFFECTS);

    for (DexProgramClass clazz : classes) {
      for (DexEncodedMethod method : clazz.methods()) {
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
        if (!method.isStaticMethod()) {
          method.registerCodeReferences(
              new DefaultUseRegistry() {
                @Override
                public boolean registerInvokeSuper(DexMethod target) {
                  DexClass targetClass = appInfo.definitionFor(target.getHolder());
                  if (targetClass != null
                      && targetClass.isProgramClass()
                      && targetClass.lookupVirtualMethod(target) == null) {
                    pinnedTypes.add(target.getHolder());
                  }
                  return true;
                }
              });
        }
      }
    }
    return pinnedTypes;
  }

  private void extractPinnedItems(
      Iterable<DexItem> items, Set<DexType> pinnedTypes, AbortReason reason) {
    for (DexItem item : items) {
      if (item instanceof DexType || item instanceof DexClass) {
        DexType type = item instanceof DexType ? (DexType) item : ((DexClass) item).type;
        // We check for the case where the type is pinned according to appInfo.isPinned, so only
        // add it here if it is not the case.
        if (!appInfo.isPinned(type)) {
          markTypeAsPinned(type, pinnedTypes, reason);
        }
      } else if (item instanceof DexField || item instanceof DexEncodedField) {
        // Pin the holder and the type of the field.
        DexField field =
            item instanceof DexField ? (DexField) item : ((DexEncodedField) item).field;
        markTypeAsPinned(field.clazz, pinnedTypes, reason);
        markTypeAsPinned(field.type, pinnedTypes, reason);
      } else if (item instanceof DexMethod || item instanceof DexEncodedMethod) {
        // Pin the holder, the return type and the parameter types of the method. If we were to
        // merge any of these types into their sub classes, then we would implicitly change the
        // signature of this method.
        DexMethod method =
            item instanceof DexMethod ? (DexMethod) item : ((DexEncodedMethod) item).method;
        markTypeAsPinned(method.holder, pinnedTypes, reason);
        markTypeAsPinned(method.proto.returnType, pinnedTypes, reason);
        for (DexType parameterType : method.proto.parameters.values) {
          markTypeAsPinned(parameterType, pinnedTypes, reason);
        }
      }
    }
  }

  private void markTypeAsPinned(DexType type, Set<DexType> pinnedTypes, AbortReason reason) {
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

  private boolean isMergeCandidate(DexProgramClass clazz, Set<DexType> pinnedTypes) {
    if (appInfo.instantiatedTypes.contains(clazz.type)
        || appInfo.isPinned(clazz.type)
        || pinnedTypes.contains(clazz.type)) {
      return false;
    }
    if (mergedClassesInverse.containsKey(clazz.type)) {
      // Do not allow merging the resulting class into its subclass.
      // TODO(christofferqa): Get rid of this limitation.
      if (Log.ENABLED) {
        AbortReason.ALREADY_MERGED.printLogMessageForClass(clazz);
      }
      return false;
    }
    DexType singleSubtype = clazz.type.getSingleSubtype();
    if (singleSubtype == null) {
      // TODO(christofferqa): Even if [clazz] has multiple subtypes, we could still merge it into
      // its subclass if [clazz] is not live. This should only be done, though, if it does not
      // lead to members being duplicated.
      return false;
    }
    DexClass targetClass = appInfo.definitionFor(singleSubtype);
    if (mergeMayLeadToIllegalAccesses(clazz, targetClass)) {
      if (Log.ENABLED) {
        AbortReason.ILLEGAL_ACCESS.printLogMessageForClass(clazz);
      }
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
      if (method.isInstanceInitializer() && disallowInlining(method)) {
        // Cannot guarantee that markForceInline() will work.
        if (Log.ENABLED) {
          AbortReason.UNSAFE_INLINING.printLogMessageForClass(clazz);
        }
        return false;
      }
    }
    return true;
  }

  private boolean mergeMayLeadToIllegalAccesses(DexClass source, DexClass target) {
    if (source.type.isSamePackage(target.type)) {
      int accessLevel =
          source.accessFlags.isPrivate() ? 0 : (source.accessFlags.isPublic() ? 2 : 1);
      int otherAccessLevel =
          target.accessFlags.isPrivate() ? 0 : (target.accessFlags.isPublic() ? 2 : 1);
      return accessLevel > otherAccessLevel;
    }
    // TODO(christofferqa): To merge [clazz] into a class from another package we need to ensure:
    // (A) All accesses to [clazz] and its members from inside the current package of [clazz] will
    //     continue to work. This is guaranteed if [clazz] is public and all members of [clazz] are
    //     either private or public.
    // (B) All accesses from [clazz] to classes or members from the current package of [clazz] will
    //     continue to work. This is guaranteed if the methods of [clazz] do not access any private
    //     or protected classes or members from the current package of [clazz].
    return true;
  }

  private void addProgramMethods(Set<Wrapper<DexMethod>> set, DexMethod method,
      Equivalence<DexMethod> equivalence) {
    DexClass definition = appInfo.definitionFor(method.holder);
    if (definition != null && definition.isProgramClass()) {
      set.add(equivalence.wrap(method));
    }
  }

  private Collection<DexMethod> getInvokes() {
    if (invokes == null) {
      // Collect all reachable methods that are not within a library class. Those defined on
      // library classes are known not to have program classes in their signature.
      // Also filter methods that only use types from library classes in their signatures. We
      // know that those won't conflict.
      Set<Wrapper<DexMethod>> filteredInvokes = new HashSet<>();
      Equivalence<DexMethod> equivalence = MethodSignatureEquivalence.get();
      appInfo.targetedMethods.forEach(m -> addProgramMethods(filteredInvokes, m, equivalence));
      invokes = filteredInvokes.stream().map(Wrapper::get).filter(this::removeNonProgram)
          .collect(Collectors.toList());
    }
    return invokes;
  }

  private boolean isProgramClass(DexType type) {
    if (type.isArrayType()) {
      type = type.toBaseType(appInfo.dexItemFactory);
    }
    if (type.isClassType()) {
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz != null && clazz.isProgramClass()) {
        return true;
      }
    }
    return false;
  }

  private boolean removeNonProgram(DexMethod dexMethod) {
    for (DexType type : dexMethod.proto.parameters.values) {
      if (isProgramClass(type)) {
        return true;
      }
    }
    return isProgramClass(dexMethod.proto.returnType);
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

  private void addAncestorsToWorklist(
      DexProgramClass clazz, Deque<DexProgramClass> worklist, Set<DexProgramClass> seenBefore) {
    if (seenBefore.contains(clazz)) {
      return;
    }

    worklist.addFirst(clazz);

    // Add super classes to worklist.
    if (clazz.superType != null) {
      DexClass definition = appInfo.definitionFor(clazz.superType);
      if (definition != null && definition.isProgramClass()) {
        addAncestorsToWorklist(definition.asProgramClass(), worklist, seenBefore);
      }
    }

    // Add super interfaces to worklist.
    for (DexType interfaceType : clazz.interfaces.values) {
      DexClass definition = appInfo.definitionFor(interfaceType);
      if (definition != null && definition.isProgramClass()) {
        addAncestorsToWorklist(definition.asProgramClass(), worklist, seenBefore);
      }
    }
  }

  private GraphLense mergeClasses(GraphLense graphLense) {
    Iterable<DexProgramClass> classes = application.classesWithDeterministicOrder();
    Deque<DexProgramClass> worklist = new ArrayDeque<>();
    Set<DexProgramClass> seenBefore = new HashSet<>();

    int numberOfMerges = 0;

    // Types that are pinned (in addition to those where appInfo.isPinned returns true).
    Set<DexType> pinnedTypes = getPinnedTypes(classes);

    Iterator<DexProgramClass> classIterator = classes.iterator();

    // Visit the program classes in a top-down order according to the class hierarchy.
    while (classIterator.hasNext() || !worklist.isEmpty()) {
      if (worklist.isEmpty()) {
        // Add the ancestors of this class (including the class itself) to the worklist in such a
        // way that all super types of the class come before the class itself.
        addAncestorsToWorklist(classIterator.next(), worklist, seenBefore);
        if (worklist.isEmpty()) {
          continue;
        }
      }

      DexProgramClass clazz = worklist.removeFirst();
      if (!seenBefore.add(clazz) || !isMergeCandidate(clazz, pinnedTypes)) {
        continue;
      }

      DexClass targetClass = appInfo.definitionFor(clazz.type.getSingleSubtype());
      assert !mergedClasses.containsKey(targetClass.type);
      if (appInfo.isPinned(targetClass.type)) {
        // We have to keep the target class intact, so we cannot merge it.
        if (Log.ENABLED) {
          AbortReason.PINNED_TARGET.printLogMessageForClass(clazz);
        }
        continue;
      }
      if (clazz.hasClassInitializer() && targetClass.hasClassInitializer()) {
        // TODO(herhut): Handle class initializers.
        if (Log.ENABLED) {
          AbortReason.STATIC_INITIALIZERS.printLogMessageForClass(clazz);
        }
        continue;
      }
      if (methodResolutionMayChange(clazz, targetClass)) {
        if (Log.ENABLED) {
          AbortReason.RESOLUTION_FOR_METHODS_MAY_CHANGE.printLogMessageForClass(clazz);
        }
        continue;
      }
      // Field resolution first considers the direct interfaces of [targetClass] before it proceeds
      // to the super class.
      if (fieldResolutionMayChange(clazz, targetClass)) {
        if (Log.ENABLED) {
          AbortReason.RESOLUTION_FOR_FIELDS_MAY_CHANGE.printLogMessageForClass(clazz);
        }
        continue;
      }
      // Guard against the case where we have two methods that may get the same signature
      // if we replace types. This is rare, so we approximate and err on the safe side here.
      if (new CollisionDetector(clazz.type, targetClass.type, getInvokes(), mergedClasses)
          .mayCollide()) {
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
      if (source.getEnclosingMethod() != null || !source.getInnerClasses().isEmpty()
          || target.getEnclosingMethod() != null || !target.getInnerClasses().isEmpty()) {
        // TODO(herhut): Consider supporting merging of inner-class attributes.
        if (Log.ENABLED) {
          AbortReason.UNSUPPORTED_ATTRIBUTES.printLogMessageForClass(source);
        }
        return false;
      }
      // Merge the class [clazz] into [targetClass] by adding all methods to
      // targetClass that are not currently contained.
      // Step 1: Merge methods
      Set<Wrapper<DexMethod>> existingMethods = new HashSet<>();
      addAll(existingMethods, target.methods(), MethodSignatureEquivalence.get());

      List<DexEncodedMethod> directMethods = new ArrayList<>();
      for (DexEncodedMethod directMethod : source.directMethods()) {
        if (directMethod.isInstanceInitializer()) {
          directMethods.add(renameConstructor(directMethod));
        } else {
          directMethods.add(directMethod);

          if (!directMethod.isStaticMethod()) {
            blockRedirectionOfSuperCalls(directMethod.method);
          }
        }
      }

      List<DexEncodedMethod> virtualMethods = new ArrayList<>();
      for (DexEncodedMethod virtualMethod : source.virtualMethods()) {
        DexEncodedMethod shadowedBy = findMethodInTarget(virtualMethod);
        if (shadowedBy != null) {
          if (virtualMethod.accessFlags.isAbstract()) {
            // Remove abstract/interface methods that are shadowed.
            deferredRenamings.map(virtualMethod.method, shadowedBy.method);
            continue;
          }
        } else {
          // The method is not shadowed. If it is abstract, we can simply move it to the subclass.
          // Non-abstract methods are handled below (they cannot simply be moved to the subclass as
          // a virtual method, because they might be the target of an invoke-super instruction).
          if (virtualMethod.accessFlags.isAbstract()) {
            DexEncodedMethod resultingVirtualMethod =
                renameMethod(virtualMethod, target.type, false);
            deferredRenamings.map(virtualMethod.method, resultingVirtualMethod.method);
            virtualMethods.add(resultingVirtualMethod);
            continue;
          }
        }

        // This virtual method could be called directly from a sub class via an invoke-super
        // instruction. Therefore, we translate this virtual method into a direct method, such that
        // relevant invoke-super instructions can be rewritten into invoke-direct instructions.
        DexEncodedMethod resultingDirectMethod = renameMethod(virtualMethod, target.type, true);
        makePrivate(resultingDirectMethod);
        directMethods.add(resultingDirectMethod);

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
          virtualMethods.add(shadowedBy);
        }

        deferredRenamings.map(virtualMethod.method, shadowedBy.method);
      }

      Collection<DexEncodedMethod> mergedDirectMethods =
          mergeItems(
              directMethods.iterator(),
              target.directMethods(),
              MethodSignatureEquivalence.get(),
              existingMethods,
              (existing, method) -> {
                DexEncodedMethod renamedMethod = renameMethod(method, target.type, true);
                deferredRenamings.map(method.method, renamedMethod.method);
                blockRedirectionOfSuperCalls(renamedMethod.method);
                return renamedMethod;
              });
      Collection<DexEncodedMethod> mergedVirtualMethods =
          mergeItems(
              virtualMethods.iterator(),
              target.virtualMethods(),
              MethodSignatureEquivalence.get(),
              existingMethods,
              this::abortOnNonAbstract);
      if (abortMerge) {
        return false;
      }
      // Step 2: Merge fields
      Set<Wrapper<DexField>> existingFields = new HashSet<>();
      addAll(existingFields, target.fields(), FieldSignatureEquivalence.get());
      Collection<DexEncodedField> mergedStaticFields = mergeItems(
          Iterators.forArray(source.staticFields()),
          target.staticFields(),
          FieldSignatureEquivalence.get(),
          existingFields,
          this::renameField);
      Collection<DexEncodedField> mergedInstanceFields = mergeItems(
          Iterators.forArray(source.instanceFields()),
          target.instanceFields(),
          FieldSignatureEquivalence.get(),
          existingFields,
          this::renameField);
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
      target.setDirectMethods(mergedDirectMethods
          .toArray(new DexEncodedMethod[mergedDirectMethods.size()]));
      target.setVirtualMethods(mergedVirtualMethods
          .toArray(new DexEncodedMethod[mergedVirtualMethods.size()]));
      target.setStaticFields(mergedStaticFields
          .toArray(new DexEncodedField[mergedStaticFields.size()]));
      target.setInstanceFields(mergedInstanceFields
          .toArray(new DexEncodedField[mergedInstanceFields.size()]));
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
        DexEncodedMethod signature, DexMethod invocationTarget) {
      DexType holder = target.type;
      DexProto proto = invocationTarget.proto;
      DexString name = signature.method.name;
      MethodAccessFlags accessFlags = signature.accessFlags.copy();
      accessFlags.setBridge();
      accessFlags.setSynthetic();
      accessFlags.unsetAbstract();
      return new DexEncodedMethod(
          application.dexItemFactory.createMethod(holder, proto, name),
          accessFlags,
          DexAnnotationSet.empty(),
          ParameterAnnotationsList.empty(),
          new SynthesizedCode(
              new ForwardMethodSourceCode(holder, proto, holder, invocationTarget, Type.DIRECT),
              registry -> registry.registerInvokeDirect(invocationTarget)),
          signature.hasClassFileVersion() ? signature.getClassFileVersion() : -1);
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

    private <T extends PresortedComparable<T>, S extends KeyedDexItem<T>> Collection<S> mergeItems(
        Iterator<S> fromItems,
        S[] toItems,
        Equivalence<T> equivalence,
        Set<Wrapper<T>> existing,
        BiFunction<S, S, S> onConflict) {
      HashMap<Wrapper<T>, S> methods = new HashMap<>();
      // First add everything from the target class. These items are not preprocessed.
      for (S item : toItems) {
        methods.put(equivalence.wrap(item.getKey()), item);
      }
      // Now add the new methods, resolving shadowing.
      addNonShadowed(fromItems, methods, equivalence, existing, onConflict);
      return methods.values();
    }

    private <T extends PresortedComparable<T>, S extends KeyedDexItem<T>> void addNonShadowed(
        Iterator<S> items,
        HashMap<Wrapper<T>, S> map,
        Equivalence<T> equivalence,
        Set<Wrapper<T>> existing,
        BiFunction<S, S, S> onConflict) {
      while (items.hasNext()) {
        S item = items.next();
        if (item == null) {
          // This item was filtered out by a preprocessing.
          continue;
        }
        Wrapper<T> wrapped = equivalence.wrap(item.getKey());
        if (existing.contains(wrapped)) {
          S resolved = onConflict.apply(map.get(wrapped), item);
          wrapped = equivalence.wrap(resolved.getKey());
          map.put(wrapped, resolved);
        } else {
          map.put(wrapped, item);
        }
      }
    }

    // Note that names returned by this function are not necessarily unique. However, class merging
    // is aborted if it turns out that the returned name is not unique.
    // TODO(christofferqa): Write a test that checks that class merging is aborted if this function
    // generates a name that is not unique.
    private DexString getFreshName(String nameString, DexType holder) {
      String freshName = nameString + "$" + holder.toSourceString().replace('.', '$');
      return application.dexItemFactory.createString(freshName);
    }

    private DexEncodedMethod abortOnNonAbstract(DexEncodedMethod existing,
        DexEncodedMethod method) {
      // Ignore if we override a bridge method that would bridge to the superclasses method.
      if (existing != null && existing.accessFlags.isBridge()) {
        InvokeSingleTargetExtractor extractor = new InvokeSingleTargetExtractor();
        existing.getCode().registerCodeReferences(extractor);
        if (extractor.getTarget() == method.method) {
          return method;
        }
      }

      // Abstract shadowed methods are removed prior to calling mergeItems.
      assert !method.accessFlags.isAbstract();

      // If [existing] is null, there is a conflict between a static and virtual method. Otherwise,
      // there is a non-abstract method that is shadowed. We translate virtual shadowed methods into
      // direct methods with a fresh name, so this situation should never happen. We currently get
      // in this situation if getFreshName returns a name that is not unique, though.
      abortMerge = true;
      return method;
    }

    private DexEncodedMethod renameConstructor(DexEncodedMethod method) {
      assert method.isInstanceInitializer();
      DexType holder = method.method.holder;
      DexEncodedMethod result =
          method.toRenamedMethod(
              getFreshName(CONSTRUCTOR_NAME, holder), application.dexItemFactory);
      result.markForceInline();
      deferredRenamings.map(method.method, result.method);
      // Renamed constructors turn into ordinary private functions. They can be private, as
      // they are only references from their direct subclass, which they were merged into.
      result.accessFlags.unsetConstructor();
      makePrivate(result);
      return result;
    }

    private DexEncodedMethod renameMethod(
        DexEncodedMethod method, DexType newHolder, boolean useFreshName) {
      // We cannot handle renaming static initializers yet and constructors should have been
      // renamed already.
      assert !method.accessFlags.isConstructor();
      DexType oldHolder = method.method.holder;
      DexString oldName = method.method.name;
      DexString newName =
          useFreshName ? getFreshName(oldName.toSourceString(), oldHolder) : oldName;
      DexMethod newSignature =
          application.dexItemFactory.createMethod(newHolder, method.method.proto, newName);
      return method.toTypeSubstitutedMethod(newSignature);
    }

    private DexEncodedField renameField(DexEncodedField existing, DexEncodedField field) {
      DexString oldName = field.field.name;
      DexType oldHolder = field.field.clazz;
      DexString newName = getFreshName(oldName.toSourceString(), oldHolder);
      DexField newSignature =
          application.dexItemFactory.createField(target.type, field.field.type, newName);
      DexEncodedField result = field.toTypeSubstitutedField(newSignature);
      deferredRenamings.map(field.field, result.field);
      return result;
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

  private static class CollisionDetector {

    private static final int NOT_FOUND = 1 << (Integer.SIZE - 1);

    // TODO(herhut): Maybe cache seenPositions for target classes.
    private final Map<DexString, Int2IntMap> seenPositions = new IdentityHashMap<>();
    private final Reference2IntMap<DexProto> targetProtoCache;
    private final Reference2IntMap<DexProto> sourceProtoCache;
    private final DexType source, target;
    private final Collection<DexMethod> invokes;
    private final Map<DexType, DexType> substituions;

    private CollisionDetector(DexType source, DexType target, Collection<DexMethod> invokes,
        Map<DexType, DexType> substitutions) {
      this.source = source;
      this.target = target;
      this.invokes = invokes;
      this.substituions = substitutions;
      this.targetProtoCache = new Reference2IntOpenHashMap<>(invokes.size() / 2);
      this.targetProtoCache.defaultReturnValue(NOT_FOUND);
      this.sourceProtoCache = new Reference2IntOpenHashMap<>(invokes.size() / 2);
      this.sourceProtoCache.defaultReturnValue(NOT_FOUND);
    }

    boolean mayCollide() {
      fillSeenPositions(invokes);
      // If the type is not used in methods at all, there cannot be any conflict.
      if (seenPositions.isEmpty()) {
        return false;
      }
      for (DexMethod method : invokes) {
        Int2IntMap positionsMap = seenPositions.get(method.name);
        if (positionsMap != null) {
          int arity = method.getArity();
          int previous = positionsMap.get(arity);
          if (previous != NOT_FOUND) {
            assert previous != 0;
            int positions = computePositionsFor(method.proto, source, sourceProtoCache,
                substituions);
            if ((positions & previous) != 0) {
              return true;
            }
          }
        }
      }
      return false;
    }

    private void fillSeenPositions(Collection<DexMethod> invokes) {
      for (DexMethod method : invokes) {
        DexType[] parameters = method.proto.parameters.values;
        int arity = parameters.length;
        int positions = computePositionsFor(method.proto, target, targetProtoCache, substituions);
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

    private int computePositionsFor(DexProto proto, DexType type,
        Reference2IntMap<DexProto> cache, Map<DexType, DexType> substitutions) {
      int result = cache.getInt(proto);
      if (result != NOT_FOUND) {
        return result;
      }
      result = 0;
      int bitsUsed = 0;
      int accumulator = 0;
      for (DexType aType : proto.parameters.values) {
        if (substitutions != null) {
          // Substitute the type with the already merged class to estimate what it will
          // look like.
          while (substitutions.containsKey(aType)) {
            aType = substitutions.get(aType);
          }
        }
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
      DexType returnType = proto.returnType;
      if (substitutions != null) {
        while (substitutions.containsKey(returnType)) {
          returnType = substitutions.get(returnType);
        }
      }
      accumulator <<= 1;
      if (returnType == type) {
        accumulator |= 1;
      }
      result |= accumulator;
      cache.put(proto, result);
      return result;
    }
  }

  private static boolean disallowInlining(DexEncodedMethod method) {
    // TODO(christofferqa): Determine the situations where markForceInline() may fail, and ensure
    // that we always return true here in these cases.
    MethodInlineDecision registry = new MethodInlineDecision();
    method.getCode().registerCodeReferences(registry);
    return registry.isInliningDisallowed();
  }

  private static class MethodInlineDecision extends UseRegistry {
    private boolean disallowInlining = false;

    public boolean isInliningDisallowed() {
      return disallowInlining;
    }

    private boolean allowInlining() {
      return true;
    }

    private boolean disallowInlining() {
      disallowInlining = true;
      return true;
    }

    @Override
    public boolean registerInvokeInterface(DexMethod method) {
      return disallowInlining();
    }

    @Override
    public boolean registerInvokeVirtual(DexMethod method) {
      return disallowInlining();
    }

    @Override
    public boolean registerInvokeDirect(DexMethod method) {
      return allowInlining();
    }

    @Override
    public boolean registerInvokeStatic(DexMethod method) {
      return allowInlining();
    }

    @Override
    public boolean registerInvokeSuper(DexMethod method) {
      return allowInlining();
    }

    @Override
    public boolean registerInstanceFieldWrite(DexField field) {
      return allowInlining();
    }

    @Override
    public boolean registerInstanceFieldRead(DexField field) {
      return allowInlining();
    }

    @Override
    public boolean registerNewInstance(DexType type) {
      return allowInlining();
    }

    @Override
    public boolean registerStaticFieldRead(DexField field) {
      return allowInlining();
    }

    @Override
    public boolean registerStaticFieldWrite(DexField field) {
      return allowInlining();
    }

    @Override
    public boolean registerTypeReference(DexType type) {
      return allowInlining();
    }
  }

  public Collection<DexType> getRemovedClasses() {
    return Collections.unmodifiableCollection(mergedClasses.keySet());
  }
}
