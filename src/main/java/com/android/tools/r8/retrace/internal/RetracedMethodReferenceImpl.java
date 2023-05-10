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
import java.util.OptionalInt;

public abstract class RetracedMethodReferenceImpl implements RetracedMethodReference {

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

  protected final OptionalInt position;

  private RetracedMethodReferenceImpl(OptionalInt position) {
    this.position = position;
  }

  @Override
  public boolean hasPosition() {
    return position.isPresent();
  }

  @Override
  public int getOriginalPositionOrDefault(int defaultPosition) {
    return position.orElse(defaultPosition);
  }

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

    private KnownRetracedMethodReferenceImpl(
        MethodReference methodReference, OptionalInt position) {
      super(position);
      assert methodReference != null;
      this.methodReference = methodReference;
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
      return RetracedClassReferenceImpl.create(methodReference.getHolderClass(), true);
    }

    @Override
    public String getMethodName() {
      return methodReference.getMethodName();
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

    private UnknownRetracedMethodReferenceImpl(
        MethodDefinition methodDefinition, OptionalInt position) {
      super(position);
      this.methodDefinition = methodDefinition;
    }

    @Override
    public RetracedClassReferenceImpl getHolderClass() {
      return RetracedClassReferenceImpl.create(methodDefinition.getHolderClass(), false);
    }

    @Override
    public String getMethodName() {
      return methodDefinition.getName();
    }

    public Optional<MethodReference> getMethodReference() {
      if (!methodDefinition.isFullMethodDefinition()) {
        return Optional.empty();
      }
      return Optional.of(methodDefinition.asFullMethodDefinition().getMethodReference());
    }
  }

  static RetracedMethodReferenceImpl create(MethodDefinition methodDefinition) {
    if (methodDefinition.isFullMethodDefinition()) {
      return create(
          methodDefinition.asFullMethodDefinition().getMethodReference(), OptionalInt.empty());
    }
    return new UnknownRetracedMethodReferenceImpl(methodDefinition, OptionalInt.empty());
  }


  static RetracedMethodReferenceImpl create(MethodReference methodReference) {
    return create(methodReference, OptionalInt.empty());
  }

  static RetracedMethodReferenceImpl create(MethodReference methodReference, OptionalInt position) {
    return new KnownRetracedMethodReferenceImpl(methodReference, position);
  }
}
