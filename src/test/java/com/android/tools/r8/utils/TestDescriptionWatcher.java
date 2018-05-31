// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * This {@link TestWatcher} passes test description information
 * (namely test class and method names) to the test framework
 * via system properties.
 */
public class TestDescriptionWatcher extends TestWatcher {

  @Override
  protected void starting(Description description) {
    System.setProperty("test_class_name", description.getClassName());
    System.setProperty("test_name", description.getMethodName());
    System.setProperty("reset_output_index", "true");
  }

  @Override
  protected void finished(Description description) {
    System.clearProperty("test_class_name");
    System.clearProperty("test_name");
  }

}
