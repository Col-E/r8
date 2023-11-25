// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.verticalclassmerging.IllegalAccessDetector;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class RespectPackageBoundaries extends MultiClassPolicy {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Mode mode;

  public RespectPackageBoundaries(AppView<? extends AppInfoWithClassHierarchy> appView, Mode mode) {
    this.appView = appView;
    this.mode = mode;
  }

  boolean shouldRestrictMergingAcrossPackageBoundary(DexProgramClass clazz) {
    // Check that the class is public, otherwise it is package private.
    if (!clazz.isPublic()) {
      return true;
    }

    // Annotations may access non-public items (or themselves be non-public), so for now we
    // conservatively restrict class merging when annotations are present.
    //
    // Note that in non-compat mode we should never see any annotations here, since we only merge
    // non-kept classes with non-kept members.
    if (clazz.hasAnnotations()
        || Iterables.any(clazz.members(), DexDefinition::hasAnyAnnotations)) {
      return true;
    }

    // Check that each implemented interface is public.
    for (DexType interfaceType : clazz.getInterfaces()) {
      if (clazz.getType().isSamePackage(interfaceType)) {
        DexClass interfaceClass = appView.definitionFor(interfaceType);
        if (interfaceClass == null || !interfaceClass.isPublic()) {
          return true;
        }
      }
    }

    // If any members are package private or protected, then their access depends on the package.
    for (DexEncodedMember<?, ?> member : clazz.members()) {
      if (member.getAccessFlags().isPackagePrivateOrProtected()) {
        return true;
      }
    }

    // If any field has a non-public type, then field merging may lead to check-cast instructions
    // being synthesized. These synthesized accesses depends on the package.
    for (DexEncodedField field : clazz.fields()) {
      DexType fieldBaseType = field.getType().toBaseType(appView.dexItemFactory());
      if (fieldBaseType.isClassType()) {
        DexClass fieldBaseClass = appView.definitionFor(fieldBaseType);
        if (fieldBaseClass == null || !fieldBaseClass.isPublic()) {
          return true;
        }
      }
    }

    // Check that all accesses from [clazz] to classes or members from the current package of
    // [clazz] will continue to work. This is guaranteed if the methods of [clazz] do not access
    // any private or protected classes or members from the current package of [clazz].
    TraversalContinuation<?, ?> result =
        clazz.traverseProgramMethods(
            method -> {
              boolean foundIllegalAccess =
                  method.registerCodeReferencesWithResult(
                      new IllegalAccessDetector(appView, method) {

                        @Override
                        protected boolean checkRewrittenFieldType(DexClassAndField field) {
                          if (mode.isInitial()) {
                            // If the type of the field is package private, we need to keep the
                            // current class in its package in case we end up synthesizing a
                            // check-cast for the field type after relaxing the type of the field
                            // after instance field merging.
                            DexType fieldBaseType = field.getType().toBaseType(dexItemFactory());
                            if (fieldBaseType.isClassType()) {
                              DexClass fieldBaseClass = appView.definitionFor(fieldBaseType);
                              if (fieldBaseClass == null || !fieldBaseClass.isPublic()) {
                                return setFoundPackagePrivateAccess();
                              }
                            }
                          } else {
                            // No relaxing of field types, hence no insertion of casts where we need
                            // to guarantee visibility.
                          }
                          return continueSearchForPackagePrivateAccess();
                        }
                      });
              if (foundIllegalAccess) {
                return TraversalContinuation.doBreak();
              }
              return TraversalContinuation.doContinue();
            });
    return result.shouldBreak();
  }

  /** Sort unrestricted classes into restricted classes if they are in the same package. */
  void tryFindRestrictedPackage(
      MergeGroup unrestrictedClasses, Map<String, MergeGroup> restrictedClasses) {
    unrestrictedClasses.removeIf(
        clazz -> {
          MergeGroup restrictedPackage = restrictedClasses.get(clazz.type.getPackageDescriptor());
          if (restrictedPackage != null) {
            restrictedPackage.add(clazz);
            return true;
          }
          return false;
        });
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    Map<String, MergeGroup> restrictedClasses = new LinkedHashMap<>();
    MergeGroup unrestrictedClasses = new MergeGroup();

    // Sort all restricted classes into packages.
    for (DexProgramClass clazz : group) {
      if (shouldRestrictMergingAcrossPackageBoundary(clazz)) {
        restrictedClasses
            .computeIfAbsent(clazz.getType().getPackageDescriptor(), ignore -> new MergeGroup())
            .add(clazz);
      } else {
        unrestrictedClasses.add(clazz);
      }
    }

    tryFindRestrictedPackage(unrestrictedClasses, restrictedClasses);
    removeTrivialGroups(restrictedClasses.values());

    // TODO(b/166577694): Add the unrestricted classes to restricted groups, but ensure they aren't
    // the merge target.
    Collection<MergeGroup> groups = new ArrayList<>(restrictedClasses.size() + 1);
    if (unrestrictedClasses.size() > 1) {
      groups.add(unrestrictedClasses);
    }
    groups.addAll(restrictedClasses.values());
    return groups;
  }

  @Override
  public String getName() {
    return "RespectPackageBoundaries";
  }
}
