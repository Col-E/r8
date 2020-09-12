// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b113347830;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import jasmin.ClassFile;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B113347830 extends TestBase {

  public static final Class CLASS = B113347830.class;
  public static final String NAME = CLASS.getSimpleName();
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public B113347830(TestParameters parameters) {
    this.parameters = parameters;
  }

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

    testForD8(Backend.DEX)
        .addProgramClassFileData(bytes)
        .setDisableDesugaring(true)
        .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
        .addOptionsModification(options -> options.testing.disableStackMapVerification = true)
        .compile();
  }
}
