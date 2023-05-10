// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.switches;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@SuppressWarnings("unchecked")
@RunWith(Parameterized.class)
public class SwitchMapInvalidOrdinalTest extends TestBase {
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public SwitchMapInvalidOrdinalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .setMinApi(parameters)
        .addInnerClasses(SwitchMapInvalidOrdinalTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "b", "0", "a");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep class"
                + "com.android.tools.r8.ir.optimize.switches.SwitchMapInvalidOrdinalTest$MyEnum {"
                + " static <fields>; }")
        .addInnerClasses(SwitchMapInvalidOrdinalTest.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // When the code reaches the switch the first time, then the switch map int[] gets
        // initialized based on the values in the enum at this point creating a mapping ordinal to
        // switch map entry. Here D and X have 3 as ordinal.
        .assertSuccessWithOutputLines("a", "b", "0", "a");
  }

  @NeverClassInline
  enum MyEnum {
    A,
    B,
    C,
    D;

    @NeverInline
    MyEnum() {}
  }

  public static class Main {
    public static void main(String[] args) {
      try {
        // Use reflection to instantiate a new enum instance, and set it to A, using getFields
        // and not getField since A is minified.
        Constructor<MyEnum> constructor =
            (Constructor<MyEnum>) MyEnum.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        MyEnum x = constructor.newInstance("X", 3);
        Field f = MyEnum.class.getFields()[0];

        // On Android, setAccessible allows to set a final field.
        f.setAccessible(true);

        f.set(null, x);
      } catch (Exception e) {
        System.out.println("Unexpected: " + e);
      }
      print(MyEnum.A);
      print(MyEnum.B);
      print(MyEnum.C);
      print(MyEnum.D);
    }

    private static void print(MyEnum e) {
      switch (e) {
        case A:
          System.out.println("a");
          break;
        case B:
          System.out.println("b");
          break;
        default:
          System.out.println("0");
      }
    }
  }
}
