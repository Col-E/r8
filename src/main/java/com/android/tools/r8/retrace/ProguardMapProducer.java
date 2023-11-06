// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.google.common.primitives.Bytes;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Interface for producing a string format of a mapping file. */
@KeepForApi
public interface ProguardMapProducer {

  InputStream get() throws IOException;

  default boolean isFileBacked() {
    return false;
  }

  @SuppressWarnings("RedundantThrows")
  default Path getPath() throws FileNotFoundException {
    return null;
  }

  static ProguardMapProducer fromString(String proguardMapString) {
    return () -> new ByteArrayInputStream(proguardMapString.getBytes(StandardCharsets.UTF_8));
  }

  static ProguardMapProducer fromPath(Path path) {
    return new ProguardMapProducer() {
      @Override
      public InputStream get() throws IOException {
        return new BufferedInputStream(new FileInputStream(path.toFile()));
      }

      @Override
      public boolean isFileBacked() {
        return true;
      }

      @Override
      public Path getPath() {
        return path;
      }
    };
  }

  static ProguardMapProducer fromBytes(byte[]... partitions) {
    return () -> new ByteArrayInputStream(Bytes.concat(partitions));
  }
}
