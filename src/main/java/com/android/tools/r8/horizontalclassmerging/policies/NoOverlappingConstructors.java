// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class NoOverlappingConstructors extends MultiClassPolicy {

  public void removeNonConflicting(Map<DexProto, Set<DexProgramClass>> overlappingConstructors) {
    Iterator<DexProto> i = overlappingConstructors.keySet().iterator();
    while (i.hasNext()) {
      DexProto proto = i.next();
      if (overlappingConstructors.get(proto).size() == 1) {
        i.remove();
      }
    }
  }

  private Set<DexProgramClass> sortedClassSet(Collection<DexProgramClass> classes) {
    Set<DexProgramClass> set =
        new TreeSet<DexProgramClass>(
            Comparator.comparing(DexProgramClass::getType, DexType::slowCompareTo));
    set.addAll(classes);
    return set;
  }

  @Override
  public Collection<List<DexProgramClass>> apply(List<DexProgramClass> group) {
    Map<DexProto, Set<DexProgramClass>> overlappingConstructors = new IdentityHashMap<>();

    for (DexProgramClass clazz : group) {
      clazz.forEachProgramDirectMethod(
          directMethod -> {
            DexEncodedMethod method = directMethod.getDefinition();
            if (method.isInstanceInitializer()) {
              overlappingConstructors
                  .computeIfAbsent(method.getProto(), ignore -> new HashSet<DexProgramClass>())
                  .add(clazz);
            }
          });
    }

    removeNonConflicting(overlappingConstructors);

    // This is probably related to the graph colouring problem so probably won't be efficient in
    // worst cases. We assume there won't be that many overlapping constructors so this should be
    // reasonable. Constructor merging should also make this obsolete.
    Collection<Set<DexProgramClass>> groups = new LinkedList<>();
    groups.add(sortedClassSet(group));

    for (Set<DexProgramClass> overlappingClasses : overlappingConstructors.values()) {
      Collection<Set<DexProgramClass>> newGroups = new LinkedList<>();

      // For every set of classes that cannot be in the same group, generate a new group with the
      // same constructor and with all remaining constructors.

      for (Set<DexProgramClass> existingGroup : groups) {
        Set<DexProgramClass> actuallyOverlapping = new HashSet<>(overlappingClasses);
        actuallyOverlapping.retainAll(existingGroup);

        if (actuallyOverlapping.size() <= 1) {
          newGroups.add(existingGroup);
        } else {
          Set<DexProgramClass> notOverlapping = new HashSet<>(existingGroup);
          notOverlapping.removeAll(overlappingClasses);
          for (DexProgramClass overlappingClass : actuallyOverlapping) {
            Set<DexProgramClass> newGroup = sortedClassSet(notOverlapping);
            newGroup.add(overlappingClass);
            newGroups.add(newGroup);
          }
        }
      }

      groups = newGroups;
    }

    // Ensure each class is only in a single group and remove singleton and empty groups.
    Set<DexProgramClass> assignedClasses = new HashSet<>();

    Iterator<Set<DexProgramClass>> i = groups.iterator();
    while (i.hasNext()) {
      Set<DexProgramClass> newGroup = i.next();
      newGroup.removeAll(assignedClasses);
      if (newGroup.size() <= 1) {
        i.remove();
      } else {
        assignedClasses.addAll(newGroup);
      }
    }

    // Map to collection
    Collection<List<DexProgramClass>> newGroups = new ArrayList<>();
    for (Set<DexProgramClass> newGroup : groups) {
      newGroups.add(new ArrayList<>(newGroup));
    }
    return newGroups;
  }
}
