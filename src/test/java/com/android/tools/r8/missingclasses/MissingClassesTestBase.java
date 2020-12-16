// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class MissingClassesTestBase extends TestBase {

  enum DontWarnConfiguration {
    DONT_WARN_MAIN_CLASS,
    DONT_WARN_MISSING_CLASS,
    NONE;

    public boolean isDontWarnMainClass() {
      return this == DONT_WARN_MAIN_CLASS;
    }

    public boolean isDontWarnMissingClass() {
      return this == DONT_WARN_MISSING_CLASS;
    }
  }

  private final DontWarnConfiguration dontWarnConfiguration;
  private final TestParameters parameters;

  @Parameters(name = "{1}, {0}")
  public static List<Object[]> data() {
    return buildParameters(
        DontWarnConfiguration.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public MissingClassesTestBase(
      DontWarnConfiguration dontWarnConfiguration, TestParameters parameters) {
    this.dontWarnConfiguration = dontWarnConfiguration;
    this.parameters = parameters;
  }

  public R8TestCompileResult compile(Class<?> mainClass, Class<?> missingClass)
      throws CompilationFailedException {
    return testForR8(parameters.getBackend())
        .addProgramClasses(mainClass)
        .addKeepMainRule(mainClass)
        .applyIf(
            dontWarnConfiguration.isDontWarnMainClass(),
            testBuilder -> testBuilder.addDontWarn(mainClass))
        .applyIf(
            dontWarnConfiguration.isDontWarnMissingClass(),
            testBuilder -> testBuilder.addDontWarn(missingClass))
        .addOptionsModification(TestingOptions::enableExperimentalMissingClassesReporting)
        .setMinApi(parameters.getApiLevel())
        .compile();
  }

  public DontWarnConfiguration getDontWarnConfiguration() {
    return dontWarnConfiguration;
  }
}
