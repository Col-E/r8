// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package instrumentationtest;

import android.content.Context;
import android.test.InstrumentationTestCase;

public class InstrumentationTest extends InstrumentationTestCase {
  Context context;

  public void setUp() throws Exception {
    super.setUp();
    context = getInstrumentation().getContext();
    assertNotNull(context);
  }

  public void testSomething() {
    assertEquals(false, true);
  }
}
