// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import java.util.List;
import java.util.Objects;

@Keep
public abstract class RetracedMethod {

  private RetracedMethod() {}

  public boolean isUnknown() {
    return true;
  }

  public final boolean isKnown() {
    return !isUnknown();
  }

  public KnownRetracedMethod asKnown() {
    return null;
  }

  public abstract RetracedClass getHolderClass();

  public abstract String getMethodName();

  public static final class KnownRetracedMethod extends RetracedMethod {

    private final MethodReference methodReference;
    private final RetracedClass classReference;

    private KnownRetracedMethod(RetracedClass classReference, MethodReference methodReference) {
      assert methodReference != null;
      this.classReference = classReference;
      this.methodReference = methodReference;
    }

    @Override
    public boolean isUnknown() {
      return false;
    }

    public boolean isVoid() {
      return methodReference.getReturnType() == null;
    }

    @Override
    public KnownRetracedMethod asKnown() {
      return this;
    }

    @Override
    public RetracedClass getHolderClass() {
      return classReference;
    }

    @Override
    public String getMethodName() {
      return methodReference.getMethodName();
    }

    public TypeReference getReturnType() {
      assert !isVoid();
      return methodReference.getReturnType();
    }

    public List<TypeReference> getFormalTypes() {
      return methodReference.getFormalTypes();
    }

    public MethodReference getClassReference() {
      return methodReference;
    }

    public boolean equalsMethodReference(MethodReference reference) {
      return methodReference.equals(reference);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      KnownRetracedMethod that = (KnownRetracedMethod) o;
      assert !methodReference.equals(that.methodReference)
          || classReference.equals(that.classReference);
      return methodReference.equals(that.methodReference);
    }

    @Override
    public int hashCode() {
      return Objects.hash(methodReference, classReference);
    }
  }

  public static final class UnknownRetracedMethod extends RetracedMethod {

    private final RetracedClass classReference;
    private final String name;

    private UnknownRetracedMethod(RetracedClass classReference, String name) {
      this.classReference = classReference;
      this.name = name;
    }

    @Override
    public RetracedClass getHolderClass() {
      return classReference;
    }

    @Override
    public String getMethodName() {
      return name;
    }
  }

  static RetracedMethod create(RetracedClass classReference, MethodReference methodReference) {
    return new KnownRetracedMethod(classReference, methodReference);
  }

  static RetracedMethod createUnknown(RetracedClass classReference, String name) {
    return new UnknownRetracedMethod(classReference, name);
  }
}
