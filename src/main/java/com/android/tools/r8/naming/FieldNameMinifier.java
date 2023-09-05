// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.graph.TopDownClassHierarchyTraversal;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

class FieldNameMinifier {

  private final AppView<AppInfoWithLiveness> appView;
  private final SubtypingInfo subtypingInfo;
  private final Map<DexField, DexString> renaming = new IdentityHashMap<>();
  private final Map<DexType, ReservedFieldNamingState> reservedNamingStates =
      new IdentityHashMap<>();
  private final MemberNamingStrategy strategy;
  private final Map<DexType, DexType> frontiers = new IdentityHashMap<>();
  private final Map<DexType, Set<ReservedFieldNamingState>> frontierStatesForInterfaces =
      new IdentityHashMap<>();

  FieldNameMinifier(
      AppView<AppInfoWithLiveness> appView,
      SubtypingInfo subtypingInfo,
      MemberNamingStrategy strategy) {
    this.appView = appView;
    this.subtypingInfo = subtypingInfo;
    this.strategy = strategy;
  }

  FieldRenaming computeRenaming(Collection<DexClass> interfaces, Timing timing) {
    // Reserve names in all classes first. We do this in subtyping order so we do not
    // shadow a reserved field in subclasses. While there is no concept of virtual field
    // dispatch in Java, field resolution still traverses the super type chain and external
    // code might use a subtype to reference the field.
    timing.begin("reserve-names");
    reserveFieldNames();
    timing.end();
    // Rename the definitions.
    timing.begin("rename-definitions");
    renameFieldsInInterfaces(interfaces);
    renameFieldsInClasses();
    renameFieldsInUnrelatedClasspathClasses();
    timing.end();
    // Rename the references that are not rebound to definitions for some reasons.
    timing.begin("rename-references");
    renameNonReboundReferences();
    timing.end();
    return new FieldRenaming(renaming);
  }

  static class FieldRenaming {

    final Map<DexField, DexString> renaming;

    private FieldRenaming(Map<DexField, DexString> renaming) {
      this.renaming = renaming;
    }

    public static FieldRenaming empty() {
      return new FieldRenaming(ImmutableMap.of());
    }
  }

  private ReservedFieldNamingState getReservedFieldNamingState(DexType type) {
    return reservedNamingStates.get(type);
  }

  private ReservedFieldNamingState getOrCreateReservedFieldNamingState(DexType type) {
    return reservedNamingStates.computeIfAbsent(
        type, ignore -> new ReservedFieldNamingState(appView));
  }

  @SuppressWarnings("ReferenceEquality")
  private void reserveFieldNames() {
    // Build up all reservations in the class hierarchy such that all reserved names are placed
    // at the boundary between a library class and a program class - referred to as the frontier.
    // Special handling is done for interfaces by always considering them to be roots. When
    // traversing down the hierarchy we built up a map from interface to reservation states:
    // - when we reach a frontier find all directly and indirectly implemented interfaces and
    //   add the current reservation state
    // - when we see a program class that implements a direct super type that is an interface also
    //   add the current reservation state. Note that even though we do not visit super interfaces
    //   here, this still works because a super interface will be in the same partition.
    // For an in depth description see MethodNameMinifier.
    TopDownClassHierarchyTraversal.forAllClasses(appView)
        .visit(
            appView.appInfo().classes(),
            clazz -> {
              DexType frontier =
                  clazz.superType == null
                      ? appView.dexItemFactory().objectType
                      : frontiers.getOrDefault(clazz.superType, clazz.type);
              // If frontier != clazz.type we have seen a program class that is on the boundary.
              // Otherwise, if we are visiting a program class then that is the frontier.
              if (frontier != clazz.type || clazz.isProgramClass()) {
                DexType existingValue = frontiers.put(clazz.type, frontier);
                assert existingValue == null;
              }
              ReservedFieldNamingState reservationState =
                  getOrCreateReservedFieldNamingState(frontier);
              for (DexEncodedField field : clazz.fields()) {
                DexString reservedName = strategy.getReservedName(field, clazz);
                if (reservedName != null) {
                  reservationState.markReserved(
                      reservedName, field.getReference().name, field.getReference().type);
                  // TODO(b/148846065): Consider lazily computing the renaming on actual lookups.
                  if (reservedName != field.getReference().name) {
                    renaming.put(field.getReference(), reservedName);
                  }
                }
              }
              if (clazz.isInterface()) {
                frontierStatesForInterfaces.put(
                    clazz.type, SetUtils.newIdentityHashSet(reservationState));
              }
              // Include all reservations from super frontier states. This will propagate reserved
              // names for interfaces down to implementing subtypes.
              for (DexType superType : clazz.allImmediateSupertypes()) {
                // No need to visit object since there are no fields there.
                if (superType != appView.dexItemFactory().objectType) {
                  ReservedFieldNamingState superReservationState =
                      getOrCreateReservedFieldNamingState(
                          frontiers.getOrDefault(superType, superType));
                  if (superReservationState != reservationState) {
                    reservationState.includeReservations(superReservationState);
                  }
                  if (clazz.isProgramClass()) {
                    DexClass superClass = appView.definitionFor(superType, clazz.asProgramClass());
                    if (superClass != null && superClass.isInterface()) {
                      frontierStatesForInterfaces.get(superType).add(reservationState);
                    }
                  }
                }
              }
              if (frontier == clazz.type && clazz.isProgramClass()) {
                patchUpAllIndirectlyImplementingInterfacesFromLibraryAndClassPath(
                    clazz.asProgramClass(), reservationState);
              }
            });
  }

  private void patchUpAllIndirectlyImplementingInterfacesFromLibraryAndClassPath(
      DexProgramClass clazz, ReservedFieldNamingState reservationState) {
    appView
        .appInfo()
        .traverseSuperTypes(
            clazz,
            (superType, superClass, isInterface) -> {
              if (isInterface && superClass.isNotProgramClass()) {
                Set<ReservedFieldNamingState> reservedNamingState =
                    frontierStatesForInterfaces.get(superType);
                if (reservedNamingState != null) {
                  reservedNamingState.add(reservationState);
                }
              }
              return TraversalContinuation.doContinue();
            });
  }

  private void renameFieldsInClasses() {
    Map<DexType, FieldNamingState> states = new IdentityHashMap<>();
    TopDownClassHierarchyTraversal.forAllClasses(appView)
        .excludeInterfaces()
        .visit(
            appView.appInfo().classes(),
            clazz -> {
              assert !clazz.isInterface();

              FieldNamingState parentState =
                  clazz.superType == null
                      ? new FieldNamingState(appView, strategy)
                      : states
                          .computeIfAbsent(
                              clazz.superType, key -> new FieldNamingState(appView, strategy))
                          .clone();

              ReservedFieldNamingState reservedNames =
                  getReservedFieldNamingState(frontiers.getOrDefault(clazz.type, clazz.type));
              FieldNamingState state = parentState.createChildState(reservedNames);
              if (clazz.isProgramClass()) {
                clazz.asProgramClass().forEachProgramField(field -> renameField(field, state));
              }

              assert !states.containsKey(clazz.type);
              states.put(clazz.type, state);
            });
  }

  @SuppressWarnings("ReferenceEquality")
  private void renameFieldsInUnrelatedClasspathClasses() {
    if (appView.options().getProguardConfiguration().hasApplyMappingFile()) {
      appView
          .appInfo()
          .forEachReferencedClasspathClass(
              clazz -> {
                for (DexEncodedField field : clazz.fields()) {
                  DexString reservedName = strategy.getReservedName(field, clazz);
                  if (reservedName != null && reservedName != field.getReference().name) {
                    renaming.put(field.getReference(), reservedName);
                  }
                }
              });
    }
  }

  private void renameFieldsInInterfaces(Collection<DexClass> interfaces) {
    // TODO(b/213415674): Only consider interfaces in the hierarchy of classes.
    InterfacePartitioning partitioning = new InterfacePartitioning(this);
    for (Set<DexClass> partition : partitioning.sortedPartitions(interfaces)) {
      renameFieldsInInterfacePartition(partition);
    }
  }

  private void renameFieldsInInterfacePartition(Set<DexClass> partition) {
    ReservedFieldNamingState namesToBeReservedInImplementsSubclasses =
        new ReservedFieldNamingState(appView);
    ReservedFieldNamingState reservedNamesInPartition = new ReservedFieldNamingState(appView);
    for (DexClass clazz : partition) {
      ReservedFieldNamingState reservedNamesInInterface =
          getReservedFieldNamingState(frontiers.getOrDefault(clazz.type, clazz.type));
      if (reservedNamesInInterface != null) {
        reservedNamesInPartition.includeReservations(reservedNamesInInterface);
        Set<ReservedFieldNamingState> reservedFieldNamingStates =
            frontierStatesForInterfaces.get(clazz.type);
        assert reservedFieldNamingStates != null;
        reservedFieldNamingStates.forEach(
            reservedStates -> {
              reservedNamesInPartition.includeReservations(reservedStates);
              reservedStates.setInterfaceMinificationState(namesToBeReservedInImplementsSubclasses);
            });
      }
    }
    FieldNamingState state = new FieldNamingState(appView, strategy, reservedNamesInPartition);
    for (DexClass clazz : partition) {
      if (clazz.isProgramClass()) {
        assert clazz.isInterface();
        clazz
            .asProgramClass()
            .forEachProgramField(
                field -> {
                  DexString newName = renameField(field, state);
                  namesToBeReservedInImplementsSubclasses.markReserved(
                      newName, field.getReference().name, field.getReference().type);
                });
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private DexString renameField(ProgramField field, FieldNamingState state) {
    DexString newName = state.getOrCreateNameFor(field);
    if (newName != field.getReference().name) {
      renaming.put(field.getReference(), newName);
    }
    return newName;
  }

  private void renameNonReboundReferences() {
    FieldAccessInfoCollection<?> fieldAccessInfoCollection =
        appView.appInfo().getFieldAccessInfoCollection();
    fieldAccessInfoCollection.forEach(this::renameNonReboundAccessesToField);
  }

  private void renameNonReboundAccessesToField(FieldAccessInfo fieldAccessInfo) {
    fieldAccessInfo.forEachIndirectAccess(this::renameNonReboundAccessToField);
  }

  @SuppressWarnings("ReferenceEquality")
  private void renameNonReboundAccessToField(DexField field) {
    // If the given field reference is a non-rebound reference to a program field, then assign the
    // same name as the resolved field.
    if (renaming.containsKey(field)) {
      return;
    }
    DexProgramClass holder = asProgramClassOrNull(appView.definitionForHolder(field));
    if (holder == null) {
      return;
    }
    DexEncodedField definition = appView.appInfo().resolveFieldOn(holder, field).getResolvedField();
    if (definition != null
        && definition.getReference() != field
        && renaming.containsKey(definition.getReference())) {
      renaming.put(field, renaming.get(definition.getReference()));
    }
  }

  static class InterfacePartitioning {

    private final FieldNameMinifier minifier;
    private final AppView<AppInfoWithLiveness> appView;
    private final Set<DexType> visited = Sets.newIdentityHashSet();

    InterfacePartitioning(FieldNameMinifier minifier) {
      this.minifier = minifier;
      appView = minifier.appView;
    }

    private List<Set<DexClass>> sortedPartitions(Collection<DexClass> interfaces) {
      List<Set<DexClass>> partitions = new ArrayList<>();
      for (DexClass clazz : interfaces) {
        if (clazz != null && visited.add(clazz.type)) {
          Set<DexClass> partition = buildSortedPartition(clazz);
          assert !partition.isEmpty();
          assert partition.stream().allMatch(DexClass::isInterface);
          assert partition.stream().map(DexClass::getType).allMatch(visited::contains);
          partitions.add(partition);
        }
      }
      return partitions;
    }

    @SuppressWarnings("ReferenceEquality")
    private Set<DexClass> buildSortedPartition(DexClass src) {
      Set<DexClass> partition = new TreeSet<>(Comparator.comparing(DexClass::getType));

      Deque<DexType> worklist = new ArrayDeque<>();
      worklist.add(src.type);

      while (!worklist.isEmpty()) {
        DexType type = worklist.removeFirst();

        DexClass clazz = appView.definitionFor(type);
        if (clazz == null) {
          continue;
        }

        for (DexType superinterface : clazz.interfaces.values) {
          if (visited.add(superinterface)) {
            worklist.add(superinterface);
          }
        }

        if (clazz.isInterface()) {
          partition.add(clazz);

          for (DexType subtype : minifier.subtypingInfo.allImmediateSubtypes(type)) {
            if (visited.add(subtype)) {
              worklist.add(subtype);
            }
          }
        } else if (clazz.type != appView.dexItemFactory().objectType) {
          if (visited.add(clazz.superType)) {
            worklist.add(clazz.superType);
          }
          for (DexType subclass : minifier.subtypingInfo.allImmediateExtendsSubtypes(type)) {
            if (visited.add(subclass)) {
              worklist.add(subclass);
            }
          }
        }
      }

      return partition;
    }
  }
}
