// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;

public class DexClassAndField {

  private final DexClass holder;
  private final DexEncodedField field;

  DexClassAndField(DexClass holder, DexEncodedField field) {
    assert holder != null;
    assert field != null;
    assert holder.type == field.holder();
    assert holder.isProgramClass() == (this instanceof ProgramField);
    this.holder = holder;
    this.field = field;
  }

  public static DexClassAndField create(DexClass holder, DexEncodedField field) {
    if (holder.isProgramClass()) {
      return new ProgramField(holder.asProgramClass(), field);
    } else {
      return new DexClassAndField(holder, field);
    }
  }

  public DexClass getHolder() {
    return holder;
  }

  public DexType getHolderType() {
    return holder.type;
  }

  public DexEncodedField getDefinition() {
    return field;
  }

  public DexField getReference() {
    return field.field;
  }

  public Origin getOrigin() {
    return holder.origin;
  }

  public boolean isProgramField() {
    return false;
  }

  public ProgramField asProgramField() {
    return null;
  }

  public String toSourceString() {
    return getReference().toSourceString();
  }

  @Override
  public String toString() {
    return toSourceString();
  }

  @Override
  public boolean equals(Object object) {
    throw new Unreachable("Unsupported attempt at comparing Class and DexClassAndField");
  }

  @Override
  public int hashCode() {
    throw new Unreachable("Unsupported attempt at computing the hashcode of DexClassAndField");
  }
}
