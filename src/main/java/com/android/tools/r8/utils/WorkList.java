// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

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

  public Set<T> getSeenSet() {
    return Collections.unmodifiableSet(seen);
  }

  public Set<T> getMutableSeenSet() {
    return seen;
  }

  public enum EqualityTest {
    EQUALS,
    IDENTITY
  }
}
