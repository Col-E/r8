// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.references;

import com.android.tools.r8.KeepForRetraceApi;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.Objects;

/** Reference to an array type. */
@KeepForApi
@KeepForRetraceApi
public final class ArrayReference implements TypeReference {

  private final int dimensions;
  private final TypeReference baseType;
  // Consider removing the descriptor field as dimensions and baseType encode the same information.
  private final String descriptor;

  private ArrayReference(int dimensions, TypeReference baseType, String descriptor) {
    assert dimensions > 0;
    this.dimensions = dimensions;
    this.baseType = baseType;
    this.descriptor = descriptor;
  }

  static ArrayReference fromDescriptor(String descriptor) {
    for (int i = 0; i < descriptor.length(); i++) {
      if (descriptor.charAt(i) != '[') {
        if (i > 0) {
          return new ArrayReference(
              i, Reference.typeFromDescriptor(descriptor.substring(i)), descriptor);
        }
        break;
      }
    }
    throw new Unreachable("Invalid array type descriptor: " + descriptor);
  }

  static ArrayReference fromBaseType(TypeReference baseType, int dimensions) {
    return new ArrayReference(
        dimensions,
        baseType,
        DescriptorUtils.toArrayDescriptor(dimensions, baseType.getDescriptor()));
  }

  public int getDimensions() {
    return dimensions;
  }

  public TypeReference getMemberType() {
    return Reference.arrayFromDescriptor(descriptor.substring(1));
  }

  public TypeReference getBaseType() {
    return baseType;
  }

  @Override
  public boolean isArray() {
    return true;
  }

  @Override
  public ArrayReference asArray() {
    return this;
  }

  @Override
  public String getDescriptor() {
    return descriptor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ArrayReference)) {
      return false;
    }
    ArrayReference other = (ArrayReference) o;
    return dimensions == other.dimensions && baseType.equals(other.baseType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dimensions, baseType);
  }
}
