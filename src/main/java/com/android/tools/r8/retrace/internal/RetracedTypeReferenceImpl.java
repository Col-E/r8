// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetracedTypeReference;
import java.util.Objects;

public final class RetracedTypeReferenceImpl implements RetracedTypeReference {

  private final TypeReference typeReference;

  private RetracedTypeReferenceImpl(TypeReference typeReference) {
    this.typeReference = typeReference;
  }

  static RetracedTypeReferenceImpl create(TypeReference typeReference) {
    return new RetracedTypeReferenceImpl(typeReference);
  }

  static RetracedTypeReference createVoid() {
    return new RetracedTypeReferenceImpl(null);
  }

  @Override
  public boolean isVoid() {
    return typeReference == null;
  }

  @Override
  public TypeReference toArray(int dimensions) {
    return Reference.array(typeReference, dimensions);
  }

  @Override
  public String getTypeName() {
    assert !isVoid();
    return typeReference.getTypeName();
  }

  @Override
  public TypeReference getTypeReference() {
    return typeReference;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return typeReference.equals(((RetracedTypeReferenceImpl) o).typeReference);
  }

  @Override
  public int hashCode() {
    return Objects.hash(typeReference);
  }
}
