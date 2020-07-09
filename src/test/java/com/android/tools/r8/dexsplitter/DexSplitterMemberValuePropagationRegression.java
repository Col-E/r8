// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexSplitterMemberValuePropagationRegression extends SplitterTestBase {

  public static final String EXPECTED = StringUtils.lines("42");

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public DexSplitterMemberValuePropagationRegression(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testPropagationFromFeature() throws Exception {
    ThrowingConsumer<R8TestCompileResult, Exception> ensureGetFromFeatureGone =
        r8TestCompileResult -> {
          // Ensure that getFromFeature from FeatureClass is inlined into the run method.
          ClassSubject clazz = r8TestCompileResult.inspector().clazz(FeatureClass.class);
          assertThat(clazz.uniqueMethodWithName("getFromFeature"), not(isPresent()));
        };
    ProcessResult processResult =
        testDexSplitter(
            parameters,
            ImmutableSet.of(BaseSuperClass.class),
            ImmutableSet.of(FeatureClass.class, FeatureEnum.class),
            FeatureClass.class,
            EXPECTED,
            ensureGetFromFeatureGone,
            builder -> builder.enableInliningAnnotations().noMinification());
    // We expect art to fail on this with the dex splitter, see b/122902374
    assertNotEquals(processResult.exitCode, 0);
    assertTrue(processResult.stderr.contains("NoClassDefFoundError"));
  }

  @Test
  public void testOnR8Splitter() throws IOException, CompilationFailedException {
    assumeTrue(parameters.isDexRuntime());
    ProcessResult processResult =
        testR8Splitter(
            parameters,
            ImmutableSet.of(BaseSuperClass.class),
            ImmutableSet.of(FeatureClass.class, FeatureEnum.class),
            FeatureClass.class,
            ConsumerUtils.emptyThrowingConsumer(),
            R8TestBuilder::enableInliningAnnotations);
    assertEquals(processResult.exitCode, 0);
    assertEquals(processResult.stdout, EXPECTED);
  }

  public abstract static class BaseSuperClass implements RunInterface {

    @NeverInline
    @Override
    public void run() {
      System.out.println(getFromFeature());
    }

    public abstract Enum<?> getFromFeature();
  }

  public static class FeatureClass extends BaseSuperClass {

    @NeverInline
    @Override
    public Enum<?> getFromFeature() {
      return FeatureEnum.A;
    }
  }

  public enum FeatureEnum {
    A;

    @Override
    public String toString() {
      return "42";
    }
  }
}
