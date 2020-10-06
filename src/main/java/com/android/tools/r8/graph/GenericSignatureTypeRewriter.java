// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.GenericSignature.EMPTY_SUPER_INTERFACES;
import static com.android.tools.r8.graph.GenericSignature.EMPTY_TYPE_ARGUMENTS;
import static com.android.tools.r8.graph.GenericSignature.EMPTY_TYPE_PARAMS;
import static com.android.tools.r8.graph.GenericSignature.NO_FIELD_TYPE_SIGNATURE;
import static com.android.tools.r8.graph.GenericSignature.StarFieldTypeSignature.STAR_FIELD_TYPE_SIGNATURE;

import com.android.tools.r8.graph.GenericSignature.ArrayTypeSignature;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.ArrayList;
import java.util.List;

public class GenericSignatureTypeRewriter {

  private final AppView<?> appView;
  private final DexProgramClass context;

  private final FieldTypeSignature objectTypeSignature;

  public GenericSignatureTypeRewriter(AppView<?> appView, DexProgramClass context) {
    this.appView = appView;
    this.context = context;
    objectTypeSignature =
        new ClassTypeSignature(appView.dexItemFactory().objectType, EMPTY_TYPE_ARGUMENTS);
  }

  public ClassSignature rewrite(ClassSignature classSignature) {
    if (classSignature.hasNoSignature() || appView.graphLens().isIdentityLens()) {
      return classSignature;
    }
    return new ClassSignatureRewriter().run(classSignature);
  }

  public FieldTypeSignature rewrite(FieldTypeSignature fieldTypeSignature) {
    if (fieldTypeSignature.hasNoSignature() || appView.graphLens().isIdentityLens()) {
      return fieldTypeSignature;
    }
    return new TypeSignatureRewriter().run(fieldTypeSignature);
  }

  private class ClassSignatureRewriter implements GenericSignatureVisitor {

    private final List<FormalTypeParameter> rewrittenTypeParameters = new ArrayList<>();
    private ClassTypeSignature rewrittenSuperClass;
    private final List<ClassTypeSignature> rewrittenSuperInterfaces = new ArrayList<>();

    @Override
    public void visitClassSignature(ClassSignature classSignature) {
      classSignature.visit(this);
    }

    @Override
    public void visitFormalTypeParameters(List<FormalTypeParameter> formalTypeParameters) {
      for (FormalTypeParameter formalTypeParameter : formalTypeParameters) {
        rewrittenTypeParameters.add(new FormalTypeParameterRewriter().run(formalTypeParameter));
      }
    }

    @Override
    public void visitSuperClass(ClassTypeSignature classTypeSignature) {
      rewrittenSuperClass = new ClassTypeSignatureRewriter(true).run(classTypeSignature);
      if (rewrittenSuperClass == null) {
        rewrittenSuperClass =
            new ClassTypeSignature(appView.dexItemFactory().objectType, EMPTY_TYPE_ARGUMENTS);
      }
    }

    @Override
    public void visitSuperInterface(ClassTypeSignature classTypeSignature) {
      ClassTypeSignature superInterface =
          new ClassTypeSignatureRewriter(true).run(classTypeSignature);
      if (superInterface != null) {
        rewrittenSuperInterfaces.add(superInterface);
      }
    }

    private ClassSignature run(ClassSignature classSignature) {
      classSignature.visit(this);
      if (rewrittenTypeParameters.isEmpty()
          && rewrittenSuperInterfaces.isEmpty()
          && rewrittenSuperClass.isNoSignature()
          && rewrittenSuperClass.type == appView.dexItemFactory().objectType) {
        return ClassSignature.NO_CLASS_SIGNATURE;
      }
      return new ClassSignature(
          rewrittenTypeParameters.isEmpty() ? EMPTY_TYPE_PARAMS : rewrittenTypeParameters,
          rewrittenSuperClass,
          rewrittenSuperInterfaces.isEmpty() ? EMPTY_SUPER_INTERFACES : rewrittenSuperInterfaces);
    }
  }

  private class FormalTypeParameterRewriter implements GenericSignatureVisitor {

    private FieldTypeSignature rewrittenClassBound = NO_FIELD_TYPE_SIGNATURE;
    private final List<FieldTypeSignature> rewrittenInterfaceBounds = new ArrayList<>();

    @Override
    public void visitClassBound(FieldTypeSignature fieldSignature) {
      rewrittenClassBound = new TypeSignatureRewriter().run(fieldSignature);
    }

    @Override
    public void visitInterfaceBound(FieldTypeSignature fieldSignature) {
      FieldTypeSignature interfaceBound = new TypeSignatureRewriter().run(fieldSignature);
      if (interfaceBound != null) {
        rewrittenInterfaceBounds.add(interfaceBound);
      }
    }

    private FormalTypeParameter run(FormalTypeParameter formalTypeParameter) {
      formalTypeParameter.visit(this);
      // Guard against the case where we have <T::...> that is, no class or interfaces bounds.
      if (rewrittenInterfaceBounds.isEmpty()
          && (rewrittenClassBound == null || !rewrittenClassBound.hasSignature())) {
        rewrittenClassBound = objectTypeSignature;
      }
      return new FormalTypeParameter(
          formalTypeParameter.name,
          rewrittenClassBound == null ? NO_FIELD_TYPE_SIGNATURE : rewrittenClassBound,
          rewrittenInterfaceBounds.isEmpty() ? EMPTY_TYPE_ARGUMENTS : rewrittenInterfaceBounds);
    }
  }

  private class TypeSignatureRewriter implements GenericSignatureVisitor {

    private TypeSignature run(TypeSignature typeSignature) {
      if (typeSignature.isBaseTypeSignature()) {
        return typeSignature;
      }
      assert typeSignature.isFieldTypeSignature();
      return run(typeSignature.asFieldTypeSignature());
    }

    private FieldTypeSignature run(FieldTypeSignature fieldTypeSignature) {
      if (fieldTypeSignature.isStar()) {
        return fieldTypeSignature;
      }
      if (fieldTypeSignature.isTypeVariableSignature()) {
        return fieldTypeSignature;
      }
      if (fieldTypeSignature.isArrayTypeSignature()) {
        ArrayTypeSignature arrayTypeSignature = fieldTypeSignature.asArrayTypeSignature();
        TypeSignature rewrittenElement = run(arrayTypeSignature.elementSignature);
        if (rewrittenElement == null) {
          return new ArrayTypeSignature(objectTypeSignature);
        }
        return rewrittenElement.toArrayTypeSignature();
      }
      assert fieldTypeSignature.isClassTypeSignature();
      ClassTypeSignature classTypeSignature = fieldTypeSignature.asClassTypeSignature();
      if (classTypeSignature.isNoSignature()) {
        return classTypeSignature;
      }
      return new ClassTypeSignatureRewriter(false).run(classTypeSignature);
    }
  }

  private class ClassTypeSignatureRewriter implements GenericSignatureVisitor {

    private final AppInfoWithLiveness appInfoWithLiveness;
    private final boolean isSuperClassOrInterface;

    // These fields are updated when iterating the modeled structure.
    private DexType currentType;

    // The following references are used to have a head and tail pointer to the classTypeSignature
    // link we are building. The topClassSignature will have a reference to the top-most package
    // and class-name. The parentClassSignature is a pointer pointing to the tail always and will
    // be linked and updated when calling ClassTypeSignature.link.
    private ClassTypeSignature topClassSignature;
    private ClassTypeSignature parentClassSignature;

    private ClassTypeSignatureRewriter(boolean isSuperClassOrInterface) {
      appInfoWithLiveness =
          appView.appInfo().hasLiveness() ? appView.appInfo().withLiveness() : null;
      this.isSuperClassOrInterface = isSuperClassOrInterface;
    }

    @Override
    public void visitSimpleClass(ClassTypeSignature classTypeSignature) {
      currentType = getTarget(classTypeSignature.type);
      if (currentType == null) {
        return;
      }
      classTypeSignature.visit(this);
    }

    @Override
    public void visitTypeArguments(List<FieldTypeSignature> typeArguments) {
      ClassTypeSignature newClassTypeSignature;
      if (typeArguments.isEmpty()) {
        newClassTypeSignature = new ClassTypeSignature(currentType, EMPTY_TYPE_ARGUMENTS);
      } else {
        List<FieldTypeSignature> rewrittenTypeArguments = new ArrayList<>(typeArguments.size());
        for (FieldTypeSignature typeArgument : typeArguments) {
          if (typeArgument.isStar()) {
            rewrittenTypeArguments.add(typeArgument);
            continue;
          }
          FieldTypeSignature rewritten = new TypeSignatureRewriter().run(typeArgument);
          if (rewritten != null) {
            rewrittenTypeArguments.add(rewritten.asArgument(typeArgument.getWildcardIndicator()));
          } else {
            rewrittenTypeArguments.add(STAR_FIELD_TYPE_SIGNATURE);
          }
        }
        newClassTypeSignature = new ClassTypeSignature(currentType, rewrittenTypeArguments);
      }
      if (topClassSignature == null) {
        topClassSignature = newClassTypeSignature;
        parentClassSignature = newClassTypeSignature;
      } else {
        ClassTypeSignature.link(parentClassSignature, newClassTypeSignature);
        parentClassSignature = newClassTypeSignature;
      }
    }

    private ClassTypeSignature run(ClassTypeSignature classTypeSignature) {
      currentType = getTarget(classTypeSignature.type);
      if (currentType == null) {
        return null;
      }
      classTypeSignature.visit(this);
      return topClassSignature;
    }

    private DexType getTarget(DexType type) {
      DexType rewrittenType = appView.graphLens().lookupType(type);
      if (appInfoWithLiveness != null && appInfoWithLiveness.wasPruned(rewrittenType)) {
        return null;
      }
      if (isSuperClassOrInterface && context.type == rewrittenType) {
        return null;
      }
      return rewrittenType;
    }
  }
}
