// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetracedFieldReference;
import java.util.Objects;

public abstract class RetracedFieldReferenceImpl implements RetracedFieldReference {

  private RetracedFieldReferenceImpl() {}

  @Override
  public boolean isUnknown() {
    return true;
  }

  @Override
  public final boolean isKnown() {
    return !isUnknown();
  }

  @Override
  public KnownRetracedFieldReferenceImpl asKnown() {
    return null;
  }

  public static final class KnownRetracedFieldReferenceImpl extends RetracedFieldReferenceImpl
      implements KnownRetracedFieldReference {

    private final FieldReference fieldReference;

    private KnownRetracedFieldReferenceImpl(FieldReference fieldReference) {
      this.fieldReference = fieldReference;
    }

    @Override
    public boolean isUnknown() {
      return false;
    }

    @Override
    public KnownRetracedFieldReferenceImpl asKnown() {
      return this;
    }

    @Override
    public RetracedClassReferenceImpl getHolderClass() {
      return RetracedClassReferenceImpl.create(fieldReference.getHolderClass(), true);
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
      KnownRetracedFieldReferenceImpl that = (KnownRetracedFieldReferenceImpl) o;
      return fieldReference.equals(that.fieldReference);
    }

    @Override
    public int hashCode() {
      return Objects.hash(fieldReference);
    }
  }

  public static final class UnknownRetracedFieldReferenceImpl extends RetracedFieldReferenceImpl {

    private final FieldDefinition fieldDefinition;

    private UnknownRetracedFieldReferenceImpl(FieldDefinition fieldDefinition) {
      this.fieldDefinition = fieldDefinition;
    }

    @Override
    public RetracedClassReferenceImpl getHolderClass() {
      return RetracedClassReferenceImpl.create(fieldDefinition.getHolderClass(), false);
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
      UnknownRetracedFieldReferenceImpl that = (UnknownRetracedFieldReferenceImpl) o;
      return fieldDefinition.equals(that.fieldDefinition);
    }

    @Override
    public int hashCode() {
      return Objects.hash(fieldDefinition);
    }
  }

  static RetracedFieldReferenceImpl create(FieldReference fieldReference) {
    return new KnownRetracedFieldReferenceImpl(fieldReference);
  }

  static RetracedFieldReferenceImpl create(FieldDefinition fieldDefinition) {
    return new UnknownRetracedFieldReferenceImpl(fieldDefinition);
  }
}
