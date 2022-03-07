// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.google.common.primitives.Bytes;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Interface for producing a string format of a mapping file. */
@Keep
public interface ProguardMapProducer {

  Reader get() throws IOException;

  static ProguardMapProducer fromString(String proguardMapString) {
    return () -> new StringReader(proguardMapString);
  }

  static ProguardMapProducer fromPath(Path path) {
    return () -> Files.newBufferedReader(path, StandardCharsets.UTF_8);
  }

  static ProguardMapProducer fromBytes(byte[]... partitions) {
    return fromString(new String(Bytes.concat(partitions), StandardCharsets.UTF_8));
  }
}
