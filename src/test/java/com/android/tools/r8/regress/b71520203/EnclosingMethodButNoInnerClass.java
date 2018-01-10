// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b71520203;

import com.android.tools.r8.AsmTestBase;
import org.junit.Test;

public class EnclosingMethodButNoInnerClass extends AsmTestBase {

  @Test
  public void test() throws Exception {
    ensureSameOutputAfterMerging("Flaf", FlafDump.dump(), Flaf$ADump.dump());
  }
}
