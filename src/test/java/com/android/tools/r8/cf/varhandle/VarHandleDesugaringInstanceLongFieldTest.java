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
public class VarHandleDesugaringInstanceLongFieldTest extends VarHandleDesugaringTestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "testSet",
          "0",
          "1",
          "2",
          "3",
          "4",
          "48",
          "49",
          "5",
          "6",
          "7",
          "8",
          "8",
          "8",
          "8",
          "8",
          "testCompareAndSet",
          "0",
          "1",
          "2",
          "3",
          "4",
          "5",
          "6",
          "7",
          "8",
          "9",
          "10",
          "48",
          "49",
          "50",
          "51",
          "52",
          "53",
          "11",
          "12",
          "13",
          "14",
          "14",
          "14",
          "14",
          "14",
          "14",
          "14",
          "14",
          "14",
          "14");
  private static final String MAIN_CLASS = VarHandle.InstanceLongField.typeName();
  private static final String JAR_ENTRY = "varhandle/InstanceLongField.class";

  @Override
  protected String getMainClass() {
    return MAIN_CLASS;
  }

  @Override
  protected String getKeepRules() {
    return "-keep class " + getMainClass() + "{ <fields>; }";
  }

  @Override
  protected List<String> getJarEntries() {
    return ImmutableList.of(JAR_ENTRY);
  }

  @Override
  protected String getExpectedOutputForReferenceImplementation() {
    return EXPECTED_OUTPUT;
  }

  @Override
  protected boolean getTestWithDesugaring() {
    return true;
  }
}
