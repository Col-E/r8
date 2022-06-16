// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
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
public class DexSplitterMemberValuePropagationRegression extends SplitterTestBase {

  public static final String EXPECTED = StringUtils.lines(FeatureEnum.class.getTypeName(), "42");

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public DexSplitterMemberValuePropagationRegression(TestParameters parameters) {
    this.parameters = parameters;
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
            ThrowableConsumer.empty(),
            testBuilder ->
                testBuilder
                    // Link against android.jar that contains ReflectiveOperationException.
                    .addLibraryFiles(parameters.getDefaultAndroidJarAbove(AndroidApiLevel.K))
                    .addDontObfuscate(FeatureEnum.class)
                    .enableInliningAnnotations());
    assertEquals(processResult.exitCode, 0);
    assertEquals(processResult.stdout, EXPECTED);
  }

  public abstract static class BaseSuperClass implements RunInterface {

    @NeverInline
    @Override
    public void run() {
      System.out.println(getClassFromFeature().getName());
      System.out.println(getEnumFromFeature());
    }

    public abstract Class<?> getClassFromFeature();

    public abstract Enum<?> getEnumFromFeature();
  }

  public static class FeatureClass extends BaseSuperClass {

    @NeverInline
    @Override
    public Class<?> getClassFromFeature() {
      return FeatureEnum.class;
    }

    @NeverInline
    @Override
    public Enum<?> getEnumFromFeature() {
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
