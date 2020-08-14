// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.EnumMembers;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.ObjectState;
import com.android.tools.r8.ir.optimize.info.LibraryOptimizationInfoInitializerFeedback;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoFactory;
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

  void run(Set<DexEncodedField> finalLibraryFields) {
    modelInstanceInitializers();
    modelStaticFinalLibraryFields(finalLibraryFields);
    modelLibraryMethodsReturningNonNull();
    modelLibraryMethodsReturningReceiver();
    modelRequireNonNullMethods();
  }

  Set<DexType> getModeledLibraryTypes() {
    return modeledLibraryTypes;
  }

  private void modelInstanceInitializers() {
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
      feedback.setInstanceInitializerInfo(
          enumConstructor,
          NonTrivialInstanceInitializerInfo.builder(fieldInitializationInfos)
              .setParent(dexItemFactory.objectMembers.constructor)
              .build());
    }
  }

  private void modelStaticFinalLibraryFields(Set<DexEncodedField> finalLibraryFields) {
    for (DexEncodedField field : finalLibraryFields) {
      if (field.isStatic()) {
        feedback.recordLibraryFieldHasAbstractValue(
            field, abstractValueFactory.createSingleFieldValue(field.field, ObjectState.empty()));
      }
    }
  }

  private void modelLibraryMethodsReturningNonNull() {
    for (DexMethod method : dexItemFactory.libraryMethodsReturningNonNull) {
      DexEncodedMethod definition = lookupMethod(method);
      if (definition != null) {
        TypeElement staticType =
            TypeElement.fromDexType(method.proto.returnType, maybeNull(), appView);
        feedback.methodReturnsObjectWithUpperBoundType(
            definition,
            appView,
            definition
                .getOptimizationInfo()
                .getDynamicUpperBoundTypeOrElse(staticType)
                .asReferenceType()
                .asDefinitelyNotNull());
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

  private void modelRequireNonNullMethods() {
    for (DexMethod requireNonNullMethod : dexItemFactory.objectsMethods.requireNonNullMethods()) {
      DexEncodedMethod definition = lookupMethod(requireNonNullMethod);
      if (definition != null) {
        feedback.methodReturnsArgument(definition, 0);

        BitSet nonNullParamOrThrow = new BitSet();
        nonNullParamOrThrow.set(0);
        feedback.setNonNullParamOrThrow(definition, nonNullParamOrThrow);

        BitSet nonNullParamOnNormalExits = new BitSet();
        nonNullParamOnNormalExits.set(0);
        feedback.setNonNullParamOnNormalExits(definition, nonNullParamOnNormalExits);
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
