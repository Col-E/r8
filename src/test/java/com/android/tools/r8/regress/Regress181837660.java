// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.dexsplitter.SplitterTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * We need to ensure that we distribute the synthetic items in the features where they where
 * generated.
 */
@RunWith(Parameterized.class)
public class Regress181837660 extends SplitterTestBase {

  public static final String EXPECTED = StringUtils.lines("42");

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public Regress181837660(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDistribution() throws Exception {
    ProcessResult processResult =
        testR8Splitter(
            parameters,
            ImmutableSet.of(BaseClass.class),
            ImmutableSet.of(FeatureClass.class),
            FeatureClass.class,
            b -> {},
            this::configure);

    assertEquals(1, processResult.exitCode);
    // We can't actually read the field since it is in the feature.
    assertTrue(processResult.stderr.contains("NoClassDefFoundError"));
  }

  private void configure(R8FullTestBuilder testBuilder) throws NoSuchMethodException {
    testBuilder.enableInliningAnnotations().noMinification().setMinApi(parameters.getApiLevel());
  }

  public static class BaseClass {
    @NeverInline
    public static String getFromFeature() {
      return FeatureClass.featureString;
    }
  }

  public static class FeatureClass implements RunInterface {

    public static String featureString = "22";

    public static String getAString() {
      return BaseClass.getFromFeature();
    }

    @Override
    public void run() {
      System.out.println(getAString());
    }
  }
}
