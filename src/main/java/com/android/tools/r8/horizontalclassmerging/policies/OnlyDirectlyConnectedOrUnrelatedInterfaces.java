// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * This policy ensures that we do not create cycles in the class hierarchy as a result of interface
 * merging.
 *
 * <p>Example: Consider that we have the following three interfaces:
 *
 * <pre>
 *   interface I extends ... {}
 *   interface J extends I, ... {}
 *   interface K extends J, ... {}
 * </pre>
 *
 * <p>In this case, it would be possible to merge the groups {I, J}, {J, K}, and {I, J, K}. Common
 * to these merge groups is that each interface in the merge group can reach all other interfaces in
 * the same merge group in the class hierarchy, without visiting any interfaces outside the merge
 * group.
 *
 * <p>The group {I, K} cannot safely be merged, as this would lead to a cycle in the class
 * hierarchy:
 *
 * <pre>
 *   interface IK extends J, ... {}
 *   interface J extends IK, ... {}
 * </pre>
 */
public class OnlyDirectlyConnectedOrUnrelatedInterfaces extends MultiClassPolicy {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Mode mode;

  public OnlyDirectlyConnectedOrUnrelatedInterfaces(
      AppView<? extends AppInfoWithClassHierarchy> appView, Mode mode) {
    this.appView = appView;
    this.mode = mode;
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    if (!group.isInterfaceGroup()) {
      return ImmutableList.of(group);
    }

    Set<DexProgramClass> classes = new LinkedHashSet<>(group.getClasses());
    Map<DexProgramClass, Set<DexProgramClass>> ineligibleForMerging =
        computeIneligibleForMergingGraph(classes);
    if (ineligibleForMerging.isEmpty()) {
      return ImmutableList.of(group);
    }

    // Extract sub-merge groups from the graph in such a way that all pairs of interfaces in each
    // merge group are not connected by an edge in the graph.
    List<MergeGroup> newGroups = new LinkedList<>();
    while (!classes.isEmpty()) {
      Iterator<DexProgramClass> iterator = classes.iterator();
      MergeGroup newGroup = new MergeGroup(iterator.next());
      Iterators.addAll(
          newGroup,
          Iterators.filter(
              iterator,
              candidate -> !isConnectedToGroup(candidate, newGroup, ineligibleForMerging)));
      if (!newGroup.isTrivial()) {
        newGroups.add(newGroup);
      }
      classes.removeAll(newGroup.getClasses());
    }
    return newGroups;
  }

  /**
   * Computes an undirected graph, where the nodes are the interfaces from the merge group, and an
   * edge I <-> J represents that I and J are not eligible for merging.
   *
   * <p>We will insert an edge I <-> J, if interface I inherits from interface J, and the path from
   * I to J in the class hierarchy includes an interface K that is outside the merge group. Note
   * that if I extends J directly we will not insert an edge I <-> J (unless there are multiple
   * paths in the class hierarchy from I to J, and one of the paths goes through an interface
   * outside the merge group).
   */
  private Map<DexProgramClass, Set<DexProgramClass>> computeIneligibleForMergingGraph(
      Set<DexProgramClass> classes) {
    Map<DexProgramClass, Set<DexProgramClass>> ineligibleForMerging = new IdentityHashMap<>();
    for (DexProgramClass clazz : classes) {
      forEachIndirectlyReachableInterfaceInMergeGroup(
          clazz,
          classes,
          other ->
              ineligibleForMerging
                  .computeIfAbsent(clazz, ignore -> Sets.newIdentityHashSet())
                  .add(other));
    }
    return ineligibleForMerging;
  }

  private void forEachIndirectlyReachableInterfaceInMergeGroup(
      DexProgramClass clazz, Set<DexProgramClass> classes, Consumer<DexProgramClass> consumer) {
    // First find the set of interfaces that can be reached via paths in the class hierarchy from
    // the given interface, without visiting any interfaces outside the merge group.
    WorkList<DexType> workList = WorkList.newIdentityWorkList(clazz.getInterfaces());
    while (workList.hasNext()) {
      DexProgramClass directlyReachableInterface =
          asProgramClassOrNull(appView.definitionFor(workList.next()));
      if (directlyReachableInterface == null) {
        continue;
      }
      // If the implemented interface is a member of the merge group, then include it's interfaces.
      if (classes.contains(directlyReachableInterface)) {
        workList.addIfNotSeen(directlyReachableInterface.getInterfaces());
      }
    }

    // Initialize a new worklist with the first layer of indirectly reachable interface types.
    Set<DexType> directlyReachableInterfaceTypes = workList.getSeenSet();
    workList = WorkList.newIdentityWorkList();
    for (DexType directlyReachableInterfaceType : directlyReachableInterfaceTypes) {
      DexProgramClass directlyReachableInterface =
          asProgramClassOrNull(appView.definitionFor(directlyReachableInterfaceType));
      if (directlyReachableInterface != null) {
        workList.addIfNotSeen(directlyReachableInterface.getInterfaces());
      }
    }

    // Report all interfaces from the merge group that are reachable in the class hierarchy from the
    // worklist.
    while (workList.hasNext()) {
      DexProgramClass indirectlyReachableInterface =
          asProgramClassOrNull(appView.definitionFor(workList.next()));
      if (indirectlyReachableInterface == null) {
        continue;
      }
      if (classes.contains(indirectlyReachableInterface)) {
        consumer.accept(indirectlyReachableInterface);
      }
      workList.addIfNotSeen(indirectlyReachableInterface.getInterfaces());
    }
  }

  private boolean isConnectedToGroup(
      DexProgramClass clazz,
      MergeGroup group,
      Map<DexProgramClass, Set<DexProgramClass>> ineligibleForMerging) {
    for (DexProgramClass member : group) {
      if (ineligibleForMerging.getOrDefault(clazz, Collections.emptySet()).contains(member)
          || ineligibleForMerging.getOrDefault(member, Collections.emptySet()).contains(clazz)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String getName() {
    return "OnlyDirectlyConnectedOrUnrelatedInterfaces";
  }

  @Override
  public boolean shouldSkipPolicy() {
    return !appView.options().horizontalClassMergerOptions().isInterfaceMergingEnabled(mode);
  }
}
