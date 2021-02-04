// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

public class ClasspathField extends DexClassAndField
    implements ClasspathMember<DexEncodedField, DexField> {

  public ClasspathField(DexClasspathClass holder, DexEncodedField field) {
    super(holder, field);
  }

  @Override
  public boolean isClasspathField() {
    return true;
  }

  @Override
  public ClasspathField asClasspathField() {
    return this;
  }

  @Override
  public boolean isClasspathMember() {
    return true;
  }

  @Override
  public DexClasspathClass getHolder() {
    DexClass holder = super.getHolder();
    assert holder.isClasspathClass();
    return holder.asClasspathClass();
  }
}
