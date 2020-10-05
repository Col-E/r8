// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;

// Source code representing synthesized lambda bridge method.

final class LambdaBridgeMethodSourceCode {

  public static CfCode build(
      LambdaClass lambdaClass, DexMethod bridgeMethod, DexMethod mainMethod) {
    return ForwardMethodBuilder.builder(lambdaClass.appView.dexItemFactory())
        .setNonStaticSource(bridgeMethod)
        .setVirtualTarget(mainMethod, false)
        .setCastArguments(lambdaClass.appView.appInfoForDesugaring())
        .setCastResult()
        .build();
  }
}
