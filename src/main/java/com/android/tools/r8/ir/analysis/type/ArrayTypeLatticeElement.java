// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;

public class ArrayTypeLatticeElement extends ReferenceTypeLatticeElement {

  private final TypeLatticeElement memberTypeLattice;

  public ArrayTypeLatticeElement(
      TypeLatticeElement memberTypeLattice, Nullability nullability) {
    super(nullability, null);
    this.memberTypeLattice = memberTypeLattice;
  }

  public DexType getArrayType(DexItemFactory factory) {
    TypeLatticeElement baseTypeLattice = getArrayBaseTypeLattice();
    DexType baseType;
    if (baseTypeLattice.isPrimitive()) {
      baseType = baseTypeLattice.asPrimitiveTypeLatticeElement().toDexType(factory);
    } else {
      assert baseTypeLattice.isClassType();
      baseType = baseTypeLattice.asClassTypeLatticeElement().getClassType();
    }
    return factory.createArrayType(getNesting(), baseType);
  }

  int getNesting() {
    int nesting = 1;
    TypeLatticeElement member = getArrayMemberTypeAsMemberType();
    while (member.isArrayType()) {
      ++nesting;
      member = member.asArrayTypeLatticeElement().getArrayMemberTypeAsMemberType();
    }
    return nesting;
  }

  TypeLatticeElement getArrayMemberTypeAsMemberType() {
    return memberTypeLattice;
  }

  public TypeLatticeElement getArrayMemberTypeAsValueType() {
    return memberTypeLattice.isFineGrainedType() ? INT : memberTypeLattice;
  }

  private TypeLatticeElement getArrayBaseTypeLattice() {
    TypeLatticeElement base = getArrayMemberTypeAsMemberType();
    while (base.isArrayType()) {
      base = base.asArrayTypeLatticeElement().getArrayMemberTypeAsMemberType();
    }
    return base;
  }

  @Override
  ReferenceTypeLatticeElement createVariant(Nullability nullability) {
    if (this.nullability == nullability) {
      return this;
    }
    return new ArrayTypeLatticeElement(memberTypeLattice, nullability);
  }

  @Override
  public TypeLatticeElement asNullable() {
    return nullability.isNullable() ? this : getOrCreateVariant(maybeNull());
  }

  @Override
  public TypeLatticeElement asNonNullable() {
    return nullability.isDefinitelyNotNull() ? this : getOrCreateVariant(definitelyNotNull());
  }

  @Override
  public boolean isBasedOnMissingClass(AppInfo appInfo) {
    return memberTypeLattice.isBasedOnMissingClass(appInfo);
  }

  @Override
  public boolean isArrayType() {
    return true;
  }

  @Override
  public ArrayTypeLatticeElement asArrayTypeLatticeElement() {
    return this;
  }

  @Override
  public String toString() {
    return memberTypeLattice.toString() + "[]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ArrayTypeLatticeElement)) {
      return false;
    }
    ArrayTypeLatticeElement other = (ArrayTypeLatticeElement) o;
    if (nullability() != other.nullability()) {
      return false;
    }
    if (type != null && other.type != null && !type.equals(other.type)) {
      return false;
    }
    return memberTypeLattice.equals(other.memberTypeLattice);
  }

  @Override
  public int hashCode() {
    return (isNullable() ? 1 : -1) * memberTypeLattice.hashCode();
  }

  ReferenceTypeLatticeElement join(ArrayTypeLatticeElement other, AppInfo appInfo) {
    TypeLatticeElement aMember = getArrayMemberTypeAsMemberType();
    TypeLatticeElement bMember = other.getArrayMemberTypeAsMemberType();
    if (aMember.equals(bMember)) {
      // Return null indicating the join is the same as the member to avoid object allocation.
      return null;
    }
    Nullability nullability = nullability().join(other.nullability());
    if (aMember.isArrayType() && bMember.isArrayType()) {
      ReferenceTypeLatticeElement join =
          aMember.asArrayTypeLatticeElement().join(bMember.asArrayTypeLatticeElement(), appInfo);
      return join == null ? null : new ArrayTypeLatticeElement(join, nullability);
    }
    if (aMember.isClassType() && bMember.isClassType()) {
      ClassTypeLatticeElement join =
          aMember.asClassTypeLatticeElement().join(bMember.asClassTypeLatticeElement(), appInfo);
      return join == null ? null : new ArrayTypeLatticeElement(join, nullability);
    }
    if (aMember.isPrimitive() || bMember.isPrimitive()) {
      return objectClassType(appInfo, nullability);
    }
    return objectArrayType(appInfo, nullability);
  }

}
