// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DexIndexedConsumerData;
import com.android.tools.r8.L8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EmptyDesugaredLibrary extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withMaximumApiLevel().build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG));
  }

  public EmptyDesugaredLibrary(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  private L8TestBuilder prepareL8() {
    return testForL8(parameters.getApiLevel())
        .apply(libraryDesugaringSpecification::configureL8TestBuilder);
  }

  static class CountingProgramConsumer extends DexIndexedConsumer.ForwardingConsumer {

    public int count = 0;

    public CountingProgramConsumer() {
      super(null);
    }

    @Override
    public void acceptDexIndexedFile(DexIndexedConsumerData data) {
      count++;
    }
  }

  private boolean expectsEmptyDesugaredLibrary(AndroidApiLevel apiLevel) {
    return !libraryDesugaringSpecification.hasAnyDesugaring(apiLevel);
  }

  @Test
  public void testEmptyDesugaredLibrary() throws Exception {
    CountingProgramConsumer programConsumer = new CountingProgramConsumer();
    prepareL8().setProgramConsumer(programConsumer).compile();
    assertEquals(
        expectsEmptyDesugaredLibrary(parameters.getApiLevel()) ? 0 : 1, programConsumer.count);
  }

  @Test
  public void testEmptyDesugaredLibraryDexZip() throws Exception {
    Path desugaredLibraryZip = prepareL8().compile().writeToZip();
    assertTrue(Files.exists(desugaredLibraryZip));
    assertEquals(
        expectsEmptyDesugaredLibrary(parameters.getApiLevel()) ? 0 : 1,
        new ZipFile(desugaredLibraryZip.toFile(), StandardCharsets.UTF_8).size());
  }
}
