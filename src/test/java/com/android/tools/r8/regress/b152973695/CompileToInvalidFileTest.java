// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b152973695;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CompileToInvalidFileTest extends TestBase {

  private static final Path INVALID_FILE = Paths.get("!@#/\\INVALID_FILE");

  private final boolean classFileConsumer;

  @Parameterized.Parameters(name = "{0}, classfileConsumer: {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  public CompileToInvalidFileTest(TestParameters parameters, boolean classFileConsumer) {
    this.classFileConsumer = classFileConsumer;
  }

  @Test
  public void testCompileToInvalidFileD8() {
    ensureInvalidFileIsInvalid();
    ProgramConsumer programConsumer =
        classFileConsumer
            ? new ClassFileConsumer.ArchiveConsumer(INVALID_FILE)
            : new ArchiveConsumer(INVALID_FILE);
    try {
      testForD8().addProgramClasses(Main.class).setProgramConsumer(programConsumer).compile();
      fail("Expected a CompilationFailedException but the code succeeded");
    } catch (CompilationFailedException ex) {
      assertInvalidFileNotFound(ex);
    } catch (Throwable t) {
      fail("Expected a CompilationFailedException but got instead " + t);
    }
  }

  @Test
  public void testCompileToInvalidFileR8() {
    ensureInvalidFileIsInvalid();
    ProgramConsumer programConsumer =
        classFileConsumer
            ? new ClassFileConsumer.ArchiveConsumer(INVALID_FILE)
            : new ArchiveConsumer(INVALID_FILE);
    try {
      testForR8(classFileConsumer ? Backend.CF : Backend.DEX)
          .addProgramClasses(Main.class)
          .addKeepMainRule(Main.class)
          .setProgramConsumer(programConsumer)
          .compile();
      fail("Expected a CompilationFailedException but the code succeeded");
    } catch (CompilationFailedException ex) {
      assertInvalidFileNotFound(ex);
    } catch (Throwable t) {
      fail("Expected a CompilationFailedException but got instead " + t);
    }
  }

  private void assertInvalidFileNotFound(CompilationFailedException ex) {
    assertTrue(ex.getCause().getMessage().contains("File not found"));
    assertTrue(ex.getCause().getMessage().contains(INVALID_FILE.toString()));
  }

  private void ensureInvalidFileIsInvalid() {
    try {
      Files.newOutputStream(
          INVALID_FILE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      fail("Expected an IOException but the code succeeded");
    } catch (IOException ignored) {
    } catch (Throwable t) {
      fail("Expected an IOException but got instead " + t);
    }
  }

  static class Main {
    public static void main(String[] args) {
      System.out.println("Hello world!");
    }
  }
}
