// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.ToolHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class ActualBotStackTraceBase implements StackTraceForTest {

  public String r8MappingFromGitSha(String sha) {
    Path resolve = ToolHelper.RETRACE_MAPS_DIR.resolve(sha + "-r8lib.jar.map");
    try {
      return new String(Files.readAllBytes(resolve));
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not read mapping file " + resolve.toString());
    }
  }
}
