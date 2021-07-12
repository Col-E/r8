// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.function.Function;

public class AndroidApiLevelUtils {

  // Static api-level indicating that the api level is min-api.
  public static final AndroidApiLevel MIN_API_LEVEL = null;

  public static AndroidApiLevel getApiLevelIfEnabledForNewMember(
      AppView<?> appView, Function<AndroidApiLevel, AndroidApiLevel> getter) {
    AndroidApiLevel apiLevelIfEnabled = getApiLevelIfEnabled(appView, getter);
    if (apiLevelIfEnabled == appView.options().minApiLevel) {
      return MIN_API_LEVEL;
    }
    return apiLevelIfEnabled;
  }

  public static AndroidApiLevel getApiLevelIfEnabled(
      AppView<?> appView, Function<AndroidApiLevel, AndroidApiLevel> getter) {
    if (!appView.options().apiModelingOptions().enableApiCallerIdentification) {
      return AndroidApiLevel.UNKNOWN;
    }
    return getter.apply(appView.options().minApiLevel);
  }

  public static OptionalBool isApiSafeForInlining(
      ProgramMethod caller, ProgramMethod inlinee, InternalOptions options) {
    if (!options.apiModelingOptions().enableApiCallerIdentification) {
      return OptionalBool.TRUE;
    }
    if (caller.getHolderType() == inlinee.getHolderType()) {
      return OptionalBool.TRUE;
    }
    return OptionalBool.of(
        caller
            .getDefinition()
            .getApiReferenceLevel(options.minApiLevel)
            .isGreaterThanOrEqualTo(
                inlinee.getDefinition().getApiReferenceLevelForCode(options.minApiLevel)));
  }
}
