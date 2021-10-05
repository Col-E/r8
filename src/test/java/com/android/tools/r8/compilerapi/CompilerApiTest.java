// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Base class for any actual API test.
 *
 * <p>Subclasses of this must only use the public API and the otherwise linked libraries (junit,
 * etc).
 */
@RunWith(Parameterized.class)
public abstract class CompilerApiTest {

  public static final String API_TEST_MODE_KEY = "API_TEST_MODE";
  public static final String API_TEST_MODE_EXTERNAL = "external";

  public static final String API_TEST_LIB_KEY = "API_TEST_LIB";
  public static final String API_TEST_LIB_YES = "yes";
  public static final String API_TEST_LIB_NO = "no";

  @Parameters(name = "{0}")
  public static List<Object> data() {
    // Simulate only running the API tests directly on the "none" runtime configuration.
    String runtimes = System.getProperty("runtimes");
    if (runtimes != null && !runtimes.contains("none")) {
      return Collections.emptyList();
    }
    return Collections.singletonList("none");
  }

  public CompilerApiTest(Object none) {
    assertEquals("none", none);
  }

  /** Predicate to determine if the test is being run externally. */
  public boolean isRunningExternal() {
    return API_TEST_MODE_EXTERNAL.equals(System.getProperty(API_TEST_MODE_KEY));
  }

  /** Predicate to determine if the test is being run for an R8 lib compilation. */
  public boolean isRunningR8Lib() {
    return API_TEST_LIB_YES.equals(System.getProperty(API_TEST_LIB_KEY));
  }
}
