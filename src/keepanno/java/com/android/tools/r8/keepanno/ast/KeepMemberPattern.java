// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

public abstract class KeepMemberPattern {

  public static KeepMemberPattern none() {
    return None.getInstance();
  }

  public static KeepMemberPattern allMembers() {
    return All.getInstance();
  }

  public static Builder memberBuilder() {
    return new Builder();
  }

  public static class Builder {
    private KeepMemberAccessPattern accessPattern = KeepMemberAccessPattern.anyMemberAccess();

    public Builder setAccessPattern(KeepMemberAccessPattern accessPattern) {
      this.accessPattern = accessPattern;
      return this;
    }

    public KeepMemberPattern build() {
      if (accessPattern.isAny()) {
        return allMembers();
      }
      return new Some(accessPattern);
    }
  }

  private static class Some extends KeepMemberPattern {
    private final KeepMemberAccessPattern accessPattern;

    public Some(KeepMemberAccessPattern accessPattern) {
      this.accessPattern = accessPattern;
    }

    @Override
    public KeepMemberAccessPattern getAccessPattern() {
      return accessPattern;
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
      return accessPattern.equals(some.accessPattern);
    }

    @Override
    public int hashCode() {
      return accessPattern.hashCode();
    }

    @Override
    public String toString() {
      return "Member{" + "access=" + accessPattern + '}';
    }
  }

  private static class All extends KeepMemberPattern {

    private static final All INSTANCE = new All();

    public static All getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isAllMembers() {
      return true;
    }

    @Override
    public KeepMemberAccessPattern getAccessPattern() {
      return KeepMemberAccessPattern.anyMemberAccess();
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

  private static class None extends KeepMemberPattern {

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

  KeepMemberPattern() {}

  public boolean isAllMembers() {
    return false;
  }

  public boolean isNone() {
    return false;
  }

  public final boolean isGeneralMember() {
    return !isNone() && !isMethod() && !isField();
  }

  public final boolean isMethod() {
    return asMethod() != null;
  }

  public KeepMethodPattern asMethod() {
    return null;
  }

  public final boolean isField() {
    return asField() != null;
  }

  public KeepFieldPattern asField() {
    return null;
  }

  public KeepMemberAccessPattern getAccessPattern() {
    throw new KeepEdgeException("Invalid access to member access pattern");
  }
}
