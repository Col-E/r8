// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.b134858535;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;

public class EventPublisherTest extends TestBase {

  public static class Main {

    public static void main(String[] args) {
      new EventPublisher$b().apply("foo");
    }
  }

  @Test
  public void testPrivateMethodsInLambdaClass() throws CompilationFailedException {
    // This test only tests if the dump can be compiled without errors.
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class, Interface.class)
        .addProgramClassFileData(EventPublisher$bDump.dump())
        .addKeepClassRules(Interface.class)
        .addKeepMainRule(Main.class)
        .allowDiagnosticInfoMessages()
        .setMinApi(AndroidApiLevel.L)
        .compile()
        // TODO(b/157537996): Handle JStyle lambdas with private methods.
        .assertAllInfoMessagesMatch(
            containsString(
                "Unrecognized Kotlin lambda"
                    + " [com.android.tools.r8.shaking.b134858535.EventPublisher$b]"));
  }
}
