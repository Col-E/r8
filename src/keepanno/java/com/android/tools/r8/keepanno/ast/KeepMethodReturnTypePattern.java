// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;


public abstract class KeepMethodReturnTypePattern {

  public static KeepMethodReturnTypePattern any() {
    return SomeType.ANY_TYPE_INSTANCE;
  }

  public static KeepMethodReturnTypePattern voidType() {
    return VoidType.getInstance();
  }

  public static KeepMethodReturnTypePattern fromType(KeepTypePattern typePattern) {
    return typePattern.isAny() ? any() : new SomeType(typePattern);
  }

  public boolean isAny() {
    return isType() && asType().isAny();
  }

  public boolean isVoid() {
    return false;
  }

  public boolean isType() {
    return asType() != null;
  }

  public KeepTypePattern asType() {
    return null;
  }

  private static class VoidType extends KeepMethodReturnTypePattern {
    private static final VoidType INSTANCE = new VoidType();

    public static VoidType getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isVoid() {
      return true;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return "void";
    }
  }

  private static class SomeType extends KeepMethodReturnTypePattern {

    private static final SomeType ANY_TYPE_INSTANCE = new SomeType(KeepTypePattern.any());

    private final KeepTypePattern typePattern;

    private SomeType(KeepTypePattern typePattern) {
      assert typePattern != null;
      this.typePattern = typePattern;
    }

    @Override
    public KeepTypePattern asType() {
      return typePattern;
    }

    @Override
    @SuppressWarnings("EqualsGetClass")
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SomeType someType = (SomeType) o;
      return typePattern.equals(someType.typePattern);
    }

    @Override
    public int hashCode() {
      return typePattern.hashCode();
    }

    @Override
    public String toString() {
      return typePattern.toString();
    }
  }
}
