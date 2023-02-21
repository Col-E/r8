// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.resolution.singletarget.Main;
import com.android.tools.r8.resolution.singletarget.one.AbstractSubClass;
import com.android.tools.r8.resolution.singletarget.one.AbstractTopClass;
import com.android.tools.r8.resolution.singletarget.one.InterfaceWithDefault;
import com.android.tools.r8.resolution.singletarget.one.IrrelevantInterfaceWithDefaultDump;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassOne;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassThree;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
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

  @Parameter(0)
  public boolean enableInliningAnnotations;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, enable inlining annotations: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(enableInliningAnnotations);
    testForRuntime(parameters)
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(ASM_CLASSES)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedOutput());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addDontObfuscate()
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(ASM_CLASSES)
        .addKeepMainRule(Main.class)
        .applyIf(
            enableInliningAnnotations,
            R8TestBuilder::enableInliningAnnotations,
            TestShrinkerBuilder::addInliningAnnotations)
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedOutput());
  }

  private String getExpectedOutput() {
    String icceOrNot =
        enableInliningAnnotations || !parameters.canUseDefaultAndStaticInterfaceMethods()
            ? "ICCE"
            : "InterfaceWithDefault";
    return StringUtils.lines(
        "SubSubClassOne",
        "SubSubClassOne",
        "AbstractTopClass",
        "SubSubClassOne",
        "AbstractTopClass",
        "com.android.tools.r8.resolution.singletarget.one.AbstractSubClass",
        "InterfaceWithDefault",
        "InterfaceWithDefault",
        icceOrNot,
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
        icceOrNot,
        "InterfaceWithDefault",
        "com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo",
        "InterfaceWithDefault",
        "InterfaceWithDefault",
        "com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo",
        "InterfaceWithDefault",
        "InterfaceWithDefault",
        "InterfaceWithDefault",
        "ICCE");
  }
}
