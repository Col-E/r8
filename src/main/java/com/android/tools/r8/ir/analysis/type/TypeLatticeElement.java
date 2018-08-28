// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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

  public boolean mustBeNull() {
    return false;
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
    if (l1.mustBeNull()) {
      return l2.asNullable();
    }
    if (l2.mustBeNull()) {
      return l1.asNullable();
    }
    if (l1.isPrimitive()) {
      return l2.isPrimitive() ? l1 : Top.getInstance();
    }
    if (l2.isPrimitive()) {
      // By the above case, !(l1.isPrimitive())
      return Top.getInstance();
    }
    // From now on, l1 and l2 are reference types, i.e., either ArrayType or ClassType.
    boolean isNullable = l1.isNullable() || l2.isNullable();
    if (l1.getClass() != l2.getClass()) {
      return objectType(appInfo, isNullable);
    }
    // From now on, l1.getClass() == l2.getClass()
    if (l1.isArrayTypeLatticeElement()) {
      assert l2.isArrayTypeLatticeElement();
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
      DexType lub =
          a1BaseReferenceType.computeLeastUpperBoundOfClasses(appInfo, a2BaseReferenceType);
      // Create the full array type.
      DexType arrayTypeLub = appInfo.dexItemFactory.createArrayType(a1Nesting, lub);
      return new ArrayTypeLatticeElement(arrayTypeLub, isNullable);
    }
    if (l1.isClassTypeLatticeElement()) {
      assert l2.isClassTypeLatticeElement();
      ClassTypeLatticeElement c1 = l1.asClassTypeLatticeElement();
      ClassTypeLatticeElement c2 = l2.asClassTypeLatticeElement();
      DexType lubType =
          c1.getClassType().computeLeastUpperBoundOfClasses(appInfo, c2.getClassType());
      return new ClassTypeLatticeElement(lubType, isNullable,
          computeLeastUpperBoundOfInterfaces(appInfo, c1.getInterfaces(), c2.getInterfaces()));
    }
    throw new Unreachable("unless a new type lattice is introduced.");
  }

  private enum InterfaceMarker {
    LEFT,
    RIGHT
  }

  private static class InterfaceWithMarker {
    final DexType itf;
    final InterfaceMarker marker;

    InterfaceWithMarker(DexType itf, InterfaceMarker marker) {
      this.itf = itf;
      this.marker = marker;
    }
  }

  static Set<DexType> computeLeastUpperBoundOfInterfaces(
      AppInfo appInfo, Set<DexType> s1, Set<DexType> s2) {
    Map<DexType, Set<InterfaceMarker>> seen = new IdentityHashMap<>();
    Queue<InterfaceWithMarker> worklist = new ArrayDeque<>();
    for (DexType itf1 : s1) {
      worklist.add(new InterfaceWithMarker(itf1, InterfaceMarker.LEFT));
    }
    for (DexType itf2 : s2) {
      worklist.add(new InterfaceWithMarker(itf2, InterfaceMarker.RIGHT));
    }
    while (!worklist.isEmpty()) {
      InterfaceWithMarker item = worklist.poll();
      DexType itf = item.itf;
      InterfaceMarker marker = item.marker;
      Set<InterfaceMarker> markers = seen.computeIfAbsent(itf, k -> new HashSet<>());
      // If this interface is a lower one in this set, skip.
      if (markers.contains(marker)) {
        continue;
      }
      // If this interface is already visited by the other set, add marker for this set and skip.
      if (markers.size() == 1) {
        markers.add(marker);
        continue;
      }
      // Otherwise, this type is freshly visited.
      markers.add(marker);
      // Put super interfaces into the worklist.
      DexClass itfClass = appInfo.definitionFor(itf);
      if (itfClass != null) {
        for (DexType superItf : itfClass.interfaces.values) {
          markers = seen.computeIfAbsent(superItf, k -> new HashSet<>());
          if (!markers.contains(marker)) {
            worklist.add(new InterfaceWithMarker(superItf, marker));
          }
        }
      }
    }

    ImmutableSet.Builder<DexType> commonBuilder = ImmutableSet.builder();
    for (Map.Entry<DexType, Set<InterfaceMarker>> entry : seen.entrySet()) {
      // Keep commonly visited interfaces only
      if (entry.getValue().size() < 2) {
        continue;
      }
      commonBuilder.add(entry.getKey());
    }
    Set<DexType> commonlyVisited = commonBuilder.build();

    ImmutableSet.Builder<DexType> lubBuilder = ImmutableSet.builder();
    for (DexType itf : commonlyVisited) {
      // If there is a strict sub interface of this interface, it is not the least element.
      if (commonlyVisited.stream().anyMatch(other -> other.isStrictSubtypeOf(itf, appInfo))) {
        continue;
      }
      lubBuilder.add(itf);
    }
    return lubBuilder.build();
  }

  private static Set<DexType> computeLeastUpperBoundOfInterfaces(
      AppInfo appInfo, Set<DexType> interfaces) {
    return computeLeastUpperBoundOfInterfaces(appInfo, interfaces, interfaces);
  }

  public static BinaryOperator<TypeLatticeElement> joiner(AppInfo appInfo) {
    return (l1, l2) -> join(appInfo, l1, l2);
  }

  public static TypeLatticeElement join(AppInfo appInfo, Stream<TypeLatticeElement> types) {
    BinaryOperator<TypeLatticeElement> joiner = joiner(appInfo);
    return types.reduce(Bottom.getInstance(), joiner, joiner);
  }

  public static TypeLatticeElement join(
      AppInfo appInfo, Stream<DexType> types, boolean isNullable) {
    return join(appInfo, types.map(t -> fromDexType(appInfo, t, isNullable)));
  }

  /**
   * Determines the strict partial order of the given {@link TypeLatticeElement}s.
   *
   * @param appInfo {@link AppInfo} to compute the least upper bound of {@link TypeLatticeElement}
   * @param l1 subject {@link TypeLatticeElement}
   * @param l2 expected to be *strict* bigger than {@param l1}
   * @return {@code true} if {@param l1} is strictly less than {@param l2}.
   */
  public static boolean strictlyLessThan(
      AppInfo appInfo, TypeLatticeElement l1, TypeLatticeElement l2) {
    if (l1.equals(l2)) {
      return false;
    }
    TypeLatticeElement lub = join(appInfo, Stream.of(l1, l2));
    return !l1.equals(lub) && l2.equals(lub);
  }

  /**
   * Determines the partial order of the given {@link TypeLatticeElement}s.
   *
   * @param appInfo {@link AppInfo} to compute the least upper bound of {@link TypeLatticeElement}
   * @param l1 subject {@link TypeLatticeElement}
   * @param l2 expected to be bigger than or equal to {@param l1}
   * @return {@code true} if {@param l1} is less than or equal to {@param l2}.
   */
  public static boolean lessThanOrEqual(
      AppInfo appInfo, TypeLatticeElement l1, TypeLatticeElement l2) {
    return l1.equals(l2) || strictlyLessThan(appInfo, l1, l2);
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

  public static TypeLatticeElement fromDexType(AppInfo appInfo, DexType type, boolean isNullable) {
    if (type == DexItemFactory.nullValueType) {
      return NullLatticeElement.getInstance();
    }
    if (type.isPrimitiveType()) {
      return PrimitiveTypeLatticeElement.getInstance();
    }
    if (type.isClassType()) {
      if (!type.isUnknown() && type.isInterface()) {
        return new ClassTypeLatticeElement(
            appInfo.dexItemFactory.objectType, isNullable, ImmutableSet.of(type));
      }
      return new ClassTypeLatticeElement(type, isNullable,
          computeLeastUpperBoundOfInterfaces(appInfo, type.implementedInterfaces(appInfo)));
    }
    assert type.isArrayType();
    return new ArrayTypeLatticeElement(type, isNullable);
  }

  public static TypeLatticeElement newArray(DexType arrayType, boolean isNullable) {
    return new ArrayTypeLatticeElement(arrayType, isNullable);
  }

  public TypeLatticeElement arrayGet(AppInfo appInfo) {
    return Bottom.getInstance();
  }

  public TypeLatticeElement checkCast(AppInfo appInfo, DexType castType) {
    TypeLatticeElement castTypeLattice = fromDexType(appInfo, castType, isNullable());
    // Special case: casting null.
    if (mustBeNull()) {
      return castTypeLattice;
    }
    if (lessThanOrEqual(appInfo, this, castTypeLattice)) {
      return this;
    }
    return castTypeLattice;
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
    return isNullable ? 1 : -1;
  }
}
