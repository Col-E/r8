// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.L8TestBuilder;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestState;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.DesugaredLibraryTestBuilder;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.BeforeClass;

public class DesugaredLibraryTestBase extends TestBase {

  private static final boolean FORCE_JDK11_DESUGARED_LIB = false;

  @BeforeClass
  public static void setUpDesugaredLibrary() {
    if (!FORCE_JDK11_DESUGARED_LIB) {
      return;
    }
    System.setProperty("desugar_jdk_json_dir", "src/library_desugar/jdk11");
    System.setProperty(
        "desugar_jdk_libs", "third_party/openjdk/desugar_jdk_libs_11/desugar_jdk_libs.jar");
    System.out.println("Forcing the usage of JDK11 desugared library.");
  }

  public static boolean isJDK11DesugaredLibrary() {
    String property = System.getProperty("desugar_jdk_json_dir", "");
    return property.contains("jdk11");
  }

  // For conversions tests, we need DexRuntimes where classes to convert are present (DexRuntimes
  // above N and O depending if Stream or Time APIs are used), but we need to compile the program
  // with a minAPI below to force the use of conversions.
  protected static TestParametersCollection getConversionParametersUpToExcluding(
      AndroidApiLevel apiLevel) {
    if (apiLevel == AndroidApiLevel.N) {
      return getTestParameters()
          .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
          .withApiLevelsEndingAtExcluding(AndroidApiLevel.N)
          .build();
    }
    if (apiLevel == AndroidApiLevel.O) {
      return getTestParameters()
          .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
          .withApiLevelsEndingAtExcluding(AndroidApiLevel.O)
          .build();
    }
    throw new Error("Unsupported conversion parameters");
  }

  protected AndroidApiLevel getRequiredCompilationAPILevel() {
    return isJDK11DesugaredLibrary() ? AndroidApiLevel.R : AndroidApiLevel.P;
  }

  protected Path getLibraryFile() {
    return ToolHelper.getAndroidJar(getRequiredCompilationAPILevel());
  }

  protected boolean requiresEmulatedInterfaceCoreLibDesugaring(TestParameters parameters) {
    return parameters.getApiLevel().isLessThan(apiLevelWithDefaultInterfaceMethodsSupport());
  }

  protected boolean requiresTimeDesugaring(TestParameters parameters, boolean isJDK11) {
    return parameters.getApiLevel().getLevel()
        < (isJDK11 ? AndroidApiLevel.S.getLevel() : AndroidApiLevel.O.getLevel());
  }

  protected boolean requiresTimeDesugaring(TestParameters parameters) {
    return requiresTimeDesugaring(parameters, isJDK11DesugaredLibrary());
  }

  protected boolean requiresAnyCoreLibDesugaring(TestParameters parameters) {
    return requiresAnyCoreLibDesugaring(parameters.getApiLevel());
  }

  protected boolean requiresAnyCoreLibDesugaring(AndroidApiLevel apiLevel, boolean isJDK11) {
    return apiLevel.getLevel()
        <= (isJDK11 ? AndroidApiLevel.R.getLevel() : AndroidApiLevel.N_MR1.getLevel());
  }

  protected boolean requiresAnyCoreLibDesugaring(AndroidApiLevel apiLevel) {
    return requiresAnyCoreLibDesugaring(apiLevel, isJDK11DesugaredLibrary());
  }

  protected DesugaredLibraryTestBuilder<?> testForDesugaredLibrary(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification runSpecification) {
    return new DesugaredLibraryTestBuilder<>(
        this, parameters, libraryDesugaringSpecification, runSpecification);
  }

  public L8TestBuilder testForL8(AndroidApiLevel apiLevel) {
    return L8TestBuilder.create(apiLevel, Backend.DEX, new TestState(temp));
  }

  public L8TestBuilder testForL8(AndroidApiLevel apiLevel, Backend backend) {
    return L8TestBuilder.create(apiLevel, backend, new TestState(temp));
  }

  protected void assertLines2By2Correct(String stdOut) {
    String[] lines = stdOut.split("\n");
    assert lines.length % 2 == 0;
    for (int i = 0; i < lines.length; i += 2) {
      assertEquals(
          "Different lines: " + lines[i] + " || " + lines[i + 1] + "\n" + stdOut,
          lines[i],
          lines[i + 1]);
    }
  }

  public static Path[] getAllFilesWithSuffixInDirectory(Path directory, String suffix)
      throws IOException {
    return Files.walk(directory)
        .filter(path -> path.toString().endsWith(suffix))
        .toArray(Path[]::new);
  }

  public interface KeepRuleConsumer extends StringConsumer {

    String get();

    static KeepRuleConsumer emptyConsumer() {
      return new KeepRuleConsumer() {

        @Override
        public String get() {
          throw new Unreachable();
        }

        @Override
        public void accept(String string, DiagnosticsHandler handler) {
          // Intentionally empty.
        }
      };
    }
  }
}
