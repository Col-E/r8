// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.utils.ComparatorUtils;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public abstract class RetracedMethodReferenceImpl implements RetracedMethodReference {

  private static final int NO_POSITION = -1;
  private static final Comparator<RetracedMethodReference> comparator =
      Comparator.comparing(RetracedMethodReference::getMethodName)
          .thenComparing(RetracedMethodReference::isKnown)
          .thenComparing(
              RetracedMethodReference::asKnown,
              Comparator.nullsFirst(
                      Comparator.comparing(
                          (KnownRetracedMethodReference m) -> {
                            if (m == null) {
                              return null;
                            }
                            return m.isVoid() ? "void" : m.getReturnType().getTypeName();
                          }))
                  .thenComparing(
                      KnownRetracedMethodReference::getFormalTypes,
                      ComparatorUtils.listComparator(
                          Comparator.comparing(TypeReference::getTypeName))));

  private RetracedMethodReferenceImpl() {}

  @Override
  public boolean isUnknown() {
    return true;
  }

  @Override
  public final boolean isKnown() {
    return !isUnknown();
  }

  @Override
  public KnownRetracedMethodReferenceImpl asKnown() {
    return null;
  }

  @Override
  public int compareTo(RetracedMethodReference other) {
    return comparator.compare(this, other);
  }

  public static final class KnownRetracedMethodReferenceImpl extends RetracedMethodReferenceImpl
      implements KnownRetracedMethodReference {

    private final MethodReference methodReference;
    private final int position;

    private KnownRetracedMethodReferenceImpl(MethodReference methodReference, int position) {
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
    public KnownRetracedMethodReferenceImpl asKnown() {
      return this;
    }

    @Override
    public RetracedClassReferenceImpl getHolderClass() {
      return RetracedClassReferenceImpl.create(methodReference.getHolderClass());
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
      KnownRetracedMethodReferenceImpl that = (KnownRetracedMethodReferenceImpl) o;
      return position == that.position && methodReference.equals(that.methodReference);
    }

    @Override
    public int hashCode() {
      return Objects.hash(methodReference, position);
    }
  }

  public static final class UnknownRetracedMethodReferenceImpl extends RetracedMethodReferenceImpl {

    private final MethodDefinition methodDefinition;
    private final int position;

    private UnknownRetracedMethodReferenceImpl(MethodDefinition methodDefinition, int position) {
      this.methodDefinition = methodDefinition;
      this.position = position;
    }

    @Override
    public RetracedClassReferenceImpl getHolderClass() {
      return RetracedClassReferenceImpl.create(methodDefinition.getHolderClass());
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

  static RetracedMethodReferenceImpl create(MethodDefinition methodDefinition) {
    return create(methodDefinition, NO_POSITION);
  }

  static RetracedMethodReferenceImpl create(MethodDefinition methodDefinition, int position) {
    if (methodDefinition.isFullMethodDefinition()) {
      return new KnownRetracedMethodReferenceImpl(
          methodDefinition.asFullMethodDefinition().getMethodReference(), position);
    }
    return new UnknownRetracedMethodReferenceImpl(methodDefinition, position);
  }

  static RetracedMethodReferenceImpl create(MethodReference methodReference) {
    return create(methodReference, NO_POSITION);
  }

  static RetracedMethodReferenceImpl create(MethodReference methodReference, int position) {
    return new KnownRetracedMethodReferenceImpl(methodReference, position);
  }
}
