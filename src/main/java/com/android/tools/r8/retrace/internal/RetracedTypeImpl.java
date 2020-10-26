// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetracedType;
import java.util.Objects;

@Keep
public final class RetracedTypeImpl implements RetracedType {

  private final TypeReference typeReference;

  private RetracedTypeImpl(TypeReference typeReference) {
    this.typeReference = typeReference;
  }

  static RetracedTypeImpl create(TypeReference typeReference) {
    return new RetracedTypeImpl(typeReference);
  }

  static RetracedType createVoid() {
    return new RetracedTypeImpl(null);
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
    return typeReference.equals(((RetracedTypeImpl) o).typeReference);
  }

  @Override
  public int hashCode() {
    return Objects.hash(typeReference);
  }
}
