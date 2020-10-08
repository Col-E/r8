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
  public void visitClassSignature(ClassSignature classSignature) {
    classSignature.visit(this);
  }

  @Override
  public void visitFieldTypeSignature(FieldTypeSignature fieldSignature) {
    printFieldTypeSignature(fieldSignature, false);
  }

  @Override
  public void visitMethodSignature(MethodTypeSignature methodSignature) {
    methodSignature.visit(this);
  }

  @Override
  public void visitMethodTypeSignatures(List<TypeSignature> typeSignatures) {
    sb.append("(");
    typeSignatures.forEach(this::visitTypeSignature);
    sb.append(")");
  }

  @Override
  public void visitReturnType(ReturnType returnType) {
    if (returnType.isVoidDescriptor()) {
      sb.append("V");
    } else {
      visitTypeSignature(returnType.typeSignature);
    }
  }

  @Override
  public void visitThrowsSignatures(List<TypeSignature> typeSignatures) {
    for (TypeSignature typeSignature : typeSignatures) {
      sb.append("^");
      visitTypeSignature(typeSignature);
    }
  }

  @Override
  public void visitFormalTypeParameters(List<FormalTypeParameter> formalTypeParameters) {
    if (formalTypeParameters.isEmpty()) {
      return;
    }
    sb.append("<");
    for (FormalTypeParameter formalTypeParameter : formalTypeParameters) {
      sb.append(formalTypeParameter.name);
      formalTypeParameter.visit(this);
    }
    sb.append(">");
  }

  @Override
  public void visitClassBound(FieldTypeSignature fieldSignature) {
    sb.append(":");
    printFieldTypeSignature(fieldSignature, false);
  }

  @Override
  public void visitInterfaceBound(FieldTypeSignature fieldSignature) {
    sb.append(":");
    printFieldTypeSignature(fieldSignature, false);
  }

  @Override
  public void visitSuperClass(ClassTypeSignature classTypeSignature) {
    printFieldTypeSignature(classTypeSignature, false);
  }

  @Override
  public void visitSuperInterface(ClassTypeSignature classTypeSignature) {
    printFieldTypeSignature(classTypeSignature, false);
  }

  @Override
  public void visitTypeSignature(TypeSignature typeSignature) {
    if (typeSignature.isBaseTypeSignature()) {
      DexType type = typeSignature.asBaseTypeSignature().type;
      sb.append(type.toDescriptorString());
    } else {
      printFieldTypeSignature(typeSignature.asFieldTypeSignature(), false);
    }
  }

  @Override
  public void visitSimpleClass(ClassTypeSignature classTypeSignature) {
    printFieldTypeSignature(classTypeSignature, true);
  }

  @Override
  public void visitTypeArguments(List<FieldTypeSignature> typeArguments) {
    if (typeArguments.isEmpty()) {
      return;
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
  }

  private void printFieldTypeSignature(
      FieldTypeSignature fieldTypeSignature, boolean printingInner) {
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
      if (classTypeSignature.isNoSignature()) {
        return;
      }
      String renamedString = namingLens.lookupDescriptor(classTypeSignature.type).toString();
      if (!printingInner) {
        sb.append("L").append(DescriptorUtils.getBinaryNameFromDescriptor(renamedString));
      } else {
        assert classTypeSignature.enclosingTypeSignature != null;
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
      classTypeSignature.visit(this);
      if (!printingInner) {
        sb.append(";");
      }
    }
  }

  @Override
  public String toString() {
    return sb.toString();
  }
}
