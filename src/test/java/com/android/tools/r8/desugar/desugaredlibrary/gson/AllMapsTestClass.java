// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.gson;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AllMapsTestClass {
  // Program class extending ConcurrentHashMap.
  static class NullableConcurrentHashMap<K, V> extends ConcurrentHashMap<K, V> {
    NullableConcurrentHashMap() {
      super();
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public V put(K key, V value) {
      if (key == null || value == null) {
        return null;
      }
      return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
      for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
        put(entry.getKey(), entry.getValue());
      }
    }
  }
  // Program class extending the library class HashMap, implementing Map.
  static class NullableHashMap<K, V> extends HashMap<K, V> {
    NullableHashMap() {
      super();
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public V put(K key, V value) {
      if (key == null || value == null) {
        return null;
      }
      return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
      for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
        put(entry.getKey(), entry.getValue());
      }
    }
  }
  // Program class implementing Map.
  static class NullableMap<K, V> implements Map<K, V> {
    private HashMap<K, V> map = new HashMap<>();

    NullableMap() {
      super();
    }

    @Override
    public int size() {
      return map.size();
    }

    @Override
    public boolean isEmpty() {
      return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
      return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
      return map.containsValue(value);
    }

    @Override
    public V get(Object key) {
      return map.get(key);
    }

    @Override
    public V put(K key, V value) {
      return map.put(key, value);
    }

    @Override
    public V remove(Object key) {
      return map.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
      for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
        put(entry.getKey(), entry.getValue());
      }
    }

    @Override
    public void clear() {
      map.clear();
    }

    @Override
    public Set<K> keySet() {
      return map.keySet();
    }

    @Override
    public Collection<V> values() {
      return map.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
      return map.entrySet();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof NullableMap)) return false;
      NullableMap<?, ?> that = (NullableMap<?, ?>) o;
      return map.equals(that.map);
    }

    @Override
    public int hashCode() {
      return Objects.hash(map);
    }
  }
  // Program class implementing ConcurrentMap.
  static class NullableConcurrentMap<K, V> implements ConcurrentMap<K, V> {
    private HashMap<K, V> map = new HashMap<>();

    NullableConcurrentMap() {
      super();
    }

    @Override
    public int size() {
      return map.size();
    }

    @Override
    public boolean isEmpty() {
      return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
      return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
      return map.containsValue(value);
    }

    @Override
    public V get(Object key) {
      return map.get(key);
    }

    @Override
    public V put(K key, V value) {
      return map.put(key, value);
    }

    @Override
    public V remove(Object key) {
      return map.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
      for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
        put(entry.getKey(), entry.getValue());
      }
    }

    @Override
    public void clear() {
      map.clear();
    }

    @Override
    public Set<K> keySet() {
      return map.keySet();
    }

    @Override
    public Collection<V> values() {
      return map.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
      return map.entrySet();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof NullableConcurrentMap)) return false;
      NullableConcurrentMap<?, ?> that = (NullableConcurrentMap<?, ?>) o;
      return map.equals(that.map);
    }

    @Override
    public int hashCode() {
      return Objects.hash(map);
    }

    @Override
    public V putIfAbsent(K key, V value) {
      return null;
    }

    @Override
    public boolean remove(Object key, Object value) {
      return false;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
      return false;
    }

    @Override
    public V replace(K key, V value) {
      return null;
    }
  }

  static class Data {
    final int id;
    final String name;

    Data(int id, String name) {
      this.id = id;
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Data)) return false;
      Data data = (Data) o;
      return id == data.id && name.equals(data.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, name);
    }

    @Override
    public String toString() {
      return "Data{" + "id=" + id + ", name='" + name + '\'' + '}';
    }
  }

  public static void main(String[] args) {
    Gson gson = new Gson();

    HashMap<Integer, Data> hashMap = new HashMap<>();
    NullableHashMap<Integer, Data> nullableHashMap = new NullableHashMap<>();
    NullableMap<Integer, Data> nullableMap = new NullableMap<>();
    NullableConcurrentMap<Integer, Data> nullableConcurrentMap = new NullableConcurrentMap<>();
    ConcurrentHashMap<Integer, Data> concurrentHashMap = new ConcurrentHashMap<>();
    NullableConcurrentHashMap<Integer, Data> nullableConcurrentHashMap =
        new NullableConcurrentHashMap<>();

    fillMap(hashMap);
    fillMap(nullableHashMap);
    fillMap(nullableMap);
    fillMap(nullableConcurrentMap);
    fillMap(concurrentHashMap);
    fillMap(nullableConcurrentHashMap);

    // Serialization.
    String hashMapJson = gson.toJson(hashMap);
    String nullableHashMapJson = gson.toJson(nullableHashMap);
    String nullableMapJson = gson.toJson(nullableMap);
    String nullableConcurrentMapJson = gson.toJson(nullableConcurrentMap);
    String concurrentHashMapJson = gson.toJson(concurrentHashMap);
    String nullableConcurrentHashMapJson = gson.toJson(nullableConcurrentHashMap);

    // Deserialization.
    Type hashMapType = new TypeToken<HashMap<Integer, Data>>() {}.getType();
    HashMap<Integer, Data> hashMapDeserialized = gson.fromJson(hashMapJson, hashMapType);
    Type nullableHashMapType = new TypeToken<NullableHashMap<Integer, Data>>() {}.getType();
    NullableHashMap<Integer, Data> nullableHashMapDeserialized =
        gson.fromJson(nullableHashMapJson, nullableHashMapType);
    Type nullableMapType = new TypeToken<NullableMap<Integer, Data>>() {}.getType();
    NullableMap<Integer, Data> nullableMapDeserialized =
        gson.fromJson(nullableMapJson, nullableMapType);
    Type nullableConcurrentMapType =
        new TypeToken<NullableConcurrentMap<Integer, Data>>() {}.getType();
    NullableConcurrentMap<Integer, Data> nullableConcurrentMapDeserialized =
        gson.fromJson(nullableConcurrentMapJson, nullableConcurrentMapType);
    Type concurrentHashMapType = new TypeToken<ConcurrentHashMap<Integer, Data>>() {}.getType();
    ConcurrentHashMap<Integer, Data> concurrentHashMapDeserialized =
        gson.fromJson(concurrentHashMapJson, concurrentHashMapType);
    Type nullableConcurrentHashMapType =
        new TypeToken<NullableConcurrentHashMap<Integer, Data>>() {}.getType();
    NullableConcurrentHashMap<Integer, Data> nullableConcurrentHashMapDeserialized =
        gson.fromJson(nullableConcurrentHashMapJson, nullableConcurrentHashMapType);

    // Printing.
    System.out.println(hashMap.getClass() == hashMapDeserialized.getClass());
    System.out.println(hashMap.equals(hashMapDeserialized));
    System.out.println(nullableHashMap.getClass() == nullableHashMapDeserialized.getClass());
    System.out.println(nullableHashMap.equals(nullableHashMapDeserialized));
    System.out.println(nullableMap.getClass() == nullableMapDeserialized.getClass());
    System.out.println(nullableMap.equals(nullableMapDeserialized));
    System.out.println(
        nullableConcurrentMap.getClass() == nullableConcurrentMapDeserialized.getClass());
    System.out.println(nullableConcurrentMap.equals(nullableConcurrentMapDeserialized));
    System.out.println(concurrentHashMap.getClass() == concurrentHashMapDeserialized.getClass());
    System.out.println(concurrentHashMap.equals(concurrentHashMapDeserialized));
    System.out.println(
        nullableConcurrentHashMap.getClass() == nullableConcurrentHashMapDeserialized.getClass());
    System.out.println(nullableConcurrentHashMap.equals(nullableConcurrentHashMapDeserialized));
  }

  public static void fillMap(Map<Integer, Data> map) {
    map.put(1, new Data(1, "a"));
    map.put(2, new Data(2, "b"));
    map.put(3, new Data(3, "c"));
  }
}
