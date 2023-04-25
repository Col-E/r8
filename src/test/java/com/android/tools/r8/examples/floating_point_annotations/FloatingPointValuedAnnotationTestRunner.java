// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.floating_point_annotations;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FloatingPointValuedAnnotationTestRunner extends ExamplesTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public FloatingPointValuedAnnotationTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return FloatingPointValuedAnnotationTest.class;
  }

  @Override
  public List<Class<?>> getTestClasses() {
    return ImmutableList.of(
        FloatingPointValuedAnnotation.class,
        FloatingPointValuedAnnotationTest.class,
        FloatingPointValuedAnnotationTest.A.class,
        FloatingPointValuedAnnotationTest.B.class,
        FloatingPointValuedAnnotationTest.C.class,
        FloatingPointValuedAnnotationTest.D.class);
  }

  @Override
  public String getExpected() {
    return StringUtils.lines("false", "false");
  }

  @Test
  public void testDesugaring() throws Exception {
    runTestDesugaring();
  }

  @Test
  public void testR8() throws Exception {
    runTestR8(
        builder ->
            builder
                .addKeepRuntimeVisibleAnnotations()
                .addKeepClassAndMembersRules(
                    FloatingPointValuedAnnotation.class,
                    FloatingPointValuedAnnotationTest.A.class,
                    FloatingPointValuedAnnotationTest.B.class,
                    FloatingPointValuedAnnotationTest.C.class,
                    FloatingPointValuedAnnotationTest.D.class));
  }

  @Test
  public void testDebug() throws Exception {
    Assume.assumeFalse(
        "VMs 13 and 14 step-out to the continuation (line 28) and not the call-site (line 25).",
        parameters.isDexRuntimeVersion(Version.V13_0_0)
            || parameters.isDexRuntimeVersion(Version.V14_0_0));
    runTestDebugComparator();
  }
}
