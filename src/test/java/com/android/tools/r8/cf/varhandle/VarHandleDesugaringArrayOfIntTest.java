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
public class VarHandleDesugaringArrayOfIntTest extends VarHandleDesugaringTestBase {

  private static final String TEST_GET_EXPECTED_OUTPUT =
      StringUtils.lines("1", "2", "1", "2", "1", "2", "1", "2", "1.0", "2.0", "1.0", "2.0").trim();

  private static final String TEST_SET_EXPECTED_OUTPUT =
      StringUtils.lines(
              "1", "0", "1", "2", "3", "2", "3", "4", "5", "4", "5", "6", "7", "6", "7", "8", "48",
              "8", "48", "49", "50", "49", "50", "51", "9", "51", "9", "10", "11", "10", "11", "12",
              "11", "12", "11", "12", "11", "12", "11", "12", "11", "12", "11", "12", "11", "12",
              "11", "12", "11", "12")
          .trim();

  private static final String TEST_COMPAREANDSET_EXPECTED_OUTPUT =
      StringUtils.lines("1", "0", "1", "0", "1", "2", "1", "3").trim();

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "testGet",
          TEST_GET_EXPECTED_OUTPUT,
          "testGetVolatile",
          TEST_GET_EXPECTED_OUTPUT,
          "testSet",
          TEST_SET_EXPECTED_OUTPUT,
          "testSetVolatile",
          TEST_SET_EXPECTED_OUTPUT,
          "testSetRelease",
          TEST_SET_EXPECTED_OUTPUT,
          "testCompareAndSet",
          TEST_COMPAREANDSET_EXPECTED_OUTPUT,
          "testWeakCompareAndSet",
          TEST_COMPAREANDSET_EXPECTED_OUTPUT,
          "testArrayVarHandleForNonSingleDimension",
          "IllegalArgumentException");

  private static final String MAIN_CLASS = VarHandle.ArrayOfInt.typeName();
  private static final String JAR_ENTRY = "varhandle/ArrayOfInt.class";

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
