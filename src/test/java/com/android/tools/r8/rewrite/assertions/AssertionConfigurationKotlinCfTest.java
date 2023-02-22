// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.Collection;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssertionConfigurationKotlinCfTest extends AssertionConfigurationKotlinTestBase {

  @Parameterized.Parameters(
      name =
          "{0}, {1}, kotlin-stdlib as library: {2}, -Xassertions=jvm: {3}, enableJvmAssertions:"
              + " {4}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  private final boolean enableJvmAssertions;

  public AssertionConfigurationKotlinCfTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean kotlinStdlibAsClasspath,
      boolean useJvmAssertions,
      boolean enableJvmAssertions) {
    super(parameters, kotlinParameters, kotlinStdlibAsClasspath, useJvmAssertions);
    this.enableJvmAssertions = enableJvmAssertions;
  }

  @Test
  public void testAssertionsForCfEnableWithStackMap() throws Exception {
    Assume.assumeTrue(useJvmAssertions);
    Assume.assumeTrue(targetVersion == KotlinTargetVersion.JAVA_8);
    // Compile time enabling or disabling assertions means the -ea flag has no effect.
    runR8Test(
        builder -> {
          builder.addAssertionsConfiguration(
              AssertionsConfiguration.Builder::compileTimeEnableAllAssertions);
        },
        inspector -> checkAssertionCodeEnabled(inspector, true),
        allAssertionsExpectedLines(),
        enableJvmAssertions);
  }

  @Test
  public void testAssertionsForCfPassThrough() throws Exception {
    // Leaving assertion code means assertions are controlled by the -ea flag.
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::passthroughAllAssertions),
        inspector -> checkAssertionCodeLeft(inspector, true),
        enableJvmAssertions ? allAssertionsExpectedLines() : noAllAssertionsExpectedLines(),
        enableJvmAssertions);
  }

  @Test
  public void testAssertionsForCfEnable() throws Exception {
    // Compile time enabling or disabling assertions means the -ea flag has no effect.
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeEnableAllAssertions),
        inspector -> checkAssertionCodeEnabled(inspector, true),
        allAssertionsExpectedLines(),
        enableJvmAssertions);
  }

  @Test
  public void testAssertionsForCfDisable() throws Exception {
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeDisableAllAssertions),
        inspector -> checkAssertionCodeRemoved(inspector, true),
        noAllAssertionsExpectedLines(),
        enableJvmAssertions);
  }
}
