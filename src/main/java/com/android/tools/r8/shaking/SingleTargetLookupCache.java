// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SingleTargetLookupCache {

  private Map<DexType, Map<DexMethod, DexEncodedMethod>> cache = new ConcurrentHashMap<>();

  @SuppressWarnings("ReferenceEquality")
  public void addToCache(DexType refinedReceiverType, DexMethod method, DexEncodedMethod target) {
    assert target != DexEncodedMethod.SENTINEL;
    Map<DexMethod, DexEncodedMethod> methodCache =
        cache.computeIfAbsent(refinedReceiverType, ignored -> new ConcurrentHashMap<>());
    target = target == null ? DexEncodedMethod.SENTINEL : target;
    assert methodCache.getOrDefault(method, target) == target;
    methodCache.putIfAbsent(method, target);
  }

  public void removeInstantiatedType(DexType instantiatedType, AppInfoWithLiveness appInfo) {
    // Remove all types in the instantiated hierarchy related to this type.
    cache.remove(instantiatedType);
    Set<DexType> seen = Sets.newIdentityHashSet();
    appInfo.forEachInstantiatedSubType(
        instantiatedType,
        instance ->
            appInfo.traverseSuperTypes(
                instance,
                (superType, subclass, ignore) -> {
                  if (seen.add(superType)) {
                    cache.remove(superType);
                    return TraversalContinuation.doContinue();
                  } else {
                    return TraversalContinuation.doBreak();
                  }
                }),
        lambda -> {
          assert false;
        });
  }

  @SuppressWarnings("ReferenceEquality")
  public DexEncodedMethod getCachedItem(DexType receiverType, DexMethod method) {
    Map<DexMethod, DexEncodedMethod> cachedMethods = cache.get(receiverType);
    if (cachedMethods == null) {
      return null;
    }
    DexEncodedMethod target = cachedMethods.get(method);
    return target == DexEncodedMethod.SENTINEL ? null : target;
  }

  public boolean hasCachedItem(DexType receiverType, DexMethod method) {
    Map<DexMethod, DexEncodedMethod> cachedMethods = cache.get(receiverType);
    if (cachedMethods == null) {
      return false;
    }
    return cachedMethods.containsKey(method);
  }

  public void clear() {
    cache = new ConcurrentHashMap<>();
  }
}
