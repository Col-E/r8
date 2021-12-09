// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class AndroidApiReferenceLevelCache {

  private final LegacyDesugaredLibrarySpecification desugaredLibrarySpecification;
  private final AndroidApiLevelCompute apiLevelCompute;
  private final AndroidApiLevelDatabase androidApiLevelDatabase;
  private final AppView<?> appView;
  private final DexItemFactory factory;

  private AndroidApiReferenceLevelCache(
      AppView<?> appView,
      AndroidApiLevelCompute apiLevelCompute,
      List<AndroidApiForHashingClass> predefinedApiTypeLookupForHashing) {
    this.appView = appView;
    this.apiLevelCompute = apiLevelCompute;
    factory = appView.dexItemFactory();
    androidApiLevelDatabase =
        new AndroidApiLevelHashingDatabaseImpl(predefinedApiTypeLookupForHashing);
    desugaredLibrarySpecification = appView.options().desugaredLibrarySpecification;
  }

  public static AndroidApiReferenceLevelCache create(
      AppView<?> appView, AndroidApiLevelCompute apiLevelCompute) {
    assert appView.options().apiModelingOptions().enableApiCallerIdentification;
    ImmutableList.Builder<AndroidApiForHashingClass> builder = ImmutableList.builder();
    appView
        .options()
        .apiModelingOptions()
        .visitMockedApiLevelsForReferences(appView.dexItemFactory(), builder::add);
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
    if (clazz != null && clazz.isProgramClass()) {
      return appView.computedMinApiLevel();
    }
    if (reference.getContextType() == factory.objectType) {
      return appView.computedMinApiLevel();
    }
    if (desugaredLibrarySpecification.isSupported(reference, appView)) {
      // If we end up desugaring the reference, the library classes is bridged by j$ which is part
      // of the program.
      return appView.computedMinApiLevel();
    }
    if (reference.isDexMethod()
        && !reference.asDexMethod().isInstanceInitializer(factory)
        && factory.objectMembers.isObjectMember(reference.asDexMethod())) {
      // If we can lookup the method it was introduced/overwritten later. Take for example
      // a default toString that was not available before some api level. If unknown we default
      // back to the static holder.
      AndroidApiLevel methodApiLevel =
          androidApiLevelDatabase.getMethodApiLevel(reference.asDexMethod());
      if (methodApiLevel != null) {
        return apiLevelCompute.of(methodApiLevel);
      }
      AndroidApiLevel typeApiLevel =
          androidApiLevelDatabase.getTypeApiLevel(reference.getContextType());
      // TODO(b/207452750): Investigate if we can return minApi here.
      return typeApiLevel == null ? ComputedApiLevel.unknown() : apiLevelCompute.of(typeApiLevel);
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
