// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.RetracedClass;

@Keep
public final class RetracedClassImpl implements RetracedClass {

  private final ClassReference classReference;

  private RetracedClassImpl(ClassReference classReference) {
    assert classReference != null;
    this.classReference = classReference;
  }

  public static RetracedClassImpl create(ClassReference classReference) {
    return new RetracedClassImpl(classReference);
  }

  @Override
  public String getTypeName() {
    return classReference.getTypeName();
  }

  @Override
  public String getBinaryName() {
    return classReference.getBinaryName();
  }

  @Override
  public RetracedTypeImpl getRetracedType() {
    return RetracedTypeImpl.create(classReference);
  }

  @Override
  public ClassReference getClassReference() {
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
    return classReference.equals(((RetracedClassImpl) o).classReference);
  }

  @Override
  public int hashCode() {
    return classReference.hashCode();
  }
}
