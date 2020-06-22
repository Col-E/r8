// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.retrace.stacktraces;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.retrace.stacktraces.StackTraceForTest;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class RetraceInternalStackTraceForTest implements StackTraceForTest {

  private static final Path retraceInternal =
      Paths.get(ToolHelper.THIRD_PARTY_DIR).resolve("retrace_internal");
  private final Path obfuscatedPath;
  private final Path deobfuscatedPath;

  public RetraceInternalStackTraceForTest(String obfuscatedFile, String deobfuscatedFile) {
    this.obfuscatedPath = retraceInternal.resolve(obfuscatedFile);
    this.deobfuscatedPath = retraceInternal.resolve(deobfuscatedFile);
  }

  @Override
  public List<String> obfuscatedStackTrace() {
    try {
      return FileUtils.readAllLines(obfuscatedPath);
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not read file " + obfuscatedPath.toString());
    }
  }

  @Override
  public String mapping() {
    Path mappingPath = retraceInternal.resolve("mapping.txt");
    try {
      return new String(Files.readAllBytes(mappingPath));
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not read mapping file " + mappingPath.toString());
    }
  }

  @Override
  public List<String> retracedStackTrace() {
    try {
      return FileUtils.readAllLines(deobfuscatedPath);
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not read file " + deobfuscatedPath.toString());
    }
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
