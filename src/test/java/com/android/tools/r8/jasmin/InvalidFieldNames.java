// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import com.android.tools.r8.ToolHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidFieldNames extends NameTestBase {

  private static final String CLASS_NAME = "Test";
  private static String FIELD_VALUE = "42";

  @Parameters(name = "\"{0}\", jvm: {1}, art: {2}")
  public static Collection<Object[]> data() {
    Collection<Object[]> data = new ArrayList<>();
    data.addAll(NameTestBase.getCommonNameTestData(false));
    data.addAll(
        Arrays.asList(
            new Object[][] {
              {new TestString("a/b"), false, false},
              {new TestString("<a"), !ToolHelper.isJava9Runtime(), false},
              {new TestString("a>"), !ToolHelper.isJava9Runtime(), false},
              {new TestString("a<b>"), !ToolHelper.isJava9Runtime(), false},
              {new TestString("<a>b"), !ToolHelper.isJava9Runtime(), false},
              {new TestString("<a>"), false, true}
            }));
    return data;
  }

  private String name;
  private boolean validForJVM;
  private boolean validForArt;

  public InvalidFieldNames(TestString name, boolean validForJVM, boolean validForArt) {
    this.name = name.getValue();
    this.validForJVM = validForJVM;
    this.validForArt = validForArt;
  }

  private JasminBuilder createJasminBuilder() {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass(CLASS_NAME);

    clazz.addStaticField(name, "I", FIELD_VALUE);

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  getstatic " + CLASS_NAME + "/" + name + " I",
        "  invokevirtual java/io/PrintStream.print(I)V",
        "  return");
    return builder;
  }

  @Test
  public void invalidFieldNames() throws Exception {
    runNameTesting(validForJVM, createJasminBuilder(), CLASS_NAME, FIELD_VALUE, validForArt, name);
  }
}
