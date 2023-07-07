// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.TypeReference;

public class TypeSubject extends Subject {

  private final CodeInspector codeInspector;
  private final DexType dexType;

  TypeSubject(CodeInspector codeInspector, DexType dexType) {
    this.codeInspector = codeInspector;
    this.dexType = dexType;
  }

  public String getTypeName() {
    return dexType.getTypeName();
  }

  public TypeReference getTypeReference() {
    return dexType.asTypeReference();
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if a type is renamed");
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if a type is synthetic");
  }

  public boolean is(String type) {
    return dexType.equals(codeInspector.toDexType(type));
  }

  public boolean is(TypeSubject type) {
    return dexType == type.dexType;
  }

  public boolean is(ClassSubject type) {
    return dexType == type.getDexProgramClass().type;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other instanceof TypeSubject) {
      TypeSubject o = (TypeSubject) other;
      assert codeInspector == o.codeInspector;
      return codeInspector == o.codeInspector && dexType == o.dexType;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return dexType.hashCode();
  }

  public String toString() {
    return dexType.toSourceString();
  }
}
