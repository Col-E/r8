// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

/**
 * This is testing the string representation of stack trace elements with built-in class loaders and
 * named/unnamed modules:
 * https://docs.oracle.com/javase/10/docs/api/java/lang/StackTraceElement.html#toString()
 */
public class NamedModuleStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "SomeFakeException: this is a fake exception",
        "\tat classloader.a.b.a/named_module@9.0/a.a(:101)",
        "\tat classloader.a.b.a//a.b(App.java:12)",
        "\tat named_module@2.1/a.c(Lib.java:80)",
        "\tat named_module/a.d(Lib.java:81)",
        "\tat a.e(MyClass.java:9)");
  }

  @Override
  public String mapping() {
    return StringUtils.joinLines(
        "com.android.tools.r8.Classloader -> classloader.a.b.a:",
        "com.android.tools.r8.Main -> a:",
        "  101:101:void main(java.lang.String[]):1:1 -> a",
        "  12:12:void foo(java.lang.String[]):2:2 -> b",
        "  80:80:void bar(java.lang.String[]):3:3 -> c",
        "  81:81:void baz(java.lang.String[]):4:4 -> d",
        "  9:9:void qux(java.lang.String[]):5:5 -> e");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "SomeFakeException: this is a fake exception",
        "\tat classloader.a.b.a/named_module@9.0/com.android.tools.r8.Main.main(Main.java:1)",
        "\tat classloader.a.b.a//com.android.tools.r8.Main.foo(Main.java:2)",
        "\tat named_module@2.1/com.android.tools.r8.Main.bar(Main.java:3)",
        "\tat named_module/com.android.tools.r8.Main.baz(Main.java:4)",
        "\tat com.android.tools.r8.Main.qux(Main.java:5)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "SomeFakeException: this is a fake exception",
        "\tat classloader.a.b.a/named_module@9.0/com.android.tools.r8.Main.void"
            + " main(java.lang.String[])(Main.java:1)",
        "\tat classloader.a.b.a//com.android.tools.r8.Main.void"
            + " foo(java.lang.String[])(Main.java:2)",
        "\tat named_module@2.1/com.android.tools.r8.Main.void bar(java.lang.String[])(Main.java:3)",
        "\tat named_module/com.android.tools.r8.Main.void baz(java.lang.String[])(Main.java:4)",
        "\tat com.android.tools.r8.Main.void qux(java.lang.String[])(Main.java:5)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
