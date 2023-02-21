// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.definitelynull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DefinitelyNullTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("value: null", "null: true", "call: NPE");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        // Pre 5 runtimes will hit verification errors due to the two definitions of A.
        .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
        .withAllApiLevels()
        .build();
  }

  public DefinitelyNullTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Path classpath = temp.newFolder().toPath().resolve("classpath.jar");
    if (parameters.isCfRuntime()) {
      writeClassesToJar(classpath, Collections.singletonList(A.class));
    } else {
      testForD8().setMinApi(parameters).addProgramClasses(A.class).compile().writeToZip(classpath);
    }
    testForR8(parameters.getBackend())
        // Disable minification so that A still refers to A given on the classpath below.
        .addDontObfuscate()
        .addProgramClasses(TestClass.class, A.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        // Prepend the full definition of class A since the compiler will have mostly eliminated it.
        .addRunClasspathFiles(classpath)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }
}
