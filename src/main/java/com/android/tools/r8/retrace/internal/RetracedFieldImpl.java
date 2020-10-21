// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetracedField;
import java.util.Objects;

@Keep
public abstract class RetracedFieldImpl implements RetracedField {

  private RetracedFieldImpl() {}

  @Override
  public boolean isUnknown() {
    return true;
  }

  @Override
  public final boolean isKnown() {
    return !isUnknown();
  }

  @Override
  public KnownRetracedFieldImpl asKnown() {
    return null;
  }

  public static final class KnownRetracedFieldImpl extends RetracedFieldImpl
      implements KnownRetracedField {

    private final FieldReference fieldReference;

    private KnownRetracedFieldImpl(FieldReference fieldReference) {
      this.fieldReference = fieldReference;
    }

    @Override
    public boolean isUnknown() {
      return false;
    }

    @Override
    public KnownRetracedFieldImpl asKnown() {
      return this;
    }

    @Override
    public RetracedClassImpl getHolderClass() {
      return RetracedClassImpl.create(fieldReference.getHolderClass());
    }

    @Override
    public String getFieldName() {
      return fieldReference.getFieldName();
    }

    @Override
    public TypeReference getFieldType() {
      return fieldReference.getFieldType();
    }

    @Override
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
      KnownRetracedFieldImpl that = (KnownRetracedFieldImpl) o;
      return fieldReference.equals(that.fieldReference);
    }

    @Override
    public int hashCode() {
      return Objects.hash(fieldReference);
    }
  }

  public static final class UnknownRetracedField extends RetracedFieldImpl {

    private final FieldDefinition fieldDefinition;

    private UnknownRetracedField(FieldDefinition fieldDefinition) {
      this.fieldDefinition = fieldDefinition;
    }

    @Override
    public RetracedClassImpl getHolderClass() {
      return RetracedClassImpl.create(fieldDefinition.getHolderClass());
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

  static RetracedFieldImpl create(FieldReference fieldReference) {
    return new KnownRetracedFieldImpl(fieldReference);
  }

  static RetracedFieldImpl create(FieldDefinition fieldDefinition) {
    return new UnknownRetracedField(fieldDefinition);
  }
}
