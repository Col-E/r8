// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.function.Consumer;
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
  public void testInliningFromFeature() throws Exception {
    // Static merging is based on sorting order, we assert that we merged to the feature.
    ThrowingConsumer<R8TestCompileResult, Exception> ensureMergingToFeature =
        r8TestCompileResult -> {
          ClassSubject clazz = r8TestCompileResult.inspector().clazz(AFeatureWithStatic.class);
          assertEquals(2, clazz.allMethods().size());
          assertThat(clazz.uniqueMethodWithName("getBase42"), isPresent());
          assertThat(clazz.uniqueMethodWithName("getFoobar"), isPresent());
        };
    Consumer<R8FullTestBuilder> configurator =
        r8FullTestBuilder ->
            r8FullTestBuilder
                .enableNoVerticalClassMergingAnnotations()
                .enableInliningAnnotations()
                .noMinification()
                .addOptionsModification(o -> o.testing.deterministicSortingBasedOnDexType = true);
    ProcessResult processResult =
        testDexSplitter(
            parameters,
            ImmutableSet.of(BaseClass.class, BaseWithStatic.class),
            ImmutableSet.of(FeatureClass.class, AFeatureWithStatic.class),
            FeatureClass.class,
            EXPECTED,
            ensureMergingToFeature,
            configurator);
    // We expect art to fail on this with the dex splitter.
    assertNotEquals(processResult.exitCode, 0);
    assertTrue(processResult.stderr.contains("NoClassDefFoundError"));
  }

  @Test
  public void testOnR8Splitter() throws IOException, CompilationFailedException {
    assumeTrue(parameters.isDexRuntime());
    Consumer<R8FullTestBuilder> configurator =
        r8FullTestBuilder ->
            r8FullTestBuilder.enableNoVerticalClassMergingAnnotations().noMinification();
    ProcessResult processResult =
        testR8Splitter(
            parameters,
            ImmutableSet.of(BaseClass.class, BaseWithStatic.class),
            ImmutableSet.of(FeatureClass.class, AFeatureWithStatic.class),
            FeatureClass.class,
            ConsumerUtils.emptyThrowingConsumer(),
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

  // Name is important, see predicate in tests/
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
