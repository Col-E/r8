// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashSet;

public class InstrumentationServerImpl extends InstrumentationServer {

  private static final InstrumentationServerImpl INSTANCE = new InstrumentationServerImpl();

  // May be set to true by the instrumentation.
  private static boolean writeToLogcat;
  private static String logcatTag;

  private final LinkedHashSet<String> lines = new LinkedHashSet<>();

  private InstrumentationServerImpl() {}

  public static InstrumentationServerImpl getInstance() {
    return InstrumentationServerImpl.INSTANCE;
  }

  public static void addMethod(String descriptor) {
    getInstance().addLine(descriptor);
  }

  private void addLine(String line) {
    synchronized (lines) {
      if (!lines.add(line)) {
        return;
      }
    }
    if (writeToLogcat) {
      writeToLogcat(line);
    }
  }

  @Override
  public void writeToFile(File file) throws IOException {
    PrintWriter writer = new PrintWriter(file, "UTF-8");
    try {
      synchronized (lines) {
        for (String line : lines) {
          writer.println(line);
        }
      }
    } finally {
      writer.close();
    }
  }

  private void writeToLogcat(String line) {
    android.util.Log.i(logcatTag, line);
  }
}
