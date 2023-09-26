// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class DexMethodSignatureMap<T> implements Map<DexMethodSignature, T> {

  private final Map<DexMethodSignature, T> backing;

  DexMethodSignatureMap(Map<DexMethodSignature, T> backing) {
    this.backing = backing;
  }

  public static <T> DexMethodSignatureMap<T> create() {
    return new DexMethodSignatureMap<>(new HashMap<>());
  }

  public static <T> DexMethodSignatureMap<T> create(DexMethodSignatureMap<T> map) {
    return new DexMethodSignatureMap<>(new HashMap<>(map.backing));
  }

  public static <T> DexMethodSignatureMap<T> createLinked() {
    return new DexMethodSignatureMap<>(new LinkedHashMap<>());
  }

  public static <T> DexMethodSignatureMap<T> empty() {
    return new DexMethodSignatureMap<>(Collections.emptyMap());
  }

  @Override
  public T put(DexMethodSignature signature, T value) {
    return backing.put(signature, value);
  }

  public T put(DexMethod method, T value) {
    return put(method.getSignature(), value);
  }

  public T put(DexEncodedMethod method, T value) {
    return put(method.getReference(), value);
  }

  @Override
  public void clear() {
    backing.clear();
  }

  @Override
  public Set<DexMethodSignature> keySet() {
    return backing.keySet();
  }

  @Override
  public Collection<T> values() {
    return backing.values();
  }

  @Override
  public Set<Entry<DexMethodSignature, T>> entrySet() {
    return backing.entrySet();
  }

  @Override
  public T getOrDefault(Object key, T defaultValue) {
    return backing.getOrDefault(key, defaultValue);
  }

  public T getOrDefault(DexEncodedMethod method, T defaultValue) {
    return getOrDefault(method.getSignature(), defaultValue);
  }

  @Override
  public void forEach(BiConsumer<? super DexMethodSignature, ? super T> action) {
    backing.forEach(action);
  }

  @Override
  public void replaceAll(BiFunction<? super DexMethodSignature, ? super T, ? extends T> function) {
    backing.replaceAll(function);
  }

  @Override
  public T putIfAbsent(DexMethodSignature key, T value) {
    return backing.putIfAbsent(key, value);
  }

  @Override
  public boolean remove(Object key, Object value) {
    return backing.remove(key, value);
  }

  @Override
  public boolean replace(DexMethodSignature key, T oldValue, T newValue) {
    return backing.replace(key, oldValue, newValue);
  }

  @Override
  public T replace(DexMethodSignature key, T value) {
    return backing.replace(key, value);
  }

  public T computeIfAbsent(
      DexClassAndMethod key, Function<? super DexMethodSignature, ? extends T> mappingFunction) {
    return computeIfAbsent(key.getMethodSignature(), mappingFunction);
  }

  @Override
  public T computeIfAbsent(
      DexMethodSignature key, Function<? super DexMethodSignature, ? extends T> mappingFunction) {
    return backing.computeIfAbsent(key, mappingFunction);
  }

  @Override
  public T computeIfPresent(
      DexMethodSignature key,
      BiFunction<? super DexMethodSignature, ? super T, ? extends T> remappingFunction) {
    return backing.computeIfPresent(key, remappingFunction);
  }

  @Override
  public T compute(
      DexMethodSignature key,
      BiFunction<? super DexMethodSignature, ? super T, ? extends T> remappingFunction) {
    return backing.compute(key, remappingFunction);
  }

  @Override
  public T merge(
      DexMethodSignature key,
      T value,
      BiFunction<? super T, ? super T, ? extends T> remappingFunction) {
    return backing.merge(key, value, remappingFunction);
  }

  public T merge(
      DexMethod method, T value, BiFunction<? super T, ? super T, ? extends T> remappingFunction) {
    return merge(method.getSignature(), value, remappingFunction);
  }

  public T merge(
      DexEncodedMethod method,
      T value,
      BiFunction<? super T, ? super T, ? extends T> remappingFunction) {
    return merge(method.getReference(), value, remappingFunction);
  }

  @Override
  public boolean containsKey(Object o) {
    return backing.containsKey(o);
  }

  @Override
  public boolean containsValue(Object value) {
    return backing.containsValue(value);
  }

  @Override
  public T get(Object key) {
    return backing.get(key);
  }

  public T get(DexEncodedMethod method) {
    return get(method.getSignature());
  }

  public boolean containsKey(DexMethodSignature signature) {
    return backing.containsKey(signature);
  }

  public boolean containsAnyKeyOf(Iterable<DexMethodSignature> signatures) {
    for (DexMethodSignature signature : signatures) {
      if (containsKey(signature)) {
        return true;
      }
    }
    return false;
  }

  public DexMethodSignatureSet intersectionWithKeys(Iterable<DexMethodSignature> signatures) {
    DexMethodSignatureSet result = DexMethodSignatureSet.create();
    for (DexMethodSignature signature : signatures) {
      if (containsKey(signature)) {
        result.add(signature);
      }
    }
    return result;
  }

  @Override
  public boolean isEmpty() {
    return backing.isEmpty();
  }

  @Override
  public T remove(Object o) {
    return backing.remove(o);
  }

  @Override
  public void putAll(Map<? extends DexMethodSignature, ? extends T> map) {
    map.forEach(this::put);
  }

  @SuppressWarnings("unchecked")
  public void putAllToIdentity(Collection<? extends DexMethodSignature> signatures) {
    for (DexMethodSignature signature : signatures) {
      put(signature, (T) signature);
    }
  }

  public T remove(DexMethodSignature signature) {
    return backing.remove(signature);
  }

  public T remove(DexEncodedMethod method) {
    return remove(method.getSignature());
  }

  @Override
  public int size() {
    return backing.size();
  }
}
