// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.references;

import com.android.tools.r8.KeepForRetraceApi;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.utils.DescriptorUtils;

/** Reference to a class type or interface type. */
@KeepForApi
@KeepForRetraceApi
public final class ClassReference implements TypeReference {

  private final String descriptor;

  private ClassReference(String descriptor) {
    this.descriptor = descriptor;
  }

  static ClassReference fromDescriptor(String descriptor) {
    return new ClassReference(descriptor);
  }

  public String getBinaryName() {
    return DescriptorUtils.getBinaryNameFromDescriptor(descriptor);
  }

  @Override
  public boolean isClass() {
    return true;
  }

  @Override
  public ClassReference asClass() {
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
    if (!(o instanceof ClassReference)) {
      return false;
    }
    return descriptor.equals(((ClassReference) o).descriptor);
  }

  @Override
  public int hashCode() {
    return descriptor.hashCode();
  }

  @Override
  public String toString() {
    return getDescriptor();
  }
}
