// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexMember;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class OrderedMergingIterator<S extends DexEncodedMember<S, T>, T extends DexMember<S, T>>
    implements Iterator<S> {

  private final List<S> one;
  private final List<S> other;
  private int oneIndex = 0;
  private int otherIndex = 0;

  public OrderedMergingIterator(List<S> one, List<S> other) {
    this.one = one;
    this.other = other;
  }

  private S getNextChecked(List<S> list, int position) {
    if (position >= list.size()) {
      throw new NoSuchElementException();
    }
    return list.get(position);
  }

  @Override
  public boolean hasNext() {
    return oneIndex < one.size() || otherIndex < other.size();
  }

  @Override
  public S next() {
    if (oneIndex >= one.size()) {
      return getNextChecked(other, otherIndex++);
    }
    if (otherIndex >= other.size()) {
      return getNextChecked(one, oneIndex++);
    }
    int comparison = one.get(oneIndex).toReference().compareTo(other.get(otherIndex).toReference());
    if (comparison < 0) {
      return one.get(oneIndex++);
    }
    if (comparison == 0) {
      throw new InternalCompilerError("Source arrays are not disjoint.");
    }
    return other.get(otherIndex++);
  }
}
