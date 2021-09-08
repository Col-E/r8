// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static com.android.tools.r8.utils.AndroidApiLevel.UNKNOWN;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;

public interface AndroidApiLevelCompute {

  AndroidApiLevel computeApiLevelForLibraryReference(DexReference reference);

  AndroidApiLevel computeApiLevelForDefinition(Iterable<DexType> types);

  default AndroidApiLevel computeApiLevelForDefinition(
      DexMember<?, ?> reference, DexItemFactory factory) {
    return computeApiLevelForDefinition(reference.getReferencedBaseTypes(factory));
  }

  static AndroidApiLevelCompute create(AppView<?> appView) {
    return appView.options().apiModelingOptions().enableApiCallerIdentification
        ? new DefaultAndroidApiLevelCompute(appView)
        : new NoAndroidApiLevelCompute();
  }

  class NoAndroidApiLevelCompute implements AndroidApiLevelCompute {

    @Override
    public AndroidApiLevel computeApiLevelForDefinition(Iterable<DexType> types) {
      return UNKNOWN;
    }

    @Override
    public AndroidApiLevel computeApiLevelForLibraryReference(DexReference reference) {
      return UNKNOWN;
    }
  }

  class DefaultAndroidApiLevelCompute implements AndroidApiLevelCompute {

    private final AndroidApiReferenceLevelCache cache;
    private final AndroidApiLevel minApiLevel;

    public DefaultAndroidApiLevelCompute(AppView<?> appView) {
      this.cache = AndroidApiReferenceLevelCache.create(appView);
      this.minApiLevel = appView.options().minApiLevel;
    }

    @Override
    public AndroidApiLevel computeApiLevelForDefinition(Iterable<DexType> types) {
      AndroidApiLevel computedLevel = minApiLevel;
      for (DexType type : types) {
        computedLevel = cache.lookupMax(type, computedLevel);
      }
      return computedLevel;
    }

    @Override
    public AndroidApiLevel computeApiLevelForLibraryReference(DexReference reference) {
      return cache.lookup(reference);
    }
  }
}
