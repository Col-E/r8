// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.regress_62300145;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress62300145TestRunner extends ExamplesTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public Regress62300145TestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return Regress.class;
  }

  @Override
  public List<Class<?>> getTestClasses() throws Exception {
    return ImmutableList.of(
        getMainClass(), Regress.A.class, Regress.B.class, Regress.InnerClass.class);
  }

  @Override
  public String getExpected() {
    return StringUtils.joinLines(
        "0: @com.android.tools.r8.examples.regress_62300145.Regress$A()",
        "1: @com.android.tools.r8.examples.regress_62300145.Regress$A()",
        "2: ");
  }

  @Test
  public void testDesugaring() throws Exception {
    runTestDesugaring();
  }

  @Test
  public void testR8() throws Exception {
    runTestR8(
        b ->
            b.addKeepAllAttributes()
                .addKeepClassAndMembersRules(Regress.InnerClass.class)
                .addKeepClassRules(Regress.A.class, Regress.B.class));
  }

  // Since DEX 9 and below have different runtime behavior it does not make sense to compare
  // single stepping.
}
