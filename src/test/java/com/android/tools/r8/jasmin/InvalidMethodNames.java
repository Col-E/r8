// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;


import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidMethodNames extends NameTestBase {

  private final String CLASS_NAME = "Test";
  private final String RESULT = "CALLED";

  private String name;
  private boolean validForJVM;
  private boolean validForArt;

  @Parameters(name = "\"{0}\", jvm: {1}, art: {2}")
  public static Collection<Object[]> data() {
    Collection<Object[]> data = new ArrayList<>();
    data.addAll(NameTestBase.getCommonNameTestData(false));
    data.addAll(
        Arrays.asList(
            new Object[][] {
              {new TestString("a/b"), false, false},
              {new TestString("<a"), false, false},
              {new TestString("a>"), !ToolHelper.isJava9Runtime(), false},
              {new TestString("<a>"), false, false}
            }));
    return data;
  }

  public InvalidMethodNames(TestString name, boolean validForJVM, boolean validForArt) {
    this.name = name.getValue();
    this.validForJVM = validForJVM;
    this.validForArt = validForArt;
  }

  JasminBuilder createJasminBuilder() {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass(CLASS_NAME);

    clazz.addStaticMethod(
        name,
        ImmutableList.of(),
        "V",
        ".limit stack 2",
        ".limit locals 0",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc \"" + RESULT + "\"",
        "  invokevirtual java/io/PrintStream.print(Ljava/lang/String;)V",
        "  return");

    clazz.addMainMethod(
        ".limit stack 0",
        ".limit locals 1",
        "  invokestatic " + CLASS_NAME + "/" + name + "()V",
        "  return");
    return builder;
  }

  static class StringReplaceData {
    final byte[] inputMutf8;
    final String placeholderString;
    final byte[] placeholderBytes;

    StringReplaceData(byte[] inputMutf8, String placeholderString, byte[] placeholderBytes) {
      this.inputMutf8 = inputMutf8;
      this.placeholderString = placeholderString;
      this.placeholderBytes = placeholderBytes;
    }
  }

  @Test
  public void invalidMethodName() throws Exception {
    runNameTesting(
        validForJVM,
        createJasminBuilder(),
        CLASS_NAME,
        RESULT,
        validForArt,
        StringUtils.toASCIIString(name));
  }
}
