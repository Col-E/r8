// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.androidplatformbuild;

import static com.android.tools.r8.MarkerMatcher.markerAndroidPlatformBuild;
import static com.android.tools.r8.MarkerMatcher.markerMinApi;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

public class AndroidPlatformBuildApiTest extends CompilerApiTestRunner {

  public static final int MIN_API_LEVEL = 31;

  public AndroidPlatformBuildApiTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testD8() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    runTest(test::runD8);
  }

  @Test
  public void testR8() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    runTest(test::runR8);
  }

  private void runTest(ThrowingConsumer<ProgramConsumer, Exception> test) throws Exception {
    Path output = temp.newFolder().toPath().resolve("out.jar");
    test.accept(new DexIndexedConsumer.ArchiveConsumer(output));
    assertThat(
        new CodeInspector(output).getMarkers(),
        CoreMatchers.everyItem(
            CoreMatchers.allOf(
                markerMinApi(AndroidApiLevel.getAndroidApiLevel(MIN_API_LEVEL)),
                markerAndroidPlatformBuild())));
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void runD8(ProgramConsumer programConsumer) throws Exception {
      D8.run(
          D8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setProgramConsumer(programConsumer)
              .setAndroidPlatformBuild(true)
              .setMinApiLevel(MIN_API_LEVEL)
              .build());
    }

    public void runR8(ProgramConsumer programConsumer) throws Exception {
      R8.run(
          R8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addProguardConfiguration(getKeepMainRules(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setProgramConsumer(programConsumer)
              .setAndroidPlatformBuild(true)
              .setMinApiLevel(MIN_API_LEVEL)
              .build());
    }

    @Test
    public void testD8() throws Exception {
      runD8(DexIndexedConsumer.emptyConsumer());
    }

    @Test
    public void testR8() throws Exception {
      runR8(DexIndexedConsumer.emptyConsumer());
    }
  }
}
