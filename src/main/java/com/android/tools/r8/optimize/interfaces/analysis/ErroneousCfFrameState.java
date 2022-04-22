// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

/** An analysis state representing that the code does not type check. */
public class ErroneousCfFrameState extends CfFrameState {

  private static final ErroneousCfFrameState INSTANCE = new ErroneousCfFrameState();

  private ErroneousCfFrameState() {}

  static ErroneousCfFrameState getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
