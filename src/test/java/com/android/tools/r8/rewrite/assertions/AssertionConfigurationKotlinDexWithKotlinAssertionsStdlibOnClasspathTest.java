// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssertionConfigurationKotlinDexWithKotlinAssertionsStdlibOnClasspathTest
    extends AssertionConfigurationKotlinDexTestBase {

  public AssertionConfigurationKotlinDexWithKotlinAssertionsStdlibOnClasspathTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(parameters, kotlinParameters, true, false);
  }
}
