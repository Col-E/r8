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
import com.android.tools.r8.graph.GenericSignature.StarFieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import com.android.tools.r8.graph.GenericSignature.WildcardIndicator;
import com.android.tools.r8.utils.ListUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GenericSignaturePartialTypeArgumentApplier implements GenericSignatureVisitor {

  private final Map<String, DexType> substitutions;
  private final DexType objectType;
  private final Set<String> introducedClassTypeVariables = new HashSet<>();
  private final Set<String> introducedMethodTypeVariables = new HashSet<>();

  // Wildcards can only be called be used in certain positions:
  // https://docs.oracle.com/javase/tutorial/java/generics/wildcards.html
  private boolean canUseWildcardInArguments = true;

  private GenericSignaturePartialTypeArgumentApplier(
      Map<String, DexType> substitutions, DexType objectType) {
    this.substitutions = substitutions;
    this.objectType = objectType;
  }

  public static GenericSignaturePartialTypeArgumentApplier build(
      AppView<?> appView, ClassSignature classSignature, Map<String, DexType> substitutions) {
    GenericSignaturePartialTypeArgumentApplier applier =
        new GenericSignaturePartialTypeArgumentApplier(
            substitutions, appView.dexItemFactory().objectType);
    classSignature.formalTypeParameters.forEach(
        parameter -> applier.introducedClassTypeVariables.add(parameter.name));
    return applier;
  }

  @Override
  public ClassSignature visitClassSignature(ClassSignature classSignature) {
    return classSignature.visit(this);
  }

  @Override
  public MethodTypeSignature visitMethodSignature(MethodTypeSignature methodSignature) {
    assert introducedMethodTypeVariables.isEmpty();
    methodSignature.formalTypeParameters.forEach(
        parameter -> introducedMethodTypeVariables.add(parameter.name));
    MethodTypeSignature rewritten = methodSignature.visit(this);
    introducedMethodTypeVariables.clear();
    return rewritten;
  }

  @Override
  public DexType visitType(DexType type) {
    return type;
  }

  @Override
  public TypeSignature visitTypeSignature(TypeSignature typeSignature) {
    if (typeSignature.isBaseTypeSignature()) {
      return typeSignature;
    }
    return visitFieldTypeSignature(typeSignature.asFieldTypeSignature());
  }

  @Override
  public FormalTypeParameter visitFormalTypeParameter(FormalTypeParameter formalTypeParameter) {
    FormalTypeParameter rewritten = formalTypeParameter.visit(this);
    // Guard against no information being present in bounds.
    assert (rewritten.getClassBound() != null && rewritten.getClassBound().hasSignature())
        || !rewritten.getInterfaceBounds().isEmpty();
    return rewritten;
  }

  @Override
  public List<FieldTypeSignature> visitInterfaceBounds(List<FieldTypeSignature> fieldSignatures) {
    if (fieldSignatures == null || fieldSignatures.isEmpty()) {
      return fieldSignatures;
    }
    return ListUtils.mapOrElse(fieldSignatures, this::visitFieldTypeSignature);
  }

  @Override
  public List<ClassTypeSignature> visitSuperInterfaces(
      List<ClassTypeSignature> interfaceSignatures) {
    if (interfaceSignatures.isEmpty()) {
      return interfaceSignatures;
    }
    canUseWildcardInArguments = false;
    List<ClassTypeSignature> map =
        ListUtils.mapOrElse(interfaceSignatures, this::visitSuperInterface);
    canUseWildcardInArguments = true;
    return map;
  }

  @Override
  public List<FieldTypeSignature> visitTypeArguments(List<FieldTypeSignature> typeArguments) {
    if (typeArguments.isEmpty()) {
      return typeArguments;
    }
    return ListUtils.mapOrElse(typeArguments, this::visitFieldTypeSignature);
  }

  @Override
  public ClassTypeSignature visitSuperInterface(ClassTypeSignature classTypeSignature) {
    return classTypeSignature.visit(this);
  }

  @Override
  public FieldTypeSignature visitClassBound(FieldTypeSignature fieldSignature) {
    return visitFieldTypeSignature(fieldSignature);
  }

  @Override
  public FieldTypeSignature visitInterfaceBound(FieldTypeSignature fieldSignature) {
    return visitFieldTypeSignature(fieldSignature);
  }

  @Override
  public ClassTypeSignature visitSimpleClass(ClassTypeSignature classTypeSignature) {
    return classTypeSignature.visit(this);
  }

  @Override
  public List<TypeSignature> visitThrowsSignatures(List<TypeSignature> typeSignatures) {
    if (typeSignatures.isEmpty()) {
      return typeSignatures;
    }
    return ListUtils.mapOrElse(typeSignatures, this::visitTypeSignature);
  }

  @Override
  public ReturnType visitReturnType(ReturnType returnType) {
    if (returnType.isVoidDescriptor()) {
      return returnType;
    }
    TypeSignature originalSignature = returnType.typeSignature;
    TypeSignature rewrittenSignature = visitTypeSignature(originalSignature);
    if (originalSignature == rewrittenSignature) {
      return returnType;
    }
    return new ReturnType(rewrittenSignature);
  }

  @Override
  public List<FormalTypeParameter> visitFormalTypeParameters(
      List<FormalTypeParameter> formalTypeParameters) {
    if (formalTypeParameters.isEmpty()) {
      return formalTypeParameters;
    }
    return ListUtils.mapOrElse(formalTypeParameters, this::visitFormalTypeParameter);
  }

  @Override
  public List<TypeSignature> visitMethodTypeSignatures(List<TypeSignature> typeSignatures) {
    if (typeSignatures.isEmpty()) {
      return typeSignatures;
    }
    return ListUtils.mapOrElse(typeSignatures, this::visitTypeSignature);
  }

  @Override
  public ClassTypeSignature visitSuperClass(ClassTypeSignature classTypeSignature) {
    canUseWildcardInArguments = false;
    ClassTypeSignature visit = classTypeSignature.visit(this);
    canUseWildcardInArguments = true;
    return visit;
  }

  @Override
  public FieldTypeSignature visitFieldTypeSignature(FieldTypeSignature fieldSignature) {
    if (fieldSignature.isStar()) {
      return fieldSignature;
    } else if (fieldSignature.isClassTypeSignature()) {
      return fieldSignature.asClassTypeSignature().visit(this);
    } else if (fieldSignature.isArrayTypeSignature()) {
      return fieldSignature.asArrayTypeSignature().visit(this);
    } else {
      assert fieldSignature.isTypeVariableSignature();
      String typeVariableName = fieldSignature.asTypeVariableSignature().typeVariable();
      if (substitutions.containsKey(typeVariableName)
          && !introducedClassTypeVariables.contains(typeVariableName)
          && !introducedMethodTypeVariables.contains(typeVariableName)) {
        DexType substitution = substitutions.get(typeVariableName);
        if (substitution == null) {
          substitution = objectType;
        }
        return substitution == objectType && canUseWildcardInArguments
            ? StarFieldTypeSignature.getStarFieldTypeSignature()
            : new ClassTypeSignature(substitution).asArgument(WildcardIndicator.NONE);
      }
      return fieldSignature;
    }
  }
}
