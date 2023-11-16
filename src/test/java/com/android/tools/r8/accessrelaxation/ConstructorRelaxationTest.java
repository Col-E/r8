// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class L1 {
  private final String x;

  private L1(String x) {
    this.x = x;
  }

  private L1() {
    this("private_x");
  }

  static L1 create() {
    return new L1();
  }

  L1(int i) {
    this(String.valueOf(i));
  }

  @Override
  public String toString() {
    return x;
  }
}

class L2_1 extends L1 {
  private String y;

  private L2_1() {
    this(21);
    this.y = "private_L2_1_y";
  }

  L2_1(int i) {
    super(i);
    this.y = "L2_1_y";
  }

  private L2_1(String y) {
    this(21);
    this.y = y;
  }

  static L2_1 create(String y) {
    return new L2_1(y);
  }

  @Override
  public String toString() {
    return super.toString() + "_" + y;
  }
}

class L2_2 extends L1 {
  private String y;

  private L2_2(int i) {
    super(i);
    this.y = "private_L2_2_y";
  }

  L2_2(String y) {
    this(22);
    this.y = y;
  }

  static L2_1 create() {
    return new L2_1(22);
  }

  @Override
  public String toString() {
    return super.toString() + "_" + y;
  }
}

class L3_1 extends L2_1 {
  private final String z;

  private L3_1(int i) {
    this(String.valueOf(i));
  }

  private L3_1(String z) {
    super(31);
    this.z = z;
  }

  static L3_1 create(int i) {
    return new L3_1(i);
  }

  @Override
  public String toString() {
    return super.toString() + "_" + z;
  }
}

class L3_2 extends L2_2 {
  private String z;

  private L3_2() {
    super("private_L3_2_y");
    this.z = "private_L3_2_z";
  }

  private L3_2(int i) {
    super(String.valueOf(i));
    this.z = "private_L3_2_z" + "_" + i;
  }

  L3_2(String z) {
    this(32);
    this.z = z;
  }

  static L3_2 create(String z) {
    return new L3_2(z);
  }

  @Override
  public String toString() {
    return super.toString() + "_" + z;
  }
}

class CtorTestMain {
  public static void main(String[] args) {
    System.out.println(L1.create());
    System.out.println(L2_1.create("main_y"));
    System.out.println(L2_2.create());
    System.out.println(L3_1.create(41));
    System.out.println(L3_2.create("main_z"));
  }
}

@RunWith(Parameterized.class)
public final class ConstructorRelaxationTest extends AccessRelaxationTestBase {
  private static final Class<?>[] CLASSES = {
      L1.class, L2_1.class, L2_2.class, L3_1.class, L3_2.class
  };

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ConstructorRelaxationTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void test() throws Exception {
    String expectedOutput =
        StringUtils.lines(
            "private_x",
            "21_main_y",
            "22_L2_1_y",
            "31_L2_1_y_41",
            "22_32_main_z");
    Class<?> mainClass = CtorTestMain.class;

    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(mainClass)
            .addProgramClasses(CLASSES)
            .addOptionsModification(
                options -> {
                  options.inlinerOptions().enableInlining = false;
                  options.getVerticalClassMergerOptions().disable();
                })
            .addDontObfuscate()
            .addKeepMainRule(mainClass)
            .allowAccessModification()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), mainClass);

    assertEquals(
        expectedOutput,
        result
            .getStdOut()
            .replace("java.lang.IncompatibleClassChangeError", "java.lang.IllegalAccessError"));

    CodeInspector codeInspector = result.inspector();
    for (Class<?> clazz : CLASSES) {
      ClassSubject classSubject = codeInspector.clazz(clazz);
      assertThat(classSubject, isPresent());
      classSubject
          .getDexProgramClass()
          .forEachMethod(
              m -> {
                assertTrue(!m.isInstanceInitializer() || m.isPublicMethod());
              });
    }
  }

}
