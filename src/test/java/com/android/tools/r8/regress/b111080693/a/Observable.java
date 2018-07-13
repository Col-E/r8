// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b111080693.a;

import java.util.ArrayList;

public abstract class Observable<T> {
  /**
   * The list of observers.  An observer can be in the list at most
   * once and will never be null.
   */
  protected final ArrayList<T> mObservers = new ArrayList<T>();
}
