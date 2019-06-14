// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package proto3;

import com.android.tools.r8.proto3.Shrinking.PartiallyUsed;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;

public class TestClass {

  public static void main(String[] args) {
    partiallyUsed_proto3();
  }

  // A protobuf payload indicating that varint field 1 is set to 42.
  // See https://developers.google.com/protocol-buffers/docs/encoding
  //
  // Since serialization and deserialization use the same schema (which we're modifying), testing
  // against wire-format data is preferred.
  private static final byte[] FIELD1_SET_TO_42 =
      new byte[] {(1 << 3) | WireFormat.WIRETYPE_VARINT, 42};

  static void partiallyUsed_proto3() {
    System.out.println("--- partiallyUsed_proto3 ---");
    PartiallyUsed pu;
    try {
      pu = PartiallyUsed.parseFrom(FIELD1_SET_TO_42);
    } catch (InvalidProtocolBufferException e) {
      System.out.println("Unexpected exception: " + e);
      throw new RuntimeException(e);
    }
    System.out.println(pu.getUsed());
  }
}
