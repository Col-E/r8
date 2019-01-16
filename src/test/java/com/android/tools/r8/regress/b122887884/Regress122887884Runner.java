// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b122887884;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

public class Regress122887884Runner extends TestBase {

  private final Class<?> CLASS = Regress122887884.class;
  private final String EXPECTED = StringUtils.lines("0");

  @Test
  public void test() throws Exception {
    testForD8().addProgramClasses(CLASS).run(CLASS).assertSuccessWithOutput(EXPECTED);
  }
}
