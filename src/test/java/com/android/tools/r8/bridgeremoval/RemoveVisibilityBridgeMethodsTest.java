// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RemoveVisibilityBridgeMethodsTest extends TestBase {

  private final boolean minification;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, minification: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RemoveVisibilityBridgeMethodsTest(boolean minification, TestParameters parameters) {
    this.minification = minification;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(RemoveVisibilityBridgeMethodsTest.class)
        .addKeepMainRule(Main.class)
        .allowAccessModification()
        .minification(minification)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess();
  }

  private void inspect(CodeInspector inspector) throws Exception {
    assertThat(inspector.method(Outer.SubClass.class.getMethod("method")), not(isPresent()));
    assertThat(inspector.method(Outer.StaticSubClass.class.getMethod("method")), not(isPresent()));
  }

  /**
   * Regression test for b76383728 to make sure we correctly identify and remove real visibility
   * forward bridge methods synthesized by javac.
   */
  @Test
  public void regressionTest_b76383728() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder superClass = jasminBuilder.addClass("SuperClass");
    superClass.addDefaultConstructor();
    superClass.addVirtualMethod("method", Collections.emptyList(), "Ljava/lang/String;",
        ".limit stack 1",
        "ldc \"Hello World\"",
        "areturn");

    // Generate a subclass with a bridge method targeting SuperClass.method().
    ClassBuilder subclass = jasminBuilder.addClass("SubClass", superClass.name);
    subclass.addBridgeMethod("getMethod", Collections.emptyList(), "Ljava/lang/String;",
        ".limit stack 1",
        "aload_0",
        "invokespecial " + superClass.name + "/method()Ljava/lang/String;",
        "areturn");

    ClassBuilder mainClass = jasminBuilder.addClass("Main");
    mainClass.addMainMethod(
        ".limit stack 3",
        ".limit locals 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "new " + subclass.name,
        "dup",
        "invokespecial " + subclass.name + "/<init>()V",
        "invokevirtual " + subclass.name + "/getMethod()Ljava/lang/String;",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        "return");

    List<byte[]> programClassFileData = jasminBuilder.buildClasses();

    // Run input program on java.
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClassFileData(programClassFileData)
          .run(parameters.getRuntime(), mainClass.name)
          .assertSuccessWithOutputLines("Hello World");
    }

    testForR8(parameters.getBackend())
        .addProgramClassFileData(programClassFileData)
        .addKeepMainRule(mainClass.name)
        .addOptionsModification(options -> options.inlinerOptions().enableInlining = false)
        .allowAccessModification()
        .minification(minification)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(superClass.name), not(isPresent()));
              assertThat(inspector.clazz(subclass.name), not(isPresent()));
            })
        .run(parameters.getRuntime(), mainClass.name)
        .assertSuccessWithOutputLines("Hello World");
  }

  static class Main {

    public static void main(String[] args) {
      new Outer().create().method();
      new Outer.StaticSubClass().method();
    }
  }

  static class Outer {

    class SuperClass {
      public void method() {}
    }

    // As SuperClass is package private SubClass will have a bridge method for "method".
    public class SubClass extends SuperClass {}

    public SubClass create() {
      return new SubClass();
    }

    static class StaticSuperClass {
      public void method() {}
    }

    // As SuperClass is package private SubClass will have a bridge method for "method".
    public static class StaticSubClass extends StaticSuperClass {}
  }
}
