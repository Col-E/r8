// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
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
public class MethodHandlesPrivateLookupInTest extends VarHandleDesugaringTestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("0", "0", "null");
  private static final String MAIN_CLASS = VarHandle.MethodHandlesPrivateLookupIn.typeName();
  private static final String JAR_ENTRY = "varhandle/MethodHandlesPrivateLookupIn.class";
  private static final List<String> JAR_ENTRIES =
      ImmutableList.of(
          "varhandle/MethodHandlesPrivateLookupIn.class", "varhandle/util/WithPrivateFields.class");

  @Override
  protected String getMainClass() {
    return MAIN_CLASS;
  }

  @Override
  protected List<String> getKeepRules() {
    return ImmutableList.of(
        "-keep class " + getMainClass() + "{ <fields>; }",
        "-keep class varhandle.util.WithPrivateFields { <fields>; }");
  }

  @Override
  protected List<String> getJarEntries() {
    return JAR_ENTRIES;
  }

  @Override
  protected String getExpectedOutputForReferenceImplementation() {
    return EXPECTED_OUTPUT;
  }

  @Override
  protected String getExpectedOutputForDesugaringImplementation() {
    // TODO(b/247076137): Desugar implementation allows VarHandle on private fields.
    return StringUtils.lines(
        "Unexpected success", "0", "Unexpected success", "0", "Unexpected success", "null");
  }
}
