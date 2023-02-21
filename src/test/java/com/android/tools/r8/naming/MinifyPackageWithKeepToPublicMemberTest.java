// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MinifyPackageWithKeepToPublicMemberTest extends TestBase {

  private final TestParameters parameters;
  private final boolean minify;
  private final String[] EXPECTED = new String[] {"Hello World!", "Hello World!"};

  @Parameters(name = "{0}, minifyA: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public MinifyPackageWithKeepToPublicMemberTest(TestParameters parameters, boolean minify) {
    this.parameters = parameters;
    this.minify = minify;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(
            transformer(A.class).removeInnerClasses().transform(),
            transformer(ReflectiveCallerOfA.class).removeInnerClasses().transform(),
            transformer(Main.class).removeInnerClasses().transform())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(A.class).removeInnerClasses().transform(),
            transformer(ReflectiveCallerOfA.class).removeInnerClasses().transform(),
            transformer(Main.class).removeInnerClasses().transform())
        .addKeepMainRule(Main.class)
        .applyIf(
            minify,
            builder -> builder.addKeepClassAndMembersRulesWithAllowObfuscation(A.class),
            builder -> builder.addKeepClassAndMembersRules(A.class))
        .addKeepRules("-keepclassmembernames class ** { *; }")
        .addKeepRules(
            "-identifiernamestring class "
                + ReflectiveCallerOfA.class.getTypeName()
                + " { java.lang.String className; }")
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClazz = inspector.clazz(A.class);
              assertThat(aClazz, isPresentAndRenamed(minify));
              ClassSubject reflectiveCallerOfAClass = inspector.clazz(ReflectiveCallerOfA.class);
              assertThat(reflectiveCallerOfAClass, isPresentAndRenamed());
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertThat(mainClass, isPresentAndNotRenamed());
              assertNotEquals(
                  mainClass.getDexProgramClass().type.getPackageDescriptor(),
                  reflectiveCallerOfAClass.getDexProgramClass().type.getPackageDescriptor());
              if (minify) {
                assertEquals(
                    aClazz.getDexProgramClass().type.getPackageDescriptor(),
                    reflectiveCallerOfAClass.getDexProgramClass().type.getPackageDescriptor());
              } else {
                assertNotEquals(
                    aClazz.getDexProgramClass().type.getPackageDescriptor(),
                    reflectiveCallerOfAClass.getDexProgramClass().type.getPackageDescriptor());
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class A {

    public A() {}

    public String foo = "Hello World!";

    public void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class ReflectiveCallerOfA {

    private static String className =
        "com.android.tools.r8.naming.MinifyPackageWithKeepToPublicMemberTest$A";

    @NeverInline
    public static void callA() throws Exception {
      Class<?> aClass = Class.forName(className);
      Object o = aClass.getDeclaredConstructor().newInstance();
      System.out.println(aClass.getDeclaredField("foo").get(o));
      aClass.getDeclaredMethod("foo").invoke(o);
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      ReflectiveCallerOfA.callA();
    }
  }
}
