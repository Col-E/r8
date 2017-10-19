// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class DebugByteCodeWriterTest {

  private ObjectToOffsetMapping emptyObjectTObjectMapping() {
    return new ObjectToOffsetMapping(
        DexApplication.builder(new DexItemFactory(), null).build(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList());
  }

  @Test
  public void testEmptyDebugInfo() {
    DexDebugInfo debugInfo = new DexDebugInfo(1, DexString.EMPTY_ARRAY, new DexDebugEvent[]{});
    DebugBytecodeWriter writer = new DebugBytecodeWriter(debugInfo, emptyObjectTObjectMapping());
    Assert.assertEquals(3, writer.generate().length);
  }
}
