// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static com.android.tools.r8.utils.AndroidApp.DEFAULT_PROGUARD_MAP_FILE;

import com.android.tools.r8.Disassemble;
import com.android.tools.r8.utils.FileUtils;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Invoke R8 on the dex files extracted from GMSCore.apk to disassemble the dex code.
@RunWith(Parameterized.class)
public class R8DisassemblerTest {

  static final String APP_DIR = "third_party/gmscore/v5/";

  @Parameters(name = "deobfuscate: {0} smali: {1}")
  public static Iterable<Object[]> data() {
    return Arrays
        .asList(new Object[][]{{false, false}, {false, true}, {true, false}, {true, true}});
  }

  @Parameter(0)
  public boolean deobfuscate;

  @Parameter(1)
  public boolean smali;

  @Test
  public void testDisassemble() throws Exception {
    // This test only ensures that we do not break disassembling of dex code. It does not
    // check the generated code. To make it fast, we get rid of the output.
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(new OutputStream() {
      public void write(int b) { /* ignore*/ }
    }));

    try {
      Disassemble.DisassembleCommand.Builder builder = Disassemble.DisassembleCommand.builder();
      builder.setUseSmali(smali);
      if (deobfuscate) {
        builder.setProguardMapFile(Paths.get(APP_DIR, DEFAULT_PROGUARD_MAP_FILE));
      }
      builder.addProgramFiles(
          Files.list(Paths.get(APP_DIR))
              .filter(FileUtils::isDexFile)
              .collect(Collectors.toList()));
      Disassemble.DisassembleCommand command = builder.build();
      Disassemble.disassemble(command);
    } finally {
      // Restore System.out for good measure.
      System.setOut(originalOut);
    }
  }
}
