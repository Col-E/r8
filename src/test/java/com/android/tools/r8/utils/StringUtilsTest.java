// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringUtilsTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public StringUtilsTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void splitLines() {
    assertListEquals(ImmutableList.of(), StringUtils.splitLines(""));
    assertListEquals(ImmutableList.of(""), StringUtils.splitLines("\n"));
    assertListEquals(ImmutableList.of("", "", ""), StringUtils.splitLines("\n\n\n"));
    assertListEquals(ImmutableList.of(" "," "), StringUtils.splitLines(" \n "));
    assertListEquals(ImmutableList.of("a","b"), StringUtils.splitLines("a\nb"));

    assertListEquals(ImmutableList.of("\r\r\r"), StringUtils.splitLines("\r\r\r"));
    assertListEquals(ImmutableList.of("", "\r"), StringUtils.splitLines("\r\n\r"));
    assertListEquals(ImmutableList.of("", "", "", "\r"), StringUtils.splitLines("\r\n\r\n\r\n\r"));
    assertListEquals(ImmutableList.of("\r ", "\r \r"), StringUtils.splitLines("\r \r\n\r \r"));
    assertListEquals(ImmutableList.of("\ra", "\rb\r"), StringUtils.splitLines("\ra\r\n\rb\r"));

    assertListEquals(ImmutableList.of("\ra\r\rb\r"), StringUtils.splitLines("\ra\r\rb\r"));
  }

  private void assertListEquals(List<String> xs, List<String> ys) {
    assertEquals(
        StringUtils.join(", ", xs, s -> '"' + StringUtils.toASCIIString(s) + '"', BraceType.SQUARE),
        StringUtils.join(
            ", ", ys, s -> '"' + StringUtils.toASCIIString(s) + '"', BraceType.SQUARE));
  }

  @Test
  public void testTrim() {
    assertEquals("", StringUtils.trim(""));
    String empty = "";
    assertSame(empty, StringUtils.trim(empty));
    assertSame(Strings.repeat("A", 1), StringUtils.trim(Strings.repeat("A", 1)));
    String oneChar = "A";
    assertSame(oneChar, StringUtils.trim(oneChar));
    assertEquals(Strings.repeat("A", 2), Strings.repeat("A", 2));
    String twoChar = "AB";
    assertSame(twoChar, StringUtils.trim(twoChar));
    assertEquals(Strings.repeat("A", 10), Strings.repeat("A", 10));
    String manyChar = Strings.repeat("A", 10);
    assertSame(manyChar, StringUtils.trim(manyChar));
  }

  @Test
  public void testTrimWithWhitespace() {
    List<String> ws =
        ImmutableList.of(
            "",
            " ",
            "  ",
            "\t ",
            " \t",
            "" + StringUtils.BOM,
            StringUtils.BOM + " " + StringUtils.BOM);
    List<String> strings = ImmutableList.of("", "A", "AB", Strings.repeat("A", 10));
    for (String before : ws) {
      for (String after : ws) {
        for (String string : strings) {
          assertEquals(string, StringUtils.trim(before + string + after));
        }
      }
    }
  }
}
