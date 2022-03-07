// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import java.util.function.Consumer;

@Keep
public interface MappingPartitionKeyInfo {

  void getKeysForClass(ClassReference reference, Consumer<String> keyConsumer);

  void getKeysForClassAndMethodName(
      ClassReference reference, String methodName, Consumer<String> keyConsumer);

  void getKeysForMethod(MethodReference reference, Consumer<String> keyConsumer);

  void getKeysForField(FieldReference fieldReference, Consumer<String> keyConsumer);

  void getKeysForType(TypeReference typeReference, Consumer<String> keyConsumer);

  static MappingPartitionKeyInfo getDefault(byte[] metadata) {
    return null;
  }
}
