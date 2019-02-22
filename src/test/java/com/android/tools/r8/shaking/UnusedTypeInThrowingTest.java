// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

class UnusedTypeInThrowing {

  public static final String EXPECTED = System.currentTimeMillis() >= 0 ? "42" : null;

  public static void main(String[] args) throws UnusedTypeInThrowingThrowable {
    System.out.print(EXPECTED);
  }
}

class UnusedTypeInThrowingThrowable extends Throwable {}

public class UnusedTypeInThrowingTest extends TestBase {

  static final Class THROWABLE_CLASS = UnusedTypeInThrowingThrowable.class;
  static final Class MAIN_CLASS = UnusedTypeInThrowing.class;

  @Test
  public void testTypeIsMarkedAsLive()
      throws IOException, CompilationFailedException, ExecutionException, NoSuchMethodException {
    CodeInspector inspector =
        testForR8(Backend.CF)
            .enableGraphInspector()
            .addProgramClasses(MAIN_CLASS)
            .addProgramClasses(THROWABLE_CLASS)
            .addKeepMainRule(MAIN_CLASS)
            .addKeepRules("-keepattributes Exceptions")
            .run(MAIN_CLASS)
            .assertSuccessWithOutput(UnusedTypeInThrowing.EXPECTED)
            .inspector();

    assertTrue(inspector.clazz(THROWABLE_CLASS).isPresent());
    // TODO(b/124217402) When done check that THROWABLE_CLASS is kept by the throwing annotation.
  }
}
