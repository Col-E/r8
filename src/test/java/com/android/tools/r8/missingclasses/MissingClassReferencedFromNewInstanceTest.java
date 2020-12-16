// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import org.junit.Test;

public class MissingClassReferencedFromNewInstanceTest extends MissingClassesTestBase {

  public MissingClassReferencedFromNewInstanceTest(
      DontWarnConfiguration dontWarnConfiguration, TestParameters parameters) {
    super(dontWarnConfiguration, parameters);
  }

  @Test
  public void test() throws Exception {
    AssertUtils.assertFailsCompilationIf(
        !getDontWarnConfiguration().isDontWarnMissingClass(),
        () -> {
          // TODO(b/175542052): Should succeed with -dontwarn, but there are spurious missing class
          //  warnings.
          compile(Main.class, MissingClass.class);
        },
        exception -> {
          assertTrue(exception instanceof CompilationFailedException);
          assertTrue(exception.getCause() instanceof CompilationError);

          // TODO(b/175542052): Only MissingClass should be reported as missing.
          assertThat(
              exception.getCause().getMessage(),
              allOf(
                  containsString(
                      "Compilation can't be completed because the class `"
                          + MissingClass.class.getTypeName()
                          + "` is missing.")));
        });
  }

  static class Main {

    public static void main(String[] args) {
      new MissingClass();
    }
  }

  static class MissingClass {}
}
