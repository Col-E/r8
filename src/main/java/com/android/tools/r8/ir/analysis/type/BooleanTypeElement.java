// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

public class BooleanTypeElement extends SinglePrimitiveTypeElement {

  private static final BooleanTypeElement INSTANCE = new BooleanTypeElement();

  static BooleanTypeElement getInstance() {
    return INSTANCE;
  }

  @Override
  public String getDescriptor() {
    return "Z";
  }

  @Override
  public String getTypeName() {
    return "boolean";
  }

  @Override
  public boolean isBoolean() {
    return true;
  }

  @Override
  public String toString() {
    return "BOOLEAN";
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
