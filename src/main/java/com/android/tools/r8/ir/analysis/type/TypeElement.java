// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static java.util.Collections.emptySet;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.Value;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

/** The base abstraction of lattice elements for local type analysis. */
public abstract class TypeElement {

  public static BottomTypeElement getBottom() {
    return BottomTypeElement.getInstance();
  }

  public static TopTypeElement getTop() {
    return TopTypeElement.getInstance();
  }

  public static BooleanTypeElement getBoolean() {
    return BooleanTypeElement.getInstance();
  }

  public static ByteTypeElement getByte() {
    return ByteTypeElement.getInstance();
  }

  public static ShortTypeElement getShort() {
    return ShortTypeElement.getInstance();
  }

  public static CharTypeElement getChar() {
    return CharTypeElement.getInstance();
  }

  public static IntTypeElement getInt() {
    return IntTypeElement.getInstance();
  }

  public static FloatTypeElement getFloat() {
    return FloatTypeElement.getInstance();
  }

  public static SinglePrimitiveTypeElement getSingle() {
    return SinglePrimitiveTypeElement.getInstance();
  }

  public static LongTypeElement getLong() {
    return LongTypeElement.getInstance();
  }

  public static DoubleTypeElement getDouble() {
    return DoubleTypeElement.getInstance();
  }

  public static WidePrimitiveTypeElement getWide() {
    return WidePrimitiveTypeElement.getInstance();
  }

  public static ReferenceTypeElement getNull() {
    return ReferenceTypeElement.getNullType();
  }

  public final TypeElement fixupClassTypeReferences(
      AppView<? extends AppInfoWithClassHierarchy> appView, Function<DexType, DexType> mapping) {
    return fixupClassTypeReferences(appView, mapping, emptySet());
  }

  public TypeElement fixupClassTypeReferences(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Function<DexType, DexType> mapping,
      Set<DexType> prunedTypes) {
    return this;
  }

  public final TypeElement rewrittenWithLens(
      AppView<? extends AppInfoWithClassHierarchy> appView, GraphLens graphLens) {
    return rewrittenWithLens(appView, graphLens, null);
  }

  public final TypeElement rewrittenWithLens(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      GraphLens graphLens,
      GraphLens codeLens) {
    return rewrittenWithLens(appView, graphLens, codeLens, Collections.emptySet());
  }

  public final TypeElement rewrittenWithLens(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      GraphLens graphLens,
      GraphLens codeLens,
      Set<DexType> prunedTypes) {
    return fixupClassTypeReferences(
        appView, type -> graphLens.lookupType(type, codeLens), prunedTypes);
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isNullable() {
    return nullability().isNullable();
  }

  @SuppressWarnings("ReferenceEquality")
  public abstract Nullability nullability();

  /**
   * Computes the least upper bound of the current and the other elements.
   *
   * @param other {@link TypeElement} to join.
   * @param appView {@link DexDefinitionSupplier}.
   * @return {@link TypeElement}, a least upper bound of {@param this} and {@param other}.
   */
  @SuppressWarnings("ReferenceEquality")
  public TypeElement join(TypeElement other, AppView<?> appView) {
    if (this == other || other.isBottom()) {
      return this;
    }
    if (isBottom()) {
      return other;
    }
    if (isTop() || other.isTop() || isPrimitiveType() != other.isPrimitiveType()) {
      return getTop();
    }
    if (isPrimitiveType()) {
      return asPrimitiveType().join(other.asPrimitiveType());
    }
    // From now on, this and other are precise reference types, i.e., either ArrayType or ClassType.
    assert isReferenceType();
    assert isPreciseType();
    assert other.isReferenceType();
    assert other.isPreciseType();
    return asReferenceType().join(other.asReferenceType(), appView);
  }

  public static TypeElement join(Iterable<TypeElement> typeLattices, AppView<?> appView) {
    TypeElement result = getBottom();
    for (TypeElement other : typeLattices) {
      result = result.join(other, appView);
    }
    return result;
  }

  /**
   * Determines the strict partial order of the given {@link TypeElement}s.
   *
   * @param other expected to be *strictly* bigger than {@param this}
   * @param appView {@link DexDefinitionSupplier} to compute the least upper bound of {@link
   *     TypeElement}
   * @return {@code true} if {@param this} is strictly less than {@param other}.
   */
  public boolean strictlyLessThan(TypeElement other, AppView<?> appView) {
    return !equals(other) && internalLessThan(other, appView);
  }

  /**
   * Determines the partial order of the given {@link TypeElement}s.
   *
   * @param other expected to be bigger than or equal to {@param this}
   * @param appView {@link DexDefinitionSupplier} to compute the least upper bound of {@link
   *     TypeElement}
   * @return {@code true} if {@param this} is less than or equal to {@param other}.
   */
  public boolean lessThanOrEqual(TypeElement other, AppView<?> appView) {
    return equals(other) || internalLessThan(other, appView);
  }

  private boolean internalLessThan(TypeElement other, AppView<?> appView) {
    // The equals check has already been done by callers, so only the join is computed.
    TypeElement lub = join(other, appView);
    return !equals(lub) && other.equals(lub);
  }

  /**
   * Determines if this {@link TypeElement} is less than or equal to the given {@link TypeElement}
   * up to nullability.
   *
   * @param other to check for equality with this
   * @return {@code true} if {@param this} is equal up to nullability with {@param other}.
   */
  @SuppressWarnings("ReferenceEquality")
  public boolean lessThanOrEqualUpToNullability(TypeElement other, AppView<?> appView) {
    if (this == other) {
      return true;
    }
    if (this.isTop()) {
      return other.isTop();
    }
    if (other.isTop()) {
      return true;
    }
    if (this.isBottom()) {
      return true;
    }
    if (other.isBottom()) {
      return false;
    }
    if (isPrimitiveType()) {
      // Primitives cannot be nullable.
      return lessThanOrEqual(other, appView);
    }
    assert isReferenceType() && other.isReferenceType();
    ReferenceTypeElement otherAsNullable =
        other.isNullable()
            ? other.asReferenceType()
            : other.asReferenceType().getOrCreateVariant(Nullability.maybeNull());
    return lessThanOrEqual(otherAsNullable, appView);
  }

  /**
   * Determines if the {@link TypeElement}s are equal up to nullability.
   *
   * @param other to check for equality with this
   * @return {@code true} if {@param this} is equal up to nullability with {@param other}.
   */
  @SuppressWarnings("ReferenceEquality")
  public boolean equalUpToNullability(TypeElement other) {
    if (this == other) {
      return true;
    }
    if (isBottom() != other.isBottom()) {
      return false;
    }
    if (isPrimitiveType() || other.isPrimitiveType()) {
      return false;
    }
    assert isReferenceType() && other.isReferenceType();
    ReferenceTypeElement thisAsMaybeNull =
        this.asReferenceType().getOrCreateVariant(Nullability.maybeNull());
    ReferenceTypeElement otherAsMaybeNull =
        other.asReferenceType().getOrCreateVariant(Nullability.maybeNull());
    return thisAsMaybeNull.equals(otherAsMaybeNull);
  }

  /**
   * Determines if this type is based on a missing class, directly or indirectly.
   *
   * @return {@code} true if this type is based on a missing class.
   * @param appView
   */
  public boolean isBasedOnMissingClass(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return false;
  }

  /**
   * Represents a type that can be everything.
   *
   * @return {@code true} if the corresponding {@link Value} could be any kinds.
   */
  public boolean isTop() {
    return false;
  }

  /**
   * Represents an empty type.
   *
   * @return {@code true} if the type of corresponding {@link Value} is not determined yet.
   */
  public boolean isBottom() {
    return false;
  }

  public boolean isReferenceType() {
    return false;
  }

  public ReferenceTypeElement asReferenceType() {
    return null;
  }

  public boolean isArrayType() {
    return false;
  }

  public boolean isPrimitiveArrayType() {
    return false;
  }

  public ArrayTypeElement asArrayType() {
    return null;
  }

  public boolean isClassType() {
    return false;
  }

  @SuppressWarnings("ReferenceEquality")
  public final boolean isClassType(DexType type) {
    assert type.isClassType();
    return isClassType() && asClassType().getClassType() == type;
  }

  public final boolean isStringType(DexItemFactory dexItemFactory) {
    return isClassType(dexItemFactory.stringType);
  }

  public ClassTypeElement asClassType() {
    return null;
  }

  public boolean isPrimitiveType() {
    return false;
  }

  public PrimitiveTypeElement asPrimitiveType() {
    return null;
  }

  public boolean isSinglePrimitive() {
    return false;
  }

  public boolean isWidePrimitive() {
    return false;
  }

  public boolean isBoolean() {
    return false;
  }

  public boolean isByte() {
    return false;
  }

  public boolean isShort() {
    return false;
  }

  public  boolean isChar() {
    return false;
  }

  public boolean isInt() {
    return false;
  }

  public boolean isFloat() {
    return false;
  }

  public boolean isLong() {
    return false;
  }

  public boolean isDouble() {
    return false;
  }

  public boolean isPreciseType() {
    return isArrayType()
        || isClassType()
        || isNullType()
        || isInt()
        || isFloat()
        || isLong()
        || isDouble()
        || isBottom();
  }

  public boolean isFineGrainedType() {
    return isBoolean()
        || isByte()
        || isShort()
        || isChar();
  }

  /**
   * Determines if this type only includes null values that are defined by a const-number
   * instruction in the same enclosing method.
   *
   * These null values can be assigned to any type.
   */
  public boolean isNullType() {
    return false;
  }

  /**
   * Determines if this type only includes null values.
   *
   * These null values cannot be assigned to any type. For example, it is a type error to "throw v"
   * where the value `v` satisfies isDefinitelyNull(), because the static type of `v` may not be a
   * subtype of Throwable.
   */
  public boolean isDefinitelyNull() {
    return nullability().isDefinitelyNull();
  }

  public boolean isDefinitelyNotNull() {
    return nullability().isDefinitelyNotNull();
  }

  @SuppressWarnings("ReferenceEquality")
  public int requiredRegisters() {
    assert !isBottom() && !isTop();
    return 1;
  }

  public static ClassTypeElement objectClassType(AppView<?> appView, Nullability nullability) {
    return fromDexType(appView.dexItemFactory().objectType, nullability, appView).asClassType();
  }

  static ArrayTypeElement objectArrayType(AppView<?> appView, Nullability nullability) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    return fromDexType(
            dexItemFactory.createArrayType(1, dexItemFactory.objectType), nullability, appView)
        .asArrayType();
  }

  public static ClassTypeElement stringClassType(AppView<?> appView) {
    return fromDexType(appView.dexItemFactory().stringType, Nullability.maybeNull(), appView)
        .asClassType();
  }

  public static ClassTypeElement classClassType(AppView<?> appView, Nullability nullability) {
    return fromDexType(appView.dexItemFactory().classType, nullability, appView).asClassType();
  }

  public static ClassTypeElement stringClassType(AppView<?> appView, Nullability nullability) {
    return fromDexType(appView.dexItemFactory().stringType, nullability, appView).asClassType();
  }

  public static TypeElement fromDexType(DexType type, Nullability nullability, AppView<?> appView) {
    return fromDexType(type, nullability, appView, false);
  }

  @SuppressWarnings("ReferenceEquality")
  public static TypeElement fromDexType(
      DexType type, Nullability nullability, AppView<?> appView, boolean asArrayElementType) {
    if (type == DexItemFactory.nullValueType) {
      assert !nullability.isDefinitelyNotNull();
      return getNull();
    }
    if (type.isPrimitiveType()) {
      return PrimitiveTypeElement.fromDexType(type, asArrayElementType);
    }
    return appView.dexItemFactory().createReferenceTypeElement(type, nullability, appView);
  }

  public boolean isValueTypeCompatible(TypeElement other) {
    return (isReferenceType() && other.isReferenceType())
        || (isSinglePrimitive() && other.isSinglePrimitive())
        || (isWidePrimitive() && other.isWidePrimitive());
  }

  @Override
  public abstract String toString();

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();
}
