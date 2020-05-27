// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package proto2;

import com.android.tools.r8.proto2.TestProto.Primitives;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

public class BuilderOnlyReferencedFromDynamicMethodTestClass {

  public static void main(String[] args) {
    GeneratedMessageLite<?, ?> primitivesInDisguise;
    try {
      primitivesInDisguise = Primitives.parseFrom(new byte[0]);
    } catch (InvalidProtocolBufferException e) {
      System.out.println("Unexpected exception: " + e);
      throw new RuntimeException(e);
    }
    Primitives primitives = (Primitives) primitivesInDisguise.toBuilder().build();
    Printer.print(primitives);
  }
}
