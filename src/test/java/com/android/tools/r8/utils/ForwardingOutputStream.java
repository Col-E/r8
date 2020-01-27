// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

public class ForwardingOutputStream extends OutputStream {

  private final List<OutputStream> listeners;

  public ForwardingOutputStream(OutputStream... listeners) {
    this.listeners = Arrays.asList(listeners);
  }

  @Override
  public void write(int b) throws IOException {
    for (OutputStream out : listeners) {
      out.write(b);
    }
  }

  @Override
  public void write(byte[] b) throws IOException {
    for (OutputStream out : listeners) {
      out.write(b);
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    for (OutputStream out : listeners) {
      out.write(b, off, len);
    }
  }

  @Override
  public void flush() throws IOException {
    for (OutputStream out : listeners) {
      out.flush();
    }
  }

  @Override
  public void close() throws IOException {
    for (OutputStream out : listeners) {
      out.close();
    }
  }
}
