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

  default void visitClassSignature(ClassSignature classSignature) {
    throw new Unreachable("Implement if visited");
  }

  default void visitMethodSignature(MethodTypeSignature methodSignature) {
    throw new Unreachable("Implement if visited");
  }

  default void visitFieldTypeSignature(FieldTypeSignature fieldSignature) {
    throw new Unreachable("Implement if visited");
  }

  default void visitFormalTypeParameters(List<FormalTypeParameter> formalTypeParameters) {
    throw new Unreachable("Implement if visited");
  }

  default void visitClassBound(FieldTypeSignature fieldSignature) {
    throw new Unreachable("Implement if visited");
  }

  default void visitInterfaceBound(FieldTypeSignature fieldSignature) {
    throw new Unreachable("Implement if visited");
  }

  default void visitSuperClass(ClassTypeSignature classTypeSignature) {
    throw new Unreachable("Implement if visited");
  }

  default void visitSuperInterface(ClassTypeSignature classTypeSignature) {
    throw new Unreachable("Implement if visited");
  }

  default void visitTypeSignature(TypeSignature typeSignature) {
    throw new Unreachable("Implement if visited");
  }

  default void visitSimpleClass(ClassTypeSignature classTypeSignature) {
    throw new Unreachable("Implement if visited");
  }

  default void visitReturnType(ReturnType returnType) {
    throw new Unreachable("Implement if visited");
  }

  default void visitMethodTypeSignatures(List<TypeSignature> typeSignatures) {
    throw new Unreachable("Implement if visited");
  }

  default void visitThrowsSignatures(List<TypeSignature> typeSignatures) {
    throw new Unreachable("Implement if visited");
  }

  default void visitTypeArguments(List<FieldTypeSignature> typeArguments) {
    throw new Unreachable("Implement if visited");
  }
}
