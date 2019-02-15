// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.experimental.graphinfo.GraphEdgeInfo;
import com.android.tools.r8.experimental.graphinfo.GraphEdgeInfo.EdgeKind;
import org.junit.Test;

public class KeepReasonsTest {

  @Test
  public void testAllReasonsHavePrintInfo() {
    for (EdgeKind value : EdgeKind.values()) {
      assertNotNull(new GraphEdgeInfo(value).getInfoPrefix());
    }
  }

}
