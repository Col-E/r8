// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.google.common.base.Predicates;
import java.util.function.Predicate;

// Per-class collection of method signatures.
//
// Example use cases:
// *) in publicizer,
//   to determine if a private method does not collide with methods in that class hierarchy.
// *) in vertical class merger,
//   before moving a default interface method to its subtype, check if it does not collide with one
//   in the given class hierarchy.
public class MethodPoolCollection extends MemberPoolCollection<DexMethod> {

  private final Predicate<DexEncodedMethod> methodTester;

  public MethodPoolCollection(AppView<AppInfoWithLiveness> appView, SubtypingInfo subtypingInfo) {
    this(appView, subtypingInfo, Predicates.alwaysTrue());
  }

  public MethodPoolCollection(
      AppView<AppInfoWithLiveness> appView,
      SubtypingInfo subtypingInfo,
      Predicate<DexEncodedMethod> methodTester) {
    super(appView, MethodSignatureEquivalence.get(), subtypingInfo);
    this.methodTester = methodTester;
  }

  public static boolean excludesPrivateInstanceMethod(DexEncodedMethod method) {
    return !method.isPrivateMethod() || method.isStatic();
  }

  @Override
  Runnable computeMemberPoolForClass(DexClass clazz) {
    return () -> {
      MemberPool<DexMethod> methodPool =
          memberPools.computeIfAbsent(clazz, k -> new MemberPool<>(equivalence, k));
      clazz.forEachMethod(
          encodedMethod -> {
            if (methodTester.test(encodedMethod)) {
              methodPool.seen(equivalence.wrap(encodedMethod.getReference()));
            }
          });
      if (clazz.superType != null) {
        DexClass superClazz = appView.definitionFor(clazz.superType);
        if (superClazz != null) {
          MemberPool<DexMethod> superPool =
              memberPools.computeIfAbsent(
                  superClazz, k -> new MemberPool<>(equivalence, superClazz));
          superPool.linkSubtype(methodPool);
          methodPool.linkSupertype(superPool);
        }
      }
      if (clazz.isInterface()) {
        for (DexType subtype : subtypingInfo.allImmediateSubtypes(clazz.type)) {
          DexClass subClazz = appView.definitionFor(subtype);
          if (subClazz != null) {
            MemberPool<DexMethod> childPool =
                memberPools.computeIfAbsent(subClazz, k -> new MemberPool<>(equivalence, subClazz));
            methodPool.linkSubtype(childPool);
            childPool.linkInterface(methodPool);
          }
        }
      }
    };
  }
}
