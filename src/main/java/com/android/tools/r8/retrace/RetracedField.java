// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.TypeReference;
import java.util.Objects;

@Keep
public abstract class RetracedField implements RetracedClassMember {

  private RetracedField() {}

  public boolean isUnknown() {
    return true;
  }

  public final boolean isKnown() {
    return !isUnknown();
  }

  public KnownRetracedField asKnown() {
    return null;
  }

  public abstract String getFieldName();

  public static final class KnownRetracedField extends RetracedField {

    private final FieldReference fieldReference;

    private KnownRetracedField(FieldReference fieldReference) {
      this.fieldReference = fieldReference;
    }

    @Override
    public boolean isUnknown() {
      return false;
    }

    @Override
    public KnownRetracedField asKnown() {
      return this;
    }

    @Override
    public RetracedClass getHolderClass() {
      return RetracedClass.create(fieldReference.getHolderClass());
    }

    @Override
    public String getFieldName() {
      return fieldReference.getFieldName();
    }

    public TypeReference getFieldType() {
      return fieldReference.getFieldType();
    }

    public FieldReference getFieldReference() {
      return fieldReference;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      KnownRetracedField that = (KnownRetracedField) o;
      return fieldReference.equals(that.fieldReference);
    }

    @Override
    public int hashCode() {
      return Objects.hash(fieldReference);
    }
  }

  public static final class UnknownRetracedField extends RetracedField {

    private final FieldDefinition fieldDefinition;

    private UnknownRetracedField(FieldDefinition fieldDefinition) {
      this.fieldDefinition = fieldDefinition;
    }

    @Override
    public RetracedClass getHolderClass() {
      return RetracedClass.create(fieldDefinition.getHolderClass());
    }

    @Override
    public String getFieldName() {
      return fieldDefinition.getName();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      UnknownRetracedField that = (UnknownRetracedField) o;
      return fieldDefinition.equals(that.fieldDefinition);
    }

    @Override
    public int hashCode() {
      return Objects.hash(fieldDefinition);
    }
  }

  static RetracedField create(FieldReference fieldReference) {
    return new KnownRetracedField(fieldReference);
  }

  static RetracedField create(FieldDefinition fieldDefinition) {
    return new UnknownRetracedField(fieldDefinition);
  }
}
