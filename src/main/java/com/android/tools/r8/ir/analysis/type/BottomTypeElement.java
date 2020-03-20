// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

public class BottomTypeElement extends TypeElement {
  private static final BottomTypeElement INSTANCE = new BottomTypeElement();

  @Override
  public Nullability nullability() {
    return Nullability.bottom();
  }

  static BottomTypeElement getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isBottom() {
    return true;
  }

  @Override
  public String toString() {
    return "BOTTOM (empty)";
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(INSTANCE);
  }
}
