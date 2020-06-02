// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.BottomUpClassHierarchyTraversal;
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
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
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
  private Map<DexType, ReservedFieldNamingState> reservedNamingStates = new IdentityHashMap<>();
  private final MemberNamingStrategy strategy;

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
    propagateReservedFieldNamesUpwards();
    renameFieldsInClasses();
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

  private void reserveFieldNames() {
    // Reserve all field names that need to be reserved.
    appView
        .appInfo()
        .forEachTypeInHierarchyOfLiveProgramClasses(
            clazz -> {
              ReservedFieldNamingState reservedNames = null;
              for (DexEncodedField field : clazz.fields()) {
                DexString reservedName = strategy.getReservedName(field, clazz);
                if (reservedName != null) {
                  if (reservedNames == null) {
                    reservedNames = getOrCreateReservedFieldNamingState(clazz.type);
                  }
                  reservedNames.markReservedDirectly(
                      reservedName, field.field.name, field.field.type);
                  // TODO(b/148846065): Consider lazily computing the renaming on actual lookups.
                  if (reservedName != field.field.name) {
                    renaming.put(field.field, reservedName);
                  }
                }
              }

              // For interfaces, propagate reserved names to all implementing classes.
              if (clazz.isInterface() && reservedNames != null) {
                for (DexType implementationType :
                    subtypingInfo.allImmediateImplementsSubtypes(clazz.type)) {
                  DexClass implementation = appView.definitionFor(implementationType);
                  if (implementation != null) {
                    assert !implementation.isInterface();
                    getOrCreateReservedFieldNamingState(implementationType)
                        .includeReservations(reservedNames);
                  }
                }
              }
            });

    // TODO(b/148846065): Consider lazily computing the renaming on actual lookups.
    appView
        .appInfo()
        .forEachReferencedClasspathClass(
            clazz -> {
              for (DexEncodedField field : clazz.fields()) {
                DexString reservedName = strategy.getReservedName(field, clazz);
                if (reservedName != null && reservedName != field.field.name) {
                  renaming.put(field.field, reservedName);
                }
              }
            });

    propagateReservedFieldNamesUpwards();
  }

  private void propagateReservedFieldNamesUpwards() {
    BottomUpClassHierarchyTraversal.forProgramClasses(appView, subtypingInfo)
        .visit(
            appView.appInfo().classes(),
            clazz -> {
              ReservedFieldNamingState reservedNames = getReservedFieldNamingState(clazz.type);
              if (reservedNames != null) {
                for (DexType supertype : clazz.allImmediateSupertypes()) {
                  if (supertype.isProgramType(appView)) {
                    getOrCreateReservedFieldNamingState(supertype)
                        .includeReservationsFromBelow(reservedNames);
                  }
                }
              }
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
                  getOrCreateReservedFieldNamingState(clazz.type);
              FieldNamingState state = parentState.createChildState(reservedNames);
              if (clazz.isProgramClass()) {
                clazz.asProgramClass().forEachProgramField(field -> renameField(field, state));
              }

              assert !states.containsKey(clazz.type);
              states.put(clazz.type, state);
            });
  }

  private void renameFieldsInInterfaces(Collection<DexClass> interfaces) {
    InterfacePartitioning partioning = new InterfacePartitioning(this);
    for (Set<DexClass> partition : partioning.sortedPartitions(interfaces)) {
      renameFieldsInInterfacePartition(partition);
    }
  }

  private void renameFieldsInInterfacePartition(Set<DexClass> partition) {
    ReservedFieldNamingState reservedNamesInPartition = new ReservedFieldNamingState(appView);
    for (DexClass clazz : partition) {
      ReservedFieldNamingState reservedNamesInInterface = getReservedFieldNamingState(clazz.type);
      if (reservedNamesInInterface != null) {
        reservedNamesInPartition.includeReservations(reservedNamesInInterface);
        reservedNamesInPartition.includeReservationsFromBelow(reservedNamesInInterface);
      }
    }

    ReservedFieldNamingState namesToBeReservedInImplementsSubclasses =
        new ReservedFieldNamingState(appView);

    FieldNamingState state = new FieldNamingState(appView, strategy, reservedNamesInPartition);
    for (DexClass clazz : partition) {
      if (clazz.isProgramClass()) {
        assert clazz.isInterface();
        clazz
            .asProgramClass()
            .forEachProgramField(
                field -> {
                  DexString newName = renameField(field, state);
                  namesToBeReservedInImplementsSubclasses.markReservedDirectly(
                      newName, field.getReference().name, field.getReference().type);
                });
      }
    }

    Set<DexType> visited = Sets.newIdentityHashSet();
    for (DexClass clazz : partition) {
      for (DexType implementationType : subtypingInfo.allImmediateImplementsSubtypes(clazz.type)) {
        if (!visited.add(implementationType)) {
          continue;
        }

        DexClass implementation = appView.definitionFor(implementationType);
        if (implementation != null) {
          getOrCreateReservedFieldNamingState(implementationType)
              .includeReservations(namesToBeReservedInImplementsSubclasses);
        }
      }
    }
  }

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
    if (definition != null && definition.field != field && renaming.containsKey(definition.field)) {
      renaming.put(field, renaming.get(definition.field));
    }
  }

  static class InterfacePartitioning {

    private final FieldNameMinifier minfier;
    private final AppView<AppInfoWithLiveness> appView;
    private final Set<DexType> visited = Sets.newIdentityHashSet();

    InterfacePartitioning(FieldNameMinifier minifier) {
      this.minfier = minifier;
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

    private Set<DexClass> buildSortedPartition(DexClass src) {
      Set<DexClass> partition = new TreeSet<>((x, y) -> x.type.slowCompareTo(y.type));

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

          for (DexType subtype : minfier.subtypingInfo.allImmediateSubtypes(type)) {
            if (visited.add(subtype)) {
              worklist.add(subtype);
            }
          }
        } else if (clazz.type != appView.dexItemFactory().objectType) {
          if (visited.add(clazz.superType)) {
            worklist.add(clazz.superType);
          }
          for (DexType subclass : minfier.subtypingInfo.allImmediateExtendsSubtypes(type)) {
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
