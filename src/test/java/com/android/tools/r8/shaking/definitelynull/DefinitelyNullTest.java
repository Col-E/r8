// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.definitelynull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DefinitelyNullTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("false", "call: NPE");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public DefinitelyNullTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Path classpath = temp.newFolder().toPath().resolve("classpath.jar");
    writeClassesToJar(classpath, Collections.singletonList(A.class));
    testForR8(parameters.getBackend())
        // Disable minification so that A still refers to A given on the classpath below.
        .noMinification()
        .addProgramClasses(TestClass.class, A.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        // Prepend the full definition of class A since the compiler will have mostly eliminated it.
        .addRunClasspathFiles(classpath)
        .run(parameters.getRuntime(), TestClass.class)
        // TODO(b/136974947): Should print "true" then "call: NPE".
        .assertSuccessWithOutput(EXPECTED);
  }
}
