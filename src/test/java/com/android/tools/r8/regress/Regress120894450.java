// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

public class Regress120894450 extends TestBase {

  @Test
  public void test() throws Exception {
    String testing = StringUtils.lines(
        "com.google.android.foobar -> EQa:",
        "    android.os.Handler com.google.android.bar.getRebindHandler() -> o",
        "    1:2:android.os.Handler com.google.android.bar.getRebindHandler():292:293 -> p",
        "    3:3:android.os.Handler com.google.android.bar.getRebindHandler():295:295 -> p");
    ClassNameMapper.mapperFromString(testing);
  }
}
