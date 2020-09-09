// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
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
public class DexSplitterInlineRegression extends SplitterTestBase {

  public static final String EXPECTED = StringUtils.lines("42");

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public DexSplitterInlineRegression(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testInliningFromFeature() throws Exception {
    ThrowingConsumer<R8TestCompileResult, Exception> ensureGetFromFeatureGone =
        r8TestCompileResult -> {
          // Ensure that getFromFeature from FeatureClass is inlined into the run method.
          ClassSubject clazz = r8TestCompileResult.inspector().clazz(FeatureClass.class);
          assertThat(clazz.uniqueMethodWithName("getFromFeature"), not(isPresent()));
        };
    Consumer<R8FullTestBuilder> configurator =
        r8FullTestBuilder ->
            r8FullTestBuilder.enableNoVerticalClassMergingAnnotations().noMinification();
    ProcessResult processResult =
        testDexSplitter(
            parameters,
            ImmutableSet.of(BaseSuperClass.class),
            ImmutableSet.of(FeatureClass.class),
            FeatureClass.class,
            EXPECTED,
            ensureGetFromFeatureGone,
            configurator);
    // We expect art to fail on this with the dex splitter, see b/122902374
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
            ImmutableSet.of(BaseSuperClass.class),
            ImmutableSet.of(FeatureClass.class),
            FeatureClass.class,
            ConsumerUtils.emptyThrowingConsumer(),
            configurator);

    assertEquals(processResult.exitCode, 0);
    assertEquals(processResult.stdout, StringUtils.lines("42"));
  }

  @NoVerticalClassMerging
  public abstract static class BaseSuperClass implements RunInterface {
    @Override
    public void run() {
      System.out.println(getFromFeature());
    }

    public abstract String getFromFeature();
  }

  public static class FeatureClass extends BaseSuperClass {
    String s;

    public FeatureClass() {
      if (System.currentTimeMillis() < 2) {
        s = "43";
      } else {
        s = "42";
      }
    }

    @Override
    public String getFromFeature() {
      return s;
    }
  }
}
