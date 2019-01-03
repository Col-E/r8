// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

class FilteredTryCatchIterator<T extends TryCatchSubject> implements Iterator<T> {

  private final TryCatchIterator iterator;
  private final Predicate<TryCatchSubject> predicate;
  private TryCatchSubject pendingNext = null;

  FilteredTryCatchIterator(
      CodeInspector codeInspector,
      MethodSubject methodSubject,
      Predicate<TryCatchSubject> predicate) {
    this.iterator = codeInspector.createTryCatchIterator(methodSubject);
    this.predicate = predicate;
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
