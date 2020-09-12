// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b113347830;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import jasmin.ClassFile;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class B113347830 {

  public static final Class CLASS = B113347830.class;
  public static final String NAME = CLASS.getSimpleName();

  @Test
  public void test() throws Exception {
    ClassFile jasminFile = new ClassFile();
    jasminFile.readJasmin(
        Files.newBufferedReader(
            Paths.get(
                ToolHelper.TESTS_DIR, "java", CLASS.getCanonicalName().replace('.', '/') + ".j")),
        "Test",
        false);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    jasminFile.write(out);
    byte[] bytes = out.toByteArray();

    D8.run(
        D8Command.builder()
            .addClassProgramData(bytes, Origin.unknown())
            .setDisableDesugaring(true)
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .build());
  }
}
