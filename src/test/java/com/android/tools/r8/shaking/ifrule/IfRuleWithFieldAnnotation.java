// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ProguardVersion;
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
  public static final String CONDITIONAL_KEEP_RULE =
      "-if class * {"
          + " @com.android.tools.r8.shaking.ifrule.IfRuleWithFieldAnnotation$SerializedName"
          + " <fields>; }\n"
          + "-keep,allowobfuscation class <1> {\n"
          + "  <init>(...);\n"
          + "  @com.android.tools.r8.shaking.ifrule.IfRuleWithFieldAnnotation$SerializedNamed"
          + " <fields>;\n"
          + "}";

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
        .addKeepRules(CONDITIONAL_KEEP_RULE)
        .setMinApi(parameters)
        .compile()
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(Bar.class).field("int", "value"), isPresent());
              assertThat(codeInspector.clazz(Bar.class).init("int"), isPresent());
            })
        .run(parameters.getRuntime(), Foo.class)
        .assertSuccessWithOutputLines(EXPECTED);
    // We should remove the class if the usage of the field is not live.
    testForR8(parameters.getBackend())
        .addProgramClasses(Foo.class, Bar.class, SerializedName.class, FooNotCallingBar.class)
        .addKeepMainRule(FooNotCallingBar.class)
        .addKeepRules(CONDITIONAL_KEEP_RULE)
        .allowUnusedProguardConfigurationRules()
        .setMinApi(parameters)
        .compile()
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(Bar.class), isAbsent());
            })
        .run(parameters.getRuntime(), FooNotCallingBar.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testProguard() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForProguard(ProguardVersion.V7_0_0)
        .addProgramClasses(Foo.class, Bar.class, SerializedName.class)
        .addDontWarn(getClass())
        .addKeepMainRule(Foo.class)
        .addKeepRules(CONDITIONAL_KEEP_RULE)
        .compile()
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(Bar.class).field("int", "value"), isPresent());
              assertThat(codeInspector.clazz(Bar.class).init("int"), isPresent());
            })
        .run(parameters.getRuntime(), Foo.class)
        .assertSuccessWithOutputLines(EXPECTED);
    testForProguard(ProguardVersion.V7_0_0)
        .addProgramClasses(Foo.class, Bar.class, SerializedName.class, FooNotCallingBar.class)
        .addDontWarn(getClass())
        .addKeepMainRule(FooNotCallingBar.class)
        .noMinification()
        .addKeepRules(CONDITIONAL_KEEP_RULE)
        .compile()
        .inspect(
            codeInspector -> {
              // The if rule above will make proguard keep the class and the constructor, but not
              // the field. If we don't have the rule, proguard will remove the class, see test
              // below.
              assertThat(codeInspector.clazz(Bar.class), isPresent());
              assertThat(codeInspector.clazz(Bar.class).init("int"), isPresent());
              assertThat(codeInspector.clazz(Bar.class).field("int", "value"), isAbsent());
            })
        .run(parameters.getRuntime(), FooNotCallingBar.class)
        .assertSuccessWithOutputLines(EXPECTED);
    // Test that without the conditional keep rule proguard correctly removes the class.
    testForProguard(ProguardVersion.V7_0_0)
        .addProgramClasses(Foo.class, Bar.class, SerializedName.class, FooNotCallingBar.class)
        .addDontWarn(getClass())
        .addKeepMainRule(FooNotCallingBar.class)
        .noMinification()
        .compile()
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(Bar.class), isAbsent());
            })
        .run(parameters.getRuntime(), FooNotCallingBar.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface SerializedName {}

  public static class Foo {

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

  public static class FooNotCallingBar {
    public static void main(String[] args) {
      System.out.println("foobar");
    }
  }

  public static class Bar {
    @SerializedName public int value;

    public Bar(int value) {
      this.value = value;
    }
  }
}
