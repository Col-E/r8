// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.DexItemFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FrameComparisonTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() {
    DexItemFactory dexItemFactory = new DexItemFactory();
    CfFrame f1 =
        CfFrame.builder()
            .push(FrameType.initialized(dexItemFactory.objectType))
            .store(0, FrameType.initialized(dexItemFactory.objectType))
            .store(1, FrameType.initialized(dexItemFactory.objectType))
            .build();
    CfFrame f2 =
        CfFrame.builder()
            .push(FrameType.initialized(dexItemFactory.objectType))
            .store(1, FrameType.initialized(dexItemFactory.objectType))
            .store(0, FrameType.initialized(dexItemFactory.objectType))
            .build();
    assertEquals(f1.hashCode(), f2.hashCode());
    assertEquals(f1, f2);
    assertEquals(f2, f1);
  }
}
