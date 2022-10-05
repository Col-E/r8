// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexSplitterFieldTypeStrengtheningTest extends SplitterTestBase {

  public static final String EXPECTED = StringUtils.lines("FeatureClass");

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public DexSplitterFieldTypeStrengtheningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testOnR8Splitter() throws IOException, CompilationFailedException {
    assumeTrue(parameters.isDexRuntime());
    ProcessResult processResult =
        testR8Splitter(
            parameters,
            ImmutableSet.of(BaseSuperClass.class),
            ImmutableSet.of(FeatureClass.class),
            FeatureClass.class,
            compileResult ->
                compileResult.inspect(
                    inspector -> {
                      ClassSubject baseSuperClassSubject = inspector.clazz(BaseSuperClass.class);
                      assertThat(baseSuperClassSubject, isPresent());

                      FieldSubject fieldSubject =
                          baseSuperClassSubject.uniqueFieldWithOriginalName("f");
                      assertThat(fieldSubject, isPresent());
                      assertEquals(
                          Object.class.getTypeName(),
                          fieldSubject.getField().getType().getTypeName());
                    }),
            // Link against android.jar that contains ReflectiveOperationException.
            testBuilder ->
                testBuilder.addLibraryFiles(
                    parameters.getDefaultAndroidJarAbove(AndroidApiLevel.K)));
    assertEquals(processResult.exitCode, 0);
    assertEquals(processResult.stdout, EXPECTED);
  }

  public abstract static class BaseSuperClass implements RunInterface {

    public static Object f;

    @Override
    public void run() {
      setFieldFromFeature();
      System.out.println(f);
    }

    public abstract void setFieldFromFeature();
  }

  public static class FeatureClass extends BaseSuperClass {

    @Override
    public void setFieldFromFeature() {
      BaseSuperClass.f = this;
    }

    @Override
    public String toString() {
      return "FeatureClass";
    }
  }
}
