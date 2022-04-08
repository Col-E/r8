// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import java.io.BufferedReader;
import java.io.IOException;

/** This is an abstraction over BufferedReader */
public interface LineReader {

  String readLine() throws IOException;

  void close() throws IOException;

  static LineReader fromBufferedReader(BufferedReader bufferedReader) {
    return new BufferedLineReader(bufferedReader);
  }

  class BufferedLineReader implements LineReader {

    private final BufferedReader bufferedReader;

    private BufferedLineReader(BufferedReader bufferedReader) {
      this.bufferedReader = bufferedReader;
    }

    @Override
    public String readLine() throws IOException {
      return bufferedReader.readLine();
    }

    @Override
    public void close() throws IOException {
      bufferedReader.close();
    }
  }
}
