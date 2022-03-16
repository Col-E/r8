// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.EnumMembers;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.ir.optimize.info.LibraryOptimizationInfoInitializerFeedback;
import com.android.tools.r8.ir.optimize.info.field.EmptyInstanceFieldInitializationInfoCollection;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoFactory;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfoCollection;
import com.android.tools.r8.ir.optimize.info.initializer.NonTrivialInstanceInitializerInfo;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Set;

public class LibraryOptimizationInfoInitializer {

  private final AbstractValueFactory abstractValueFactory;
  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;

  private final LibraryOptimizationInfoInitializerFeedback feedback =
      LibraryOptimizationInfoInitializerFeedback.getInstance();
  private final Set<DexType> modeledLibraryTypes = Sets.newIdentityHashSet();

  LibraryOptimizationInfoInitializer(AppView<?> appView) {
    this.abstractValueFactory = appView.abstractValueFactory();
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  void run() {
    modelInstanceInitializers();
    modelLibraryMethodsNonNullParamOrThrow();
    modelLibraryMethodsReturningNonNull();
    modelLibraryMethodsReturningReceiver();
    modelLibraryMethodsWithoutSideEffects();
    modelRequireNonNullMethods();
  }

  Set<DexType> getModeledLibraryTypes() {
    return modeledLibraryTypes;
  }

  private void modelInstanceInitializers() {
    DexEncodedMethod objectConstructor = lookupMethod(dexItemFactory.objectMembers.constructor);
    if (objectConstructor != null) {
      InstanceFieldInitializationInfoCollection fieldInitializationInfos =
          EmptyInstanceFieldInitializationInfoCollection.getInstance();
      feedback.setInstanceInitializerInfoCollection(
          objectConstructor,
          InstanceInitializerInfoCollection.of(
              NonTrivialInstanceInitializerInfo.builder(fieldInitializationInfos).build()));
    }

    EnumMembers enumMembers = dexItemFactory.enumMembers;
    DexEncodedMethod enumConstructor = lookupMethod(enumMembers.constructor);
    if (enumConstructor != null) {
      LibraryFieldSynthesis.synthesizeEnumFields(appView);
      InstanceFieldInitializationInfoFactory factory =
          appView.instanceFieldInitializationInfoFactory();
      InstanceFieldInitializationInfoCollection fieldInitializationInfos =
          InstanceFieldInitializationInfoCollection.builder()
              .recordInitializationInfo(
                  enumMembers.nameField, factory.createArgumentInitializationInfo(1))
              .recordInitializationInfo(
                  enumMembers.ordinalField, factory.createArgumentInitializationInfo(2))
              .build();
      feedback.setInstanceInitializerInfoCollection(
          enumConstructor,
          InstanceInitializerInfoCollection.of(
              NonTrivialInstanceInitializerInfo.builder(fieldInitializationInfos)
                  .setParent(dexItemFactory.objectMembers.constructor)
                  .build()));
    }
  }

  private void modelStaticFinalLibraryFields(Set<DexEncodedField> finalLibraryFields) {
    for (DexEncodedField field : finalLibraryFields) {
      if (field.isStatic()) {
        feedback.recordLibraryFieldHasAbstractValue(
            field,
            abstractValueFactory.createSingleFieldValue(field.getReference(), ObjectState.empty()));
      }
    }
  }

  private void modelLibraryMethodsNonNullParamOrThrow() {
    dexItemFactory.libraryMethodsNonNullParamOrThrow.forEach(
        (method, nonNullParamOrThrow) -> {
          DexEncodedMethod definition = lookupMethod(method);
          if (definition != null) {
            assert nonNullParamOrThrow.length > 0;
            int size = nonNullParamOrThrow[nonNullParamOrThrow.length - 1] + 1;
            BitSet bitSet = new BitSet(size);
            for (int argumentIndex : nonNullParamOrThrow) {
              assert argumentIndex < size;
              bitSet.set(argumentIndex);
            }
            feedback.setNonNullParamOrThrow(definition, bitSet);

            // Also set non-null-param-on-normal-exits info.
            if (definition.getOptimizationInfo().hasNonNullParamOnNormalExits()) {
              definition.getOptimizationInfo().getNonNullParamOnNormalExits().or(bitSet);
            } else {
              feedback.setNonNullParamOnNormalExits(definition, (BitSet) bitSet.clone());
            }
          }
        });
  }

  private void modelLibraryMethodsReturningNonNull() {
    for (DexMethod method : dexItemFactory.libraryMethodsReturningNonNull) {
      DexEncodedMethod definition = lookupMethod(method);
      if (definition != null) {
        assert definition.getOptimizationInfo().getDynamicType().isUnknown()
            || definition.getOptimizationInfo().getDynamicType().isNotNullType();
        feedback.setDynamicReturnType(definition, appView, DynamicType.definitelyNotNull());
      }
    }
  }

  private void modelLibraryMethodsReturningReceiver() {
    for (DexMethod method : dexItemFactory.libraryMethodsReturningReceiver) {
      DexEncodedMethod definition = lookupMethod(method);
      if (definition != null) {
        feedback.methodReturnsArgument(definition, 0);
      }
    }
  }

  private void modelLibraryMethodsWithoutSideEffects() {
    appView
        .getLibraryMethodSideEffectModelCollection()
        .forEachSideEffectFreeFinalMethod(
            method -> {
              DexEncodedMethod definition = lookupMethod(method);
              if (definition != null) {
                feedback.methodMayNotHaveSideEffects(definition);
              }
            });
  }

  private void modelRequireNonNullMethods() {
    for (DexMethod requireNonNullMethod : dexItemFactory.objectsMethods.requireNonNullMethods()) {
      DexEncodedMethod definition = lookupMethod(requireNonNullMethod);
      if (definition != null) {
        feedback.methodReturnsArgument(definition, 0);
      }
    }
  }

  private DexEncodedMethod lookupMethod(DexMethod method) {
    DexClass holder = appView.definitionForHolder(method);
    DexEncodedMethod definition = method.lookupOnClass(holder);
    if (definition != null) {
      modeledLibraryTypes.add(method.holder);
      return definition;
    }
    return null;
  }
}
