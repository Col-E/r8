// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package proto2;

import com.android.tools.r8.proto2.Shrinking.HasFlaggedOffExtension;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;
import java.nio.ByteBuffer;

public class HasFlaggedOffExtensionBuilderTestClass {

  // A protobuf payload indicating that varint field 1 is set to 42.
  // See https://developers.google.com/protocol-buffers/docs/encoding
  //
  // Since serialization and deserialization use the same schema (which we're modifying), testing
  // against wire-format data is preferred.
  private static final byte[] FIELD1_SET_TO_42 =
      new byte[] {(1 << 3) | WireFormat.WIRETYPE_VARINT, 42};

  // A protobuf payload indicating that field 10 is a message whose field 1 is set to 42.
  private static final byte[] MESSAGE10_WITH_FIELD1_SET_TO_42 =
      ByteBuffer.allocate(4)
          .put(
              new byte[] {
                (10 << 3) | WireFormat.WIRETYPE_LENGTH_DELIMITED, (byte) FIELD1_SET_TO_42.length
              })
          .put(FIELD1_SET_TO_42)
          .array();

  public static void main(String[] args) {
    HasFlaggedOffExtension msg;
    try {
      msg =
          HasFlaggedOffExtension.parseFrom(
              MESSAGE10_WITH_FIELD1_SET_TO_42, ExtensionRegistryLite.getGeneratedRegistry());
    } catch (InvalidProtocolBufferException e) {
      System.out.println("Unexpected exception: " + e);
      throw new RuntimeException(e);
    }
    Printer.print(HasFlaggedOffExtension.newBuilder(msg).build());
  }
}
