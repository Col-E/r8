// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import java.util.Collection;

/**
 * A pattern for matching items in the program.
 *
 * <p>An item pattern can be any item, or it can describe a family of classes or a family of members
 * on a classes.
 *
 * <p>A pattern cannot describe both a class *and* a member of a class. Either it is a pattern on
 * classes or it is a pattern on members. The distinction is defined by having a "none" member
 * pattern.
 */
public abstract class KeepItemPattern {

  public static KeepItemPattern anyClass() {
    return KeepClassItemPattern.any();
  }

  public static KeepItemPattern anyMember() {
    return KeepMemberItemPattern.any();
  }

  public boolean isClassItemPattern() {
    return asClassItemPattern() != null;
  }

  public boolean isMemberItemPattern() {
    return asMemberItemPattern() != null;
  }

  public KeepClassItemPattern asClassItemPattern() {
    return null;
  }

  public KeepMemberItemPattern asMemberItemPattern() {
    return null;
  }

  public abstract Collection<KeepBindingReference> getBindingReferences();

  public abstract KeepItemReference toItemReference();
}

