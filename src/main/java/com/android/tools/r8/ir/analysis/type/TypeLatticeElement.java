// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.MemberType;
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
  public static final ReferenceTypeLatticeElement REFERENCE =
      ReferenceTypeLatticeElement.getReferenceTypeLatticeElement();

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
    // From now on, this and other are reference types, but might be imprecise yet.
    assert isReference() && other.isReference();
    if (!isPreciseType() || !other.isPreciseType()) {
      if (isReferenceInstance()) {
        return this;
      }
      assert other.isReferenceInstance();
      return other;
    }
    // From now on, this and other are precise reference types, i.e., either ArrayType or ClassType.
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
      DexType lub =
          a1BaseReferenceType.computeLeastUpperBoundOfClasses(appInfo, a2BaseReferenceType);
      // Create the full array type.
      DexType arrayTypeLub = appInfo.dexItemFactory.createArrayType(a1Nesting, lub);
      return new ArrayTypeLatticeElement(arrayTypeLub, isNullable);
    }
    if (isClassType()) {
      assert other.isClassType();
      ClassTypeLatticeElement c1 = asClassTypeLatticeElement();
      ClassTypeLatticeElement c2 = other.asClassTypeLatticeElement();
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
    return (l1, l2) -> l1.join(l2, appInfo);
  }

  public static TypeLatticeElement join(Stream<TypeLatticeElement> types, AppInfo appInfo) {
    BinaryOperator<TypeLatticeElement> joiner = joiner(appInfo);
    return types.reduce(BottomTypeLatticeElement.getInstance(), joiner, joiner);
  }

  public static TypeLatticeElement join(
      Stream<DexType> types, boolean isNullable, AppInfo appInfo) {
    return join(types.map(t -> fromDexType(t, isNullable, appInfo)), appInfo);
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
        || isInt()
        || isFloat()
        || isLong()
        || isDouble();
  }

  public boolean isNull() {
    return false;
  }

  public boolean isReferenceInstance() {
    return false;
  }

  public int requiredRegisters() {
    assert !isBottom() && !isTop();
    return isWide() ? 2 : 1;
  }

  public static ClassTypeLatticeElement objectClassType(AppInfo appInfo, boolean isNullable) {
    return new ClassTypeLatticeElement(appInfo.dexItemFactory.objectType, isNullable);
  }

  static ArrayTypeLatticeElement objectArrayType(AppInfo appInfo, int nesting, boolean isNullable) {
    return new ArrayTypeLatticeElement(
        appInfo.dexItemFactory.createArrayType(nesting, appInfo.dexItemFactory.objectType),
        isNullable);
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

  public static TypeLatticeElement fromDexType(DexType type) {
    if (type == DexItemFactory.nullValueType) {
      return NULL;
    }
    return fromTypeDescriptorChar((char) type.descriptor.content[0]);
  }

  public static TypeLatticeElement fromTypeDescriptorChar(char descriptor) {
    switch (descriptor) {
      case 'L':
        // TODO(jsjeon): class type with Object?
      case '[':
        // TODO(jsjeon): array type with Object?
        return REFERENCE;
      default:
        return PrimitiveTypeLatticeElement.fromTypeDescriptorChar(descriptor);
    }
  }

  public static TypeLatticeElement fromMemberType(MemberType type) {
    switch (type) {
      case BOOLEAN:
      case BYTE:
      case CHAR:
      case SHORT:
      case INT:
        return INT;
      case FLOAT:
        return FLOAT;
      case INT_OR_FLOAT:
        return SINGLE;
      case LONG:
        return LONG;
      case DOUBLE:
        return DOUBLE;
      case LONG_OR_DOUBLE:
        return WIDE;
      case OBJECT:
        return REFERENCE;
      default:
        throw new Unreachable("Unexpected member type: " + type);
    }
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
