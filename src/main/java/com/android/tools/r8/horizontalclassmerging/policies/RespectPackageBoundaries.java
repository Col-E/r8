// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.VerticalClassMerger.IllegalAccessDetector;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class RespectPackageBoundaries extends MultiClassPolicy {
  private final AppView<AppInfoWithLiveness> appView;

  public RespectPackageBoundaries(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  boolean shouldRestrictMergingAcrossPackageBoundary(DexProgramClass clazz) {
    // Check that the class is public, otherwise it is package private.
    if (!clazz.isPublic()) {
      return true;
    }

    // If any members are package private or protected, then their access depends on the package.
    for (DexEncodedMember<?, ?> member : clazz.members()) {
      if (member.getAccessFlags().isPackagePrivateOrProtected()) {
        return true;
      }
    }

    // Check that all accesses from [clazz] to classes or members from the current package of
    // [clazz] will continue to work. This is guaranteed if the methods of [clazz] do not access
    // any private or protected classes or members from the current package of [clazz].
    IllegalAccessDetector registry = new IllegalAccessDetector(appView, clazz);
    TraversalContinuation result =
        clazz.traverseProgramMethods(
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

  /** Sort unrestricted classes into restricted classes if they are in the same package. */
  void tryFindRestrictedPackage(
      LinkedList<DexProgramClass> unrestrictedClasses,
      Map<String, List<DexProgramClass>> restrictedClasses) {
    Iterator<DexProgramClass> i = unrestrictedClasses.iterator();
    while (i.hasNext()) {
      DexProgramClass clazz = i.next();
      Collection<DexProgramClass> restrictedPackage =
          restrictedClasses.get(clazz.type.getPackageDescriptor());
      if (restrictedPackage != null) {
        restrictedPackage.add(clazz);
        i.remove();
      }
    }
  }

  @Override
  public Collection<List<DexProgramClass>> apply(List<DexProgramClass> group) {
    Map<String, List<DexProgramClass>> restrictedClasses = new LinkedHashMap<>();
    LinkedList<DexProgramClass> unrestrictedClasses = new LinkedList<>();

    // Sort all restricted classes into packages.
    for (DexProgramClass clazz : group) {
      if (shouldRestrictMergingAcrossPackageBoundary(clazz)) {
        restrictedClasses
            .computeIfAbsent(clazz.type.getPackageDescriptor(), ignore -> new ArrayList<>())
            .add(clazz);
      } else {
        unrestrictedClasses.add(clazz);
      }
    }

    tryFindRestrictedPackage(unrestrictedClasses, restrictedClasses);
    removeTrivialGroups(restrictedClasses.values());

    // TODO(b/166577694): Add the unrestricted classes to restricted groups, but ensure they aren't
    // the merge target.
    Collection<List<DexProgramClass>> groups = new ArrayList<>(restrictedClasses.size() + 1);
    if (unrestrictedClasses.size() > 1) {
      groups.add(unrestrictedClasses);
    }
    groups.addAll(restrictedClasses.values());
    return groups;
  }
}
