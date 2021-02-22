// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProguardMapReaderPackageInfoTest extends TestBase {

  private final String MAPPING =
      StringUtils.joinLines(
          "android.support.v4.internal.package-info -> android.support.v4.internal.package-info:",
          "# {\"id\":\"sourceFile\",\"fileName\":\"SourceFile\"}");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ProguardMapReaderPackageInfoTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void parseMappings() throws IOException {
    ClassNameMapper.mapperFromString(MAPPING);
  }
}
