// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.inspector.internal;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.inspector.FieldInspector;
import com.android.tools.r8.inspector.ValueInspector;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.Reference;
import java.util.Optional;

public class FieldInspectorImpl implements FieldInspector {
  private final ClassInspectorImpl parent;
  private final DexEncodedField field;
  private FieldReference reference = null;

  public FieldInspectorImpl(ClassInspectorImpl parent, DexEncodedField field) {
    this.parent = parent;
    this.field = field;
  }

  @Override
  public FieldReference getFieldReference() {
    if (reference == null) {
      reference =
          Reference.field(
              parent.getClassReference(),
              field.field.name.toString(),
              Reference.typeFromDescriptor(field.field.type.toDescriptorString()));
    }
    return reference;
  }

  @Override
  public boolean isStatic() {
    return field.accessFlags.isStatic();
  }

  @Override
  public boolean isFinal() {
    return field.accessFlags.isFinal();
  }

  @Override
  public Optional<ValueInspector> getInitialValue() {
    if (field.isStatic() && field.getStaticValue() != null) {
      return Optional.of(new ValueInspectorImpl(field.getStaticValue(), field.field.type));
    }
    return Optional.empty();
  }
}
