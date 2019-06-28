// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

// Transitional interface until b/117969690 is resolved.
public interface CfOrJarCode {
  ConstraintWithTarget computeInliningConstraint(
      DexEncodedMethod encodedMethod,
      AppView<AppInfoWithLiveness> appView,
      GraphLense graphLense,
      DexType invocationContext);


  void markReachabilitySensitive();

  void makeStatic(String protoDescriptor);
}
