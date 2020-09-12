// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.errors.Unreachable;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.function.Predicate;

@SuppressWarnings("NullableProblems")
public class ImmutableDeque<T> extends ArrayDeque<T> {

  private boolean isClosed = false;

  private ImmutableDeque(Collection<T> items) {
    super(items);
  }

  private void close() {
    isClosed = true;
  }

  @Override
  public void push(T t) {
    throw new Unreachable("Modification not allowed on immutable structure");
  }

  @Override
  public T pop() {
    throw new Unreachable("Modification not allowed on immutable structure");
  }

  @Override
  public void addFirst(T t) {
    throw new Unreachable("Modification not allowed on immutable structure");
  }

  @Override
  public void addLast(T t) {
    if (isClosed) {
      throw new Unreachable("Modification not allowed on immutable structure");
    } else {
      super.addLast(t);
    }
  }

  @Override
  public boolean removeFirstOccurrence(Object o) {
    throw new Unreachable("Modification not allowed on immutable structure");
  }

  @Override
  public boolean remove(Object o) {
    throw new Unreachable("Modification not allowed on immutable structure");
  }

  @Override
  public T removeFirst() {
    throw new Unreachable("Modification not allowed on immutable structure");
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new Unreachable("Modification not allowed on immutable structure");
  }

  @Override
  public boolean removeIf(Predicate<? super T> filter) {
    throw new Unreachable("Modification not allowed on immutable structure");
  }

  @Override
  public boolean removeLastOccurrence(Object o) {
    throw new Unreachable("Modification not allowed on immutable structure");
  }

  @Override
  public T removeLast() {
    throw new Unreachable("Modification not allowed on immutable structure");
  }

  @Override
  public T remove() {
    throw new Unreachable("Modification not allowed on immutable structure");
  }

  @Override
  public boolean add(T t) {
    if (isClosed) {
      throw new Unreachable("Modification not allowed on immutable structure");
    } else {
      return super.add(t);
    }
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    if (isClosed) {
      throw new Unreachable("Modification not allowed on immutable structure");
    } else {
      return super.addAll(c);
    }
  }

  @SafeVarargs
  public static <T> Deque<T> of(T... items) {
    ImmutableDeque<T> deque = new ImmutableDeque<>(Arrays.asList(items));
    deque.close();
    return deque;
  }
}
