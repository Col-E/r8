// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.whyareyoukeeping;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import org.junit.Test;

class A {

}

public class WhyAreYouKeepingTest extends TestBase {
  @Test
  public void test() throws Exception {
    String proguardConfig = String.join("\n", ImmutableList.of(
        "-keep class " + A.class.getCanonicalName() + " { *; }",
        "-whyareyoukeeping class " + A.class.getCanonicalName()
    ));
    PrintStream stdout = System.out;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    System.setOut(new PrintStream(baos));
    compileWithR8(ImmutableList.of(A.class), proguardConfig);
    String output = new String(baos.toByteArray(), Charset.defaultCharset());
    System.setOut(stdout);
    String expected = String.join(System.lineSeparator(), ImmutableList.of(
        "com.android.tools.r8.shaking.whyareyoukeeping.A",
        "|- is live because referenced in keep rule:",
        "|    -keep class com.android.tools.r8.shaking.whyareyoukeeping.A {",
        "|      *;",
        "|    };",
        ""));
    assertEquals(expected, output);
  }
}
