// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.heap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.HeapUtils;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class HeapDumpTest extends TestBase {

  @Test
  public void testHeapDump() throws Exception {
    Path heapDumpDir = temp.newFolder().toPath();
    Path heapDump = heapDumpDir.resolve("test.hprof");
    HeapUtils.dumpHeap(heapDump, true);
    assertTrue(heapDump.toFile().exists());
    String header = "JAVA PROFILE 1.0.2";
    assertTrue(heapDump.toFile().length() > header.length());
    try (InputStream is = Files.newInputStream(heapDump)) {
      byte[] buffer = new byte[header.length()];
      int bytes = is.read(buffer);
      assertEquals(buffer.length, bytes);
      assertEquals(header, new String(buffer));
    }
  }
}
