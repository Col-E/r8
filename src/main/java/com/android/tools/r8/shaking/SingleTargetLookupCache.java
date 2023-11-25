// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SingleTargetLookupCache {

  private final Map<DexType, Map<DexMethod, DexClassAndMethod>> positiveCache =
      new ConcurrentHashMap<>();
  private final Map<DexType, Set<DexMethod>> negativeCache = new ConcurrentHashMap<>();

  public void addNoSingleTargetToCache(DexType refinedReceiverType, DexMethod method) {
    assert !hasPositiveCacheHit(refinedReceiverType, method);
    negativeCache
        .computeIfAbsent(refinedReceiverType, ignoreKey(ConcurrentHashMap::newKeySet))
        .add(method);
  }

  public DexClassAndMethod addToCache(
      DexType refinedReceiverType, DexMethod method, DexClassAndMethod target) {
    if (target == null) {
      addNoSingleTargetToCache(refinedReceiverType, method);
      return null;
    }
    assert !ObjectUtils.identical(target.getDefinition(), DexEncodedMethod.SENTINEL);
    assert !hasNegativeCacheHit(refinedReceiverType, method);
    assert !hasPositiveCacheHit(refinedReceiverType, method)
        || getPositiveCacheHit(refinedReceiverType, method).isStructurallyEqualTo(target);
    positiveCache
        .computeIfAbsent(refinedReceiverType, ignoreKey(ConcurrentHashMap::new))
        .put(method, target);
    return target;
  }

  public void removeInstantiatedType(DexType instantiatedType, AppInfoWithLiveness appInfo) {
    // Remove all types in the instantiated hierarchy related to this type.
    positiveCache.remove(instantiatedType);
    negativeCache.remove(instantiatedType);
    Set<DexType> seen = Sets.newIdentityHashSet();
    appInfo.forEachInstantiatedSubType(
        instantiatedType,
        instance ->
            appInfo.traverseSuperTypes(
                instance,
                (superType, subclass, ignore) -> {
                  if (seen.add(superType)) {
                    positiveCache.remove(superType);
                    negativeCache.remove(superType);
                    return TraversalContinuation.doContinue();
                  } else {
                    return TraversalContinuation.doBreak();
                  }
                }),
        lambda -> {
          assert false;
        });
  }

  public boolean hasPositiveCacheHit(DexType receiverType, DexMethod method) {
    return positiveCache.getOrDefault(receiverType, Collections.emptyMap()).containsKey(method);
  }

  public DexClassAndMethod getPositiveCacheHit(DexType receiverType, DexMethod method) {
    return positiveCache.getOrDefault(receiverType, Collections.emptyMap()).get(method);
  }

  public boolean hasNegativeCacheHit(DexType receiverType, DexMethod method) {
    return negativeCache.getOrDefault(receiverType, Collections.emptySet()).contains(method);
  }
}
