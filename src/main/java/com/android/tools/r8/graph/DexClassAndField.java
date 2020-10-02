// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

public class DexClassAndField extends DexClassAndMember<DexEncodedField, DexField> {

  DexClassAndField(DexClass holder, DexEncodedField field) {
    super(holder, field);
    assert holder.isProgramClass() == (this instanceof ProgramField);
  }

  public static DexClassAndField create(DexClass holder, DexEncodedField field) {
    if (holder.isProgramClass()) {
      return new ProgramField(holder.asProgramClass(), field);
    } else {
      return new DexClassAndField(holder, field);
    }
  }

  @Override
  public FieldAccessFlags getAccessFlags() {
    return getDefinition().getAccessFlags();
  }

  public boolean isProgramField() {
    return false;
  }

  public ProgramField asProgramField() {
    return null;
  }
}
