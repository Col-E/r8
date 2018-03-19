// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;


import com.android.tools.r8.ToolHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidClassNames extends NameTestBase {

  private static final String RESULT = "MAIN";
  private static final String MAIN_CLASS = "Main";

  @Parameters(name = "\"{0}\", jvm: {1}, art: {2}")
  public static Collection<Object[]> data() {
    Collection<Object[]> data = new ArrayList<>();
    data.addAll(NameTestBase.getCommonNameTestData(true));
    data.addAll(
        Arrays.asList(
            new Object[][] {
              {new TestString("a/b/c/a/D/"), true, false},
              {
                new TestString("a<b"),
                !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime(),
                false
              },
              {
                new TestString("a>b"),
                !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime(),
                false
              },
              {
                new TestString("<a>b"),
                !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime(),
                false
              },
              {
                new TestString("<a>"),
                !ToolHelper.isWindows() && !ToolHelper.isJava9Runtime(),
                false
              }
            }));
    return data;
  }

  private String name;
  private boolean validForJVM;
  private boolean validForArt;

  public InvalidClassNames(TestString name, boolean validForJVM, boolean validForArt) {
    this.name = name.getValue();
    this.validForJVM = validForJVM;
    this.validForArt = validForArt;
  }

  private JasminBuilder createJasminBuilder() {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass(name);
    clazz.addStaticMethod(
        "run",
        Collections.emptyList(),
        "V",
        ".limit stack 2",
        ".limit locals 0",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc \"" + RESULT + "\"",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  return");

    clazz = builder.addClass(MAIN_CLASS);
    clazz.addMainMethod(
        ".limit stack 0", ".limit locals 1", "invokestatic " + name + "/run()V", "  return");

    return builder;
  }

  @Test
  public void invalidClassName() throws Exception {
    runNameTesting(validForJVM, createJasminBuilder(), MAIN_CLASS, RESULT, validForArt, name);
  }
}
