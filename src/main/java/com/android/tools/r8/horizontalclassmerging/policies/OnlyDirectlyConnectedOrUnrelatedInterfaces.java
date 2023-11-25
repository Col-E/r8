// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicyWithPreprocessing;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

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
public class OnlyDirectlyConnectedOrUnrelatedInterfaces
    extends MultiClassPolicyWithPreprocessing<SubtypingInfo> {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Mode mode;

  // The interface merge groups that this policy has committed to so far.
  private final Map<DexProgramClass, MergeGroup> committed = new IdentityHashMap<>();

  public OnlyDirectlyConnectedOrUnrelatedInterfaces(
      AppView<? extends AppInfoWithClassHierarchy> appView, Mode mode) {
    this.appView = appView;
    this.mode = mode;
  }

  // TODO(b/270398965): Replace LinkedList.
  @Override
  @SuppressWarnings({"JdkObsolete", "MixedMutabilityReturnType"})
  public Collection<MergeGroup> apply(MergeGroup group, SubtypingInfo subtypingInfo) {
    if (!group.isInterfaceGroup()) {
      return ImmutableList.of(group);
    }

    List<MergeGroupWithInfo> newGroupsWithInfo = new ArrayList<>();
    for (DexProgramClass clazz : group) {
      Set<DexProgramClass> superInterfaces = computeSuperInterfaces(clazz);
      Set<DexProgramClass> subInterfaces = computeSubInterfaces(clazz, subtypingInfo);

      MergeGroupWithInfo newGroup = null;
      for (MergeGroupWithInfo candidateGroup : newGroupsWithInfo) {
        // Check if adding `clazz` to `candidateGroup` would introduce a super interface that is
        // also a sub interface. In that case we must abort since merging would lead to a cycle in
        // the class hierarchy.
        if (candidateGroup.isSafeToAddSubAndSuperInterfaces(
            clazz, subInterfaces, superInterfaces)) {
          newGroup = candidateGroup;
          break;
        }
      }

      if (newGroup != null) {
        newGroup.add(clazz, superInterfaces, subInterfaces);
      } else {
        newGroup = new MergeGroupWithInfo(clazz, superInterfaces, subInterfaces);
        newGroupsWithInfo.add(newGroup);
      }
      committed.put(clazz, newGroup.getGroup());
    }

    List<MergeGroup> newGroups = new LinkedList<>();
    for (MergeGroupWithInfo newGroupWithInfo : newGroupsWithInfo) {
      MergeGroup newGroup = newGroupWithInfo.getGroup();
      if (newGroup.isTrivial()) {
        assert !newGroup.isEmpty();
        committed.remove(newGroup.getClasses().getFirst());
      } else {
        newGroups.add(newGroup);
      }
    }
    return newGroups;
  }

  private Set<DexProgramClass> computeSuperInterfaces(DexProgramClass clazz) {
    return computeTransitiveSubOrSuperInterfaces(clazz, DexClass::getInterfaces);
  }

  private Set<DexProgramClass> computeSubInterfaces(
      DexProgramClass clazz, SubtypingInfo subtypingInfo) {
    return computeTransitiveSubOrSuperInterfaces(
        clazz, definition -> subtypingInfo.allImmediateExtendsSubtypes(definition.getType()));
  }

  private Set<DexProgramClass> computeTransitiveSubOrSuperInterfaces(
      DexProgramClass clazz,
      Function<DexProgramClass, Iterable<DexType>> immediateSubOrSuperInterfacesProvider) {
    WorkList<DexProgramClass> workList = WorkList.newWorkList(new LinkedHashSet<>());
    // Intentionally not marking `clazz` as seen, since we only want the strict sub/super types.
    workList.addIgnoringSeenSet(clazz);
    workList.process(
        interfaceDefinition -> {
          MergeGroup group = committed.get(interfaceDefinition);
          if (group != null) {
            workList.addIfNotSeen(group);
          }
          for (DexType immediateSubOrSuperInterfaceType :
              immediateSubOrSuperInterfacesProvider.apply(interfaceDefinition)) {
            DexProgramClass immediateSubOrSuperInterface =
                asProgramClassOrNull(appView.definitionFor(immediateSubOrSuperInterfaceType));
            if (immediateSubOrSuperInterface != null) {
              workList.addIfNotSeen(immediateSubOrSuperInterface);
            }
          }
        });
    assert !workList.isSeen(clazz);
    return workList.getMutableSeenSet();
  }

  @Override
  public void clear() {
    committed.clear();
  }

  @Override
  public String getName() {
    return "OnlyDirectlyConnectedOrUnrelatedInterfaces";
  }

  @Override
  public SubtypingInfo preprocess(Collection<MergeGroup> groups, ExecutorService executorService) {
    return SubtypingInfo.create(appView);
  }

  @Override
  public boolean shouldSkipPolicy() {
    return !appView.options().horizontalClassMergerOptions().isInterfaceMergingEnabled(mode);
  }

  static class MergeGroupWithInfo {

    private final MergeGroup group;
    private final Set<DexProgramClass> members;
    private final Set<DexProgramClass> superInterfaces;
    private final Set<DexProgramClass> subInterfaces;

    MergeGroupWithInfo(
        DexProgramClass clazz,
        Set<DexProgramClass> superInterfaces,
        Set<DexProgramClass> subInterfaces) {
      this.group = new MergeGroup(clazz);
      this.members = SetUtils.newIdentityHashSet(clazz);
      this.superInterfaces = superInterfaces;
      this.subInterfaces = subInterfaces;
    }

    void add(
        DexProgramClass clazz,
        Set<DexProgramClass> newSuperInterfaces,
        Set<DexProgramClass> newSubInterfaces) {
      group.add(clazz);
      members.add(clazz);
      Iterables.addAll(
          superInterfaces,
          Iterables.filter(
              newSuperInterfaces, superInterface -> !members.contains(superInterface)));
      superInterfaces.remove(clazz);
      Iterables.addAll(
          subInterfaces,
          Iterables.filter(newSubInterfaces, subInterface -> !members.contains(subInterface)));
      subInterfaces.remove(clazz);
    }

    MergeGroup getGroup() {
      return group;
    }

    boolean isSafeToAddSubAndSuperInterfaces(
        DexProgramClass clazz,
        Set<DexProgramClass> newSubInterfaces,
        Set<DexProgramClass> newSuperInterfaces) {
      // Check that adding the new sub and super interfaces to the group is safe.
      for (DexProgramClass newSubInterface : newSubInterfaces) {
        if (!group.contains(newSubInterface) && superInterfaces.contains(newSubInterface)) {
          return false;
        }
      }
      for (DexProgramClass newSuperInterface : newSuperInterfaces) {
        if (!group.contains(newSuperInterface) && subInterfaces.contains(newSuperInterface)) {
          return false;
        }
      }
      // Check that adding the sub and super interfaces of the group to the current class is safe.
      for (DexProgramClass subInterface : subInterfaces) {
        if (subInterface != clazz && newSuperInterfaces.contains(subInterface)) {
          return false;
        }
      }
      for (DexProgramClass superInterface : superInterfaces) {
        if (superInterface != clazz && newSubInterfaces.contains(superInterface)) {
          return false;
        }
      }
      return true;
    }
  }
}
