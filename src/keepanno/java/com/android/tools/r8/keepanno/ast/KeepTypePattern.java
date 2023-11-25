// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

public abstract class KeepTypePattern {

  public static KeepTypePattern any() {
    return Any.getInstance();
  }

  public static KeepTypePattern fromDescriptor(String typeDescriptor) {
    return new Some(typeDescriptor);
  }

  public boolean isAny() {
    return false;
  }

  public String getDescriptor() {
    return null;
  }

  private static class Some extends KeepTypePattern {

    private final String descriptor;

    private Some(String descriptor) {
      assert descriptor != null;
      this.descriptor = descriptor;
    }

    @Override
    public String getDescriptor() {
      return descriptor;
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
      Some some = (Some) o;
      return descriptor.equals(some.descriptor);
    }

    @Override
    public int hashCode() {
      return descriptor.hashCode();
    }

    @Override
    public String toString() {
      return descriptor;
    }
  }

  private static class Any extends KeepTypePattern {

    private static final Any INSTANCE = new Any();

    public static Any getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isAny() {
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
      return "<any>";
    }
  }
}
