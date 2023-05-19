// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.RetracedClassReference;

public final class RetracedClassReferenceImpl implements RetracedClassReference {

  private final ClassReference classReference;
  private final boolean hasResult;

  private RetracedClassReferenceImpl(ClassReference classReference, boolean hasResult) {
    assert classReference != null;
    this.classReference = classReference;
    this.hasResult = hasResult;
  }

  public static RetracedClassReferenceImpl create(
      ClassReference classReference, boolean hasResult) {
    return new RetracedClassReferenceImpl(classReference, hasResult);
  }

  @Override
  public boolean isUnknown() {
    return !isKnown();
  }

  @Override
  public boolean isKnown() {
    return hasResult;
  }

  @Override
  public String getTypeName() {
    return classReference.getTypeName();
  }

  @Override
  public String getDescriptor() {
    return classReference.getDescriptor();
  }

  @Override
  public String getBinaryName() {
    return classReference.getBinaryName();
  }

  @Override
  public RetracedTypeReferenceImpl getRetracedType() {
    return RetracedTypeReferenceImpl.create(classReference);
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
    return classReference.equals(((RetracedClassReferenceImpl) o).classReference);
  }

  @Override
  public int hashCode() {
    return classReference.hashCode();
  }
}
