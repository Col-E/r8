// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.Reference;
import java.util.Objects;

/** Internal encoding of a field that allows for having either basic info or full info. */
abstract class FieldDefinition implements Definition {

  static FieldDefinition create(ClassReference obfuscatedReference, String fieldName) {
    return new BaseFieldDefinition(obfuscatedReference, fieldName);
  }

  public static FieldDefinition create(FieldReference field) {
    return new FullFieldDefinition(field);
  }

  abstract FieldDefinition substituteHolder(ClassReference newHolder);

  public boolean isFullFieldDefinition() {
    return false;
  }

  public FullFieldDefinition asFullFieldDefinition() {
    return null;
  }

  static class BaseFieldDefinition extends FieldDefinition {
    private final ClassReference classReference;
    private final String name;

    private BaseFieldDefinition(ClassReference classReference, String name) {
      this.classReference = classReference;
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public ClassReference getHolderClass() {
      return classReference;
    }

    @Override
    FieldDefinition substituteHolder(ClassReference newHolder) {
      return FieldDefinition.create(newHolder, name);
    }

    @Override
    @SuppressWarnings("EqualsGetClass")
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BaseFieldDefinition that = (BaseFieldDefinition) o;
      return classReference.equals(that.classReference) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(classReference, name);
    }
  }

  static class FullFieldDefinition extends FieldDefinition {

    private final FieldReference fieldReference;

    private FullFieldDefinition(FieldReference fieldReference) {
      this.fieldReference = fieldReference;
    }

    @Override
    public boolean isFullFieldDefinition() {
      return true;
    }

    @Override
    public FullFieldDefinition asFullFieldDefinition() {
      return this;
    }

    @Override
    public String getName() {
      return fieldReference.getFieldName();
    }

    @Override
    public ClassReference getHolderClass() {
      return fieldReference.getHolderClass();
    }

    @Override
    FieldDefinition substituteHolder(ClassReference newHolder) {
      return create(
          Reference.field(newHolder, fieldReference.getFieldName(), fieldReference.getFieldType()));
    }

    public FieldReference getFieldReference() {
      return fieldReference;
    }

    @Override
    @SuppressWarnings("EqualsGetClass")
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      FullFieldDefinition that = (FullFieldDefinition) o;
      return fieldReference.equals(that.fieldReference);
    }

    @Override
    public int hashCode() {
      return fieldReference.hashCode();
    }
  }
}
