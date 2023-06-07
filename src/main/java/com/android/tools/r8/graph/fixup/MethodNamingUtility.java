// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.fixup;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.collections.DexMethodSignatureBiMap;
import com.google.common.collect.BiMap;
import java.util.function.BiConsumer;

public class MethodNamingUtility {

  private final DexItemFactory factory;
  private final DexMethodSignatureBiMap<DexMethodSignature> inheritedSignatures;
  private final BiMap<DexMethod, DexMethod> localSignatures;

  public MethodNamingUtility(
      DexItemFactory factory,
      DexMethodSignatureBiMap<DexMethodSignature> inheritedSignatures,
      BiMap<DexMethod, DexMethod> localSignatures) {
    this.factory = factory;
    this.inheritedSignatures = inheritedSignatures;
    this.localSignatures = localSignatures;
  }

  public DexMethod nextUniqueMethod(
      DexEncodedMethod method, DexProto newProto, DexType initExtraType) {
    DexMethod reference = method.getReference();

    if (method.isClassInitializer()) {
      assert reference.getProto() == newProto;
      return reference;
    }

    if (method.isInstanceInitializer()) {
      assert initExtraType != null;
      return nextUniqueInitializer(reference, newProto, initExtraType);
    }

    if (method.isNonPrivateVirtualMethod()) {
      return nextUniqueVirtualMethod(reference, newProto);
    }

    return nextUniquePrivateOrStaticMethod(reference, newProto);
  }

  private DexMethod nextUniqueInitializer(
      DexMethod reference, DexProto newProto, DexType initExtraType) {
    assert !inheritedSignatures.containsKey(reference.getSignature());

    // 1) We check if the reference has already been reserved (pinning).
    DexMethod remapped = localSignatures.get(reference);
    if (remapped != null) {
      assert remapped.getProto() == newProto;
      return remapped.withHolder(reference.getHolderType(), factory);
    }

    // 2) We check for collision with already mapped methods.
    DexMethod newMethod = reference.withProto(newProto, factory);
    if (localSignatures.containsValue(newMethod)) {
      // This collides with something that has been renamed into this.
      newMethod =
          factory.createInstanceInitializerWithFreshProto(
              newMethod, initExtraType, tryMethod -> !localSignatures.containsValue(tryMethod));
    }

    // 3) Finally register the new method and return it.
    assert !localSignatures.containsValue(newMethod);
    localSignatures.put(reference, newMethod);
    return newMethod;
  }

  private DexMethod nextUniquePrivateOrStaticMethod(DexMethod reference, DexProto newProto) {
    return nextUniqueMethod(reference, newProto, localSignatures::put);
  }

  private DexMethod nextUniqueVirtualMethod(DexMethod reference, DexProto newProto) {
    return nextUniqueMethod(
        reference,
        newProto,
        (from, to) -> inheritedSignatures.put(from.getSignature(), to.getSignature()));
  }

  private boolean anyCollision(DexMethod method) {
    return localSignatures.containsValue(method)
        || inheritedSignatures.containsValue(method.getSignature());
  }

  private DexMethod nextUniqueMethod(
      DexMethod reference, DexProto newProto, BiConsumer<DexMethod, DexMethod> registration) {
    // 1) We check if the reference has already been reserved (pinning or override).
    DexMethodSignature remappedSignature = inheritedSignatures.get(reference.getSignature());
    if (remappedSignature != null) {
      assert remappedSignature.getProto() == newProto;
      return remappedSignature.withHolder(reference.getHolderType(), factory);
    }

    // 2) We check for collision with already mapped methods.
    DexMethod newMethod = reference.withProto(newProto, factory);
    if (anyCollision(newMethod)) {
      newMethod =
          factory.createFreshMethodNameWithoutHolder(
              newMethod.getName().toString(),
              newMethod.getProto(),
              newMethod.getHolderType(),
              tryMethod -> !anyCollision(tryMethod));
    }

    // 3) Finally register the new method and return it.
    assert !anyCollision(newMethod);
    registration.accept(reference, newMethod);
    return newMethod;
  }
}
