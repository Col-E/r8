// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;


public abstract class KeepMembersPattern {

  public static KeepMembersPattern none() {
    return None.getInstance();
  }

  public static KeepMembersPattern all() {
    return All.getInstance();
  }

  private static class All extends KeepMembersPattern {

    private static final All INSTANCE = new All();

    public static All getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isAll() {
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

  private static class None extends KeepMembersPattern {

    private static final None INSTANCE = new None();

    public static None getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isNone() {
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
      return "<none>";
    }
  }

  KeepMembersPattern() {}

  public boolean isAll() {
    return false;
  }

  public boolean isNone() {
    return false;
  }

  public final boolean isMethod() {
    return asMethod() != null;
  }

  public KeepMethodPattern asMethod() {
    return null;
  }
}
