// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.conversionTests;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Test;

public class TimeConversionCompilationTest extends APIConversionTestBase {

  @Test
  public void testTime() throws Exception {
    Path[] timeConversionClasses = getTimeConversionClasses();
    testForD8()
        .addProgramFiles(timeConversionClasses)
        .setMinApi(AndroidApiLevel.O)
        .compile()
        .inspect(this::checkTimeConversionAPIs);
  }

  private void checkTimeConversionAPIs(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("com.android.tools.r8.conversion.TimeConversions");
    assertThat(clazz, isPresent());
    assertEquals(7, clazz.allMethods().size());
    long countTo =
        clazz.allMethods().stream()
            .filter(m -> m.getMethod().method.name.toString().equals("to"))
            .count();
    assertEquals(3, countTo);
    long countFrom =
        clazz.allMethods().stream()
            .filter(m -> m.getMethod().method.name.toString().equals("from"))
            .count();
    assertEquals(3, countFrom);
  }
}
