// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

/**
 * A reference to an item pattern.
 *
 * <p>A reference can either be a binding-reference to an item pattern or the item pattern itself.
 */
public abstract class KeepItemReference {

  public final boolean isClassItemReference() {
    return asClassItemReference() != null;
  }

  public final boolean isMemberItemReference() {
    return asMemberItemReference() != null;
  }

  public KeepClassItemReference asClassItemReference() {
    return null;
  }

  public KeepMemberItemReference asMemberItemReference() {
    return null;
  }

  // Helpers below.

  /* Returns true if the reference is a binding to a class or member. */
  public final boolean isBindingReference() {
    return asBindingReference() != null;
  }

  /* Returns true if the reference is an item pattern for a class or member. */
  public final boolean isItemPattern() {
    return asItemPattern() != null;
  }

  public final boolean isClassItemPattern() {
    return asClassItemPattern() != null;
  }

  public final boolean isMemberItemPattern() {
    return asMemberItemPattern() != null;
  }

  public KeepBindingReference asBindingReference() {
    return null;
  }

  public KeepItemPattern asItemPattern() {
    return null;
  }

  public KeepClassItemPattern asClassItemPattern() {
    return null;
  }

  public KeepMemberItemPattern asMemberItemPattern() {
    return null;
  }
}
