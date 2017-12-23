// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Value;
import java.util.function.BiFunction;

/**
 * The base abstraction of lattice elements for local type analysis.
 */
abstract public class TypeLatticeElement {
  private final boolean isNullable;

  TypeLatticeElement(boolean isNullable) {
    this.isNullable = isNullable;
  }

  boolean isNullable() {
    return isNullable;
  }

  /**
   * Defines how to join with null.
   *
   * @return {@link TypeLatticeElement} a result of joining with null.
   */
  abstract TypeLatticeElement asNullable();

  String isNullableString() {
    return isNullable() ? "" : "@NonNull ";
  }

  /**
   * Computes the least upper bound of the current and the other elements.
   *
   * @param appInfo {@link AppInfoWithSubtyping} that contains subtype info.
   * @param l1 {@link TypeLatticeElement} to join.
   * @param l2 {@link TypeLatticeElement} to join.
   * @return {@link TypeLatticeElement}, a least upper bound of {@param l1} and {@param l2}.
   */
  static TypeLatticeElement join(
      AppInfoWithSubtyping appInfo, TypeLatticeElement l1, TypeLatticeElement l2) {
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
      // !(l1 instanceof PrimitiveTypeLatticeElement)
      return Top.getInstance();
    }
    // From now on, l1 and l2 are either PrimitiveArrayType, ArrayType, or ClassType.
    boolean isNullable = l1.isNullable() || l2.isNullable();
    if (l1.getClass() != l2.getClass()) {
      return objectType(appInfo, isNullable);
    }
    // From now on, l1.getClass() == l2.getClass()
    if (l1 instanceof PrimitiveArrayTypeLatticeElement) {
      PrimitiveArrayTypeLatticeElement a1 = (PrimitiveArrayTypeLatticeElement) l1;
      PrimitiveArrayTypeLatticeElement a2 = (PrimitiveArrayTypeLatticeElement) l2;
      if (a1.nesting != a2.nesting) {
        int min = Math.min(a1.nesting, a2.nesting);
        if (min > 1) {
          return objectArrayType(appInfo, min - 1, isNullable);
        } else {
          return objectType(appInfo, isNullable);
        }
      } else {
        return l1;
      }
    }
    if (l1 instanceof ArrayTypeLatticeElement) {
      ArrayTypeLatticeElement a1 = (ArrayTypeLatticeElement) l1;
      ArrayTypeLatticeElement a2 = (ArrayTypeLatticeElement) l2;
      if (a1.nesting != a2.nesting) {
        int min = Math.min(a1.nesting, a2.nesting);
        return objectArrayType(appInfo, min, isNullable);
      } else {
        // Same nesting, same base type.
        if (a1.elementType == a2.elementType) {
          return a1.isNullable() ? a1 : a2;
        } else if (a1.elementType.isClassType() && a2.elementType.isClassType()) {
          // For different class element types, compute the least upper bound of element types.
          DexType lub = a1.elementType.computeLeastUpperBound(appInfo, a2.elementType);
          return new ArrayTypeLatticeElement(lub, a1.nesting, isNullable);
        }
        // Otherwise, fall through to the end, where TOP will be returned.
      }
    }
    if (l1 instanceof ClassTypeLatticeElement) {
      ClassTypeLatticeElement c1 = (ClassTypeLatticeElement) l1;
      ClassTypeLatticeElement c2 = (ClassTypeLatticeElement) l2;
      if (c1.classType == c2.classType) {
        return c1.isNullable() ? c1 : c2;
      } else {
        DexType lub = c1.classType.computeLeastUpperBound(appInfo, c2.classType);
        return new ClassTypeLatticeElement(lub, isNullable);
      }
    }
    throw new Unreachable("unless a new type lattice is introduced.");
  }

  static BiFunction<TypeLatticeElement, TypeLatticeElement, TypeLatticeElement> joiner(
      AppInfoWithSubtyping appInfo) {
    return (l1, l2) -> join(appInfo, l1, l2);
  }

  /**
   * Represents a type that can be everything.
   *
   * @return true if the corresponding {@link Value} could be any kinds.
   */
  boolean isTop() {
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

  static ClassTypeLatticeElement objectType(AppInfo appInfo, boolean isNullable) {
    return new ClassTypeLatticeElement(appInfo.dexItemFactory.objectType, isNullable);
  }

  static ArrayTypeLatticeElement objectArrayType(AppInfo appInfo, int nesting, boolean isNullable) {
    return new ArrayTypeLatticeElement(appInfo.dexItemFactory.objectType, nesting, isNullable);
  }

  public static TypeLatticeElement fromDexType(
      AppInfoWithSubtyping appInfo, DexType type, boolean isNullable) {
    if (type.isPrimitiveType()) {
      return PrimitiveTypeLatticeElement.getInstance();
    }
    if (type.isPrimitiveArrayType()) {
      return new PrimitiveArrayTypeLatticeElement(
          type.getNumberOfLeadingSquareBrackets(), isNullable);
    }
    if (type.isClassType()) {
      return new ClassTypeLatticeElement(type, isNullable);
    }
    assert type.isArrayType();
    return new ArrayTypeLatticeElement(
        type.toBaseType(appInfo.dexItemFactory),
        type.getNumberOfLeadingSquareBrackets(),
        isNullable);
  }

  public static TypeLatticeElement newArray(
      AppInfoWithSubtyping appInfo, DexType arrayType, boolean isNullable) {
    DexType baseType = arrayType.toBaseType(appInfo.dexItemFactory);
    assert baseType != arrayType;
    int nesting = arrayType.getNumberOfLeadingSquareBrackets();
    if (baseType.isClassType()) {
      return new ArrayTypeLatticeElement(baseType, nesting, isNullable);
    } else {
      return newPrimitiveArray(nesting, isNullable);
    }
  }

  public static TypeLatticeElement newPrimitiveArray(int nesting, boolean isNullable) {
    return new PrimitiveArrayTypeLatticeElement(nesting, isNullable);
  }

  public TypeLatticeElement arrayGet(AppInfoWithSubtyping appInfo) {
    return Top.getInstance();
  }

  public TypeLatticeElement checkCast(AppInfoWithSubtyping appInfo, DexType castType) {
    return fromDexType(appInfo, castType, isNullable());
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
