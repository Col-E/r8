// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;

public class TwoWord extends SingletonFrameType implements WideFrameType {

  static final TwoWord SINGLETON = new TwoWord();

  private TwoWord() {}

  @Override
  public boolean isTwoWord() {
    return true;
  }

  @Override
  public boolean isWide() {
    return true;
  }

  @Override
  public WideFrameType asWide() {
    return this;
  }

  @Override
  public int getWidth() {
    return 2;
  }

  @Override
  public WideFrameType join(WideFrameType frameType) {
    // The join of wide with one of {double, long, wide} is wide.
    return this;
  }

  @Override
  public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
    throw new Unreachable("Should only be used for verification");
  }

  @Override
  public String toString() {
    return "twoword";
  }
}
