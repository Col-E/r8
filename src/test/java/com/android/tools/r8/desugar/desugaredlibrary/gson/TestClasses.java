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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestClasses {

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
    private Map<K, V> map = new HashMap<>();

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

    @Nullable
    @Override
    public V put(K key, V value) {
      return map.put(key, value);
    }

    @Override
    public V remove(Object key) {
      return map.remove(key);
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> m) {
      for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
        put(entry.getKey(), entry.getValue());
      }
    }

    @Override
    public void clear() {
      map.clear();
    }

    @NotNull
    @Override
    public Set<K> keySet() {
      return map.keySet();
    }

    @NotNull
    @Override
    public Collection<V> values() {
      return map.values();
    }

    @NotNull
    @Override
    public Set<Entry<K, V>> entrySet() {
      return map.entrySet();
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

  static class TestClass {

    public static void main(String[] args) {
      Gson gson = new Gson();
      HashMap<Integer, Data> hashMap = new HashMap<>();
      NullableHashMap<Integer, Data> nullableHashMap = new NullableHashMap<>();
      NullableMap<Integer, Data> nullableMap = new NullableMap<>();
      ConcurrentHashMap<Integer, Data> concurrentHashMap = new ConcurrentHashMap<>();
      NullableConcurrentHashMap<Integer, Data> nullableConcurrentHashMap =
          new NullableConcurrentHashMap<>();

      fillMap(hashMap);
      fillMap(nullableHashMap);
      fillMap(nullableMap);
      fillMap(concurrentHashMap);
      fillMap(nullableConcurrentHashMap);

      String hashMapJson = gson.toJson(hashMap);
      String nullableHashMapJson = gson.toJson(nullableHashMap);
      String nullableMapJson = gson.toJson(nullableMap);
      String concurrentHashMapJson = gson.toJson(concurrentHashMap);
      String nullableConcurrentHashMapJson = gson.toJson(nullableConcurrentHashMap);

      Type hashMapType = new TypeToken<HashMap<Integer, Data>>() {}.getType();
      HashMap<Integer, Data> hashMapDeserialized = gson.fromJson(hashMapJson, hashMapType);
      Type nullableHashMapType = new TypeToken<HashMap<Integer, Data>>() {}.getType();
      HashMap<Integer, Data> nullableHashMapDeserialized =
          gson.fromJson(nullableHashMapJson, nullableHashMapType);
      Type nullableMapType = new TypeToken<HashMap<Integer, Data>>() {}.getType();
      HashMap<Integer, Data> nullableMapDeserialized =
          gson.fromJson(nullableMapJson, nullableMapType);
      Type concurrentHashMapType = new TypeToken<ConcurrentHashMap<Integer, Data>>() {}.getType();
      ConcurrentHashMap<Integer, Data> concurrentHashMapDeserialized =
          gson.fromJson(concurrentHashMapJson, concurrentHashMapType);
      Type nullableConcurrentHashMapType =
          new TypeToken<NullableConcurrentHashMap<Integer, Data>>() {}.getType();
      NullableConcurrentHashMap<Integer, Data> nullableConcurrentHashMapDeserialized =
          gson.fromJson(nullableConcurrentHashMapJson, nullableConcurrentHashMapType);

      System.out.println(hashMap.equals(hashMapDeserialized));
      System.out.println(nullableHashMap.equals(nullableHashMapDeserialized));
      System.out.println(nullableMap.equals(nullableMapDeserialized));
      System.out.println(concurrentHashMap.equals(concurrentHashMapDeserialized));
      System.out.println(nullableConcurrentHashMap.equals(nullableConcurrentHashMapDeserialized));
    }

    public static void fillMap(Map<Integer, Data> map) {
      map.put(1, new Data(1, "a"));
      map.put(2, new Data(2, "b"));
      map.put(3, new Data(3, "c"));
    }
  }
}
