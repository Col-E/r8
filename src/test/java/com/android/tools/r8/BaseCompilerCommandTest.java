// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertFalse;

// Utilities common to all compiler command tests.
public class BaseCompilerCommandTest {

  public static void assertDesugaringDisabled(BaseCompilerCommand command) {
    assertFalse(command.getEnableDesugaring());
    assertFalse(command.getInternalOptions().enableDesugaring);
  }

}
