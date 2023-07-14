// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class AffectedValues implements Set<Value> {

  private static final AffectedValues EMPTY = new AffectedValues(ImmutableSet.of());

  private final Set<Value> affectedValues;

  public AffectedValues() {
    this(Sets.newIdentityHashSet());
  }

  private AffectedValues(Set<Value> affectedValues) {
    this.affectedValues = affectedValues;
  }

  public static AffectedValues empty() {
    return EMPTY;
  }

  public void narrowingWithAssumeRemoval(AppView<?> appView, IRCode code) {
    if (hasNext()) {
      new TypeAnalysis(appView, code).narrowingWithAssumeRemoval(this);
    }
  }

  public void widening(AppView<?> appView, IRCode code) {
    if (hasNext()) {
      new TypeAnalysis(appView, code).widening(this);
    }
  }

  @Override
  public boolean add(Value value) {
    return affectedValues.add(value);
  }

  @Override
  public boolean addAll(Collection<? extends Value> c) {
    return affectedValues.addAll(c);
  }

  @Override
  public void clear() {
    affectedValues.clear();
  }

  @Override
  public boolean contains(Object o) {
    return affectedValues.contains(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return affectedValues.containsAll(c);
  }

  public boolean hasNext() {
    return !isEmpty();
  }

  @Override
  public boolean isEmpty() {
    return affectedValues.isEmpty();
  }

  @Override
  public Iterator<Value> iterator() {
    return affectedValues.iterator();
  }

  @Override
  public boolean remove(Object o) {
    return affectedValues.remove(o);
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    return affectedValues.removeAll(c);
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return affectedValues.retainAll(c);
  }

  @Override
  public int size() {
    return affectedValues.size();
  }

  @Override
  public Object[] toArray() {
    return affectedValues.toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    return affectedValues.toArray(a);
  }
}
