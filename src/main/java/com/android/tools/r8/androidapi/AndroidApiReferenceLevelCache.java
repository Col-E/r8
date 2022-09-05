// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ConsumerUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.BiConsumer;

public class AndroidApiReferenceLevelCache {

  private final AndroidApiLevelCompute apiLevelCompute;
  private final AndroidApiLevelDatabase androidApiLevelDatabase;
  private final AppView<?> appView;
  private final DexItemFactory factory;

  private AndroidApiReferenceLevelCache(
      AppView<?> appView,
      AndroidApiLevelCompute apiLevelCompute,
      List<AndroidApiForHashingReference> predefinedApiTypeLookupForHashing) {
    this.appView = appView;
    this.apiLevelCompute = apiLevelCompute;
    factory = appView.dexItemFactory();
    androidApiLevelDatabase =
        new AndroidApiLevelHashingDatabaseImpl(
            predefinedApiTypeLookupForHashing, appView.options(), appView.reporter());
  }

  public static AndroidApiReferenceLevelCache create(
      AppView<?> appView, AndroidApiLevelCompute apiLevelCompute) {
    assert appView.options().apiModelingOptions().isApiLibraryModelingEnabled();
    ImmutableList.Builder<AndroidApiForHashingReference> builder = ImmutableList.builder();
    BiConsumer<DexReference, AndroidApiLevel> addItemToList =
        ConsumerUtils.andThen(AndroidApiForHashingReference::create, builder::add);
    AndroidApiLevelDatabaseHelper.visitAdditionalKnownApiReferences(
        appView.dexItemFactory(), addItemToList);
    appView
        .options()
        .apiModelingOptions()
        .visitMockedApiLevelsForReferences(appView.dexItemFactory(), addItemToList);
    return new AndroidApiReferenceLevelCache(appView, apiLevelCompute, builder.build());
  }

  public ComputedApiLevel lookupMax(
      DexReference reference, ComputedApiLevel minApiLevel, ComputedApiLevel unknownValue) {
    assert !minApiLevel.isNotSetApiLevel();
    return lookup(reference, unknownValue).max(minApiLevel);
  }

  public ComputedApiLevel lookup(DexReference reference, ComputedApiLevel unknownValue) {
    DexType contextType = reference.getContextType();
    if (contextType.isArrayType()) {
      if (reference.isDexMethod() && reference.asDexMethod().match(factory.objectMembers.clone)) {
        return appView.computedMinApiLevel();
      }
      return lookup(contextType.toBaseType(factory), unknownValue);
    }
    if (contextType.isPrimitiveType() || contextType.isVoidType()) {
      return appView.computedMinApiLevel();
    }
    DexClass clazz = appView.definitionFor(contextType);
    if (clazz == null) {
      return unknownValue;
    }
    if (!clazz.isLibraryClass()) {
      return appView.computedMinApiLevel();
    }
    if (reference.getContextType() == factory.objectType) {
      return appView.computedMinApiLevel();
    }
    if (appView
        .options()
        .machineDesugaredLibrarySpecification
        .isContextTypeMaintainedOrRewritten(reference)) {
      // If we end up desugaring the reference, the library classes is bridged by j$ which is part
      // of the program.
      return appView.computedMinApiLevel();
    }
    if (reference.isDexMethod()
        && !reference.asDexMethod().isInstanceInitializer(factory)
        && factory.objectMembers.isObjectMember(reference.asDexMethod())) {
      AndroidApiLevel typeApiLevel =
          androidApiLevelDatabase.getTypeApiLevel(reference.getContextType());
      return typeApiLevel == null
          ? appView.computedMinApiLevel()
          : apiLevelCompute.of(typeApiLevel);
    }
    AndroidApiLevel foundApiLevel =
        reference.apply(
            androidApiLevelDatabase::getTypeApiLevel,
            androidApiLevelDatabase::getFieldApiLevel,
            androidApiLevelDatabase::getMethodApiLevel);
    return (foundApiLevel == null)
        ? unknownValue
        : apiLevelCompute.of(foundApiLevel.max(appView.options().getMinApiLevel()));
  }
}
