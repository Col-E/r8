// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;

public class ArrayTypeLatticeElement extends ReferenceTypeLatticeElement {

  private final TypeLatticeElement memberTypeLattice;

  public ArrayTypeLatticeElement(TypeLatticeElement memberTypeLattice, boolean isNullable) {
    super(isNullable, null);
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
  public ReferenceTypeLatticeElement getOrCreateDualLattice() {
    if (dual != null) {
      return dual;
    }
    synchronized (this) {
      if (dual == null) {
        ArrayTypeLatticeElement dual =
            new ArrayTypeLatticeElement(memberTypeLattice, !isNullable());
        linkDualLattice(this, dual);
      }
    }
    return this.dual;
  }

  @Override
  public TypeLatticeElement asNullable() {
    return isNullable() ? this : getOrCreateDualLattice();
  }

  @Override
  public TypeLatticeElement asNonNullable() {
    return !isNullable() ? this : getOrCreateDualLattice();
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
    if (isNullable() != other.isNullable()) {
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
    boolean isNullable = isNullable() || other.isNullable();
    if (aMember.isArrayType() && bMember.isArrayType()) {
      ReferenceTypeLatticeElement join =
          aMember.asArrayTypeLatticeElement().join(bMember.asArrayTypeLatticeElement(), appInfo);
      return join == null ? null : new ArrayTypeLatticeElement(join, isNullable);
    }
    if (aMember.isClassType() && bMember.isClassType()) {
      ClassTypeLatticeElement join =
          aMember.asClassTypeLatticeElement().join(bMember.asClassTypeLatticeElement(), appInfo);
      return join == null ? null : new ArrayTypeLatticeElement(join, isNullable);
    }
    if (aMember.isPrimitive() || bMember.isPrimitive()) {
      return objectClassType(appInfo, isNullable);
    }
    return objectArrayType(appInfo, isNullable);
  }

}
