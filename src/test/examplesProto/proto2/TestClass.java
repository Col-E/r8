// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package proto2;

import com.android.tools.r8.proto2.Graph.IsExtendedWithOptional;
import com.android.tools.r8.proto2.Graph.IsExtendedWithRequiredField;
import com.android.tools.r8.proto2.Graph.IsRepeatedlyExtendedWithRequiredField;
import com.android.tools.r8.proto2.Graph.UsedRoot;
import com.android.tools.r8.proto2.Shrinking.ContainsFlaggedOffField;
import com.android.tools.r8.proto2.Shrinking.HasFlaggedOffExtension;
import com.android.tools.r8.proto2.Shrinking.PartiallyUsed;
import com.android.tools.r8.proto2.Shrinking.PartiallyUsedWithExtension;
import com.android.tools.r8.proto2.Shrinking.UsedViaHazzer;
import com.android.tools.r8.proto2.Shrinking.UsedViaOneofCase;
import com.android.tools.r8.proto2.Shrinking.UsesOnlyRepeatedFields;
import com.android.tools.r8.proto2.TestProto.Primitives;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;
import java.nio.ByteBuffer;

public class TestClass {

  public static void main(String[] args) {
    roundtrip();
    partiallyUsed_proto2();
    usedViaHazzer();
    usedViaOneofCase();
    usesOnlyRepeatedFields();
    containsFlaggedOffField();
    hasFlaggedOffExtension();
    useOneExtension();
    keepMapAndRequiredFields();
  }

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

  // smoke test
  static void roundtrip() {
    System.out.println("--- roundtrip ---");
    Primitives primitives =
        Primitives.newBuilder()
            .setFooInt32(123)
            .setOneofString("asdf")
            .setBarInt64(Long.MAX_VALUE)
            .setQuxString("qwerty")
            .build();

    Primitives roundtripped;
    try {
      roundtripped = Primitives.parseFrom(primitives.toByteArray());
    } catch (InvalidProtocolBufferException e) {
      System.out.println("Unexpected exception: " + e);
      throw new RuntimeException(e);
    }

    System.out.println(roundtripped.equals(primitives));
    System.out.println(roundtripped.getFooInt32());
    System.out.println(roundtripped.getOneofString());
    System.out.println(roundtripped.getBarInt64());
    System.out.println(roundtripped.getQuxString());
  }

  static void partiallyUsed_proto2() {
    System.out.println("--- partiallyUsed_proto2 ---");
    PartiallyUsed pu;
    try {
      pu = PartiallyUsed.parseFrom(FIELD1_SET_TO_42);
    } catch (InvalidProtocolBufferException e) {
      System.out.println("Unexpected exception: " + e);
      throw new RuntimeException(e);
    }
    System.out.println(pu.hasUsed());
    System.out.println(pu.getUsed());
  }

  static void usedViaHazzer() {
    System.out.println("--- usedViaHazzer ---");
    UsedViaHazzer uvh;
    try {
      uvh = UsedViaHazzer.parseFrom(FIELD1_SET_TO_42);
    } catch (InvalidProtocolBufferException e) {
      System.out.println("Unexpected exception: " + e);
      throw new RuntimeException(e);
    }
    System.out.println(uvh.hasUsed());
  }

  static void usedViaOneofCase() {
    System.out.println("--- usedViaOneofCase ---");
    UsedViaOneofCase msg;
    try {
      msg = UsedViaOneofCase.parseFrom(FIELD1_SET_TO_42);
    } catch (InvalidProtocolBufferException e) {
      System.out.println("Unexpected exception: " + e);
      throw new RuntimeException(e);
    }
    System.out.println(msg.hasUsed());
  }

  static void usesOnlyRepeatedFields() {
    System.out.println("--- usesOnlyRepeatedFields ---");
    UsesOnlyRepeatedFields msg;
    try {
      msg = UsesOnlyRepeatedFields.parseFrom(FIELD1_SET_TO_42);
    } catch (InvalidProtocolBufferException e) {
      System.out.println("Unexpected exception: " + e);
      throw new RuntimeException(e);
    }
    System.out.println(msg.getUsedCount());
  }

  static void containsFlaggedOffField() {
    System.out.println("--- containsFlaggedOffField ---");
    ContainsFlaggedOffField.Builder builder = ContainsFlaggedOffField.newBuilder();
    if (alwaysFalse()) {
      builder.setConditionallyUsed(1);
    }
    System.out.println(builder.build().getSerializedSize());
  }

  static void hasFlaggedOffExtension() {
    System.out.println("--- hasFlaggedOffExtension ---");
    HasFlaggedOffExtension msg;
    try {
      msg =
          HasFlaggedOffExtension.parseFrom(
              MESSAGE10_WITH_FIELD1_SET_TO_42, ExtensionRegistryLite.getGeneratedRegistry());
    } catch (InvalidProtocolBufferException e) {
      System.out.println("Unexpected exception: " + e);
      throw new RuntimeException(e);
    }
    if (alwaysFalse()) {
      System.out.println(msg.getExtension(HasFlaggedOffExtension.Ext.ext).getX());
    }
    System.out.println(msg.getSerializedSize());
  }

  static boolean alwaysFalse() {
    return false;
  }

  static void useOneExtension() {
    System.out.println("--- useOneExtension ---");

    PartiallyUsedWithExtension msg;
    try {
      msg =
          PartiallyUsedWithExtension.parseFrom(
              MESSAGE10_WITH_FIELD1_SET_TO_42, ExtensionRegistryLite.getGeneratedRegistry());
    } catch (InvalidProtocolBufferException e) {
      System.out.println("Unexpected exception: " + e);
      throw new RuntimeException(e);
    }

    PartiallyUsedWithExtension.ExtA ext = msg.getExtension(PartiallyUsedWithExtension.ExtA.extA);
    System.out.println(ext.getX());
  }

  static void keepMapAndRequiredFields() {
    System.out.println("--- keepMapAndRequiredFields ---");

    UsedRoot msg;
    try {
      msg = UsedRoot.parseFrom(new byte[0], ExtensionRegistryLite.getGeneratedRegistry());
    } catch (InvalidProtocolBufferException e) {
      System.out.println("Unexpected exception: " + e);
      throw new RuntimeException(e);
    }
    System.out.println(msg.isInitialized());
    // Force extension edges to be kept.  This test is for verifying that we keep *fields* which
    // lead to extensions with required fields.  b/123031088 is for keeping the extensions.
    System.out.println(IsExtendedWithRequiredField.Ext.ext.getNumber());
    System.out.println(IsRepeatedlyExtendedWithRequiredField.Ext.ext.getNumber());
    System.out.println(IsExtendedWithOptional.Ext.ext.getNumber());
  }
}
