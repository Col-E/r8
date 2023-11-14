// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;

public final class KeepMemberBindingReference extends KeepBindingReference {

  KeepMemberBindingReference(KeepBindingSymbol name) {
    super(name);
  }

  @Override
  public KeepMemberBindingReference asMemberBindingReference() {
    return this;
  }

  @Override
  public KeepItemReference toItemReference() {
    return KeepMemberItemReference.fromBindingReference(this);
  }

  @Override
  public String toString() {
    return "member-ref(" + super.toString() + ")";
  }
}
