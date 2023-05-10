// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignature.ReturnType;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import java.util.List;
import java.util.function.BiConsumer;

class GenericSignatureTypeVisitor implements GenericSignatureVisitor {

  private final ProgramDefinition context;
  private final BiConsumer<DexType, ProgramDefinition> visitedTypeConsumer;

  GenericSignatureTypeVisitor(
      ProgramDefinition context, BiConsumer<DexType, ProgramDefinition> visitedTypeConsumer) {
    this.context = context;
    this.visitedTypeConsumer = visitedTypeConsumer;
  }

  @Override
  public ClassSignature visitClassSignature(ClassSignature classSignature) {
    if (classSignature.hasNoSignature()) {
      return classSignature;
    }
    classSignature.visitWithoutRewrite(this);
    return classSignature;
  }

  @Override
  public MethodTypeSignature visitMethodSignature(MethodTypeSignature methodSignature) {
    if (methodSignature.hasNoSignature()) {
      return methodSignature;
    }
    return methodSignature.visit(this);
  }

  @Override
  public FieldTypeSignature visitFieldTypeSignature(FieldTypeSignature fieldSignature) {
    if (fieldSignature.hasNoSignature()) {
      return fieldSignature;
    }
    if (fieldSignature.isStar()) {
      return fieldSignature;
    }
    if (fieldSignature.isTypeVariableSignature()) {
      return fieldSignature;
    }
    if (fieldSignature.isArrayTypeSignature()) {
      fieldSignature.asArrayTypeSignature().visit(this);
      return fieldSignature;
    }
    assert fieldSignature.isClassTypeSignature();
    return fieldSignature.asClassTypeSignature().visit(this);
  }

  @Override
  public List<FormalTypeParameter> visitFormalTypeParameters(
      List<FormalTypeParameter> formalTypeParameters) {
    formalTypeParameters.forEach(this::visitFormalTypeParameter);
    return formalTypeParameters;
  }

  @Override
  public FieldTypeSignature visitClassBound(FieldTypeSignature fieldSignature) {
    if (fieldSignature.hasNoSignature()) {
      return fieldSignature;
    }
    return visitFieldTypeSignature(fieldSignature);
  }

  @Override
  public List<FieldTypeSignature> visitInterfaceBounds(List<FieldTypeSignature> fieldSignatures) {
    fieldSignatures.forEach(this::visitInterfaceBound);
    return fieldSignatures;
  }

  @Override
  public FieldTypeSignature visitInterfaceBound(FieldTypeSignature fieldSignature) {
    return visitFieldTypeSignature(fieldSignature);
  }

  @Override
  public ClassTypeSignature visitSuperClass(ClassTypeSignature classTypeSignatureOrNullForObject) {
    if (classTypeSignatureOrNullForObject == null) {
      return classTypeSignatureOrNullForObject;
    }
    return classTypeSignatureOrNullForObject.visit(this);
  }

  @Override
  public List<ClassTypeSignature> visitSuperInterfaces(
      List<ClassTypeSignature> interfaceSignatures) {
    interfaceSignatures.forEach(this::visitSuperInterface);
    return interfaceSignatures;
  }

  @Override
  public ClassTypeSignature visitSuperInterface(ClassTypeSignature classTypeSignature) {
    return classTypeSignature.visit(this);
  }

  @Override
  public TypeSignature visitTypeSignature(TypeSignature typeSignature) {
    if (typeSignature.isBaseTypeSignature()) {
      return typeSignature;
    }
    assert typeSignature.isFieldTypeSignature();
    return visitFieldTypeSignature(typeSignature.asFieldTypeSignature());
  }

  @Override
  public ClassTypeSignature visitEnclosing(
      ClassTypeSignature enclosingSignature, ClassTypeSignature enclosedSignature) {
    return enclosingSignature.visit(this);
  }

  @Override
  public ReturnType visitReturnType(ReturnType returnType) {
    if (returnType.isVoidDescriptor()) {
      return returnType;
    }
    visitTypeSignature(returnType.typeSignature);
    return returnType;
  }

  @Override
  public List<TypeSignature> visitMethodTypeSignatures(List<TypeSignature> typeSignatures) {
    typeSignatures.forEach(this::visitTypeSignature);
    return typeSignatures;
  }

  @Override
  public List<TypeSignature> visitThrowsSignatures(List<TypeSignature> typeSignatures) {
    typeSignatures.forEach(this::visitTypeSignature);
    return typeSignatures;
  }

  @Override
  public List<FieldTypeSignature> visitTypeArguments(
      DexType originalType, DexType lookedUpType, List<FieldTypeSignature> typeArguments) {
    typeArguments.forEach(this::visitFieldTypeSignature);
    return typeArguments;
  }

  @Override
  public FormalTypeParameter visitFormalTypeParameter(FormalTypeParameter formalTypeParameter) {
    return formalTypeParameter.visit(this);
  }

  @Override
  public DexType visitType(DexType type) {
    visitedTypeConsumer.accept(type, context);
    return type;
  }
}
