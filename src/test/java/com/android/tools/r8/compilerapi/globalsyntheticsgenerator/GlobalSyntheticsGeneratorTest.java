// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.globalsyntheticsgenerator;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.GlobalSyntheticsGenerator;
import com.android.tools.r8.GlobalSyntheticsGeneratorCommand;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import org.junit.Test;

public class GlobalSyntheticsGeneratorTest extends CompilerApiTestRunner {

  public GlobalSyntheticsGeneratorTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testGlobalSynthetics() throws Exception {
    new ApiTest(ApiTest.PARAMETERS)
        .run(
            new DexIndexedConsumer.ArchiveConsumer(
                temp.newFolder().toPath().resolve("output.zip")));
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void run(ProgramConsumer programConsumer) throws Exception {
      GlobalSyntheticsGenerator.run(
          GlobalSyntheticsGeneratorCommand.builder()
              .addLibraryFiles(getAndroidJar())
              .setMinApiLevel(33)
              .setProgramConsumer(programConsumer)
              .build());
    }

    @Test
    public void testGlobalSynthetics() throws Exception {
      run(DexIndexedConsumer.emptyConsumer());
    }
  }
}
