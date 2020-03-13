// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SingleTargetLookupCache {

  private Map<DexType, Map<DexMethod, DexEncodedMethod>> cache = new ConcurrentHashMap<>();

  public void addToCache(DexType refinedReceiverType, DexMethod method, DexEncodedMethod target) {
    assert target != DexEncodedMethod.SENTINEL;
    Map<DexMethod, DexEncodedMethod> methodCache =
        cache.computeIfAbsent(refinedReceiverType, ignored -> new ConcurrentHashMap<>());
    target = target == null ? DexEncodedMethod.SENTINEL : target;
    assert methodCache.getOrDefault(method, target) == target;
    methodCache.putIfAbsent(method, target);
  }

  public void removeInstantiatedType(DexType instantiatedType, AppInfoWithLiveness appInfo) {
    // Remove all types in the hierarchy related to this type.
    cache.remove(instantiatedType);
    DexClass clazz = appInfo.definitionFor(instantiatedType);
    if (clazz == null) {
      return;
    }
    appInfo.forEachSuperType(clazz, (type, ignore) -> cache.remove(type));
    appInfo.subtypes(instantiatedType).forEach(cache::remove);
  }

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
