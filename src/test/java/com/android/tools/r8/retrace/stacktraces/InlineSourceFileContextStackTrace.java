// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class InlineSourceFileContextStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "  at com.google.appreduce.remapper.KotlinJavaSourceFileTestObject"
            + ".main(KotlinJavaSourceFileTestObject.java:1)");
  }

  @Override
  public String mapping() {
    return StringUtils.joinLines(
        "com.google.appreduce.remapper.KotlinJavaSourceFileTestLibrary ->"
            + " com.google.appreduce.remapper.KotlinJavaSourceFileTestLibrary:",
        "# {\"id\":\"sourceFile\",\"fileName\":\"KotlinJavaSourceFileTestLibrary.kt\"}",
        "    void <init>() -> <init>",
        "com.google.appreduce.remapper.KotlinJavaSourceFileTestObject ->"
            + " com.google.appreduce.remapper.KotlinJavaSourceFileTestObject:",
        "    void <init>() -> <init>",
        "    1:1:void com.google.appreduce.remapper.KotlinJavaSourceFileTestLibrary"
            + ".throwsException():22:22 -> main",
        "    1:1:void com.google.appreduce.remapper.KotlinJavaSourceFileTestLibrary"
            + ".callsThrowsException():19 -> main",
        "    1:1:void main(java.lang.String[]):32 -> main",
        "    2:7:void printStackTraceUpToMain(java.lang.Exception):19:24 -> main",
        "    2:7:void main(java.lang.String[]):34 -> main");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "  at com.google.appreduce.remapper.KotlinJavaSourceFileTestLibrary"
            + ".throwsException(KotlinJavaSourceFileTestLibrary.kt:22)",
        "  at com.google.appreduce.remapper.KotlinJavaSourceFileTestLibrary"
            + ".callsThrowsException(KotlinJavaSourceFileTestLibrary.kt:19)",
        "  at com.google.appreduce.remapper.KotlinJavaSourceFileTestObject"
            + ".main(KotlinJavaSourceFileTestObject.java:32)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "  at com.google.appreduce.remapper.KotlinJavaSourceFileTestLibrary"
            + ".void throwsException()(KotlinJavaSourceFileTestLibrary.kt:22)",
        "  at com.google.appreduce.remapper.KotlinJavaSourceFileTestLibrary"
            + ".void callsThrowsException()(KotlinJavaSourceFileTestLibrary.kt:19)",
        "  at com.google.appreduce.remapper.KotlinJavaSourceFileTestObject"
            + ".void main(java.lang.String[])(KotlinJavaSourceFileTestObject.java:32)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
