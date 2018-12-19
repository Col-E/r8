// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.references;

import com.android.tools.r8.Keep;
import com.android.tools.r8.errors.Unreachable;

/** Reference to an array type. */
@Keep
public final class ArrayReference implements TypeReference {

  private final int dimentions;
  private final TypeReference baseType;
  private String descriptor;

  private ArrayReference(int dimentions, TypeReference baseType, String descriptor) {
    this.dimentions = dimentions;
    this.baseType = baseType;
    this.descriptor = descriptor;
  }

  static ArrayReference fromDescriptor(String descriptor) {
    for (int i = 0; i < descriptor.length(); i++) {
      if (descriptor.charAt(i) != '[') {
        return new ArrayReference(
            i, Reference.typeFromDescriptor(descriptor.substring(i)), descriptor);
      }
    }
    throw new Unreachable("Invalid array type descriptor: " + descriptor);
  }

  public int getDimentions() {
    return dimentions;
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
  public String toDescriptor() {
    return descriptor;
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
