// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Value;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

/**
 * The base abstraction of lattice elements for local type analysis.
 */
abstract public class TypeLatticeElement {
  private final boolean isNullable;

  TypeLatticeElement(boolean isNullable) {
    this.isNullable = isNullable;
  }

  public boolean isNullable() {
    return isNullable;
  }

  /**
   * Defines how to join with null or switch to nullable lattice element.
   *
   * @return {@link TypeLatticeElement} a result of joining with null.
   */
  abstract TypeLatticeElement asNullable();

  /**
   * Defines how to switch to non-nullable lattice element.
   *
   * @return {@link TypeLatticeElement} a similar lattice element with nullable flag flipped.
   */
  public TypeLatticeElement asNonNullable() {
    throw new Unreachable("Flipping nullable is not allowed in general.");
  }

  String isNullableString() {
    return isNullable() ? "" : "@NonNull ";
  }

  /**
   * Computes the least upper bound of the current and the other elements.
   *
   * @param appInfo {@link AppInfo}.
   * @param l1 {@link TypeLatticeElement} to join.
   * @param l2 {@link TypeLatticeElement} to join.
   * @return {@link TypeLatticeElement}, a least upper bound of {@param l1} and {@param l2}.
   */
  public static TypeLatticeElement join(
      AppInfo appInfo, TypeLatticeElement l1, TypeLatticeElement l2) {
    if (l1.isBottom()) {
      return l2;
    }
    if (l2.isBottom()) {
      return l1;
    }
    if (l1.isTop() || l2.isTop()) {
      return Top.getInstance();
    }
    if (l1 instanceof NullLatticeElement) {
      return l2.asNullable();
    }
    if (l2 instanceof NullLatticeElement) {
      return l1.asNullable();
    }
    if (l1 instanceof PrimitiveTypeLatticeElement) {
      return l2 instanceof PrimitiveTypeLatticeElement ? l1 : Top.getInstance();
    }
    if (l2 instanceof PrimitiveTypeLatticeElement) {
      // By the above case !(l1 instanceof PrimitiveTypeLatticeElement)
      return Top.getInstance();
    }
    // From now on, l1 and l2 are reference types, i.e., either ArrayType or ClassType.
    boolean isNullable = l1.isNullable() || l2.isNullable();
    if (l1.getClass() != l2.getClass()) {
      return objectType(appInfo, isNullable);
    }
    // From now on, l1.getClass() == l2.getClass()
    if (l1.isArrayTypeLatticeElement()) {
      ArrayTypeLatticeElement a1 = l1.asArrayTypeLatticeElement();
      ArrayTypeLatticeElement a2 = l2.asArrayTypeLatticeElement();
      // Identical types are the same elements
      if (a1.getArrayType() == a2.getArrayType()) {
        return a1.isNullable() ? a1 : a2;
      }
      // If non-equal, find the inner-most reference types for each.
      DexType a1BaseReferenceType = a1.getArrayBaseType(appInfo.dexItemFactory);
      int a1Nesting = a1.getNesting();
      if (a1BaseReferenceType.isPrimitiveType()) {
        a1Nesting--;
        a1BaseReferenceType = appInfo.dexItemFactory.objectType;
      }
      DexType a2BaseReferenceType = a2.getArrayBaseType(appInfo.dexItemFactory);
      int a2Nesting = a2.getNesting();
      if (a2BaseReferenceType.isPrimitiveType()) {
        a2Nesting--;
        a2BaseReferenceType = appInfo.dexItemFactory.objectType;
      }
      assert a1BaseReferenceType.isClassType() && a2BaseReferenceType.isClassType();
      // If any nestings hit zero object is the join.
      if (a1Nesting == 0 || a2Nesting == 0) {
        return objectType(appInfo, isNullable);
      }
      // If the nestings differ the join is the smallest nesting level.
      if (a1Nesting != a2Nesting) {
        int min = Math.min(a1Nesting, a2Nesting);
        return objectArrayType(appInfo, min, isNullable);
      }
      // For different class element types, compute the least upper bound of element types.
      DexType lub = a1BaseReferenceType.computeLeastUpperBound(appInfo, a2BaseReferenceType);
      // Create the full array type.
      DexType arrayTypeLub = appInfo.dexItemFactory.createArrayType(a1Nesting, lub);
      return new ArrayTypeLatticeElement(arrayTypeLub, isNullable);
    }
    if (l1.isClassTypeLatticeElement()) {
      ClassTypeLatticeElement c1 = l1.asClassTypeLatticeElement();
      ClassTypeLatticeElement c2 = l2.asClassTypeLatticeElement();
      if (c1.getClassType() == c2.getClassType()) {
        return c1.isNullable() ? c1 : c2;
      } else {
        DexType lub = c1.getClassType().computeLeastUpperBound(appInfo, c2.getClassType());
        return new ClassTypeLatticeElement(lub, isNullable);
      }
    }
    throw new Unreachable("unless a new type lattice is introduced.");
  }

  static BinaryOperator<TypeLatticeElement> joiner(AppInfo appInfo) {
    return (l1, l2) -> join(appInfo, l1, l2);
  }

  public static TypeLatticeElement join(AppInfo appInfo, Stream<TypeLatticeElement> types) {
    BinaryOperator<TypeLatticeElement> joiner = joiner(appInfo);
    return types.reduce(Bottom.getInstance(), joiner::apply, joiner::apply);
  }

  /**
   * Represents a type that can be everything.
   *
   * @return true if the corresponding {@link Value} could be any kinds.
   */
  public boolean isTop() {
    return false;
  }

  /**
   * Represents an empty type.
   *
   * @return true if the type of corresponding {@link Value} is not determined yet.
   */
  boolean isBottom() {
    return false;
  }

  public boolean isArrayTypeLatticeElement() {
    return false;
  }

  public ArrayTypeLatticeElement asArrayTypeLatticeElement() {
    return null;
  }

  public boolean isClassTypeLatticeElement() {
    return false;
  }

  public ClassTypeLatticeElement asClassTypeLatticeElement() {
    return null;
  }

  public boolean isPrimitive() {
    return false;
  }

  static ClassTypeLatticeElement objectType(AppInfo appInfo, boolean isNullable) {
    return new ClassTypeLatticeElement(appInfo.dexItemFactory.objectType, isNullable);
  }

  static ArrayTypeLatticeElement objectArrayType(AppInfo appInfo, int nesting, boolean isNullable) {
    return new ArrayTypeLatticeElement(
        appInfo.dexItemFactory.createArrayType(nesting, appInfo.dexItemFactory.objectType),
        isNullable);
  }

  public static TypeLatticeElement fromDexType(DexType type, boolean isNullable) {
    if (type == DexItemFactory.nullValueType) {
      return NullLatticeElement.getInstance();
    }
    if (type.isPrimitiveType()) {
      return PrimitiveTypeLatticeElement.getInstance();
    }
    if (type.isClassType()) {
      return new ClassTypeLatticeElement(type, isNullable);
    }
    assert type.isArrayType();
    return new ArrayTypeLatticeElement(type, isNullable);
  }

  public static TypeLatticeElement newArray(DexType arrayType, boolean isNullable) {
    return new ArrayTypeLatticeElement(arrayType, isNullable);
  }

  public TypeLatticeElement arrayGet(AppInfo appInfo) {
    return Top.getInstance();
  }

  public TypeLatticeElement checkCast(AppInfo appInfo, DexType castType) {
    return fromDexType(castType, isNullable());
  }

  @Override
  abstract public String toString();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || o.getClass() != this.getClass()) {
      return false;
    }
    TypeLatticeElement otherElement = (TypeLatticeElement) o;
    return otherElement.isNullable() == isNullable;
  }

  @Override
  public int hashCode() {
    return isNullable ? 1 : 0;
  }
}
