// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.varhandle;

import com.android.tools.r8.examples.jdk9.VarHandle;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class VarHandleDesugaringArrayOfLongTest extends VarHandleDesugaringTestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "testGet",
          "1",
          "2",
          "1",
          "2",
          "1",
          "2",
          "1.0",
          "2.0",
          "1.0",
          "2.0",
          "testGetVolatile",
          "1",
          "2",
          "1",
          "2",
          "1",
          "2",
          "1.0",
          "2.0",
          "1.0",
          "2.0",
          "testSet",
          "1",
          "0",
          "1",
          "2",
          "3",
          "2",
          "3",
          "4",
          "5",
          "4",
          "5",
          "6",
          "7",
          "6",
          "7",
          "8",
          "48",
          "8",
          "48",
          "49",
          "50",
          "49",
          "50",
          "51",
          "9",
          "51",
          "9",
          "10",
          "11",
          "10",
          "11",
          "12",
          "13",
          "12",
          "13",
          "14",
          "15",
          "14",
          "15",
          "16",
          "15",
          "16",
          "15",
          "16",
          "15",
          "16",
          "15",
          "16",
          "15",
          "16",
          "15",
          "16",
          "15",
          "16",
          "testSetVolatile",
          "1",
          "0",
          "1",
          "2",
          "3",
          "2",
          "3",
          "4",
          "5",
          "4",
          "5",
          "6",
          "7",
          "6",
          "7",
          "8",
          "48",
          "8",
          "48",
          "49",
          "50",
          "49",
          "50",
          "51",
          "9",
          "51",
          "9",
          "10",
          "11",
          "10",
          "11",
          "12",
          "13",
          "12",
          "13",
          "14",
          "15",
          "14",
          "15",
          "16",
          "15",
          "16",
          "15",
          "16",
          "15",
          "16",
          "15",
          "16",
          "15",
          "16",
          "15",
          "16",
          "15",
          "16",
          "testArrayVarHandleForNonSingleDimension",
          "IllegalArgumentException");

  private static final String MAIN_CLASS = VarHandle.ArrayOfLong.typeName();
  private static final String JAR_ENTRY = "varhandle/ArrayOfLong.class";

  @Override
  protected String getMainClass() {
    return MAIN_CLASS;
  }

  @Override
  protected List<String> getJarEntries() {
    return ImmutableList.of(JAR_ENTRY);
  }

  @Override
  protected String getExpectedOutputForReferenceImplementation() {
    return StringUtils.lines(EXPECTED_OUTPUT.trim(), "Got array element VarHandle");
  }

  @Override
  protected String getExpectedOutputForDesugaringImplementation() {
    return StringUtils.lines(EXPECTED_OUTPUT.trim(), "UnsupportedOperationException");
  }
}
