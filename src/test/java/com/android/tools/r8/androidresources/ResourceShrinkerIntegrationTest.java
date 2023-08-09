// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.ResourceTracing;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ResourceShrinkerIntegrationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testResourceShrinkerClassAvailable() throws Exception {
    if (ToolHelper.isNewGradleSetup()) {
      assertTrue(
          ResourceTracing.getImpl().getClass() != ResourceTracing.NoOpResourceTracingImpl.class);
    } else {
      assertTrue(
          ResourceTracing.getImpl().getClass() == ResourceTracing.NoOpResourceTracingImpl.class);
    }
  }
}
