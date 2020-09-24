// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import java.util.Objects;

@Keep
public final class RetracedType {

  private final TypeReference typeReference;

  private RetracedType(TypeReference typeReference) {
    this.typeReference = typeReference;
  }

  static RetracedType create(TypeReference typeReference) {
    return new RetracedType(typeReference);
  }

  public boolean isVoid() {
    return typeReference == null;
  }

  public TypeReference toArray(int dimensions) {
    return Reference.array(typeReference, dimensions);
  }

  public String getTypeName() {
    assert !isVoid();
    return typeReference.getTypeName();
  }

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
    return typeReference.equals(((RetracedType) o).typeReference);
  }

  @Override
  public int hashCode() {
    return Objects.hash(typeReference);
  }
}
