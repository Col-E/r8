// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetracedMethod;
import com.android.tools.r8.utils.ComparatorUtils;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Keep
public abstract class RetracedMethodImpl implements RetracedMethod {

  private static final int NO_POSITION = -1;

  private RetracedMethodImpl() {}

  @Override
  public boolean isUnknown() {
    return true;
  }

  @Override
  public final boolean isKnown() {
    return !isUnknown();
  }

  @Override
  public KnownRetracedMethodImpl asKnown() {
    return null;
  }

  @Override
  public int compareTo(RetracedMethod other) {
    return Comparator.comparing(RetracedMethod::getMethodName)
        .thenComparing(RetracedMethod::isKnown)
        .thenComparing(
            RetracedMethod::asKnown,
            Comparator.nullsFirst(
                    Comparator.comparing(
                        (KnownRetracedMethod m) -> {
                          if (m == null) {
                            return null;
                          }
                          return m.isVoid() ? "void" : m.getReturnType().getTypeName();
                        }))
                .thenComparing(
                    KnownRetracedMethod::getFormalTypes,
                    ComparatorUtils.listComparator(
                        Comparator.comparing(TypeReference::getTypeName))))
        .compare(this, other);
  }

  public static final class KnownRetracedMethodImpl extends RetracedMethodImpl
      implements KnownRetracedMethod {

    private final MethodReference methodReference;
    private final int position;

    private KnownRetracedMethodImpl(MethodReference methodReference, int position) {
      assert methodReference != null;
      this.methodReference = methodReference;
      this.position = position;
    }

    @Override
    public boolean isUnknown() {
      return false;
    }

    @Override
    public boolean isVoid() {
      return methodReference.getReturnType() == null;
    }

    @Override
    public KnownRetracedMethodImpl asKnown() {
      return this;
    }

    @Override
    public RetracedClassImpl getHolderClass() {
      return RetracedClassImpl.create(methodReference.getHolderClass());
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

    @Override
    public TypeReference getReturnType() {
      assert !isVoid();
      return methodReference.getReturnType();
    }

    @Override
    public List<TypeReference> getFormalTypes() {
      return methodReference.getFormalTypes();
    }

    @Override
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
      KnownRetracedMethodImpl that = (KnownRetracedMethodImpl) o;
      return position == that.position && methodReference.equals(that.methodReference);
    }

    @Override
    public int hashCode() {
      return Objects.hash(methodReference, position);
    }
  }

  public static final class UnknownRetracedMethodImpl extends RetracedMethodImpl {

    private final MethodDefinition methodDefinition;
    private final int position;

    private UnknownRetracedMethodImpl(MethodDefinition methodDefinition, int position) {
      this.methodDefinition = methodDefinition;
      this.position = position;
    }

    @Override
    public RetracedClassImpl getHolderClass() {
      return RetracedClassImpl.create(methodDefinition.getHolderClass());
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

  static RetracedMethodImpl create(MethodDefinition methodDefinition) {
    return create(methodDefinition, NO_POSITION);
  }

  static RetracedMethodImpl create(MethodDefinition methodDefinition, int position) {
    if (methodDefinition.isFullMethodDefinition()) {
      return new KnownRetracedMethodImpl(
          methodDefinition.asFullMethodDefinition().getMethodReference(), position);
    }
    return new UnknownRetracedMethodImpl(methodDefinition, position);
  }

  static RetracedMethodImpl create(MethodReference methodReference) {
    return create(methodReference, NO_POSITION);
  }

  static RetracedMethodImpl create(MethodReference methodReference, int position) {
    return new KnownRetracedMethodImpl(methodReference, position);
  }
}
