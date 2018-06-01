// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b78493232;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ToolHelper;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class Regress78493232 extends AsmTestBase {

  @Test
  public void test() throws Exception {
    // Run test on JVM and ART(x86) to ensure expected behavior.
    // Running the same test on an ARM JIT causes errors.
    ensureSameOutput(
        Regress78493232Dump.CLASS_NAME,
        Regress78493232Dump.dump(),
        ToolHelper.getClassAsBytes(Regress78493232Utils.class));
  }

  // Main method to build a test jar for testing on device.
  public static void main(String[] args) throws CompilationFailedException, IOException {
    Path output = args.length > 0
        ? Paths.get(args[0])
        : Paths.get("Regress78493232.jar");
    ArchiveConsumer consumer = new ArchiveConsumer(output);
    consumer.accept(Regress78493232Dump.dump(), Regress78493232Dump.CLASS_DESC, null);
    consumer.accept(
        ToolHelper.getClassAsBytes(Regress78493232Utils.class),
        Regress78493232Dump.UTILS_CLASS_DESC,
        null);
    consumer.finished(null);
  }
}
