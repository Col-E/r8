// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.TextOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class UTF8TextOutputStream implements TextOutputStream {

  private final OutputStream outputStream;

  public UTF8TextOutputStream(Path path) throws IOException {
    this(Files.newOutputStream(path));
  }

  public UTF8TextOutputStream(OutputStream outputStream) {
    this.outputStream = outputStream;
  }

  @Override
  public OutputStream getOutputStream() {
    return outputStream;
  }

  @Override
  public Charset getCharset() {
    return StandardCharsets.UTF_8;
  }
}
