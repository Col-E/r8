// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.MethodSignatureEquivalence;

// Per-class collection of method signatures.
//
// Example use cases:
// *) in publicizer,
//   to determine if a private method does not collide with methods in that class hierarchy.
// *) in vertical class merger,
//   before moving a default interface method to its subtype, check if it does not collide with one
//   in the given class hierarchy.
// *) in uninstantiated type optimizer,
//   to avoid signature collisions while discarding unused return type or parameters.
// *) in unused argument removal,
//   to avoid removing unused arguments from a virtual method if it is overriding another method or
//   being overridden by a method in a subtype, and to check that a virtual method after unused
//   argument removal does not collide with one in the existing class hierarchy.
// TODO(b/66369976): to determine if a certain method can be made `final`.
public class MethodPoolCollection extends MemberPoolCollection<DexMethod> {

  public MethodPoolCollection(AppView<? extends AppInfoWithSubtyping> appView) {
    super(appView, MethodSignatureEquivalence.get());
  }

  @Override
  Runnable computeMemberPoolForClass(DexClass clazz) {
    return () -> {
      MemberPool<DexMethod> methodPool =
          memberPools.computeIfAbsent(clazz, k -> new MemberPool<>(equivalence));
      clazz.forEachMethod(
          encodedMethod -> {
            // We will add private instance methods when we promote them.
            if (!encodedMethod.isPrivateMethod() || encodedMethod.isStatic()) {
              methodPool.seen(equivalence.wrap(encodedMethod.method));
            }
          });
      if (clazz.superType != null) {
        DexClass superClazz = appView.definitionFor(clazz.superType);
        if (superClazz != null) {
          MemberPool<DexMethod> superPool =
              memberPools.computeIfAbsent(superClazz, k -> new MemberPool<>(equivalence));
          superPool.linkSubtype(methodPool);
          methodPool.linkSupertype(superPool);
        }
      }
      if (clazz.isInterface()) {
        for (DexType subtype : appView.appInfo().allImmediateSubtypes(clazz.type)) {
          DexClass subClazz = appView.definitionFor(subtype);
          if (subClazz != null) {
            MemberPool<DexMethod> childPool =
                memberPools.computeIfAbsent(subClazz, k -> new MemberPool<>(equivalence));
            methodPool.linkSubtype(childPool);
            childPool.linkInterface(methodPool);
          }
        }
      }
    };
  }
}
