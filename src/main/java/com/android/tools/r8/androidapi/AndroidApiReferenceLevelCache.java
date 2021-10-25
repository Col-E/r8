// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
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

  private AndroidApiReferenceLevelCache(AppView<?> appView) {
    this(appView, ImmutableList.of());
  }

  private AndroidApiReferenceLevelCache(
      AppView<?> appView, List<AndroidApiForHashingClass> predefinedApiTypeLookupForHashing) {
    this.appView = appView;
    // TODO(b/199934316): When the implementation has been decided on, remove the others.
    if (appView.options().apiModelingOptions().useHashingDatabase) {
      androidApiLevelDatabase =
          new AndroidApiLevelHashingDatabaseImpl(
              appView.dexItemFactory(), predefinedApiTypeLookupForHashing);
    } else {
      androidApiLevelDatabase =
          new AndroidApiLevelObjectDatabaseImpl(predefinedApiTypeLookupForHashing);
    }
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
      if (reference.isDexMethod()
          && reference.asDexMethod().match(appView.dexItemFactory().objectMembers.clone)) {
        return appView.options().minApiLevel;
      }
      return lookup(contextType.toBaseType(appView.dexItemFactory()));
    }
    if (contextType.isPrimitiveType() || contextType.isVoidType()) {
      return appView.options().minApiLevel;
    }
    DexClass clazz = appView.definitionFor(contextType);
    if (clazz == null) {
      return AndroidApiLevel.UNKNOWN;
    }
    if (!clazz.isLibraryClass()) {
      return appView.options().minApiLevel;
    }
    if (isReferenceToJavaLangObject(reference)) {
      return appView.options().minApiLevel;
    }
    if (desugaredLibraryConfiguration.isSupported(reference, appView)) {
      // If we end up desugaring the reference, the library classes is bridged by j$ which is part
      // of the program.
      return appView.options().minApiLevel;
    }
    return reference
        .apply(
            androidApiLevelDatabase::getTypeApiLevel,
            androidApiLevelDatabase::getFieldApiLevel,
            androidApiLevelDatabase::getMethodApiLevel)
        .max(appView.options().minApiLevel);
  }

  private boolean isReferenceToJavaLangObject(DexReference reference) {
    if (reference.getContextType() == appView.dexItemFactory().objectType) {
      return true;
    }
    return reference.isDexMethod()
        && appView.dexItemFactory().objectMembers.isObjectMember(reference.asDexMethod());
  }
}
