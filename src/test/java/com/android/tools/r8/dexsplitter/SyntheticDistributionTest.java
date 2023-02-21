// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.apimodel.ApiModelingTestHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
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
    ThrowableConsumer<R8TestCompileResult> ensureLambdaNotInBase =
        r8TestCompileResult ->
            r8TestCompileResult.inspect(
                base ->
                    assertFalse(
                        base.allClasses().stream().anyMatch(FoundClassSubject::isSynthetic)),
                feature ->
                    assertTrue(
                        feature.allClasses().stream().anyMatch(FoundClassSubject::isSynthetic)));
    ProcessResult processResult =
        testR8Splitter(
            parameters,
            ImmutableSet.of(BaseSuperClass.class, MyFunction.class),
            ImmutableSet.of(FeatureClass.class),
            FeatureClass.class,
            ensureLambdaNotInBase,
            this::configure);
    assertEquals(0, processResult.exitCode);
    assertEquals(StringUtils.lines("42foobar"), processResult.stdout);
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
            .apply(this::configure)
            .compile();

    compileResult
        .runFeature(parameters.getRuntime(), FeatureClass.class, compileResult.getFeature(0))
        .assertSuccessWithOutputLines("42foobar");

    compileResult
        .runFeature(parameters.getRuntime(), Feature2Class.class, compileResult.getFeature(1))
        .assertSuccessWithOutputLines("43barfoo");
  }

  private void configure(R8FullTestBuilder testBuilder) throws NoSuchMethodException {
    testBuilder
        // Link against android.jar that contains ReflectiveOperationException.
        .addLibraryFiles(parameters.getDefaultAndroidJarAbove(AndroidApiLevel.K))
        .addKeepMethodRules(
            Reference.methodFromMethod(
                BaseSuperClass.class.getDeclaredMethod(
                    "keptApplyLambda", MyFunction.class, String.class)))
        .enableInliningAnnotations()
        .addDontObfuscate()
        // BaseDexClassLoader was introduced at api level 14.
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .setMinApi(parameters);
  }

  public abstract static class BaseSuperClass implements RunInterface {
    @Override
    public void run() {
      System.out.println(getFromFeature());
    }

    public abstract String getFromFeature();

    public String keptApplyLambda(MyFunction fn, String arg) {
      return fn.apply(arg);
    }
  }

  public interface MyFunction {
    String apply(String s);
  }

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
      return keptApplyLambda(f, "42");
    }
  }

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
      return keptApplyLambda(f, "43");
    }
  }
}
