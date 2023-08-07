// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.b72538146;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B72538146 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withAllApiLevels()
        .build();
  }

  public B72538146(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    // Compile the parent and child applications into separate dex applications.
    List<Class<?>> parentClasses =
        ImmutableList.of(
            Parent.class,
            Parent.Inner1.class,
            Parent.Inner2.class,
            Parent.Inner3.class,
            Parent.Inner4.class);

    Path parent =
        testForD8().addProgramClasses(parentClasses).setMinApi(parameters).compile().writeToZip();

    Path child =
        testForD8()
            .addProgramClasses(Child.class)
            .addClasspathClasses(parentClasses)
            .setMinApi(parameters)
            .compile()
            .writeToZip();

    // Run the classloader test loading the two dex applications.
    testForD8()
        .addProgramFiles(
            Paths.get(ToolHelper.TESTS_BUILD_DIR)
                .resolve("examplesAndroidApi")
                .resolve("classes")
                .resolve("classloader")
                .resolve("Runner.class"))
        .setMinApi(parameters)
        .compile()
        .run(
            parameters.getRuntime(),
            "classloader.Runner",
            parent.toString(),
            child.toString(),
            Child.class.getTypeName())
        .assertSuccessWithOutput("SUCCESS");
  }
}
