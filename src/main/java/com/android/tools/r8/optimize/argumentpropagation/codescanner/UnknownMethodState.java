// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

// Use this when the nothing is known.
public class UnknownMethodState extends MethodState {

  private static final UnknownMethodState INSTANCE = new UnknownMethodState();

  private UnknownMethodState() {}

  public static UnknownMethodState get() {
    return INSTANCE;
  }

  @Override
  public boolean isUnknown() {
    return true;
  }
}
