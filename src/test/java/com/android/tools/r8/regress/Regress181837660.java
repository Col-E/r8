// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.dexsplitter.SplitterTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
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

  @Test
  public void testRegress181571571() throws Exception {
    ProcessResult processResult =
        testR8Splitter(
            parameters,
            ImmutableSet.of(BaseClass.class),
            ImmutableSet.of(FeatureClass.class),
            FeatureClass.class,
            b -> {},
            this::configureNoInlineAnnotations);
    // This should not succeed as illustrated by non inlining case
    assertEquals(1, processResult.exitCode);
    // We can't actually read the field since it is in the feature.
    assertThat(processResult.stderr, containsString("NoClassDefFoundError"));
  }

  @Test
  public void testRegress181571571StillInlineValid() throws Exception {
    ProcessResult processResult =
        testR8Splitter(
            parameters,
            ImmutableSet.of(Base2Class.class),
            ImmutableSet.of(Feature2Class.class),
            Feature2Class.class,
            r8TestCompileResult ->
                r8TestCompileResult.inspect(
                    base -> assertFalse(base.clazz(Base2Class.class).isPresent())),
            this::configureNoInlineAnnotations);
    assertEquals(0, processResult.exitCode);
    assertEquals(processResult.stdout, "42\n");
  }

  private void configure(R8FullTestBuilder testBuilder) {
    configureNoInlineAnnotations(testBuilder);
    testBuilder.enableInliningAnnotations();
  }

  private void configureNoInlineAnnotations(R8FullTestBuilder testBuilder) {
    testBuilder
        // Link against android.jar that contains ReflectiveOperationException.
        .addLibraryFiles(parameters.getDefaultAndroidJarAbove(AndroidApiLevel.K))
        .addDontObfuscate()
        .setMinApi(parameters);
  }

  public static class BaseClass {

    @NeverInline
    public static String getFromFeature() {
      return FeatureClass.featureString;
    }

    @NeverInline
    public static String getSecondFromFeature() {
      return FeatureClass.getFeatureString();
    }
  }

  public static class FeatureClass implements RunInterface {

    public static String featureString = "22";

    public static String getFeatureString() {
      return "42";
    }

    public static String getAString() {
      return BaseClass.getFromFeature();
    }

    public static String getSecondString() {
      return BaseClass.getSecondFromFeature();
    }

    @Override
    public void run() {
      System.out.println(getAString());
      System.out.println(getSecondString());
    }
  }

  public static class Base2Class {
    public static String getFromFeature() {
      return System.currentTimeMillis() > 2 ? "42" : "-19";
    }
  }

  public static class Feature2Class implements RunInterface {
    public static String getAString() {
      return Base2Class.getFromFeature();
    }

    @Override
    public void run() {
      System.out.println(getAString());
    }
  }
}
