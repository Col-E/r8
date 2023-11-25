// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;

public abstract class KeepBindingReference {

  public static KeepClassBindingReference forClass(KeepBindingSymbol name) {
    return new KeepClassBindingReference(name);
  }

  public static KeepMemberBindingReference forMember(KeepBindingSymbol name) {
    return new KeepMemberBindingReference(name);
  }

  public static KeepBindingReference forItem(KeepBindingSymbol name, KeepItemPattern item) {
    return item.isClassItemPattern() ? forClass(name) : forMember(name);
  }

  private final KeepBindingSymbol name;

  KeepBindingReference(KeepBindingSymbol name) {
    this.name = name;
  }

  public abstract KeepItemReference toItemReference();

  public KeepBindingSymbol getName() {
    return name;
  }

  public final boolean isClassType() {
    return asClassBindingReference() != null;
  }

  public final boolean isMemberType() {
    return asMemberBindingReference() != null;
  }

  public KeepClassBindingReference asClassBindingReference() {
    return null;
  }

  public KeepMemberBindingReference asMemberBindingReference() {
    return null;
  }

  @Override
  public String toString() {
    return name.toString();
  }
}
