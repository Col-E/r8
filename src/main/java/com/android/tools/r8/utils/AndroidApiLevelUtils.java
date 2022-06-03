// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.LibraryMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.inliner.NopWhyAreYouNotInliningReporter;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;

public class AndroidApiLevelUtils {

  public static boolean isApiSafeForInlining(
      ProgramMethod caller, ProgramMethod inlinee, InternalOptions options) {
    return isApiSafeForInlining(
        caller, inlinee, options, NopWhyAreYouNotInliningReporter.getInstance());
  }

  public static boolean isApiSafeForInlining(
      ProgramMethod caller,
      ProgramMethod inlinee,
      InternalOptions options,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (!options.apiModelingOptions().enableApiCallerIdentification) {
      return true;
    }
    if (caller.getHolderType() == inlinee.getHolderType()) {
      return true;
    }
    ComputedApiLevel callerApiLevelForCode = caller.getDefinition().getApiLevelForCode();
    if (callerApiLevelForCode.isUnknownApiLevel()) {
      whyAreYouNotInliningReporter.reportCallerHasUnknownApiLevel();
      return false;
    }
    // For inlining we only measure if the code has invokes into the library.
    ComputedApiLevel inlineeApiLevelForCode = inlinee.getDefinition().getApiLevelForCode();
    if (!caller
        .getDefinition()
        .getApiLevelForCode()
        .isGreaterThanOrEqualTo(inlineeApiLevelForCode)) {
      whyAreYouNotInliningReporter.reportInlineeHigherApiCall(
          callerApiLevelForCode, inlineeApiLevelForCode);
      return false;
    }
    return true;
  }

  public static ComputedApiLevel getApiReferenceLevelForMerging(
      AppView<?> appView, AndroidApiLevelCompute apiLevelCompute, DexProgramClass clazz) {
    // The api level of a class is the max level of it's members, super class and interfaces.
    return getMembersApiReferenceLevelForMerging(
        clazz,
        apiLevelCompute.computeApiLevelForDefinition(
            clazz.allImmediateSupertypes(), apiLevelCompute.getPlatformApiLevelOrUnknown(appView)));
  }

  private static ComputedApiLevel getMembersApiReferenceLevelForMerging(
      DexProgramClass clazz, ComputedApiLevel memberLevel) {
    // Based on b/138781768#comment57 there is almost no penalty for having an unknown reference
    // as long as we are not invoking or accessing a field on it. Therefore we can disregard static
    // types of fields and only consider method code api levels.
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.hasCode()) {
        memberLevel = memberLevel.max(method.getApiLevelForCode());
      }
      if (memberLevel.isUnknownApiLevel()) {
        return memberLevel;
      }
    }
    return memberLevel;
  }

  public static boolean isApiSafeForMemberRebinding(
      LibraryMethod method,
      DexMethod original,
      AndroidApiLevelCompute androidApiLevelCompute,
      InternalOptions options) {
    ComputedApiLevel apiLevel =
        androidApiLevelCompute.computeApiLevelForLibraryReference(
            method.getReference(), ComputedApiLevel.unknown());
    if (apiLevel.isUnknownApiLevel()) {
      return false;
    }
    assert options.apiModelingOptions().enableApiCallerIdentification;
    ComputedApiLevel apiLevelOfOriginal =
        androidApiLevelCompute.computeApiLevelForLibraryReference(
            original, ComputedApiLevel.unknown());
    if (apiLevelOfOriginal.isUnknownApiLevel()) {
      return false;
    }
    return apiLevelOfOriginal
        .asKnownApiLevel()
        .max(apiLevel)
        .asKnownApiLevel()
        .getApiLevel()
        .isLessThanOrEqualTo(options.getMinApiLevel());
  }
}
