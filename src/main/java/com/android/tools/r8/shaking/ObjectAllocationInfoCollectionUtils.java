// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.utils.TraversalContinuation;

public class ObjectAllocationInfoCollectionUtils {

  public static boolean mayHaveFinalizeMethodDirectlyOrIndirectly(
      AppView<AppInfoWithLiveness> appView, ClassTypeElement type) {
    return mayHaveFinalizeMethodDirectlyOrIndirectly(
        appView, type, appView.appInfo().getObjectAllocationInfoCollection());
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean mayHaveFinalizeMethodDirectlyOrIndirectly(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ClassTypeElement type,
      ObjectAllocationInfoCollection objectAllocationInfoCollection) {
    // Special case for java.lang.Object.
    if (type.getClassType() == appView.dexItemFactory().objectType
        && !type.getInterfaces().isEmpty()) {
      return type.getInterfaces()
          .anyMatch(
              (iface, isKnown) -> mayHaveFinalizer(appView, objectAllocationInfoCollection, iface));
    }
    return mayHaveFinalizeMethodDirectlyOrIndirectly(
        appView, type.getClassType(), objectAllocationInfoCollection);
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean mayHaveFinalizeMethodDirectlyOrIndirectly(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      DexType type,
      ObjectAllocationInfoCollection objectAllocationInfoCollection) {
    // Special case for java.lang.Object.
    if (type == appView.dexItemFactory().objectType) {
      // The type java.lang.Object could be any instantiated type. Assume a finalizer exists.
      return true;
    }
    return mayHaveFinalizer(appView, objectAllocationInfoCollection, type);
  }

  private static boolean mayHaveFinalizer(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ObjectAllocationInfoCollection objectAllocationInfoCollection,
      DexType type) {
    // A type may have an active finalizer if any derived instance has a finalizer.
    return objectAllocationInfoCollection
        .traverseInstantiatedSubtypes(
            type,
            clazz -> {
              if (objectAllocationInfoCollection.isInterfaceWithUnknownSubtypeHierarchy(clazz)) {
                return TraversalContinuation.doBreak();
              } else {
                SingleResolutionResult resolution =
                    appView
                        .appInfo()
                        .resolveMethodOnLegacy(
                            clazz, appView.dexItemFactory().objectMembers.finalize)
                        .asSingleResolution();
                if (resolution != null && resolution.getResolvedHolder().isProgramClass()) {
                  return TraversalContinuation.doBreak();
                }
              }
              return TraversalContinuation.doContinue();
            },
            lambda -> {
              // Lambda classes do not have finalizers.
              return TraversalContinuation.doContinue();
            },
            appView.appInfo())
        .isBreak();
  }
}
