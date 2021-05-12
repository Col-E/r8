// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.GenericSignatureTypeVariableRemover.TypeParameterContext.empty;
import static com.google.common.base.Predicates.alwaysFalse;

import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class GenericSignatureTypeVariableRemover {

  private final DexType objectType;
  private final Map<DexReference, TypeParameterSubstitutions> formalsInfo;
  private final Map<DexReference, DexReference> enclosingInfo;

  private static class TypeParameterSubstitutions {

    private final Map<String, DexType> parametersWithBounds;

    private TypeParameterSubstitutions(Map<String, DexType> parametersWithBounds) {
      this.parametersWithBounds = parametersWithBounds;
    }

    private static TypeParameterSubstitutions create(List<FormalTypeParameter> formals) {
      Map<String, DexType> map = new IdentityHashMap<>();
      formals.forEach(
          formal -> {
            DexType bound = null;
            if (formal.getClassBound() != null
                && formal.getClassBound().hasSignature()
                && formal.getClassBound().isClassTypeSignature()) {
              bound = formal.getClassBound().asClassTypeSignature().type;
            } else if (!formal.getInterfaceBounds().isEmpty()
                && formal.getInterfaceBounds().get(0).isClassTypeSignature()) {
              bound = formal.getInterfaceBounds().get(0).asClassTypeSignature().type;
            }
            map.put(formal.getName(), bound);
          });
      return new TypeParameterSubstitutions(map);
    }
  }

  static class TypeParameterContext {

    private static final TypeParameterContext EMPTY =
        new TypeParameterContext(Collections.emptyMap(), Collections.emptySet());

    private final Map<String, DexType> prunedParametersWithBounds;
    private final Set<String> liveParameters;

    private TypeParameterContext(
        Map<String, DexType> prunedParametersWithBounds, Set<String> liveParameters) {
      this.prunedParametersWithBounds = prunedParametersWithBounds;
      this.liveParameters = liveParameters;
    }

    private TypeParameterContext combine(TypeParameterSubstitutions information, boolean dead) {
      if (information == null) {
        return this;
      }
      HashMap<String, DexType> newPruned = new HashMap<>(prunedParametersWithBounds);
      HashSet<String> newLiveParameters = new HashSet<>(liveParameters);
      information.parametersWithBounds.forEach(
          (param, type) -> {
            if (dead) {
              newPruned.put(param, type);
              newLiveParameters.remove(param);
            } else {
              newLiveParameters.add(param);
              newPruned.remove(param);
            }
          });
      return new TypeParameterContext(newPruned, newLiveParameters);
    }

    public static TypeParameterContext empty() {
      return EMPTY;
    }
  }

  private GenericSignatureTypeVariableRemover(
      Map<DexReference, TypeParameterSubstitutions> formalsInfo,
      Map<DexReference, DexReference> enclosingInfo,
      DexType objectType) {
    this.formalsInfo = formalsInfo;
    this.enclosingInfo = enclosingInfo;
    this.objectType = objectType;
  }

  public static GenericSignatureTypeVariableRemover create(
      AppView<?> appView, List<DexProgramClass> programClasses) {
    Map<DexReference, TypeParameterSubstitutions> formalsInfo = new IdentityHashMap<>();
    Map<DexReference, DexReference> enclosingInfo = new IdentityHashMap<>();
    programClasses.forEach(
        clazz -> {
          // Build up a map of type variables to bounds for every reference such that we can
          // lookup the information even after we prune the generic signatures.
          if (clazz.getClassSignature().isValid()) {
            formalsInfo.put(
                clazz.getReference(),
                TypeParameterSubstitutions.create(clazz.classSignature.getFormalTypeParameters()));
            clazz.forEachProgramMethod(
                method -> {
                  MethodTypeSignature methodSignature =
                      method.getDefinition().getGenericSignature();
                  if (methodSignature.isValid()) {
                    formalsInfo.put(
                        method.getReference(),
                        TypeParameterSubstitutions.create(
                            methodSignature.getFormalTypeParameters()));
                  }
                });
          }
          // Build up an enclosing class context such that the enclosing class can be looked up
          // even after inner class and enclosing method attribute attributes are removed.
          InnerClassAttribute innerClassAttribute = clazz.getInnerClassAttributeForThisClass();
          if (innerClassAttribute != null) {
            enclosingInfo.put(clazz.getType(), innerClassAttribute.getOuter());
          }
          EnclosingMethodAttribute enclosingMethodAttribute = clazz.getEnclosingMethodAttribute();
          if (enclosingMethodAttribute != null) {
            enclosingInfo.put(
                clazz.getType(),
                enclosingMethodAttribute.getEnclosingMethod() != null
                    ? enclosingMethodAttribute.getEnclosingMethod()
                    : enclosingMethodAttribute.getEnclosingClass());
          }
        });
    return new GenericSignatureTypeVariableRemover(
        formalsInfo, enclosingInfo, appView.dexItemFactory().objectType);
  }

  private TypeParameterContext computeTypeParameterContext(
      AppView<?> appView,
      DexReference reference,
      Predicate<DexType> wasPruned,
      boolean seenPruned) {
    if (reference == null) {
      return empty();
    }
    DexType contextType = reference.getContextType();
    // TODO(b/187035453): We should visit generic signatures in the enqueuer.
    DexClass clazz = appView.appInfo().definitionForWithoutExistenceAssert(contextType);
    boolean prunedHere = seenPruned || clazz == null;
    if (appView.hasLiveness()
        && appView.withLiveness().appInfo().getMissingClasses().contains(contextType)) {
      prunedHere = seenPruned;
    }
    // Lookup the formals in the enclosing context.
    TypeParameterContext typeParameterContext =
        computeTypeParameterContext(
                appView,
                enclosingInfo.get(contextType),
                wasPruned,
                prunedHere
                    || hasPrunedRelationship(
                        appView, enclosingInfo.get(contextType), contextType, wasPruned))
            // Add formals for the context
            .combine(formalsInfo.get(contextType), prunedHere);
    if (!reference.isDexMethod()) {
      return typeParameterContext;
    }
    prunedHere = prunedHere || clazz == null || clazz.lookupMethod(reference.asDexMethod()) == null;
    return typeParameterContext.combine(formalsInfo.get(reference), prunedHere);
  }

  private static boolean hasPrunedRelationship(
      AppView<?> appView,
      DexReference enclosingReference,
      DexType enclosedClassType,
      Predicate<DexType> wasPruned) {
    assert enclosedClassType != null;
    if (enclosingReference == null) {
      // There is no relationship, so it does not really matter what we return since the
      // algorithm will return the base case.
      return true;
    }
    if (wasPruned.test(enclosingReference.getContextType()) || wasPruned.test(enclosedClassType)) {
      return true;
    }
    DexClass enclosingClass = appView.definitionFor(enclosingReference.getContextType());
    DexClass enclosedClass = appView.definitionFor(enclosedClassType);
    if (enclosingClass == null || enclosedClass == null) {
      return true;
    }
    if (enclosingReference.isDexMethod()) {
      return enclosedClass.getEnclosingMethodAttribute() == null
          || enclosedClass.getEnclosingMethodAttribute().getEnclosingMethod() != enclosingReference;
    } else {
      InnerClassAttribute innerClassAttribute = enclosedClass.getInnerClassAttributeForThisClass();
      if (innerClassAttribute != null) {
        return innerClassAttribute.getOuter() != enclosingReference;
      }
      return enclosedClass.getEnclosingMethodAttribute() == null
          || enclosedClass.getEnclosingMethodAttribute().getEnclosingClass() != enclosingReference;
    }
  }

  private static boolean hasGenericTypeVariables(
      AppView<?> appView, DexType type, Predicate<DexType> wasPruned) {
    if (wasPruned.test(type)) {
      return false;
    }
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null || clazz.isNotProgramClass() || clazz.getClassSignature().isInvalid()) {
      return true;
    }
    return !clazz.getClassSignature().getFormalTypeParameters().isEmpty();
  }

  public void removeDeadGenericSignatureTypeVariables(AppView<?> appView) {
    Predicate<DexType> wasPruned =
        appView.hasLiveness() ? appView.withLiveness().appInfo()::wasPruned : alwaysFalse();
    GenericSignaturePartialTypeArgumentApplier baseArgumentApplier =
        GenericSignaturePartialTypeArgumentApplier.build(
            objectType,
            (enclosing, enclosed) -> hasPrunedRelationship(appView, enclosing, enclosed, wasPruned),
            type -> hasGenericTypeVariables(appView, type, wasPruned));
    appView
        .appInfo()
        .classes()
        .forEach(
            clazz -> {
              if (clazz.getClassSignature().isInvalid()) {
                return;
              }
              TypeParameterContext computedClassFormals =
                  computeTypeParameterContext(appView, clazz.getType(), wasPruned, false);
              GenericSignaturePartialTypeArgumentApplier classArgumentApplier =
                  baseArgumentApplier.addSubstitutionsAndVariables(
                      computedClassFormals.prunedParametersWithBounds,
                      computedClassFormals.liveParameters);
              clazz.setClassSignature(
                  classArgumentApplier.visitClassSignature(clazz.getClassSignature()));
              clazz
                  .methods()
                  .forEach(
                      method -> {
                        MethodTypeSignature methodSignature = method.getGenericSignature();
                        if (methodSignature.hasSignature()
                            && method.getGenericSignature().isValid()) {
                          // The reflection api do not distinguish static methods context from
                          // virtual methods.
                          method.setGenericSignature(
                              classArgumentApplier
                                  .buildForMethod(methodSignature.getFormalTypeParameters())
                                  .visitMethodSignature(methodSignature));
                        }
                      });
              clazz
                  .instanceFields()
                  .forEach(
                      field -> {
                        if (field.getGenericSignature().hasSignature()
                            && field.getGenericSignature().isValid()) {
                          field.setGenericSignature(
                              classArgumentApplier.visitFieldTypeSignature(
                                  field.getGenericSignature()));
                        }
                      });
            });
  }
}
