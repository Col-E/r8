// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

public class InstrumentationServerImpl extends InstrumentationServer {

  private static final InstrumentationServerImpl INSTANCE = new InstrumentationServerImpl();

  private final StringBuilder builder = new StringBuilder();

  // May be set to true by the instrumentation.
  private final boolean writeToLogcat = false;
  private final String logcatTag = "r8";

  private InstrumentationServerImpl() {}

  public static InstrumentationServerImpl getInstance() {
    return InstrumentationServerImpl.INSTANCE;
  }

  public static void addNonSyntheticMethod(String descriptor) {
    getInstance().addLine(descriptor);
  }

  public static void addSyntheticMethod(String descriptor) {
    getInstance().addLine('S' + descriptor);
  }

  private synchronized void addLine(String line) {
    if (writeToLogcat) {
      writeToLogcat(line);
    } else {
      builder.append(line).append('\n');
    }
  }

  @Override
  public synchronized void writeToFile(File file) throws IOException {
    FileOutputStream stream = new FileOutputStream(file);
    try {
      stream.write(builder.toString().getBytes(Charset.forName("UTF-8")));
    } finally {
      stream.close();
    }
  }

  private void writeToLogcat(String line) {
    android.util.Log.v(logcatTag, line);
  }
}
