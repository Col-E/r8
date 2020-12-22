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
import com.android.tools.r8.graph.analysis.EnqueuerAnalysis;
import com.android.tools.r8.shaking.Enqueuer.EnqueuerDefinitionSupplier;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import java.util.List;

public class GenericSignatureEnqueuerAnalysis extends EnqueuerAnalysis {

  private final EnqueuerDefinitionSupplier enqueuerDefinitionSupplier;

  public GenericSignatureEnqueuerAnalysis(EnqueuerDefinitionSupplier enqueuerDefinitionSupplier) {
    this.enqueuerDefinitionSupplier = enqueuerDefinitionSupplier;
  }

  @Override
  public void processNewlyLiveClass(DexProgramClass clazz, EnqueuerWorklist worklist) {
    new GenericSignatureTypeVisitor(clazz, enqueuerDefinitionSupplier)
        .visitClassSignature(clazz.getClassSignature());
  }

  @Override
  public void processNewlyLiveField(ProgramField field, ProgramDefinition context) {
    new GenericSignatureTypeVisitor(context, enqueuerDefinitionSupplier)
        .visitFieldTypeSignature(field.getDefinition().getGenericSignature());
  }

  @Override
  public void processNewlyLiveMethod(ProgramMethod method, ProgramDefinition context) {
    new GenericSignatureTypeVisitor(context, enqueuerDefinitionSupplier)
        .visitMethodSignature(method.getDefinition().getGenericSignature());
  }

  private static class GenericSignatureTypeVisitor implements GenericSignatureVisitor {

    private final ProgramDefinition context;
    private final EnqueuerDefinitionSupplier enqueuerDefinitionSupplier;

    private GenericSignatureTypeVisitor(
        ProgramDefinition context, EnqueuerDefinitionSupplier enqueuerDefinitionSupplier) {
      this.context = context;
      this.enqueuerDefinitionSupplier = enqueuerDefinitionSupplier;
    }

    @Override
    public void visitClassSignature(ClassSignature classSignature) {
      if (classSignature.hasNoSignature()) {
        return;
      }
      classSignature.visit(this);
    }

    @Override
    public void visitMethodSignature(MethodTypeSignature methodSignature) {
      if (methodSignature.hasNoSignature()) {
        return;
      }
      methodSignature.visit(this);
    }

    @Override
    public void visitFieldTypeSignature(FieldTypeSignature fieldSignature) {
      if (fieldSignature.hasNoSignature()) {
        return;
      }
      if (fieldSignature.isStar()) {
        return;
      }
      if (fieldSignature.isTypeVariableSignature()) {
        return;
      }
      if (fieldSignature.isArrayTypeSignature()) {
        fieldSignature.asArrayTypeSignature().visit(this);
        return;
      }
      assert fieldSignature.isClassTypeSignature();
      visitClassTypeSignature(fieldSignature.asClassTypeSignature());
    }

    private void visitClassTypeSignature(ClassTypeSignature classTypeSignature) {
      enqueuerDefinitionSupplier.definitionFor(classTypeSignature.type, context);
      classTypeSignature.visit(this);
    }

    @Override
    public void visitFormalTypeParameters(List<FormalTypeParameter> formalTypeParameters) {
      formalTypeParameters.forEach(formalTypeParameter -> formalTypeParameter.visit(this));
    }

    @Override
    public void visitClassBound(FieldTypeSignature fieldSignature) {
      visitFieldTypeSignature(fieldSignature);
    }

    @Override
    public void visitInterfaceBound(FieldTypeSignature fieldSignature) {
      visitFieldTypeSignature(fieldSignature);
    }

    @Override
    public void visitSuperClass(ClassTypeSignature classTypeSignature) {
      visitClassTypeSignature(classTypeSignature);
    }

    @Override
    public void visitSuperInterface(ClassTypeSignature classTypeSignature) {
      visitClassTypeSignature(classTypeSignature);
    }

    @Override
    public void visitTypeSignature(TypeSignature typeSignature) {
      if (typeSignature.isBaseTypeSignature()) {
        return;
      }
      assert typeSignature.isFieldTypeSignature();
      visitFieldTypeSignature(typeSignature.asFieldTypeSignature());
    }

    @Override
    public void visitSimpleClass(ClassTypeSignature classTypeSignature) {
      visitClassTypeSignature(classTypeSignature);
    }

    @Override
    public void visitReturnType(ReturnType returnType) {
      if (returnType.isVoidDescriptor()) {
        return;
      }
      visitTypeSignature(returnType.typeSignature);
    }

    @Override
    public void visitMethodTypeSignatures(List<TypeSignature> typeSignatures) {
      typeSignatures.forEach(this::visitTypeSignature);
    }

    @Override
    public void visitThrowsSignatures(List<TypeSignature> typeSignatures) {
      typeSignatures.forEach(this::visitTypeSignature);
    }

    @Override
    public void visitTypeArguments(List<FieldTypeSignature> typeArguments) {
      typeArguments.forEach(this::visitFieldTypeSignature);
    }
  }
}
