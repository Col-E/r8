// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.TypeReference;
import java.util.Objects;

@Keep
public abstract class RetracedField {

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

  public abstract RetracedClass getHolderClass();

  public abstract String getFieldName();

  public static final class KnownRetracedField extends RetracedField {

    private final RetracedClass classReference;
    private final FieldReference fieldReference;

    private KnownRetracedField(RetracedClass classReference, FieldReference fieldReference) {
      this.classReference = classReference;
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
      return classReference;
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
      assert !fieldReference.equals(that.fieldReference)
          || classReference.equals(that.classReference);
      return fieldReference.equals(that.fieldReference);
    }

    @Override
    public int hashCode() {
      return Objects.hash(classReference, fieldReference);
    }
  }

  public static final class UnknownRetracedField extends RetracedField {

    private final RetracedClass classReference;
    private final String name;

    private UnknownRetracedField(RetracedClass classReference, String name) {
      this.classReference = classReference;
      this.name = name;
    }

    @Override
    public RetracedClass getHolderClass() {
      return classReference;
    }

    @Override
    public String getFieldName() {
      return name;
    }
  }

  static RetracedField create(RetracedClass classReference, FieldReference fieldReference) {
    return new KnownRetracedField(classReference, fieldReference);
  }

  static RetracedField createUnknown(RetracedClass classReference, String name) {
    return new UnknownRetracedField(classReference, name);
  }
}
