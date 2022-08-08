// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexSplitterMergeRegression extends SplitterTestBase {

  public static final String EXPECTED = StringUtils.lines("42", "foobar");

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public DexSplitterMergeRegression(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testOnR8Splitter() throws IOException, CompilationFailedException {
    assumeTrue(parameters.isDexRuntime());
    ThrowableConsumer<R8FullTestBuilder> configurator =
        r8FullTestBuilder ->
            r8FullTestBuilder
                // Link against android.jar that contains ReflectiveOperationException.
                .addLibraryFiles(parameters.getDefaultAndroidJarAbove(AndroidApiLevel.K))
                .enableNoVerticalClassMergingAnnotations()
                .addDontObfuscate();
    ProcessResult processResult =
        testR8Splitter(
            parameters,
            ImmutableSet.of(BaseClass.class, BaseWithStatic.class),
            ImmutableSet.of(FeatureClass.class, AFeatureWithStatic.class),
            FeatureClass.class,
            ThrowableConsumer.empty(),
            configurator);

    assertEquals(processResult.exitCode, 0);
    assertTrue(processResult.stdout.equals(StringUtils.lines("42", "foobar")));
  }

  @NoVerticalClassMerging
  public static class BaseClass implements RunInterface {

    @Override
    @NeverInline
    public void run() {
      System.out.println(BaseWithStatic.getBase42());
    }
  }

  public static class BaseWithStatic {

    @NeverInline
    public static int getBase42() {
      if (System.currentTimeMillis() < 2) {
        return 43;
      } else {
        return 42;
      }
    }
  }

  public static class FeatureClass extends BaseClass {

    @Override
    public void run() {
      super.run();
      System.out.println(AFeatureWithStatic.getFoobar());
    }
  }

  public static class AFeatureWithStatic {

    @NeverInline
    public static String getFoobar() {
      if (System.currentTimeMillis() < 2) {
        return "barFoo";
      } else {
        return "foobar";
      }
    }
  }
}
