// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions.assertionhandler;

public class AssertionHandlers {
  static String methodWithAssertionError(Throwable assertion) {
    return assertion.getStackTrace()[0].getMethodName();
  }

  public static void assertionHandler(Throwable assertion) {
    System.out.println(
        "assertionHandler: "
            + (assertion.getMessage() != null
                ? assertion.getMessage()
                : methodWithAssertionError(assertion)));
  }

  public static void assertionHandlerRethrowing(Throwable assertion) throws Throwable {
    System.out.println(
        "assertionHandlerRethrowing: "
            + (assertion.getMessage() != null
                ? assertion.getMessage()
                : methodWithAssertionError(assertion)));
    throw assertion;
  }
}
