// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.ClassReference;

@Keep
public final class RetracedClass {

  private final ClassReference classReference;

  private RetracedClass(ClassReference classReference) {
    assert classReference != null;
    this.classReference = classReference;
  }

  public static RetracedClass create(ClassReference classReference) {
    return new RetracedClass(classReference);
  }

  public String getTypeName() {
    return classReference.getTypeName();
  }

  public String getBinaryName() {
    return classReference.getBinaryName();
  }

  public RetracedType getRetracedType() {
    return RetracedType.create(classReference);
  }

  ClassReference getClassReference() {
    return classReference;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return classReference.equals(((RetracedClass) o).classReference);
  }

  @Override
  public int hashCode() {
    return classReference.hashCode();
  }
}
