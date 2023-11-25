// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class WorkList<T> {

  private final Deque<T> workingList = new ArrayDeque<>();
  private final Set<T> seen;

  public static <T> WorkList<T> newEqualityWorkList() {
    return new WorkList<T>(EqualityTest.EQUALS);
  }

  public static <T> WorkList<T> newEqualityWorkList(T item) {
    WorkList<T> workList = new WorkList<>(EqualityTest.EQUALS);
    workList.addIfNotSeen(item);
    return workList;
  }

  public static <T> WorkList<T> newEqualityWorkList(Iterable<T> items) {
    WorkList<T> workList = new WorkList<>(EqualityTest.EQUALS);
    workList.addIfNotSeen(items);
    return workList;
  }

  public static <T> WorkList<T> newIdentityWorkList() {
    return new WorkList<>(EqualityTest.IDENTITY);
  }

  public static <T> WorkList<T> newIdentityWorkList(T item) {
    WorkList<T> workList = new WorkList<>(EqualityTest.IDENTITY);
    workList.addIfNotSeen(item);
    return workList;
  }

  public static <T> WorkList<T> newIdentityWorkList(T item, Set<T> seen) {
    WorkList<T> workList = new WorkList<>(seen);
    workList.addIfNotSeen(item);
    return workList;
  }

  public static <T> WorkList<T> newIdentityWorkList(Iterable<T> items) {
    WorkList<T> workList = new WorkList<>(EqualityTest.IDENTITY);
    workList.addIfNotSeen(items);
    return workList;
  }

  public static <T> WorkList<T> newWorkList(Set<T> seen) {
    return new WorkList<>(seen);
  }

  private WorkList(EqualityTest equalityTest) {
    this(equalityTest == EqualityTest.EQUALS ? new HashSet<>() : Sets.newIdentityHashSet());
  }

  private WorkList(Set<T> seen) {
    this.seen = seen;
  }

  public void addIgnoringSeenSet(T item) {
    workingList.addLast(item);
  }

  public void addAllIgnoringSeenSet(Iterable<T> items) {
    items.forEach(workingList::addLast);
  }

  public void addIfNotSeen(Iterable<? extends T> items) {
    items.forEach(this::addIfNotSeen);
  }

  public void addIfNotSeen(T[] items) {
    for (T item : items) {
      addIfNotSeen(item);
    }
  }

  public boolean addIfNotSeen(T item) {
    if (seen.add(item)) {
      workingList.addLast(item);
      return true;
    }
    return false;
  }

  public boolean addFirstIfNotSeen(T item) {
    if (seen.add(item)) {
      workingList.addFirst(item);
      return true;
    }
    return false;
  }

  public WorkList<T> process(Consumer<T> consumer) {
    return process((item, ignored) -> consumer.accept(item));
  }

  public WorkList<T> process(BiConsumer<T, WorkList<T>> consumer) {
    while (hasNext()) {
      consumer.accept(next(), this);
    }
    return this;
  }

  public <TB, TC> TraversalContinuation<TB, TC> run(Function<T, TraversalContinuation<TB, TC>> fn) {
    return run((item, ignored) -> fn.apply(item));
  }

  public <TB, TC> TraversalContinuation<TB, TC> run(
      BiFunction<T, WorkList<T>, TraversalContinuation<TB, TC>> fn) {
    while (hasNext()) {
      TraversalContinuation<TB, TC> result = fn.apply(next(), this);
      if (result.shouldBreak()) {
        return result;
      }
    }
    return TraversalContinuation.doContinue();
  }

  public void addFirstIgnoringSeenSet(T item) {
    workingList.addFirst(item);
  }

  public boolean hasNext() {
    return !workingList.isEmpty();
  }

  public boolean isEmpty() {
    return !hasNext();
  }

  public boolean isSeen(T item) {
    return seen.contains(item);
  }

  public void markAsSeen(T item) {
    seen.add(item);
  }

  public void markAsSeen(Iterable<T> items) {
    items.forEach(this::markAsSeen);
  }

  public T next() {
    assert hasNext();
    return workingList.removeFirst();
  }

  public T removeSeen() {
    T next = next();
    seen.remove(next);
    return next;
  }

  public Set<T> getSeenSet() {
    return SetUtils.unmodifiableForTesting(seen);
  }

  public Set<T> getMutableSeenSet() {
    return seen;
  }

  public enum EqualityTest {
    EQUALS,
    IDENTITY
  }
}
