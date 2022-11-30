// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.varhandle;

import com.android.tools.r8.examples.jdk9.VarHandle;
import com.android.tools.r8.utils.StringUtils;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class VarHandleDesugaringArrayOfIntTest extends VarHandleDesugaringTestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("1", "0", "1", "0", "1", "2", "1", "3");
  private static final String MAIN_CLASS = VarHandle.ArrayOfInt.typeName();
  private static final String JAR_ENTRY = "varhandle/ArrayOfInt.class";

  @Override
  protected String getMainClass() {
    return MAIN_CLASS;
  }

  @Override
  protected String getJarEntry() {
    return JAR_ENTRY;
  }

  @Override
  protected String getExpectedOutput() {
    return EXPECTED_OUTPUT;
  }
}
