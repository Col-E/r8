// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.origin.EmbeddedOrigin;
import com.android.tools.r8.utils.AndroidApp;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8CommandTestJdkLib extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  private final TestParameters parameters;

  public R8CommandTestJdkLib(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void jdkLib() throws Throwable {
    CfRuntime runtime = parameters.getRuntime().asCf();
    R8Command command = parse("--lib", runtime.getJavaHome().toString());
    AndroidApp inputApp = ToolHelper.getApp(command);
    assertEquals(1, inputApp.getLibraryResourceProviders().size());
    if (runtime.isNewerThan(CfVm.JDK8)) {
      assertTrue(inputApp.getLibraryResourceProviders().get(0) instanceof JdkClassFileProvider);
    } else {
      assertTrue(inputApp.getLibraryResourceProviders().get(0) instanceof ArchiveClassFileProvider);
    }
  }

  private R8Command parse(String... args) throws CompilationFailedException {
    return R8Command.parse(args, EmbeddedOrigin.INSTANCE).build();
  }
}
