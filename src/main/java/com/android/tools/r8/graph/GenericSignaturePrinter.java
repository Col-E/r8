// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.graph.GenericSignature.WildcardIndicator;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.List;
import java.util.function.Predicate;

public class GenericSignaturePrinter implements GenericSignatureVisitor {

  private final NamingLens namingLens;
  private final Predicate<DexType> isTypeMissing;

  public GenericSignaturePrinter(NamingLens namingLens, Predicate<DexType> isTypeMissing) {
    this.namingLens = namingLens;
    this.isTypeMissing = isTypeMissing;
  }

  private final StringBuilder sb = new StringBuilder();

  @Override
  public ClassSignature visitClassSignature(ClassSignature classSignature) {
    classSignature.visitWithoutRewrite(this);
    return classSignature;
  }

  @Override
  public FieldTypeSignature visitFieldTypeSignature(FieldTypeSignature fieldSignature) {
    printFieldTypeSignature(fieldSignature, false);
    return fieldSignature;
  }

  @Override
  public MethodTypeSignature visitMethodSignature(MethodTypeSignature methodSignature) {
    return methodSignature.visit(this);
  }

  @Override
  public List<TypeSignature> visitMethodTypeSignatures(List<TypeSignature> typeSignatures) {
    sb.append("(");
    typeSignatures.forEach(this::visitTypeSignature);
    sb.append(")");
    return typeSignatures;
  }

  @Override
  public ReturnType visitReturnType(ReturnType returnType) {
    if (returnType.isVoidDescriptor()) {
      sb.append("V");
    } else {
      visitTypeSignature(returnType.typeSignature);
    }
    return returnType;
  }

  @Override
  public List<TypeSignature> visitThrowsSignatures(List<TypeSignature> typeSignatures) {
    for (TypeSignature typeSignature : typeSignatures) {
      sb.append("^");
      visitTypeSignature(typeSignature);
    }
    return typeSignatures;
  }

  @Override
  public List<FormalTypeParameter> visitFormalTypeParameters(
      List<FormalTypeParameter> formalTypeParameters) {
    if (formalTypeParameters.isEmpty()) {
      return formalTypeParameters;
    }
    sb.append("<");
    formalTypeParameters.forEach(this::visitFormalTypeParameter);
    sb.append(">");
    return formalTypeParameters;
  }

  @Override
  public FieldTypeSignature visitClassBound(FieldTypeSignature fieldSignature) {
    sb.append(":");
    if (fieldSignature.hasNoSignature()) {
      return fieldSignature;
    }
    printFieldTypeSignature(fieldSignature, false);
    return fieldSignature;
  }

  @Override
  public List<FieldTypeSignature> visitInterfaceBounds(List<FieldTypeSignature> fieldSignatures) {
    fieldSignatures.forEach(this::visitInterfaceBound);
    return fieldSignatures;
  }

  @Override
  public FieldTypeSignature visitInterfaceBound(FieldTypeSignature fieldSignature) {
    sb.append(":");
    printFieldTypeSignature(fieldSignature, false);
    return fieldSignature;
  }

  @Override
  public ClassTypeSignature visitSuperClass(ClassTypeSignature classTypeSignatureOrNullForObject) {
    if (classTypeSignatureOrNullForObject == null) {
      sb.append("Ljava/lang/Object;");
    } else {
      printFieldTypeSignature(classTypeSignatureOrNullForObject, false);
    }
    return classTypeSignatureOrNullForObject;
  }

  @Override
  public List<ClassTypeSignature> visitSuperInterfaces(
      List<ClassTypeSignature> interfaceSignatures) {
    interfaceSignatures.forEach(this::visitSuperInterface);
    return interfaceSignatures;
  }

  @Override
  public ClassTypeSignature visitSuperInterface(ClassTypeSignature classTypeSignature) {
    printFieldTypeSignature(classTypeSignature, false);
    return classTypeSignature;
  }

  @Override
  public TypeSignature visitTypeSignature(TypeSignature typeSignature) {
    if (typeSignature.isBaseTypeSignature()) {
      DexType type = typeSignature.asBaseTypeSignature().type;
      sb.append(type.toDescriptorString());
    } else {
      printFieldTypeSignature(typeSignature.asFieldTypeSignature(), false);
    }
    return typeSignature;
  }

  @Override
  public ClassTypeSignature visitEnclosing(
      ClassTypeSignature enclosingSignature, ClassTypeSignature enclosedSignature) {
    printFieldTypeSignature(enclosingSignature, true);
    return enclosingSignature;
  }

  @Override
  public List<FieldTypeSignature> visitTypeArguments(
      DexType originalType, DexType lookedUpType, List<FieldTypeSignature> typeArguments) {
    if (typeArguments.isEmpty()) {
      return typeArguments;
    }
    sb.append("<");
    for (FieldTypeSignature typeArgument : typeArguments) {
      WildcardIndicator wildcardIndicator = typeArgument.getWildcardIndicator();
      if (wildcardIndicator != WildcardIndicator.NONE) {
        assert wildcardIndicator != WildcardIndicator.NOT_AN_ARGUMENT;
        sb.append(wildcardIndicator == WildcardIndicator.POSITIVE ? "+" : "-");
      }
      visitTypeSignature(typeArgument);
    }
    sb.append(">");
    return typeArguments;
  }

  @Override
  public FormalTypeParameter visitFormalTypeParameter(FormalTypeParameter formalTypeParameter) {
    sb.append(formalTypeParameter.name);
    return formalTypeParameter.visit(this);
  }

  private void printFieldTypeSignature(
      FieldTypeSignature fieldTypeSignature, boolean printingOuter) {
    // For inner member classes we only print the inner name and the type-arguments.
    if (fieldTypeSignature.isStar()) {
      sb.append("*");
    } else if (fieldTypeSignature.isTypeVariableSignature()) {
      sb.append("T").append(fieldTypeSignature.asTypeVariableSignature().typeVariable).append(";");
    } else if (fieldTypeSignature.isArrayTypeSignature()) {
      sb.append("[");
      fieldTypeSignature.asArrayTypeSignature().visit(this);
    } else {
      assert fieldTypeSignature.isClassTypeSignature();
      ClassTypeSignature classTypeSignature = fieldTypeSignature.asClassTypeSignature();
      if (classTypeSignature.hasNoSignature()) {
        return;
      }
      // Visit enclosing before printing the type name to ensure we
      if (classTypeSignature.enclosingTypeSignature != null) {
        visitEnclosing(classTypeSignature.enclosingTypeSignature, classTypeSignature);
      }
      String renamedString = namingLens.lookupDescriptor(classTypeSignature.type).toString();
      if (classTypeSignature.enclosingTypeSignature == null) {
        sb.append("L").append(DescriptorUtils.getBinaryNameFromDescriptor(renamedString));
      } else {
        DexType enclosingType = classTypeSignature.enclosingTypeSignature.type;
        String outerDescriptor = namingLens.lookupDescriptor(enclosingType).toString();
        String innerClassName = DescriptorUtils.getInnerClassName(outerDescriptor, renamedString);
        if (innerClassName == null && isTypeMissing.test(classTypeSignature.type)) {
          assert renamedString.equals(classTypeSignature.type.toDescriptorString());
          innerClassName =
              DescriptorUtils.getInnerClassName(enclosingType.toDescriptorString(), renamedString);
        }
        if (innerClassName == null) {
          // We can no longer encode the inner name in the generic signature.
          return;
        }
        sb.append(".").append(innerClassName);
      }
      visitTypeArguments(null, null, classTypeSignature.typeArguments);
      if (!printingOuter) {
        sb.append(";");
      }
    }
  }

  @Override
  public DexType visitType(DexType type) {
    // We need to delay printing of class type until enclosing class has been visited. We therefore
    // only print in printFieldTypeSignature.
    return type;
  }

  @Override
  public String toString() {
    return sb.toString();
  }
}
