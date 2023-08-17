// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

public class ShortTypeElement extends SinglePrimitiveTypeElement {

  private static final ShortTypeElement INSTANCE = new ShortTypeElement();

  static ShortTypeElement getInstance() {
    return INSTANCE;
  }

  @Override
  public String getDescriptor() {
    return "S";
  }

  @Override
  public String getTypeName() {
    return "short";
  }

  @Override
  public boolean isShort() {
    return true;
  }

  @Override
  public String toString() {
    return "SHORT";
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
