// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation.assume;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.AssumeInfoCollection;

public class AssumeInfoLookup {

  public static AssumeInfo lookupAssumeInfo(
      AppView<AppInfoWithLiveness> appView,
      SingleResolutionResult<?> resolutionResult,
      DexClassAndMethod singleTarget) {
    AssumeInfoCollection assumeInfoCollection = appView.getAssumeInfoCollection();
    AssumeInfo resolutionLookup = assumeInfoCollection.get(resolutionResult.getResolutionPair());
    AssumeInfo singleTargetLookup =
        singleTarget != null ? assumeInfoCollection.get(singleTarget) : null;
    return singleTargetLookup != null
        ? resolutionLookup.meet(singleTargetLookup)
        : resolutionLookup;
  }
}
