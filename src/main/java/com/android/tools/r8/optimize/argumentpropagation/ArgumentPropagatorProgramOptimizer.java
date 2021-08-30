// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;

public class ArgumentPropagatorProgramOptimizer {

  private final AppView<AppInfoWithLiveness> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;

  public ArgumentPropagatorProgramOptimizer(
      AppView<AppInfoWithLiveness> appView, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    this.appView = appView;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
  }

  // TODO(b/190154391): Remove parameters with constant values.
  // TODO(b/190154391): Remove unused parameters by simulating they are constant.
  // TODO(b/190154391): Strengthen the static type of parameters.
  // TODO(b/190154391): If we learn that a method returns a constant, then consider changing its
  //  return type to void.
  // TODO(b/69963623): If we optimize a method to be unconditionally throwing (because it has a
  //  bottom parameter), then for each caller that becomes unconditionally throwing, we could
  //  also enqueue the caller's callers for reprocessing. This would propagate the throwing
  //  information to all call sites.
  public ArgumentPropagatorGraphLens.Builder optimize(
      Set<DexProgramClass> stronglyConnectedProgramClasses) {
    return ArgumentPropagatorGraphLens.builder(appView);
  }
}
