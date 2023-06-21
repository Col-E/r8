// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.accessmodification;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.optimize.utils.NonProgramMethodsCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.collections.DexMethodSignatureBiMap;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.google.common.collect.Sets;
import java.util.Set;

public class AccessModifierNamingState {

  // The set of private method signatures that have been publicized. These method signatures are
  // "blocked" to ensure that virtual methods with the same method signature are given a different
  // name.
  private final DexMethodSignatureSet blockedMethodSignatures = DexMethodSignatureSet.create();

  // Records which method signatures in the component have been mapped to. This uses a bidirectional
  // map to allow efficiently finding a fresh method signature in the component.
  private final DexMethodSignatureBiMap<DexMethodSignature> reservedMethodSignatures;

  private AccessModifierNamingState(
      DexMethodSignatureBiMap<DexMethodSignature> reservedMethodSignatures) {
    this.reservedMethodSignatures = reservedMethodSignatures;
  }

  static AccessModifierNamingState createInitialNamingState(
      AppView<AppInfoWithLiveness> appView,
      Set<DexProgramClass> stronglyConnectedComponent,
      NonProgramMethodsCollection nonProgramMethodsCollection) {
    DexMethodSignatureBiMap<DexMethodSignature> reservedSignatures =
        new DexMethodSignatureBiMap<>();
    Set<ClasspathOrLibraryClass> seenNonProgramClasses = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : stronglyConnectedComponent) {
      // Reserve the signatures that are pinned in this class.
      clazz.forEachProgramMethodMatching(
          method -> !method.isInstanceInitializer() && !method.getAccessFlags().isPrivate(),
          method -> {
            KeepMethodInfo keepInfo = appView.getKeepInfo(method);
            InternalOptions options = appView.options();
            if (!keepInfo.isOptimizationAllowed(options) || !keepInfo.isShrinkingAllowed(options)) {
              DexMethodSignature methodSignature = method.getMethodSignature();
              reservedSignatures.put(methodSignature, methodSignature);
            }
          });
      // Reserve the signatures in the library.
      clazz.forEachImmediateSuperClassMatching(
          appView,
          (supertype, superclass) ->
              superclass != null
                  && !superclass.isProgramClass()
                  && seenNonProgramClasses.add(superclass.asClasspathOrLibraryClass()),
          (supertype, superclass) ->
              reservedSignatures.putAllToIdentity(
                  nonProgramMethodsCollection.getOrComputeNonProgramMethods(
                      superclass.asClasspathOrLibraryClass())));
    }
    return new AccessModifierNamingState(reservedSignatures);
  }

  void addBlockedMethodSignature(DexMethodSignature signature) {
    blockedMethodSignatures.add(signature);
  }

  void addRenaming(DexMethodSignature signature, DexMethodSignature newSignature) {
    reservedMethodSignatures.put(signature, newSignature);
  }

  DexMethodSignature getReservedSignature(DexMethodSignature signature) {
    return reservedMethodSignatures.get(signature);
  }

  boolean isFree(DexMethodSignature signature) {
    return !blockedMethodSignatures.contains(signature)
        && !reservedMethodSignatures.containsValue(signature);
  }
}
