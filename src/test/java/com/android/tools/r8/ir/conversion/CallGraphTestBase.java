// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.ir.conversion.CallGraph.Node;

class CallGraphTestBase extends TestBase {
  private DexItemFactory dexItemFactory = new DexItemFactory();

  Node createNode(String methodName) {
    DexMethod signature =
        dexItemFactory.createMethod(
            dexItemFactory.objectType,
            dexItemFactory.createProto(dexItemFactory.voidType),
            methodName);
    return new Node(
        new DexEncodedMethod(
            signature, null, DexAnnotationSet.empty(), ParameterAnnotationsList.empty(), null));
  }

  Node createForceInlinedNode(String methodName) {
    Node node = createNode(methodName);
    node.method.getMutableOptimizationInfo().markForceInline();
    return node;
  }
}
