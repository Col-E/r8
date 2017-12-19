// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.utils.StringUtils.BraceType;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;

public class StringUtilsTest {

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
        StringUtils.join(xs, ", ", BraceType.SQUARE, s -> '"' + StringUtils.toASCIIString(s) + '"'),
        StringUtils.join(ys, ", ", BraceType.SQUARE, s -> '"' + StringUtils.toASCIIString(s) + '"')
    );
  }
}
