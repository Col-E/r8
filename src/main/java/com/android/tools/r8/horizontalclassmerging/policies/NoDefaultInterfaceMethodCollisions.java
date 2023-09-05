// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.BottomUpClassHierarchyTraversal;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.graph.TopDownClassHierarchyTraversal;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicyWithPreprocessing;
import com.android.tools.r8.horizontalclassmerging.policies.NoDefaultInterfaceMethodCollisions.InterfaceInfo;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * This policy prevents that interface merging changes semantics of invoke-interface/invoke-virtual
 * instructions that dispatch to default interface methods.
 *
 * <p>As a simple example, consider the following snippet of code. If we merge interfaces I and K,
 * then we effectively add the default interface method K.m() to I, which would change the semantics
 * of calls to A.m().
 *
 * <pre>
 *   interface I {}
 *   interface J {
 *     default void m() { print("J"); }
 *   }
 *   interface K {
 *     default void m() { print("K"); }
 *   }
 *   class A implements I, J {}
 * </pre>
 *
 * Note that we also cannot merge I with K, even if K does not declare any methods directly:
 *
 * <pre>
 *   interface K0 {
 *     default void m() { print("K"); }
 *   }
 *   interface K extends K0 {}
 * </pre>
 *
 * Also, note that this is not a problem if class A overrides void m().
 */
public class NoDefaultInterfaceMethodCollisions
    extends MultiClassPolicyWithPreprocessing<Map<DexType, InterfaceInfo>> {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Mode mode;

  public NoDefaultInterfaceMethodCollisions(
      AppView<? extends AppInfoWithClassHierarchy> appView, Mode mode) {
    this.appView = appView;
    this.mode = mode;
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group, Map<DexType, InterfaceInfo> infos) {
    if (!group.isInterfaceGroup()) {
      return ImmutableList.of(group);
    }

    // For each interface I in the group, check that each (non-interface) subclass of I does not
    // inherit a default method that is also declared by another interface J in the merge group.
    //
    // Note that the primary piece of work is carried out in the preprocess() method
    //
    // TODO(b/173990042): Consider forming multiple groups instead of just filtering. In practice,
    //  this rarely leads to much filtering, though, since the use of default methods is somewhat
    //  limited.
    MergeGroup newGroup = new MergeGroup();
    for (DexProgramClass clazz : group) {
      Set<DexMethod> newDefaultMethodsAddedToClassByMerge =
          computeNewDefaultMethodsAddedToClassByMerge(clazz, group, infos);
      if (isSafeToAddDefaultMethodsToClass(clazz, newDefaultMethodsAddedToClassByMerge, infos)) {
        newGroup.add(clazz);
      }
    }
    return newGroup.isTrivial() ? Collections.emptyList() : ListUtils.newLinkedList(newGroup);
  }

  private Set<DexMethod> computeNewDefaultMethodsAddedToClassByMerge(
      DexProgramClass clazz, MergeGroup group, Map<DexType, InterfaceInfo> infos) {
    // Run through the other classes in the merge group, and add the default interface methods that
    // they declare (or inherit from a super interface) to a set.
    Set<DexMethod> newDefaultMethodsAddedToClassByMerge = Sets.newIdentityHashSet();
    for (DexProgramClass other : group) {
      if (other != clazz) {
        Collection<Set<DexMethod>> inheritedDefaultMethodsFromOther =
            infos.get(other.getType()).getInheritedDefaultMethods().values();
        inheritedDefaultMethodsFromOther.forEach(newDefaultMethodsAddedToClassByMerge::addAll);
      }
    }
    return newDefaultMethodsAddedToClassByMerge;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isSafeToAddDefaultMethodsToClass(
      DexProgramClass clazz,
      Set<DexMethod> newDefaultMethodsAddedToClassByMerge,
      Map<DexType, InterfaceInfo> infos) {
    // Check if there is a subclass of this interface, which inherits a default interface method
    // that would also be added by to this interface by merging the interfaces in the group.
    Map<DexMethodSignature, Set<DexMethod>> defaultMethodsInheritedBySubclassesOfClass =
        infos.get(clazz.getType()).getDefaultMethodsInheritedBySubclasses();
    for (DexMethod newDefaultMethodAddedToClassByMerge : newDefaultMethodsAddedToClassByMerge) {
      Set<DexMethod> defaultMethodsInheritedBySubclassesOfClassWithSameSignature =
          defaultMethodsInheritedBySubclassesOfClass.getOrDefault(
              newDefaultMethodAddedToClassByMerge.getSignature(), emptySet());
      // Look for a method different from the method we're adding.
      for (DexMethod method : defaultMethodsInheritedBySubclassesOfClassWithSameSignature) {
        if (method != newDefaultMethodAddedToClassByMerge) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public Map<DexType, InterfaceInfo> preprocess(
      Collection<MergeGroup> groups, ExecutorService executorService) {
    SubtypingInfo subtypingInfo = SubtypingInfo.create(appView);
    Collection<DexProgramClass> classesOfInterest = computeClassesOfInterest(subtypingInfo);
    Map<DexType, DexMethodSignatureSet> inheritedClassMethodsPerClass =
        computeInheritedClassMethodsPerProgramClass(classesOfInterest);
    Map<DexType, Map<DexMethodSignature, Set<DexMethod>>> inheritedDefaultMethodsPerClass =
        computeInheritedDefaultMethodsPerProgramType(
            classesOfInterest, inheritedClassMethodsPerClass);

    // Finally, do a bottom-up traversal, pushing the inherited default methods upwards.
    Map<DexType, Map<DexMethodSignature, Set<DexMethod>>>
        defaultMethodsInheritedBySubclassesPerClass =
            computeDefaultMethodsInheritedBySubclassesPerProgramClass(
                classesOfInterest, inheritedDefaultMethodsPerClass, groups, subtypingInfo);

    // Store the computed information for each interface that is subject to merging.
    Map<DexType, InterfaceInfo> infos = new IdentityHashMap<>();
    for (MergeGroup group : groups) {
      if (group.isInterfaceGroup()) {
        for (DexProgramClass clazz : group) {
          infos.put(
              clazz.getType(),
              new InterfaceInfo(
                  inheritedDefaultMethodsPerClass.getOrDefault(clazz.getType(), emptyMap()),
                  defaultMethodsInheritedBySubclassesPerClass.getOrDefault(
                      clazz.getType(), emptyMap())));
        }
      }
    }
    return infos;
  }

  /** Returns the set of program classes that must be considered during preprocessing. */
  private Collection<DexProgramClass> computeClassesOfInterest(SubtypingInfo subtypingInfo) {
    // TODO(b/173990042): Limit result to the set of classes that are in the same as one of
    //  the interfaces that is subject to merging.
    return appView.appInfo().classes();
  }

  /**
   * For each class, computes the (transitive) set of virtual methods that is declared on the class
   * itself or one of its (non-interface) super classes.
   */
  private Map<DexType, DexMethodSignatureSet> computeInheritedClassMethodsPerProgramClass(
      Collection<DexProgramClass> classesOfInterest) {
    Map<DexType, DexMethodSignatureSet> inheritedClassMethodsPerClass = new IdentityHashMap<>();
    TopDownClassHierarchyTraversal.forAllClasses(appView)
        .excludeInterfaces()
        .visit(
            classesOfInterest,
            clazz -> {
              DexMethodSignatureSet classMethods =
                  DexMethodSignatureSet.create(
                      inheritedClassMethodsPerClass.getOrDefault(
                          clazz.getSuperType(), DexMethodSignatureSet.empty()));
              for (DexEncodedMethod method : clazz.virtualMethods()) {
                classMethods.add(method.getSignature());
              }
              inheritedClassMethodsPerClass.put(clazz.getType(), classMethods);
            });
    inheritedClassMethodsPerClass
        .keySet()
        .removeIf(type -> asProgramClassOrNull(appView.definitionFor(type)) == null);
    return inheritedClassMethodsPerClass;
  }

  /**
   * For each class or interface, computes the (transitive) set of virtual methods that is declared
   * on the class itself or one of its (non-interface) super classes.
   */
  private Map<DexType, Map<DexMethodSignature, Set<DexMethod>>>
      computeInheritedDefaultMethodsPerProgramType(
          Collection<DexProgramClass> classesOfInterest,
          Map<DexType, DexMethodSignatureSet> inheritedClassMethodsPerClass) {
    Map<DexType, Map<DexMethodSignature, Set<DexMethod>>> inheritedDefaultMethodsPerType =
        new IdentityHashMap<>();
    TopDownClassHierarchyTraversal.forAllClasses(appView)
        .visit(
            classesOfInterest,
            clazz -> {
              // Compute the set of default method signatures that this class inherits from its
              // super class and interfaces.
              Map<DexMethodSignature, Set<DexMethod>> inheritedDefaultMethods = new HashMap<>();
              for (DexType supertype : clazz.allImmediateSupertypes()) {
                Map<DexMethodSignature, Set<DexMethod>> inheritedDefaultMethodsFromSuperType =
                    inheritedDefaultMethodsPerType.getOrDefault(supertype, emptyMap());
                inheritedDefaultMethodsFromSuperType.forEach(
                    (signature, methods) ->
                        inheritedDefaultMethods
                            .computeIfAbsent(signature, ignore -> Sets.newIdentityHashSet())
                            .addAll(methods));
              }

              // If this is an interface, also include the default methods it declares.
              if (clazz.isInterface()) {
                for (DexEncodedMethod method :
                    clazz.virtualMethods(DexEncodedMethod::isDefaultMethod)) {
                  inheritedDefaultMethods
                      .computeIfAbsent(method.getSignature(), ignore -> Sets.newIdentityHashSet())
                      .add(method.getReference());
                }
              }

              // Remove all default methods that are declared as (non-interface) class methods on
              // the current class.
              inheritedDefaultMethods
                  .keySet()
                  .removeAll(
                      inheritedClassMethodsPerClass.getOrDefault(
                          clazz.getType(), DexMethodSignatureSet.empty()));

              if (!inheritedDefaultMethods.isEmpty()) {
                inheritedDefaultMethodsPerType.put(clazz.getType(), inheritedDefaultMethods);
              }
            });
    inheritedDefaultMethodsPerType
        .keySet()
        .removeIf(type -> asProgramClassOrNull(appView.definitionFor(type)) == null);
    return inheritedDefaultMethodsPerType;
  }

  /**
   * Performs a bottom-up traversal of the hierarchy, where the inherited default methods of each
   * class are pushed upwards. This accumulates the set of default methods that are inherited by all
   * subclasses of a given interface.
   */
  private Map<DexType, Map<DexMethodSignature, Set<DexMethod>>>
      computeDefaultMethodsInheritedBySubclassesPerProgramClass(
          Collection<DexProgramClass> classesOfInterest,
          Map<DexType, Map<DexMethodSignature, Set<DexMethod>>> inheritedDefaultMethodsPerClass,
          Collection<MergeGroup> groups,
          SubtypingInfo subtypingInfo) {
    // Build a mapping from class types to their merge group.
    Map<DexType, Iterable<DexProgramClass>> classGroupsByType =
        MapUtils.newIdentityHashMap(
            builder ->
                Iterables.filter(groups, MergeGroup::isClassGroup)
                    .forEach(group -> group.forEach(clazz -> builder.put(clazz.getType(), group))));

    // Copy the map from classes to their inherited default methods.
    Map<DexType, Map<DexMethodSignature, Set<DexMethod>>>
        defaultMethodsInheritedBySubclassesPerClass =
            MapUtils.clone(
                inheritedDefaultMethodsPerClass,
                new HashMap<>(),
                outerValue ->
                    MapUtils.clone(outerValue, new HashMap<>(), SetUtils::newIdentityHashSet));

    // Propagate data upwards. If classes A and B are in a merge group, we need to push the state
    // for A to all of B's supertypes, and the state for B to all of A's supertypes.
    //
    // Therefore, it is important that we don't process any of A's supertypes until B has been
    // processed, since that would lead to inadequate upwards propagation. To achieve this, we
    // simulate that both A and B are subtypes of A's and B's supertypes.
    Function<DexType, Iterable<DexType>> immediateSubtypesProvider =
        type -> {
          Set<DexType> immediateSubtypesAfterClassMerging = Sets.newIdentityHashSet();
          for (DexType immediateSubtype : subtypingInfo.allImmediateSubtypes(type)) {
            Iterable<DexProgramClass> group = classGroupsByType.get(immediateSubtype);
            if (group != null) {
              group.forEach(member -> immediateSubtypesAfterClassMerging.add(member.getType()));
            } else {
              immediateSubtypesAfterClassMerging.add(immediateSubtype);
            }
          }
          return immediateSubtypesAfterClassMerging;
        };

    BottomUpClassHierarchyTraversal.forProgramClasses(appView, immediateSubtypesProvider)
        .visit(
            classesOfInterest,
            clazz -> {
              // Push the current class' default methods upwards to all super classes.
              Map<DexMethodSignature, Set<DexMethod>> defaultMethodsToPropagate =
                  defaultMethodsInheritedBySubclassesPerClass.getOrDefault(
                      clazz.getType(), emptyMap());
              Iterable<DexProgramClass> group =
                  classGroupsByType.getOrDefault(clazz.getType(), IterableUtils.singleton(clazz));
              for (DexProgramClass member : group) {
                for (DexType supertype : member.allImmediateSupertypes()) {
                  Map<DexMethodSignature, Set<DexMethod>>
                      defaultMethodsInheritedBySubclassesForSupertype =
                          defaultMethodsInheritedBySubclassesPerClass.computeIfAbsent(
                              supertype, ignore -> new HashMap<>());
                  defaultMethodsToPropagate.forEach(
                      (signature, methods) ->
                          defaultMethodsInheritedBySubclassesForSupertype
                              .computeIfAbsent(signature, ignore -> Sets.newIdentityHashSet())
                              .addAll(methods));
                }
              }
            });
    defaultMethodsInheritedBySubclassesPerClass
        .keySet()
        .removeIf(type -> asProgramClassOrNull(appView.definitionFor(type)) == null);
    return defaultMethodsInheritedBySubclassesPerClass;
  }

  @Override
  public String getName() {
    return "NoDefaultInterfaceMethodCollisions";
  }

  @Override
  public boolean shouldSkipPolicy() {
    return !appView.options().horizontalClassMergerOptions().isInterfaceMergingEnabled(mode);
  }

  static class InterfaceInfo {

    // The set of default interface methods (grouped by signature) that this interface declares or
    // inherits from one of its (transitive) super interfaces.
    private final Map<DexMethodSignature, Set<DexMethod>> inheritedDefaultMethods;

    // The set of default interface methods (grouped by signature) that subclasses of this interface
    // inherits from one of its (transitively) implemented super interfaces.
    private final Map<DexMethodSignature, Set<DexMethod>> defaultMethodsInheritedBySubclasses;

    InterfaceInfo(
        Map<DexMethodSignature, Set<DexMethod>> inheritedDefaultMethods,
        Map<DexMethodSignature, Set<DexMethod>> defaultMethodsInheritedBySubclasses) {
      this.inheritedDefaultMethods = inheritedDefaultMethods;
      this.defaultMethodsInheritedBySubclasses = defaultMethodsInheritedBySubclasses;
    }

    Map<DexMethodSignature, Set<DexMethod>> getInheritedDefaultMethods() {
      return inheritedDefaultMethods;
    }

    Map<DexMethodSignature, Set<DexMethod>> getDefaultMethodsInheritedBySubclasses() {
      return defaultMethodsInheritedBySubclasses;
    }
  }
}
