// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.fail;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanTopLevelFlags;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class MaintainAndRewritePrefixTest extends DesugaredLibraryTestBase implements Opcodes {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntime(Version.DEFAULT).withCfRuntime(CfVm.JDK11).build(),
        LibraryDesugaringSpecification.getJdk8Jdk11());
  }

  public MaintainAndRewritePrefixTest(
      TestParameters parameters, LibraryDesugaringSpecification libraryDesugaringSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  /**
   * Add this library desugaring configuration:
   * "library_flags": [
   *  {
   *   "rewrite_prefix": {"java.time.": "j$.time."},
   *   "maintain_prefix": ["java.time."],
   *  }
   * ],
   */
  private static void specifyDesugaredLibrary(InternalOptions options) {
    HumanRewritingFlags rewritingFlags =
        HumanRewritingFlags.builder(options.reporter, Origin.unknown())
            .putRewritePrefix("java.time.", "j$.time.")
            .putMaintainPrefix("java.time.")
            .build();
    options.setDesugaredLibrarySpecification(
        new HumanDesugaredLibrarySpecification(HumanTopLevelFlags.testing(), rewritingFlags, true));
  }

  @Test
  public void test() {
    try {
      testForL8(AndroidApiLevel.B, parameters.getBackend())
          .apply(libraryDesugaringSpecification::configureL8TestBuilder)
          .addOptionsModifier(MaintainAndRewritePrefixTest::specifyDesugaredLibrary)
          .compile();
      fail();
    } catch (Exception e) {
      Throwable cause = e.getCause();
      org.junit.Assert.assertTrue(cause instanceof CompilationError);
      CompilationError ce = (CompilationError) cause;
      org.junit.Assert.assertTrue(
          ce.getMessage()
              .contains(
                  "The compilation cannot proceed because the desugared library specification"
                      + " contains ambiguous flags that the compiler cannot interpret"));
    }
  }
}
