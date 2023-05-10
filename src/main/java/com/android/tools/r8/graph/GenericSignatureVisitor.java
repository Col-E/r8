// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignature.ReturnType;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import java.util.List;

public interface GenericSignatureVisitor {

  default ClassSignature visitClassSignature(ClassSignature classSignature) {
    throw new Unreachable("Implement if visited");
  }

  default MethodTypeSignature visitMethodSignature(MethodTypeSignature methodSignature) {
    throw new Unreachable("Implement if visited");
  }

  default FieldTypeSignature visitFieldTypeSignature(FieldTypeSignature fieldSignature) {
    throw new Unreachable("Implement if visited");
  }

  default List<FormalTypeParameter> visitFormalTypeParameters(
      List<FormalTypeParameter> formalTypeParameters) {
    throw new Unreachable("Implement if visited");
  }

  default FormalTypeParameter visitFormalTypeParameter(FormalTypeParameter formalTypeParameter) {
    throw new Unreachable("Implement if visited");
  }

  default FieldTypeSignature visitClassBound(FieldTypeSignature fieldSignature) {
    throw new Unreachable("Implement if visited");
  }

  default List<FieldTypeSignature> visitInterfaceBounds(List<FieldTypeSignature> fieldSignatures) {
    throw new Unreachable("Implement if visited");
  }

  default FieldTypeSignature visitInterfaceBound(FieldTypeSignature fieldSignature) {
    throw new Unreachable("Implement if visited");
  }

  default ClassTypeSignature visitSuperClass(ClassTypeSignature classTypeSignatureOrNullForObject) {
    throw new Unreachable("Implement if visited");
  }

  default List<ClassTypeSignature> visitSuperInterfaces(
      List<ClassTypeSignature> interfaceSignatures) {
    throw new Unreachable("Implement if visited");
  }

  default ClassTypeSignature visitSuperInterface(ClassTypeSignature classTypeSignature) {
    throw new Unreachable("Implement if visited");
  }

  default TypeSignature visitTypeSignature(TypeSignature typeSignature) {
    throw new Unreachable("Implement if visited");
  }

  default ClassTypeSignature visitEnclosing(
      ClassTypeSignature enclosingSignature, ClassTypeSignature enclosedSignature) {
    throw new Unreachable("Implement if visited");
  }

  default ReturnType visitReturnType(ReturnType returnType) {
    throw new Unreachable("Implement if visited");
  }

  default List<TypeSignature> visitMethodTypeSignatures(List<TypeSignature> typeSignatures) {
    throw new Unreachable("Implement if visited");
  }

  default List<TypeSignature> visitThrowsSignatures(List<TypeSignature> typeSignatures) {
    throw new Unreachable("Implement if visited");
  }

  default List<FieldTypeSignature> visitTypeArguments(
      DexType originalType, DexType lookedUpType, List<FieldTypeSignature> typeArguments) {
    throw new Unreachable("Implement if visited");
  }

  default DexType visitType(DexType type) {
    throw new Unreachable("Implement if visited");
  }
}
