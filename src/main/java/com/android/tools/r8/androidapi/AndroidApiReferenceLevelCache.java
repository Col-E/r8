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
import java.util.HashMap;

public class AndroidApiReferenceLevelCache {

  private final DesugaredLibraryConfiguration desugaredLibraryConfiguration;
  private final AndroidApiLevelDatabase androidApiLevelDatabase;
  private final AppView<?> appView;

  private AndroidApiReferenceLevelCache(AppView<?> appView) {
    this(appView, new HashMap<>());
  }

  private AndroidApiReferenceLevelCache(
      AppView<?> appView, HashMap<DexType, AndroidApiClass> predefinedApiTypeLookup) {
    this.appView = appView;
    androidApiLevelDatabase = new AndroidApiLevelDatabaseImpl(predefinedApiTypeLookup);
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
    // The apiTypeLookup is build lazily except for the mocked api types that we define in tests
    // externally.
    HashMap<DexType, AndroidApiClass> predefinedApiTypeLookup = new HashMap<>();
    appView
        .options()
        .apiModelingOptions()
        .visitMockedApiReferences(
            (classReference, androidApiClass) ->
                predefinedApiTypeLookup.put(
                    appView.dexItemFactory().createType(classReference.getDescriptor()),
                    androidApiClass));
    return new AndroidApiReferenceLevelCache(appView, predefinedApiTypeLookup);
  }

  public AndroidApiLevel lookupMax(DexReference reference, AndroidApiLevel minApiLevel) {
    return lookup(reference).max(minApiLevel);
  }

  public AndroidApiLevel lookup(DexReference reference) {
    DexType contextType = reference.getContextType();
    if (contextType.isArrayType()) {
      return lookup(contextType.toBaseType(appView.dexItemFactory()));
    }
    if (contextType.isPrimitiveType() || contextType.isVoidType()) {
      return AndroidApiLevel.B;
    }
    DexClass clazz = appView.definitionFor(contextType);
    if (clazz == null) {
      return AndroidApiLevel.UNKNOWN;
    }
    if (!clazz.isLibraryClass()) {
      return appView.options().minApiLevel;
    }
    if (desugaredLibraryConfiguration.isSupported(reference, appView)) {
      // If we end up desugaring the reference, the library classes is bridged by j$ which is part
      // of the program.
      return appView.options().minApiLevel;
    }
    return reference.apply(
        androidApiLevelDatabase::getTypeApiLevel,
        androidApiLevelDatabase::getFieldApiLevel,
        androidApiLevelDatabase::getMethodApiLevel);
  }
}
