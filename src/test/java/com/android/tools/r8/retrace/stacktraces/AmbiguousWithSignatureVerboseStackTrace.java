// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class AmbiguousWithSignatureVerboseStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "java.lang.IndexOutOfBoundsException",
        "\tat java.util.ArrayList.get(ArrayList.java:411)",
        "\tat com.android.tools.r8.Internal.zza(Unknown)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.Internal -> com.android.tools.r8.Internal:",
        "  10:10:void foo(int):10:10 -> zza",
        "  11:11:void foo(int,int):11:11 -> zza",
        "  12:12:void foo(int,boolean):12:12 -> zza",
        "  13:13:boolean foo(int,int):13:13 -> zza");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "java.lang.IndexOutOfBoundsException",
        "\tat java.util.ArrayList.get(ArrayList.java:411)",
        "\tat com.android.tools.r8.Internal.boolean foo(int,int)(Internal.java)",
        "\t<OR> at com.android.tools.r8.Internal.void foo(int)(Internal.java)",
        "\t<OR> at com.android.tools.r8.Internal.void foo(int,boolean)(Internal.java)",
        "\t<OR> at com.android.tools.r8.Internal.void foo(int,int)(Internal.java)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
