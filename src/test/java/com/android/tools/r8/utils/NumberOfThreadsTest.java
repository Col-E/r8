// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import org.junit.Test;

public class NumberOfThreadsTest {

  @Test(expected = IllegalArgumentException.class)
  public void zeroProcessorTest() {
    ThreadUtils.getExecutorServiceForProcessors(0).shutdown();
  }

  @Test
  public void singleProcessorTest() {
    ThreadUtils.getExecutorServiceForProcessors(1).shutdown();
  }

  @Test
  public void twoProcessorsTest() {
    ThreadUtils.getExecutorServiceForProcessors(2).shutdown();
  }

}
