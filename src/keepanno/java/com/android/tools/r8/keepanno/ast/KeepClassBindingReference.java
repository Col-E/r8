// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;

public final class KeepClassBindingReference extends KeepBindingReference {

  KeepClassBindingReference(KeepBindingSymbol name) {
    super(name);
  }

  @Override
  public KeepClassBindingReference asClassBindingReference() {
    return this;
  }

  public KeepClassItemReference toClassItemReference() {
    return KeepClassItemReference.fromBindingReference(this);
  }

  @Override
  public KeepItemReference toItemReference() {
    return toClassItemReference();
  }

  @Override
  public String toString() {
    return "class-ref(" + super.toString() + ")";
  }
}
