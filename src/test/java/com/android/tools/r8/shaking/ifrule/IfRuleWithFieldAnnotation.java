// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IfRuleWithFieldAnnotation extends TestBase {

  static final String EXPECTED = "foobar";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IfRuleWithFieldAnnotation(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Foo.class, Bar.class, SerializedName.class)
        .addKeepMainRule(Foo.class)
        .addKeepRules(
            "-if class * {"
                + " @com.android.tools.r8.shaking.ifrule.IfRuleWithFieldAnnotation$SerializedName"
                + " <fields>; }\n"
                + "-keep,allowobfuscation class <1> {\n"
                + "  <init>(...);\n"
                + "  @com.android.tools.r8.shaking.ifrule.IfRuleWithFieldAnnotation$SerializedNamed"
                + " <fields>;\n"
                + "}")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(Bar.class).field("int", "value"), isPresent());
              assertThat(codeInspector.clazz(Bar.class).init("int"), isPresent());
            })
        .run(parameters.getRuntime(), Foo.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface SerializedName {}

  public static class Foo {
    public static Object object;

    public static void main(String[] args) {
      callOnBar(args);
      System.out.println("foobar");
    }

    private static void callOnBar(String[] args) {
      if (System.currentTimeMillis() == 0) {
        int i = ((Bar) instantiateObject()).value;
        System.out.println(i);
      }
    }

    private static Object instantiateObject() {
      try {
        return Class.forName("class" + System.currentTimeMillis()).newInstance();
      } catch (Exception e) {
        e.printStackTrace();
      }
      return null;
    }
  }

  public static class Bar {
    @SerializedName public int value;

    public Bar(int value) {
      this.value = value;
    }
  }
}
