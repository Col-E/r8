// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

public abstract class KeepMemberItemReference extends KeepItemReference {

  public static KeepMemberItemReference fromBindingReference(
      KeepMemberBindingReference bindingReference) {
    return new MemberBinding(bindingReference);
  }

  public static KeepMemberItemReference fromMemberItemPattern(KeepMemberItemPattern itemPattern) {
    return new MemberItem(itemPattern);
  }

  @Override
  public final KeepMemberItemReference asMemberItemReference() {
    return this;
  }

  private static final class MemberBinding extends KeepMemberItemReference {

    private final KeepMemberBindingReference bindingReference;

    private MemberBinding(KeepMemberBindingReference bindingReference) {
      this.bindingReference = bindingReference;
    }

    @Override
    public KeepBindingReference asBindingReference() {
      return bindingReference;
    }

    @Override
    public String toString() {
      return bindingReference.toString();
    }
  }

  private static final class MemberItem extends KeepMemberItemReference {

    private final KeepMemberItemPattern itemPattern;

    public MemberItem(KeepMemberItemPattern itemPattern) {
      this.itemPattern = itemPattern;
    }

    @Override
    public KeepItemPattern asItemPattern() {
      return itemPattern;
    }

    @Override
    public KeepMemberItemPattern asMemberItemPattern() {
      return itemPattern;
    }

    @Override
    public String toString() {
      return itemPattern.toString();
    }
  }
}
