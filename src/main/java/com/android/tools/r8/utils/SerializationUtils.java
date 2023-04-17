// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SerializationUtils {

  private static final byte ZERO_BYTE = (byte) 0;

  public static byte getZeroByte() {
    return ZERO_BYTE;
  }

  public static void writeUTFOfIntSize(DataOutputStream dataOutputStream, String string)
      throws IOException {
    // Similar to dataOutputStream.writeUTF except it uses an int for length in bytes.
    byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
    dataOutputStream.writeInt(bytes.length);
    dataOutputStream.write(bytes);
  }
}
