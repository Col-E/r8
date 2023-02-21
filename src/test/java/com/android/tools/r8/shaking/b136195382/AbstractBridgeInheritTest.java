// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.b136195382;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.b136195382.package1.Factory;
import com.android.tools.r8.shaking.b136195382.package1.Service;
import com.android.tools.r8.shaking.b136195382.package2.Main;
import com.android.tools.r8.shaking.b136195382.package2.SubFactory;
import com.android.tools.r8.shaking.b136195382.package2.SubService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AbstractBridgeInheritTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AbstractBridgeInheritTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRemovingBridge() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            Service.class, Factory.class, SubService.class, SubFactory.class, Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }
}
