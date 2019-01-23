// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

class Tester {
  public int foo() {
    float[][][] fs = new float[1][2][3];
    return fs.length;
  }

  public static void main(String[] args) {
    System.out.println(new Tester().foo());
  }
}

// The DirectoryClasspathProvider asserts lookups are reference types which witnessed the issue.
public class RegressionForPrimitiveDefinitionForLookup extends TestBase {

  public final Class<Tester> CLASS = Tester.class;
  public String EXPECTED = StringUtils.lines("1");

  @Test
  public void testWithArchiveClasspath() throws Exception {
    testForD8()
        .addClasspathClasses(CLASS)
        .addProgramClasses(CLASS)
        .run(CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithDirectoryClasspath() throws Exception {
    testForD8()
        .addClasspathFiles(ToolHelper.getClassPathForTests())
        .addProgramClasses(CLASS)
        .run(CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }
}
