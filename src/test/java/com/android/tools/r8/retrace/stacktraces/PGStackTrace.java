// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class PGStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "09-16 15:43:01.249 23316 23316 E AndroidRuntime: java.lang.NullPointerException: Attempt"
            + " to invoke virtual method 'boolean"
            + " com.google.android.foo(com.google.android.foo.Data$Key)' on a null object"
            + " reference",
        "09-16 15:43:01.249 23316 23316 E AndroidRuntime:        at"
            + " com.google.apps.sectionheader.SectionHeaderListController.onToolbarStateChanged(PG:586)",
        "09-16 15:43:01.249 23316 23316 E AndroidRuntime:        at"
            + " com.google.apps.Controller.onToolbarStateChanged(PG:1087)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.google.apps.sectionheader.SectionHeaderListController "
            + "-> com.google.apps.sectionheader.SectionHeaderListController:",
        "com.google.apps.Controller " + "-> com.google.apps.Controller:");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "09-16 15:43:01.249 23316 23316 E AndroidRuntime: java.lang.NullPointerException: Attempt"
            + " to invoke virtual method 'boolean"
            + " com.google.android.foo(com.google.android.foo.Data$Key)' on a null object"
            + " reference",
        "09-16 15:43:01.249 23316 23316 E AndroidRuntime:        at"
            + " com.google.apps.sectionheader.SectionHeaderListController.onToolbarStateChanged(SectionHeaderListController.java:586)",
        "09-16 15:43:01.249 23316 23316 E AndroidRuntime:        at"
            + " com.google.apps.Controller.onToolbarStateChanged(Controller.java:1087)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "09-16 15:43:01.249 23316 23316 E AndroidRuntime: java.lang.NullPointerException: Attempt"
            + " to invoke virtual method 'boolean"
            + " com.google.android.foo(com.google.android.foo.Data$Key)' on a null object"
            + " reference",
        "09-16 15:43:01.249 23316 23316 E AndroidRuntime:        at"
            + " com.google.apps.sectionheader.SectionHeaderListController.onToolbarStateChanged(SectionHeaderListController.java:586)",
        "09-16 15:43:01.249 23316 23316 E AndroidRuntime:        at"
            + " com.google.apps.Controller.onToolbarStateChanged(Controller.java:1087)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
