// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.LRUCacheTable;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * The base abstraction of lattice elements for local type analysis.
 */
public abstract class TypeLatticeElement {
  public static final BottomTypeLatticeElement BOTTOM = BottomTypeLatticeElement.getInstance();
  public static final TopTypeLatticeElement TOP = TopTypeLatticeElement.getInstance();
  public static final IntTypeLatticeElement INT = IntTypeLatticeElement.getInstance();
  public static final FloatTypeLatticeElement FLOAT = FloatTypeLatticeElement.getInstance();
  public static final SingleTypeLatticeElement SINGLE = SingleTypeLatticeElement.getInstance();
  public static final LongTypeLatticeElement LONG = LongTypeLatticeElement.getInstance();
  public static final DoubleTypeLatticeElement DOUBLE = DoubleTypeLatticeElement.getInstance();
  public static final WideTypeLatticeElement WIDE = WideTypeLatticeElement.getInstance();
  public static final ReferenceTypeLatticeElement NULL =
      ReferenceTypeLatticeElement.getNullTypeLatticeElement();

  private static final LRUCacheTable<Set<DexType>, Set<DexType>, Set<DexType>>
      leastUpperBoundOfInterfacesTable = LRUCacheTable.create(8, 8);

  // TODO(b/72693244): Switch to NullLatticeElement.
  private final boolean isNullable;

  TypeLatticeElement(boolean isNullable) {
    this.isNullable = isNullable;
  }

  public boolean isNullable() {
    return isNullable;
  }

  public NullLatticeElement nullElement() {
    if (isNull()) {
      return NullLatticeElement.definitelyNull();
    }
    if (!isNullable()) {
      return NullLatticeElement.definitelyNotNull();
    }
    return NullLatticeElement.maybeNull();
  }

  /**
   * Defines how to join with null or switch to nullable lattice element.
   *
   * @return {@link TypeLatticeElement} a result of joining with null.
   */
  public abstract TypeLatticeElement asNullable();

  /**
   * Defines how to switch to non-nullable lattice element.
   *
   * @return {@link TypeLatticeElement} a similar lattice element with nullable flag flipped.
   */
  public TypeLatticeElement asNonNullable() {
    return BOTTOM;
  }

  String isNullableString() {
    return isNullable() ? "" : "@NonNull ";
  }

  /**
   * Computes the least upper bound of the current and the other elements.
   *
   * @param other {@link TypeLatticeElement} to join.
   * @param appInfo {@link AppInfo}.
   * @return {@link TypeLatticeElement}, a least upper bound of {@param this} and {@param other}.
   */
  public TypeLatticeElement join(TypeLatticeElement other, AppInfo appInfo) {
    if (this == other) {
      return this;
    }
    if (isBottom()) {
      return other;
    }
    if (other.isBottom()) {
      return this;
    }
    if (isTop() || other.isTop()) {
      return TOP;
    }
    if (isNull()) {
      return other.asNullable();
    }
    if (other.isNull()) {
      return asNullable();
    }
    if (isPrimitive()) {
      return other.isPrimitive()
          ? PrimitiveTypeLatticeElement.join(
              asPrimitiveTypeLatticeElement(), other.asPrimitiveTypeLatticeElement())
          : TOP;
    }
    if (other.isPrimitive()) {
      // By the above case, !(isPrimitive())
      return TOP;
    }
    // From now on, this and other are precise reference types, i.e., either ArrayType or ClassType.
    assert isReference() && other.isReference();
    assert isPreciseType() && other.isPreciseType();
    boolean isNullable = isNullable() || other.isNullable();
    if (getClass() != other.getClass()) {
      return objectClassType(appInfo, isNullable);
    }
    // From now on, getClass() == other.getClass()
    if (isArrayType()) {
      assert other.isArrayType();
      ArrayTypeLatticeElement a1 = asArrayTypeLatticeElement();
      ArrayTypeLatticeElement a2 = other.asArrayTypeLatticeElement();
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
        return objectClassType(appInfo, isNullable);
      }
      // If the nestings differ the join is the smallest nesting level.
      if (a1Nesting != a2Nesting) {
        int min = Math.min(a1Nesting, a2Nesting);
        return objectArrayType(appInfo, min, isNullable);
      }
      // For different class element types, compute the least upper bound of element types.
      DexType baseTypeLub =
          a1BaseReferenceType.computeLeastUpperBoundOfClasses(appInfo, a2BaseReferenceType);
      // Create the full array type.
      DexType arrayTypeLub = appInfo.dexItemFactory.createArrayType(a1Nesting, baseTypeLub);
      return fromDexType(arrayTypeLub, isNullable, appInfo);
    }
    if (isClassType()) {
      assert other.isClassType();
      ClassTypeLatticeElement c1 = asClassTypeLatticeElement();
      ClassTypeLatticeElement c2 = other.asClassTypeLatticeElement();
      DexType lubType =
          c1.getClassType().computeLeastUpperBoundOfClasses(appInfo, c2.getClassType());
      Set<DexType> c1lubItfs = c1.getInterfaces();
      Set<DexType> c2lubItfs = c2.getInterfaces();
      Set<DexType> lubItfs = null;
      if (c1lubItfs.size() == c2lubItfs.size() && c1lubItfs.containsAll(c2lubItfs)) {
        lubItfs = c1lubItfs;
      }
      if (lubItfs == null) {
        lubItfs = computeLeastUpperBoundOfInterfaces(appInfo, c1lubItfs, c2lubItfs);
      }
      return new ClassTypeLatticeElement(lubType, isNullable, lubItfs);
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

  public static Set<DexType> computeLeastUpperBoundOfInterfaces(
      AppInfo appInfo, Set<DexType> s1, Set<DexType> s2) {
    Set<DexType> cached = leastUpperBoundOfInterfacesTable.get(s1, s2);
    if (cached != null) {
      return cached;
    }
    cached = leastUpperBoundOfInterfacesTable.get(s2, s1);
    if (cached != null) {
      return cached;
    }
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
      boolean notTheLeast = false;
      for (DexType other : commonlyVisited) {
        if (other.isStrictSubtypeOf(itf, appInfo)) {
          notTheLeast = true;
          break;
        }
      }
      if (notTheLeast) {
        continue;
      }
      lubBuilder.add(itf);
    }
    Set<DexType> lub = lubBuilder.build();
    // Cache the computation result only if the given two sets of interfaces are different.
    if (s1.size() != s2.size() || !s1.containsAll(s2)) {
      synchronized (leastUpperBoundOfInterfacesTable) {
        leastUpperBoundOfInterfacesTable.put(s1, s2, lub);
      }
    }
    return lub;
  }

  public static TypeLatticeElement join(
      Iterable<TypeLatticeElement> typeLattices, AppInfo appInfo) {
    TypeLatticeElement result = BOTTOM;
    for (TypeLatticeElement other : typeLattices) {
      result = result.join(other, appInfo);
    }
    return result;
  }

  public static TypeLatticeElement joinTypes(
      Iterable<DexType> types, boolean isNullable, AppInfo appInfo) {
    TypeLatticeElement result = BOTTOM;
    for (DexType type : types) {
      result = result.join(fromDexType(type, isNullable, appInfo), appInfo);
    }
    return result;
  }

  /**
   * Determines the strict partial order of the given {@link TypeLatticeElement}s.
   *
   * @param other expected to be *strictly* bigger than {@param this}
   * @param appInfo {@link AppInfo} to compute the least upper bound of {@link TypeLatticeElement}
   * @return {@code true} if {@param this} is strictly less than {@param other}.
   */
  public boolean strictlyLessThan(TypeLatticeElement other, AppInfo appInfo) {
    if (equals(other)) {
      return false;
    }
    TypeLatticeElement lub = join(other, appInfo);
    return !equals(lub) && other.equals(lub);
  }

  /**
   * Determines the partial order of the given {@link TypeLatticeElement}s.
   *
   * @param other expected to be bigger than or equal to {@param this}
   * @param appInfo {@link AppInfo} to compute the least upper bound of {@link TypeLatticeElement}
   * @return {@code true} if {@param this} is less than or equal to {@param other}.
   */
  public boolean lessThanOrEqual(TypeLatticeElement other, AppInfo appInfo) {
    return equals(other) || strictlyLessThan(other, appInfo);
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

  public boolean isReference() {
    return false;
  }

  public boolean isArrayType() {
    return false;
  }

  public ArrayTypeLatticeElement asArrayTypeLatticeElement() {
    return null;
  }

  public boolean isClassType() {
    return false;
  }

  public ClassTypeLatticeElement asClassTypeLatticeElement() {
    return null;
  }

  public boolean isPrimitive() {
    return false;
  }

  public PrimitiveTypeLatticeElement asPrimitiveTypeLatticeElement() {
    return null;
  }

  public boolean isSingle() {
    return false;
  }

  public boolean isWide() {
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
        || isNull()
        || isInt()
        || isFloat()
        || isLong()
        || isDouble()
        || isBottom();
  }

  /**
   * Should use {@link #isConstantNull()} or {@link #isDefinitelyNull()} instead.
   */
  @Deprecated
  public boolean isNull() {
    return false;
  }

  /**
   * Determines if this type only includes null values that are defined by a const-number
   * instruction in the same enclosing method.
   *
   * These null values can be assigned to any type.
   */
  public boolean isConstantNull() {
    return isNull();
  }

  /**
   * Determines if this type only includes null values.
   *
   * These null values cannot be assigned to any type. For example, it is a type error to "throw v"
   * where the value `v` satisfies isDefinitelyNull(), because the static type of `v` may not be a
   * subtype of Throwable.
   */
  public boolean isDefinitelyNull() {
    return nullElement().isDefinitelyNull();
  }

  public boolean isReferenceInstance() {
    return false;
  }

  public int requiredRegisters() {
    assert !isBottom() && !isTop();
    return isWide() ? 2 : 1;
  }

  static TypeLatticeElement objectClassType(AppInfo appInfo, boolean isNullable) {
    return fromDexType(appInfo.dexItemFactory.objectType, isNullable, appInfo);
  }

  static TypeLatticeElement objectArrayType(AppInfo appInfo, int nesting, boolean isNullable) {
    return fromDexType(
        appInfo.dexItemFactory.createArrayType(nesting, appInfo.dexItemFactory.objectType),
        isNullable, appInfo);
  }

  public static TypeLatticeElement classClassType(AppInfo appInfo) {
    return fromDexType(appInfo.dexItemFactory.classType, false, appInfo);
  }

  public static TypeLatticeElement stringClassType(AppInfo appInfo) {
    return fromDexType(appInfo.dexItemFactory.stringType, false, appInfo);
  }

  public static TypeLatticeElement fromDexType(DexType type, boolean isNullable, AppInfo appInfo) {
    if (type == DexItemFactory.nullValueType) {
      return NULL;
    }
    if (type.isPrimitiveType()) {
      return PrimitiveTypeLatticeElement.fromDexType(type);
    }
    return appInfo.dexItemFactory.createReferenceTypeLatticeElement(type, isNullable, appInfo);
  }

  public static TypeLatticeElement fromNumericType(NumericType type) {
    switch (type) {
      case BYTE:
      case CHAR:
      case SHORT:
      case INT:
        return INT;
      case LONG:
        return LONG;
      case FLOAT:
        return FLOAT;
      case DOUBLE:
        return DOUBLE;
      default:
        throw new Unreachable("Unexpected numeric type: " + type);
    }
  }

  public boolean isValueTypeCompatible(TypeLatticeElement other) {
    return (isReference() && other.isReference())
        || (isSingle() && other.isSingle())
        || (isWide() && other.isWide());
  }

  public static TypeLatticeElement newArray(DexType arrayType, boolean isNullable) {
    return new ArrayTypeLatticeElement(arrayType, isNullable);
  }

  public TypeLatticeElement arrayGet(AppInfo appInfo) {
    return BOTTOM;
  }

  public TypeLatticeElement checkCast(AppInfo appInfo, DexType castType) {
    TypeLatticeElement castTypeLattice = fromDexType(castType, isNullable(), appInfo);
    if (lessThanOrEqual(castTypeLattice, appInfo)) {
      return this;
    }
    return castTypeLattice;
  }

  @Override
  public abstract String toString();

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();
}
