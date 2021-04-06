// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.google.common.io.CharSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Interface for producing a string format of a mapping file. */
@Keep
public interface ProguardMapProducer {

  BufferedReader get() throws IOException;

  static ProguardMapProducer fromString(String proguardMapString) {
    return () -> CharSource.wrap(proguardMapString).openBufferedStream();
  }

  static ProguardMapProducer fromReader(Reader reader) {
    return () -> new BufferedReader(reader);
  }

  static ProguardMapProducer fromPath(Path path) {
    return () -> Files.newBufferedReader(path, StandardCharsets.UTF_8);
  }
}
