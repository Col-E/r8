// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dfs;

import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;

public class DFSStack<T> {

  private final Deque<T> stack;
  private final Set<T> stackSet;

  private DFSStack(Deque<T> stack, Set<T> stackSet) {
    this.stack = stack;
    this.stackSet = stackSet;
  }

  public static <T> DFSStack<T> createIdentityStack() {
    return new DFSStack<>(new ArrayDeque<>(), Sets.newIdentityHashSet());
  }

  public boolean contains(T item) {
    return stackSet.contains(item);
  }

  public Deque<T> getCycleStartingAt(T entry) {
    Deque<T> cycle = new ArrayDeque<>();
    do {
      assert !stack.isEmpty();
      cycle.addLast(stack.removeLast());
    } while (cycle.getLast() != entry);
    recoverStack(cycle);
    return cycle;
  }

  public void handle(DFSWorklistItem<T> item) {
    if (item.isNewlyVisited()) {
      push(item.getValue());
    } else {
      assert item.isFullyVisited();
      pop(item.getValue());
    }
  }

  public void pop(T expectedItem) {
    T popped = stack.removeLast();
    assert popped == expectedItem;
    boolean removed = stackSet.remove(popped);
    assert removed;
  }

  public void push(T item) {
    stack.addLast(item);
    boolean added = stackSet.add(item);
    assert added;
  }

  private void recoverStack(Deque<T> extractedCycle) {
    Iterator<T> descendingIt = extractedCycle.descendingIterator();
    while (descendingIt.hasNext()) {
      stack.addLast(descendingIt.next());
    }
  }
}
