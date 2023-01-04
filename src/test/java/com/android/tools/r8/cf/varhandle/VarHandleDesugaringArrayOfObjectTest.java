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
public class VarHandleDesugaringArrayOfObjectTest extends VarHandleDesugaringTestBase {

  private static final String TEST_GET_EXPECTED_OUTPUT =
      StringUtils.lines(
              "1", "2", "1", "2", "1", "2", "1", "2", "1.0", "2.0", "1.0", "2.0", "3", "4", "3",
              "4", "3", "4", "3.0", "4.0", "3.0", "4.0")
          .trim();

  private static final String TEST_SET_EXPECTED_OUTPUT =
      StringUtils.lines(
              "5", "5", "true", "true", "6", "6", "true", "true", "7", "7", "true", "true", "8",
              "8", "true", "true", "9.0", "9.0", "true", "true", "10.0", "10.0", "true", "true",
              "11.0", "11.0", "true", "true", "12.0", "12.0", "true", "true", "A", "A", "true",
              "true", "B", "B", "true", "true")
          .trim();

  private static final String TEST_COMPAREANDSET_EXPECTED_OUTPUT =
      StringUtils.lines(
              "null", "A(1)", "true", "A(2)", "true", "1", "2", "3", "4", "4", "4", "4", "5", "6",
              "7", "8", "8", "8", "8", "false", "8", "false", "8", "8", "8", "8", "8", "8", "8",
              "8", "8", "8", "8", "8", "8", "8")
          .trim();

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

  private static final String MAIN_CLASS = VarHandle.ArrayOfObject.typeName();
  private static final List<String> JAR_ENTRIES =
      ImmutableList.of("varhandle/ArrayOfObject.class", "varhandle/ArrayOfObject$A.class");

  @Override
  protected String getMainClass() {
    return MAIN_CLASS;
  }

  @Override
  protected List<String> getJarEntries() {
    return JAR_ENTRIES;
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
