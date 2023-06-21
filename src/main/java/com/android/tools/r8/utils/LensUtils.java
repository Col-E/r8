// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.lens.GraphLens;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntFunction;

public class LensUtils {

  /**
   * Helper to lens rewrite a Map.
   *
   * @param map The Map that needs to be rewritten. The map may be mutated by this method, thus the
   *     original unrewritten map should never be used after calling this method.
   * @param mapFactory Function that given a capacity returns a Map with the given capacity.
   * @param preprocessing Function that given a (key, value) pair returns some data that is passed
   *     to the rewriting of the key using {@param keyRewriter} and to the rewriting of the value
   *     using {@param valueRewriter}. This can be used if the key and value shares some common data
   *     that needs rewriting.
   * @param keyRewriter Function that rewrites a key.
   * @param valueRewriter Function that rewrites a value.
   * @param valueMerger Function that merges two values that have the same rewritten key.
   */
  public static <K, V, P> Map<K, V> mutableRewriteMap(
      Map<K, V> map,
      IntFunction<Map<K, V>> mapFactory,
      BiFunction<K, V, P> preprocessing,
      TriFunction<K, V, P, K> keyRewriter,
      TriFunction<K, V, P, V> valueRewriter,
      TriFunction<K, V, V, V> valueMerger) {
    Collection<K> elementsToRemove = null;
    Map<K, V> rewrittenMap = null;
    for (Entry<K, V> entry : map.entrySet()) {
      K key = entry.getKey();
      V value = entry.getValue();
      P data = preprocessing.apply(key, value);
      K rewrittenKey = keyRewriter.apply(key, value, data);

      // If the key is mapped to null then remove if from `map` unless there are other changes.
      if (rewrittenKey == null) {
        if (rewrittenMap == null) {
          if (elementsToRemove == null) {
            elementsToRemove = new ArrayList<>();
          }
          elementsToRemove.add(key);
        }
        continue;
      }

      V rewrittenValue = valueRewriter.apply(key, value, data);
      if (rewrittenKey == key && rewrittenValue == value) {
        if (rewrittenMap == null) {
          // Don't write this entry until we find the first change.
          continue;
        }
      } else {
        if (rewrittenMap == null) {
          rewrittenMap = mapFactory.apply(map.size());
          MapUtils.forEachUntilExclusive(map, rewrittenMap::put, key);
          if (elementsToRemove != null) {
            assert !elementsToRemove.isEmpty();
            rewrittenMap.keySet().removeAll(elementsToRemove);
            elementsToRemove = null;
          }
        }
      }
      V existingRewrittenValue = rewrittenMap.get(rewrittenKey);
      rewrittenMap.put(
          rewrittenKey,
          existingRewrittenValue != null
              ? valueMerger.apply(rewrittenKey, rewrittenValue, existingRewrittenValue)
              : rewrittenValue);
    }
    if (rewrittenMap != null) {
      assert elementsToRemove == null;
      return MapUtils.trimCapacityIfSizeLessThan(rewrittenMap, mapFactory, map.size());
    } else {
      if (elementsToRemove != null) {
        assert !elementsToRemove.isEmpty();
        map.keySet().removeAll(elementsToRemove);
        return MapUtils.trimCapacity(map, mapFactory);
      }
      return map;
    }
  }

  public static Set<DexEncodedMethod> rewrittenWithRenamedSignature(
      Set<DexEncodedMethod> methods, DexDefinitionSupplier definitions, GraphLens lens) {
    GraphLens appliedLens = GraphLens.getIdentityLens();
    Set<DexEncodedMethod> result = Sets.newIdentityHashSet();
    for (DexEncodedMethod method : methods) {
      result.add(method.rewrittenWithLens(lens, appliedLens, definitions));
    }
    return result;
  }

  public static <T extends DexReference> void rewriteAndApplyIfNotPrimitiveType(
      GraphLens graphLens, T reference, Consumer<T> rewrittenConsumer) {
    T rewrittenReference = graphLens.rewriteReference(reference);
    // Enum unboxing can change a class type to int which leads to errors going forward since
    // the root set should not have primitive types.
    if (rewrittenReference.isDexType() && rewrittenReference.asDexType().isPrimitiveType()) {
      return;
    }
    rewrittenConsumer.accept(rewrittenReference);
  }
}
