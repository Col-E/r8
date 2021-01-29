// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/** Interface for producing a string format of a mapping file. */
@Keep
public interface ProguardMapProducer {

  String get() throws IOException;

  static ProguardMapProducer fromReader(Reader reader) {
    return () -> {
      try (BufferedReader br = new BufferedReader(reader)) {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
          sb.append(line).append('\n');
        }
        return sb.toString();
      }
    };
  }
}
