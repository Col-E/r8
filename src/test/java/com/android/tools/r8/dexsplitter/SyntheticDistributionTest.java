// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
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
    ThrowingConsumer<R8TestCompileResult, Exception> ensureLambdaNotInBase =
        r8TestCompileResult -> {
          r8TestCompileResult.inspect(
              base ->
                  assertFalse(base.allClasses().stream().anyMatch(FoundClassSubject::isSynthetic)),
              feature ->
                  assertTrue(
                      feature.allClasses().stream().anyMatch(FoundClassSubject::isSynthetic)));
        };
    ProcessResult processResult =
        testR8Splitter(
            parameters,
            ImmutableSet.of(BaseSuperClass.class),
            ImmutableSet.of(FeatureClass.class, MyFunction.class),
            FeatureClass.class,
            ensureLambdaNotInBase,
            configurator);
    assertEquals(processResult.exitCode, 0);
    assertEquals(processResult.stdout, StringUtils.lines("42foobar"));
  }

  @Test
  public void testNoMergingAcrossBoundaries() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(BaseSuperClass.class, MyFunction.class)
            .addFeatureSplitRuntime()
            .addFeatureSplit(FeatureClass.class)
            .addFeatureSplit(Feature2Class.class)
            .addKeepFeatureMainRules(BaseSuperClass.class, FeatureClass.class, Feature2Class.class)
            .enableNoStaticClassMergingAnnotations()
            .noMinification()
            .enableInliningAnnotations()
            .setMinApi(parameters.getApiLevel())
            .compile();

    compileResult
        .runFeature(parameters.getRuntime(), FeatureClass.class, compileResult.getFeature(0))
        .assertSuccessWithOutputLines("42foobar");

    compileResult
        .runFeature(parameters.getRuntime(), Feature2Class.class, compileResult.getFeature(1))
        .assertSuccessWithOutputLines("43barfoo");
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
    public static String getAString() {
      return System.currentTimeMillis() < 2 ? "Not happening" : "foobar";
    }

    @Override
    public void run() {
      super.run();
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

  @NoStaticClassMerging
  public static class Feature2Class extends BaseSuperClass {
    @NeverInline
    public static String getAString() {
      return System.currentTimeMillis() < 2 ? "Not happening" : "barfoo";
    }

    @Override
    public void run() {
      super.run();
    }

    @Override
    public String getFromFeature() {
      // The lambda is the same as in FeatureClass, but we should not share it since there is
      // no way for Feature2Class to access code in Feature1Class (assuming either that
      // Feature1Class is not installed of isolated splits are used).
      String s = getAString();
      return useTheLambda(a -> a.concat(s));
    }

    @NeverInline
    private String useTheLambda(MyFunction f) {
      return f.apply("43");
    }
  }
}
