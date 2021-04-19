// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

/** Non-kept class for internal access from tests. */
public class RetraceHelper {

  public static void runForTesting(RetraceCommand command, boolean allowExperimentalMapping) {
    Retrace.runForTesting(command, allowExperimentalMapping);
  }
}
