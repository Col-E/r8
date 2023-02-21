// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.proto;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/235733922 */
@RunWith(Parameterized.class)
public class ProtoNormalizationEnumParameterTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepClassAndMembersRules(Main.class)
        .addKeepClassRules(CustomAnnotation.class)
        .addKeepRuntimeVisibleParameterAnnotations()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class, "foo", "bar")
        .assertSuccessWithOutputLines("2foobar", "TEST_1");
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  public @interface CustomAnnotation {}

  public enum MyEnum {
    TEST_1("Foo"),
    TEST_2("Bar");

    private final String str;

    MyEnum(@CustomAnnotation String str) {
      this.str = str;
    }

    public String getStr() {
      return TEST_1 + str;
    }
  }

  public static class Main {

    @NeverInline
    // The test(int foo, String bar, String baz) is needed to have
    // MyEnum.<init>(String name, int ordinal, String str) written into this proto.
    public static void test(int foo, String bar, String baz) {
      System.out.println(foo + bar + baz);
    }

    @NeverInline
    public static void main(String[] args) {
      test(args.length, args[0], args[1]);
      MyEnum val = System.currentTimeMillis() > 0 ? MyEnum.TEST_1 : MyEnum.TEST_2;
      System.out.println(val);
    }
  }
}
