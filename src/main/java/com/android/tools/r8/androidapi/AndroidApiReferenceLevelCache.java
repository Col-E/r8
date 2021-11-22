// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryConfiguration;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class AndroidApiReferenceLevelCache {

  private final DesugaredLibraryConfiguration desugaredLibraryConfiguration;
  private final AndroidApiLevelDatabase androidApiLevelDatabase;
  private final AppView<?> appView;
  private final DexItemFactory factory;

  private AndroidApiReferenceLevelCache(AppView<?> appView) {
    this(appView, ImmutableList.of());
  }

  private AndroidApiReferenceLevelCache(
      AppView<?> appView, List<AndroidApiForHashingClass> predefinedApiTypeLookupForHashing) {
    this.appView = appView;
    factory = appView.dexItemFactory();
    androidApiLevelDatabase =
        new AndroidApiLevelHashingDatabaseImpl(factory, predefinedApiTypeLookupForHashing);
    desugaredLibraryConfiguration = appView.options().desugaredLibraryConfiguration;
  }

  public static AndroidApiReferenceLevelCache create(AppView<?> appView) {
    if (!appView.options().apiModelingOptions().enableApiCallerIdentification) {
      // If enableApiCallerIdentification is not enabled then override lookup to always return
      // AndroidApiLevel.B.
      return new AndroidApiReferenceLevelCache(appView) {
        @Override
        public AndroidApiLevel lookup(DexReference reference) {
          return AndroidApiLevel.B;
        }
      };
    }
    ImmutableList.Builder<AndroidApiForHashingClass> builder = ImmutableList.builder();
    appView
        .options()
        .apiModelingOptions()
        .visitMockedApiLevelsForReferences(appView.dexItemFactory(), builder::add);
    return new AndroidApiReferenceLevelCache(appView, builder.build());
  }

  public AndroidApiLevel lookupMax(DexReference reference, AndroidApiLevel minApiLevel) {
    return lookup(reference).max(minApiLevel);
  }

  public AndroidApiLevel lookup(DexReference reference) {
    DexType contextType = reference.getContextType();
    if (contextType.isArrayType()) {
      if (reference.isDexMethod() && reference.asDexMethod().match(factory.objectMembers.clone)) {
        return appView.options().getMinApiLevel();
      }
      return lookup(contextType.toBaseType(factory));
    }
    if (contextType.isPrimitiveType() || contextType.isVoidType()) {
      return appView.options().getMinApiLevel();
    }
    DexClass clazz = appView.definitionFor(contextType);
    if (clazz == null) {
      return AndroidApiLevel.UNKNOWN;
    }
    if (!clazz.isLibraryClass()) {
      return appView.options().getMinApiLevel();
    }
    if (reference.getContextType() == factory.objectType) {
      return appView.options().getMinApiLevel();
    }
    if (desugaredLibraryConfiguration.isSupported(reference, appView)) {
      // If we end up desugaring the reference, the library classes is bridged by j$ which is part
      // of the program.
      return appView.options().getMinApiLevel();
    }
    if (reference.isDexMethod() && factory.objectMembers.isObjectMember(reference.asDexMethod())) {
      // If we can lookup the method it was introduced/overwritten later. Take for example
      // a default constructor that was not available before som api level. If unknown we default
      // back to the static holder.
      AndroidApiLevel methodApiLevel =
          androidApiLevelDatabase.getMethodApiLevel(reference.asDexMethod());
      return methodApiLevel == AndroidApiLevel.UNKNOWN
          ? androidApiLevelDatabase.getTypeApiLevel(reference.getContextType())
          : methodApiLevel;
    }
    return reference
        .apply(
            androidApiLevelDatabase::getTypeApiLevel,
            androidApiLevelDatabase::getFieldApiLevel,
            androidApiLevelDatabase::getMethodApiLevel)
        .max(appView.options().getMinApiLevel());
  }
}
