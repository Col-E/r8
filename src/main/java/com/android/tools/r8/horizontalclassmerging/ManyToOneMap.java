// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

/**
 * This mapping class is used to track method mappings for horizontal class merging. Essentially it
 * is a bidirectional many to one map, but with support for having unidirectional mappings and with
 * support for remapping the values to new values using {@link ManyToOneMap#remap(BiMap, Function,
 * Function)}. It also supports generating an inverse mapping {@link ManyToOneInverseMap} that can
 * be used by the graph lens using {@link ManyToOneMap#inverse(Function)}. The inverse map is a
 * bidirectional one to one map with additional non-bidirectional representative entries.
 */
public class ManyToOneMap<K, V> {
  private final Map<K, V> forwardMap = new IdentityHashMap<>();
  private final Map<V, Set<K>> inverseMap = new IdentityHashMap<>();
  private final Map<V, K> representativeMap = new IdentityHashMap<>();

  public Map<K, V> getForwardMap() {
    return forwardMap;
  }

  public Set<K> lookupReverse(V to) {
    return inverseMap.get(to);
  }

  public V put(K from, V to) {
    return forwardMap.put(from, to);
  }

  public void putInverse(K from, V to) {
    inverseMap.computeIfAbsent(to, ignore -> new HashSet<>()).add(from);
  }

  public K setRepresentative(K from, V to) {
    putInverse(from, to);
    return representativeMap.put(to, from);
  }

  public ManyToOneInverseMap<K, V> inverse(Function<Set<K>, K> pickRepresentative) {
    BiMap<V, K> biMap = HashBiMap.create();
    Map<V, K> extraMap = new HashMap<>();
    for (Entry<V, Set<K>> entry : inverseMap.entrySet()) {
      K representative = representativeMap.get(entry.getKey());
      if (entry.getValue().size() == 1) {
        K singleton = entry.getValue().iterator().next();
        assert representative == null || singleton == representative;
        if (representative == null) {
          biMap.put(entry.getKey(), singleton);
        } else {
          extraMap.put(entry.getKey(), singleton);
        }
      } else {
        if (representative == null) {
          representative = pickRepresentative.apply(entry.getValue());
        } else {
          assert representative == entry.getKey() || entry.getValue().contains(representative);
        }
        extraMap.put(entry.getKey(), representative);
      }
    }

    return new ManyToOneInverseMap<>(biMap, extraMap);
  }

  public <NewV> ManyToOneMap<K, NewV> remap(
      BiMap<V, NewV> biMap, Function<V, NewV> notInBiMap, Function<V, K> notInForwardMap) {
    ManyToOneMap<K, NewV> newMap = new ManyToOneMap<>();

    // All entries that should be remapped and are already in the forward and/or inverse mappings
    // should only be remapped in the directions they are already mapped in.
    BiMap<V, NewV> biMapCopy = HashBiMap.create(biMap);
    for (Entry<V, Set<K>> entry : inverseMap.entrySet()) {
      NewV to = biMapCopy.remove(entry.getKey());
      if (to == null) {
        to = biMap.getOrDefault(entry.getKey(), notInBiMap.apply(entry.getKey()));
      }
      newMap.inverseMap.put(to, entry.getValue());
    }
    for (Entry<K, V> entry : forwardMap.entrySet()) {
      NewV newTo = biMapCopy.remove(entry.getValue());
      if (newTo == null) {
        newTo = biMap.getOrDefault(entry.getValue(), notInBiMap.apply(entry.getValue()));
      }
      newMap.forwardMap.put(entry.getKey(), newTo);
    }

    // All new entries should be mapped in both directions.
    for (Entry<V, NewV> entry : biMapCopy.entrySet()) {
      newMap.forwardMap.put(notInForwardMap.apply(entry.getKey()), entry.getValue());
      newMap
          .inverseMap
          .computeIfAbsent(entry.getValue(), ignore -> new HashSet<>())
          .add(notInForwardMap.apply(entry.getKey()));
    }

    // Representatives are always in the inverse mapping, so they should always be remapped as new
    // representatives.
    for (Entry<V, K> entry : representativeMap.entrySet()) {
      NewV newTo = biMap.get(entry.getKey());
      if (newTo == null) {
        newTo = notInBiMap.apply(entry.getKey());
      }
      newMap.representativeMap.put(newTo, entry.getValue());
    }

    return newMap;
  }
}
