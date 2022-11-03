// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

// TODO: finish this.
public abstract class KeepMethodAccessPattern {

  public static KeepMethodAccessPattern any() {
    return Any.getInstance();
  }

  public abstract boolean isAny();

  private static class Any extends KeepMethodAccessPattern {

    private static Any INSTANCE = null;

    private static Any getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new Any();
      }
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
      return "*";
    }
  }
}
