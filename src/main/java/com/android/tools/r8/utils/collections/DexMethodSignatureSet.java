// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

public class DexMethodSignatureSet implements Collection<DexMethodSignature> {

  private static final DexMethodSignatureSet EMPTY =
      new DexMethodSignatureSet(Collections.emptySet());

  private final Set<DexMethodSignature> backing;

  private DexMethodSignatureSet(Set<DexMethodSignature> backing) {
    this.backing = backing;
  }

  public static DexMethodSignatureSet create() {
    return new DexMethodSignatureSet(new HashSet<>());
  }

  public static DexMethodSignatureSet create(DexMethodSignatureSet collection) {
    return new DexMethodSignatureSet(new HashSet<>(collection.backing));
  }

  public static DexMethodSignatureSet createConcurrent() {
    return new DexMethodSignatureSet(SetUtils.newConcurrentHashSet());
  }

  public static DexMethodSignatureSet createLinked() {
    return new DexMethodSignatureSet(new LinkedHashSet<>());
  }

  public static DexMethodSignatureSet empty() {
    return EMPTY;
  }

  @Override
  public boolean add(DexMethodSignature signature) {
    return backing.add(signature);
  }

  public boolean add(DexMethod method) {
    return add(method.getSignature());
  }

  public boolean add(DexEncodedMethod method) {
    return add(method.getReference());
  }

  public boolean add(DexClassAndMethod method) {
    return add(method.getReference());
  }

  @Override
  public boolean addAll(Collection<? extends DexMethodSignature> collection) {
    return backing.addAll(collection);
  }

  public void addAllMethods(Iterable<DexEncodedMethod> methods) {
    methods.forEach(this::add);
  }

  public void addAll(DexMethodSignatureSet signatures) {
    addAll(signatures.backing);
  }

  public <T> void addAll(Iterable<T> elements, Function<T, Iterable<DexMethodSignature>> fn) {
    for (T element : elements) {
      Iterables.addAll(this, fn.apply(element));
    }
  }

  @Override
  public void clear() {
    backing.clear();
  }

  @Override
  public boolean contains(Object o) {
    return backing.contains(o);
  }

  public boolean contains(DexMethodSignature signature) {
    return backing.contains(signature);
  }

  public boolean contains(DexMethod method) {
    return contains(method.getSignature());
  }

  public boolean contains(DexEncodedMethod method) {
    return contains(method.getSignature());
  }

  public boolean contains(DexClassAndMethod method) {
    return contains(method.getMethodSignature());
  }

  @Override
  public boolean containsAll(Collection<?> collection) {
    return backing.containsAll(collection);
  }

  public boolean containsAnyOf(Iterable<DexMethodSignature> signatures) {
    for (DexMethodSignature signature : signatures) {
      if (contains(signature)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isEmpty() {
    return backing.isEmpty();
  }

  @Override
  public Iterator<DexMethodSignature> iterator() {
    return backing.iterator();
  }

  @Override
  public boolean remove(Object o) {
    return backing.remove(o);
  }

  public boolean remove(DexMethodSignature signature) {
    return backing.remove(signature);
  }

  public boolean remove(DexEncodedMethod method) {
    return remove(method.getSignature());
  }

  @Override
  public boolean removeAll(Collection<?> collection) {
    return backing.removeAll(collection);
  }

  public void removeAllMethods(Iterable<DexEncodedMethod> methods) {
    methods.forEach(this::remove);
  }

  @Override
  public boolean retainAll(Collection<?> collection) {
    return backing.retainAll(collection);
  }

  @Override
  public int size() {
    return backing.size();
  }

  @Override
  public Object[] toArray() {
    return backing.toArray();
  }

  @Override
  public <T> T[] toArray(T[] ts) {
    return backing.toArray(ts);
  }
}
