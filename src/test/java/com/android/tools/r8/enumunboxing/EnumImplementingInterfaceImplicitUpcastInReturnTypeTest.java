// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumImplementingInterfaceImplicitUpcastInReturnTypeTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  private final String[] EXPECTED = new String[] {"Foo"};

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumImplementingInterfaceImplicitUpcastInReturnTypeTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(EnumImplementingInterfaceImplicitUpcastInReturnTypeTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumImplementingInterfaceImplicitUpcastInReturnTypeTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters.getApiLevel())
        .addEnumUnboxingInspector(inspector -> inspector.assertNotUnboxed(MyEnum.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public interface I {

    String get();
  }

  @NeverClassInline
  public enum MyEnum implements I {
    C("Foo"),
    D("Bar");

    private final String value;

    MyEnum(String value) {
      this.value = value;
    }

    @Override
    public String get() {
      return value;
    }
  }

  public static class Main implements I {

    public static I i;

    public static void main(String[] args) throws Exception {
      I i = System.currentTimeMillis() == 0 ? new Main() : identity(MyEnum.C);
      setInterfaceValue(i);
      System.out.println(Main.i.get());
    }

    @NeverInline
    private static void setInterfaceValue(I i) {
      Main.i = i;
    }

    @NeverInline
    private static I identity(MyEnum myEnum) {
      if (System.currentTimeMillis() == 0) {
        throw new RuntimeException("Foo");
      }
      return myEnum;
    }

    @Override
    public String get() {
      if (System.currentTimeMillis() == 0) {
        throw new RuntimeException("Foo");
      }
      return "Hello World!";
    }
  }
}
