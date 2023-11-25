// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.inputmap;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class InputMapTest extends CompilerApiTestRunner {

  public InputMapTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testD8() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    runTest(test::runTestD8);
  }

  @Test
  public void testR8() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    runTest(test::runTestR8);
  }

  private void runTest(ThrowingBiConsumer<ProgramConsumer, StringConsumer, Exception> test)
      throws Exception {
    Path output = temp.newFolder().toPath().resolve("out.jar");
    StringBuilder mappingBuilder = new StringBuilder();
    BooleanBox didGetMappingContent = new BooleanBox(false);
    test.accept(
        new DexIndexedConsumer.ArchiveConsumer(output),
        (mappingContent, handler) -> {
          mappingBuilder.append(mappingContent);
          didGetMappingContent.set(true);
        });
    assertTrue(didGetMappingContent.get());

    // Extract the map hash from the file. This is always set by R8 to a SHA 256 hash.
    String mappingContent = mappingBuilder.toString();
    assertThat(mappingContent, containsString("com.android.tools.r8.compilerapi.originaldata ->"));
  }

  public static class ApiTest extends CompilerApiTest {
    private static final List<String> mappingLines =
        Arrays.asList(
            "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
            "com.android.tools.r8.compilerapi.originaldata ->"
                + " com.android.tools.r8.compilerapi.mockdata:");

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void runTestR8(ProgramConsumer programConsumer, StringConsumer mappingConsumer)
        throws Exception {
      temp.create();
      Path inputMap = temp.newFolder().toPath().resolve("r8inputmapping.txt");
      Files.write(inputMap, mappingLines, StandardOpenOption.CREATE_NEW);
      R8.run(
          R8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addProguardConfiguration(getKeepMainRules(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setProgramConsumer(programConsumer)
              .setProguardMapConsumer(mappingConsumer)
              .setProguardMapInputFile(inputMap)
              .build());
    }

    public void runTestD8(ProgramConsumer programConsumer, StringConsumer mappingConsumer)
        throws Exception {
      temp.create();
      Path inputMap = temp.newFolder().toPath().resolve("d8inputmap.txt");
      Files.write(inputMap, mappingLines, StandardOpenOption.CREATE_NEW);
      D8.run(
          D8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setProgramConsumer(programConsumer)
              .setProguardMapConsumer(mappingConsumer)
              .setProguardMapInputFile(inputMap)
              .build());
    }

    @Test
    public void testD8() throws Exception {
      runTestD8(DexIndexedConsumer.emptyConsumer(), StringConsumer.emptyConsumer());
    }

    @Test
    public void testR8() throws Exception {
      runTestR8(DexIndexedConsumer.emptyConsumer(), StringConsumer.emptyConsumer());
    }
  }
}
