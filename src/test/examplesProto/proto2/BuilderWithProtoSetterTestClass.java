// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package proto2;

import com.android.tools.r8.proto2.TestProto.NestedMessage;
import com.android.tools.r8.proto2.TestProto.OuterMessage;

public class BuilderWithProtoSetterTestClass {

  public static void main(String[] args) {
    System.out.println("builderWithProtoSetter");
    OuterMessage outerMessage =
        OuterMessage.newBuilder()
            .setNestedField(NestedMessage.newBuilder().setFooInt64(42).build())
            .build();
    System.out.println(outerMessage.getNestedField().getFooInt64());
  }
}
