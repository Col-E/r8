// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

public abstract class KeepMemberPattern {

  public static KeepMemberPattern anyMember() {
    return KeepMemberAnyPattern.getInstance();
  }

  private static class KeepMemberAnyPattern extends KeepMemberPattern {
    private static KeepMemberAnyPattern INSTANCE = null;

    public static KeepMemberAnyPattern getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepMemberAnyPattern();
      }
      return INSTANCE;
    }

    @Override
    public boolean isAnyMember() {
      return true;
    }
  }

  public boolean isAnyMember() {
    return false;
  }

  abstract static class Builder<T extends Builder<T>> {

    public abstract T self();

    Builder() {}
  }
}
