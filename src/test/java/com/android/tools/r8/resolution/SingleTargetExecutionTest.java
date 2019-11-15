// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.resolution.singletarget.Main;
import com.android.tools.r8.resolution.singletarget.one.AbstractSubClass;
import com.android.tools.r8.resolution.singletarget.one.AbstractTopClass;
import com.android.tools.r8.resolution.singletarget.one.InterfaceWithDefault;
import com.android.tools.r8.resolution.singletarget.one.IrrelevantInterfaceWithDefaultDump;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassOne;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassThree;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleTargetExecutionTest extends AsmTestBase {

  public static List<Class<?>> CLASSES =
      ImmutableList.of(
          InterfaceWithDefault.class,
          AbstractTopClass.class,
          AbstractSubClass.class,
          SubSubClassOne.class,
          SubSubClassTwo.class,
          SubSubClassThree.class,
          Main.class);

  public static List<byte[]> ASM_CLASSES = ImmutableList.of(
      getBytesFromAsmClass(IrrelevantInterfaceWithDefaultDump::dump)
  );

  public static final String EXPECTED =
      StringUtils.lines(
          "SubSubClassOne",
          "SubSubClassOne",
          "AbstractTopClass",
          "SubSubClassOne",
          "AbstractTopClass",
          "com.android.tools.r8.resolution.singletarget.one.AbstractSubClass",
          "InterfaceWithDefault",
          "InterfaceWithDefault",
          "ICCE",
          "com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo",
          "com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo",
          "AbstractTopClass",
          "com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo",
          "AbstractTopClass",
          "com.android.tools.r8.resolution.singletarget.one.AbstractSubClass",
          "InterfaceWithDefault",
          "com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo",
          "InterfaceWithDefault",
          "InterfaceWithDefault",
          "InterfaceWithDefault",
          "ICCE",
          "InterfaceWithDefault",
          "com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo",
          "InterfaceWithDefault",
          "InterfaceWithDefault",
          "com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo",
          "InterfaceWithDefault",
          "InterfaceWithDefault",
          "InterfaceWithDefault",
          "ICCE");

  public final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SingleTargetExecutionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(ASM_CLASSES)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .noMinification()
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(ASM_CLASSES)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }
}
