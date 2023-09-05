// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.GenericSignature.getEmptyTypeArguments;
import static com.google.common.base.Predicates.alwaysFalse;

import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignature.ReturnType;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import com.android.tools.r8.graph.GenericSignature.WildcardIndicator;
import com.android.tools.r8.utils.ListUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class GenericSignatureTypeRewriter {

  private final DexItemFactory factory;
  private final Predicate<DexType> wasPruned;
  private final Function<DexType, DexType> lookupType;
  private final DexProgramClass context;

  private final ClassTypeSignature objectTypeSignature;
  private final Predicate<DexType> hasGenericTypeVariables;

  public GenericSignatureTypeRewriter(
      AppView<?> appView, DexProgramClass context, Predicate<DexType> hasGenericTypeVariables) {
    this(
        appView.dexItemFactory(),
        appView.appInfo().hasLiveness()
            ? appView.appInfo().withLiveness()::wasPruned
            : alwaysFalse(),
        appView.graphLens()::lookupType,
        context,
        hasGenericTypeVariables);
  }

  public GenericSignatureTypeRewriter(
      DexItemFactory factory,
      Predicate<DexType> wasPruned,
      Function<DexType, DexType> lookupType,
      DexProgramClass context,
      Predicate<DexType> hasGenericTypeVariables) {
    this.factory = factory;
    this.wasPruned = wasPruned;
    this.lookupType = lookupType;
    this.context = context;
    this.hasGenericTypeVariables = hasGenericTypeVariables;
    objectTypeSignature = new ClassTypeSignature(factory.objectType, getEmptyTypeArguments());
  }

  public ClassSignature rewrite(ClassSignature classSignature) {
    if (classSignature.hasNoSignature() || classSignature.isInvalid()) {
      return classSignature;
    }
    return new GenericSignatureRewriter(factory).visitClassSignature(classSignature);
  }

  public FieldTypeSignature rewrite(FieldTypeSignature fieldTypeSignature) {
    if (fieldTypeSignature.hasNoSignature() || fieldTypeSignature.isInvalid()) {
      return fieldTypeSignature;
    }
    FieldTypeSignature rewrittenSignature =
        new GenericSignatureRewriter(factory).visitFieldTypeSignature(fieldTypeSignature);
    return rewrittenSignature == null ? FieldTypeSignature.noSignature() : rewrittenSignature;
  }

  public MethodTypeSignature rewrite(MethodTypeSignature methodTypeSignature) {
    if (methodTypeSignature.hasNoSignature() || methodTypeSignature.isInvalid()) {
      return methodTypeSignature;
    }
    return new GenericSignatureRewriter(factory).visitMethodSignature(methodTypeSignature);
  }

  private class GenericSignatureRewriter implements GenericSignatureVisitor {

    private final DexItemFactory factory;

    GenericSignatureRewriter(DexItemFactory factory) {
      this.factory = factory;
    }

    @Override
    public ClassSignature visitClassSignature(ClassSignature classSignature) {
      return classSignature.visit(this, factory);
    }

    @Override
    public MethodTypeSignature visitMethodSignature(MethodTypeSignature methodSignature) {
      return methodSignature.visit(this);
    }

    @Override
    public FieldTypeSignature visitFieldTypeSignature(FieldTypeSignature fieldSignature) {
      if (fieldSignature.isStar() || fieldSignature.isTypeVariableSignature()) {
        return fieldSignature;
      } else if (fieldSignature.isArrayTypeSignature()) {
        return fieldSignature.asArrayTypeSignature().visit(this);
      } else {
        assert fieldSignature.isClassTypeSignature();
        return fieldSignature.asClassTypeSignature().visit(this);
      }
    }

    @Override
    public TypeSignature visitTypeSignature(TypeSignature typeSignature) {
      if (typeSignature.isBaseTypeSignature()) {
        return typeSignature;
      } else {
        return visitFieldTypeSignature(typeSignature.asFieldTypeSignature());
      }
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
    public FormalTypeParameter visitFormalTypeParameter(FormalTypeParameter formalTypeParameter) {
      FormalTypeParameter rewritten = formalTypeParameter.visit(this);
      // Guard against no information being present in bounds.
      boolean isEmptyClassBound =
          rewritten.getClassBound() == null || rewritten.getClassBound().hasNoSignature();
      if (isEmptyClassBound && rewritten.getInterfaceBounds().isEmpty()) {
        return new FormalTypeParameter(
            formalTypeParameter.getName(), objectTypeSignature, rewritten.getInterfaceBounds());
      }
      return rewritten;
    }

    @Override
    public ClassTypeSignature visitSuperClass(
        ClassTypeSignature classTypeSignatureOrNullForObject) {
      if (classTypeSignatureOrNullForObject == null) {
        return classTypeSignatureOrNullForObject;
      }
      return classTypeSignatureOrNullForObject.visit(this);
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public List<ClassTypeSignature> visitSuperInterfaces(
        List<ClassTypeSignature> interfaceSignatures) {
      if (interfaceSignatures.isEmpty()) {
        return interfaceSignatures;
      }
      List<ClassTypeSignature> rewrittenInterfaces =
          ListUtils.mapOrElse(interfaceSignatures, this::visitSuperInterface);
      // Map against the actual interfaces implemented on the class for us to still preserve
      // type arguments.
      List<ClassTypeSignature> finalInterfaces = new ArrayList<>(rewrittenInterfaces.size());
      context.interfaces.forEach(
          iface -> {
            ClassTypeSignature rewrittenSignature =
                ListUtils.firstMatching(rewrittenInterfaces, rewritten -> rewritten.type == iface);
            finalInterfaces.add(
                rewrittenSignature != null ? rewrittenSignature : new ClassTypeSignature(iface));
          });
      return finalInterfaces;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public ClassTypeSignature visitSuperInterface(ClassTypeSignature classTypeSignature) {
      ClassTypeSignature rewritten = classTypeSignature.visit(this);
      return rewritten == null || rewritten.type() == context.type ? null : rewritten;
    }

    @Override
    public List<TypeSignature> visitMethodTypeSignatures(List<TypeSignature> typeSignatures) {
      if (typeSignatures.isEmpty()) {
        return typeSignatures;
      }
      return ListUtils.mapOrElse(
          typeSignatures,
          typeSignature -> {
            TypeSignature rewrittenSignature = visitTypeSignature(typeSignature);
            return rewrittenSignature == null ? objectTypeSignature : rewrittenSignature;
          });
    }

    @Override
    public ReturnType visitReturnType(ReturnType returnType) {
      if (returnType.isVoidDescriptor()) {
        return ReturnType.VOID;
      } else {
        TypeSignature originalType = returnType.typeSignature();
        TypeSignature rewrittenType = visitTypeSignature(originalType);
        if (rewrittenType == null) {
          return ReturnType.VOID;
        } else if (rewrittenType == originalType) {
          return returnType;
        } else {
          return new ReturnType(rewrittenType);
        }
      }
    }

    @Override
    public List<TypeSignature> visitThrowsSignatures(List<TypeSignature> typeSignatures) {
      if (typeSignatures.isEmpty()) {
        return typeSignatures;
      }
      // If a throwing type is no longer found we remove it from the signature.
      return ListUtils.mapOrElse(typeSignatures, this::visitTypeSignature);
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
      if (fieldSignatures.isEmpty()) {
        return fieldSignatures;
      }
      return ListUtils.mapOrElse(fieldSignatures, this::visitFieldTypeSignature);
    }

    @Override
    public FieldTypeSignature visitInterfaceBound(FieldTypeSignature fieldSignature) {
      return visitFieldTypeSignature(fieldSignature);
    }

    @Override
    public ClassTypeSignature visitEnclosing(
        ClassTypeSignature enclosingSignature, ClassTypeSignature enclosedSignature) {
      return enclosingSignature.visit(this);
    }

    @Override
    public List<FieldTypeSignature> visitTypeArguments(
        DexType originalType, DexType lookedUpType, List<FieldTypeSignature> typeArguments) {
      assert lookedUpType != null;
      if (typeArguments.isEmpty()) {
        return typeArguments;
      }
      // If the original type has been pruned it must be because the old type has been merged into
      // the looked up type. We can therefore not guarantee the type arguments to be consistent and
      // have to remove them.
      if (wasPruned.test(originalType) || !hasGenericTypeVariables.test(lookedUpType)) {
        return getEmptyTypeArguments();
      }
      return ListUtils.mapOrElse(
          typeArguments,
          fieldTypeSignature -> {
            FieldTypeSignature rewrittenSignature = visitFieldTypeSignature(fieldTypeSignature);
            return rewrittenSignature == null
                ? objectTypeSignature.asArgument(WildcardIndicator.NONE)
                : rewrittenSignature;
          });
    }

    @Override
    public DexType visitType(DexType type) {
      DexType rewrittenType = lookupType.apply(type);
      return wasPruned.test(rewrittenType) ? null : rewrittenType;
    }
  }
}
