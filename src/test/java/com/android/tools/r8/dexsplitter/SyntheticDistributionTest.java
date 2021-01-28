// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoStaticClassMerging;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.google.common.collect.ImmutableSet;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * We need to ensure that we distribute the synthetic items in the features where they where
 * generated.
 */
@RunWith(Parameterized.class)
public class SyntheticDistributionTest extends SplitterTestBase {

  public static final String EXPECTED = StringUtils.lines("42");

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public SyntheticDistributionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDistribution() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    Consumer<R8FullTestBuilder> configurator =
        r8FullTestBuilder ->
            r8FullTestBuilder
                .noMinification()
                .enableNoStaticClassMergingAnnotations()
                .enableInliningAnnotations()
                .addInliningAnnotations()
                .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.O));
    ThrowingConsumer<R8TestCompileResult, Exception> ensureInlined =
        r8TestCompileResult -> {
          // TODO(b/178320316): Validate that the synthetic is not in the base.
        };
    ProcessResult processResult =
        testR8Splitter(
            parameters,
            ImmutableSet.of(BaseSuperClass.class),
            ImmutableSet.of(FeatureClass.class, MyFunction.class),
            FeatureClass.class,
            ensureInlined,
            configurator);
    // TODO(b/178320316): Should pass when synthetics are pushed down to the feature.
    // Currently the Lambda class is placed in the base, but it is implementing the MyFunction
    // interface, which is only in the feature. This leads to a verification error (art version
    // specific, e.g., :
    // dalvikvm I 01-25 13:05:54 416262 416262 class_linker.cc:215] Rejecting re-init on
    //   previously-failed class
    //   java.lang.Class<com.android.tools.r8.dexsplitter.SyntheticDistributionTest$FeatureClass
    //   -$$ExternalSyntheticLambda0>: java.lang.NoClassDefFoundError:
    //   Failed resolution of:
    //   Lcom/android/tools/r8/dexsplitter/SyntheticDistributionTest$MyFunction;
    assertFalse(processResult.exitCode == 0);
    assertFalse(processResult.stdout.equals(StringUtils.lines("42foobar")));
  }

  public abstract static class BaseSuperClass implements RunInterface {
    @Override
    public void run() {
      System.out.println(getFromFeature());
    }

    public abstract String getFromFeature();
  }

  public interface MyFunction {
    String apply(String s);
  }

  @NoStaticClassMerging
  public static class FeatureClass extends BaseSuperClass {
    @NeverInline
    private static String getAString() {
      return System.currentTimeMillis() < 2 ? "Not happening" : "foobar";
    }

    @Override
    public String getFromFeature() {
      String s = getAString();
      return useTheLambda(a -> a.concat(s));
    }

    @NeverInline
    private String useTheLambda(MyFunction f) {
      return f.apply("42");
    }
  }
}
