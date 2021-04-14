// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class GenericSignatureTypeVariableRemover {

  private final AppView<?> appView;
  private final Predicate<InnerClassAttribute> innerClassPruned;
  private final Predicate<EnclosingMethodAttribute> enclosingClassOrMethodPruned;

  public GenericSignatureTypeVariableRemover(
      AppView<?> appView,
      Predicate<InnerClassAttribute> innerClassPruned,
      Predicate<EnclosingMethodAttribute> enclosingClassOrMethodPruned) {
    this.appView = appView;
    this.innerClassPruned = innerClassPruned;
    this.enclosingClassOrMethodPruned = enclosingClassOrMethodPruned;
  }

  public void removeDeadGenericSignatureTypeVariables(DexProgramClass clazz) {
    if (clazz.getClassSignature().hasNoSignature() || clazz.getClassSignature().isInvalid()) {
      return;
    }
    Map<String, DexType> substitutions = new HashMap<>();
    getPrunedTypeParameters(clazz, substitutions, false);
    if (substitutions.isEmpty()) {
      return;
    }
    GenericSignaturePartialTypeArgumentApplier genericSignatureTypeArgumentApplier =
        GenericSignaturePartialTypeArgumentApplier.build(
            appView, clazz.getClassSignature(), substitutions);
    clazz.setClassSignature(
        genericSignatureTypeArgumentApplier.visitClassSignature(clazz.getClassSignature()));
    clazz
        .methods()
        .forEach(
            method -> {
              if (method.getGenericSignature().hasSignature()
                  && method.getGenericSignature().isValid()
                  && method.isVirtualMethod()) {
                method.setGenericSignature(
                    genericSignatureTypeArgumentApplier.visitMethodSignature(
                        method.getGenericSignature()));
              }
            });
    clazz
        .instanceFields()
        .forEach(
            field -> {
              if (field.getGenericSignature().hasSignature()
                  && field.getGenericSignature().isValid()) {
                field.setGenericSignature(
                    genericSignatureTypeArgumentApplier.visitFieldTypeSignature(
                        field.getGenericSignature()));
              }
            });
  }

  private void getPrunedTypeParameters(
      DexClass clazz, Map<String, DexType> substitutions, boolean seenPruned) {
    InnerClassAttribute innerClassAttribute = clazz.getInnerClassAttributeForThisClass();
    if (innerClassAttribute != null
        && innerClassAttribute.getOuter() != null
        && (seenPruned || innerClassPruned.test(innerClassAttribute))) {
      DexClass outerClass = appView.definitionFor(innerClassAttribute.getOuter());
      if (outerClass != null && outerClass.getClassSignature().isValid()) {
        updateMap(outerClass.getClassSignature().getFormalTypeParameters(), substitutions);
        getPrunedTypeParameters(outerClass, substitutions, true);
      }
    }
    if (clazz.getEnclosingMethodAttribute() != null
        && (seenPruned || enclosingClassOrMethodPruned.test(clazz.getEnclosingMethodAttribute()))) {
      DexClass outerClass =
          appView.definitionFor(clazz.getEnclosingMethodAttribute().getEnclosingType());
      if (outerClass == null) {
        return;
      }
      if (clazz.getEnclosingMethodAttribute().getEnclosingMethod() != null) {
        DexEncodedMethod enclosingMethod =
            outerClass.lookupMethod(clazz.getEnclosingMethodAttribute().getEnclosingMethod());
        if (enclosingMethod != null) {
          updateMap(enclosingMethod.getGenericSignature().getFormalTypeParameters(), substitutions);
          if (enclosingMethod.isStatic()) {
            return;
          }
        }
      }
      if (outerClass.getClassSignature().isValid()) {
        updateMap(outerClass.getClassSignature().getFormalTypeParameters(), substitutions);
      }
      getPrunedTypeParameters(outerClass, substitutions, true);
    }
  }

  private void updateMap(
      List<FormalTypeParameter> formalTypeParameters, Map<String, DexType> substitutions) {
    // We are updating the map going from inner most to outer, thus the any overriding formal type
    // parameters will be in the substitution map already.
    formalTypeParameters.forEach(
        parameter -> {
          if (substitutions.containsKey(parameter.getName())) {
            return;
          }
          // The null substitution will use the wildcard as argument, which is smaller than using
          // Ljava/lang/Object;
          DexType substitution = null;
          FieldTypeSignature classBound = parameter.getClassBound();
          if (classBound != null && classBound.isClassTypeSignature()) {
            substitution = classBound.asClassTypeSignature().type();
          }
          substitutions.put(parameter.getName(), substitution);
        });
  }
}
