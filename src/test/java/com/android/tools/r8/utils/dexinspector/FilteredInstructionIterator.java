// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

class FilteredInstructionIterator<T extends InstructionSubject> implements Iterator<T> {

  private DexInspector dexInspector;
  private final InstructionIterator iterator;
  private final Predicate<InstructionSubject> predicate;
  private InstructionSubject pendingNext = null;

  FilteredInstructionIterator(
      DexInspector dexInspector, MethodSubject method, Predicate<InstructionSubject> predicate) {
    this.dexInspector = dexInspector;
    this.iterator = dexInspector.createInstructionIterator(method);
    this.predicate = predicate;
    hasNext();
  }

  @Override
  public boolean hasNext() {
    if (pendingNext == null) {
      while (iterator.hasNext()) {
        pendingNext = iterator.next();
        if (predicate.test(pendingNext)) {
          break;
        }
        pendingNext = null;
      }
    }
    return pendingNext != null;
  }

  @Override
  public T next() {
    hasNext();
    if (pendingNext == null) {
      throw new NoSuchElementException();
    }
    // We cannot tell if the provided predicate will only match instruction subjects of type T.
    @SuppressWarnings("unchecked")
    T result = (T) pendingNext;
    pendingNext = null;
    return result;
  }
}
