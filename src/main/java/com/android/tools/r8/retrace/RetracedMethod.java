// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Keep
public abstract class RetracedMethod implements RetracedClassMember {

  private static final int NO_POSITION = -1;

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

  public abstract String getMethodName();

  public abstract boolean hasPosition();

  public abstract int getOriginalPositionOrDefault(int defaultPosition);

  public static final class KnownRetracedMethod extends RetracedMethod {

    private final MethodReference methodReference;
    private final int position;

    private KnownRetracedMethod(MethodReference methodReference, int position) {
      assert methodReference != null;
      this.methodReference = methodReference;
      this.position = position;
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
      return RetracedClass.create(methodReference.getHolderClass());
    }

    @Override
    public String getMethodName() {
      return methodReference.getMethodName();
    }

    @Override
    public boolean hasPosition() {
      return position != NO_POSITION;
    }

    @Override
    public int getOriginalPositionOrDefault(int defaultPosition) {
      return hasPosition() ? position : defaultPosition;
    }

    public TypeReference getReturnType() {
      assert !isVoid();
      return methodReference.getReturnType();
    }

    public List<TypeReference> getFormalTypes() {
      return methodReference.getFormalTypes();
    }

    public MethodReference getMethodReference() {
      return methodReference;
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
      return position == that.position && methodReference.equals(that.methodReference);
    }

    @Override
    public int hashCode() {
      return Objects.hash(methodReference, position);
    }
  }

  public static final class UnknownRetracedMethod extends RetracedMethod {

    private final MethodDefinition methodDefinition;
    private final int position;

    private UnknownRetracedMethod(MethodDefinition methodDefinition, int position) {
      this.methodDefinition = methodDefinition;
      this.position = position;
    }

    @Override
    public RetracedClass getHolderClass() {
      return RetracedClass.create(methodDefinition.getHolderClass());
    }

    @Override
    public String getMethodName() {
      return methodDefinition.getName();
    }

    @Override
    public boolean hasPosition() {
      return position != NO_POSITION;
    }

    @Override
    public int getOriginalPositionOrDefault(int defaultPosition) {
      return hasPosition() ? position : defaultPosition;
    }

    public Optional<MethodReference> getMethodReference() {
      if (!methodDefinition.isFullMethodDefinition()) {
        return Optional.empty();
      }
      return Optional.of(methodDefinition.asFullMethodDefinition().getMethodReference());
    }
  }

  static RetracedMethod create(MethodDefinition methodDefinition) {
    return create(methodDefinition, NO_POSITION);
  }

  static RetracedMethod create(MethodDefinition methodDefinition, int position) {
    if (methodDefinition.isFullMethodDefinition()) {
      return new KnownRetracedMethod(
          methodDefinition.asFullMethodDefinition().getMethodReference(), position);
    }
    return new UnknownRetracedMethod(methodDefinition, position);
  }

  static RetracedMethod create(MethodReference methodReference) {
    return create(methodReference, NO_POSITION);
  }

  static RetracedMethod create(MethodReference methodReference, int position) {
    return new KnownRetracedMethod(methodReference, position);
  }
}
