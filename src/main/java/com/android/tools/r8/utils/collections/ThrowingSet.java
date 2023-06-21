// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.errors.Unreachable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class ThrowingSet<T> implements Set<T> {

  private static final ThrowingSet<?> INSTANCE = new ThrowingSet<>();

  private ThrowingSet() {}

  @SuppressWarnings("unchecked")
  public static <T> ThrowingSet<T> get() {
    return (ThrowingSet<T>) INSTANCE;
  }

  public static boolean isThrowingSet(Set<?> set) {
    return set == get();
  }

  @Override
  public boolean add(T t) {
    throw new Unreachable();
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    throw new Unreachable();
  }

  @Override
  public void clear() {
    throw new Unreachable();
  }

  @Override
  public boolean contains(Object o) {
    throw new Unreachable();
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    throw new Unreachable();
  }

  @Override
  public boolean isEmpty() {
    throw new Unreachable();
  }

  @Override
  public Iterator<T> iterator() {
    throw new Unreachable();
  }

  @Override
  public boolean remove(Object o) {
    throw new Unreachable();
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new Unreachable();
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new Unreachable();
  }

  @Override
  public int size() {
    throw new Unreachable();
  }

  @Override
  public Object[] toArray() {
    throw new Unreachable();
  }

  @Override
  public <T1> T1[] toArray(T1[] a) {
    throw new Unreachable();
  }
}
