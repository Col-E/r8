// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumenosideeffects;

import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssumenosideeffectsVisibleMethodsTest extends TestBase {
  private static final Class<?> MAIN = TestClass.class;

  enum TestConfig {
    RULE_THAT_REFERS_LIB_BASE,
    RULE_THAT_REFERS_PRG_BASE,
    RULE_THAT_REFERS_PRG_SUB,
    RULE_WITH_EXTENDS_LIB_BASE,
    RULE_WITH_EXTENDS_PRG_BASE;
    // The remaining case(s) are not tested since no items are matched.
    // RULE_WITH_EXTENDS_PRG_SUB;

    public String getKeepRule() {
      switch (this) {
        case RULE_THAT_REFERS_LIB_BASE:
          return StringUtils.lines(
              "-assumenosideeffects class " + LibraryBase.class.getTypeName() + " {",
              "  throwing(...);",
              "  debug(...);",
              "}");
        case RULE_THAT_REFERS_PRG_BASE:
          return StringUtils.lines(
              "-assumenosideeffects class " + ProgramBase.class.getTypeName() + " {",
              "  throwing(...);",
              "  debug(...);",
              "}");
        case RULE_THAT_REFERS_PRG_SUB:
          return StringUtils.lines(
              "-assumenosideeffects class " + ProgramSub.class.getTypeName() + " {",
              "  throwing(...);",
              "  debug(...);",
              "}");
        case RULE_WITH_EXTENDS_LIB_BASE:
          return StringUtils.lines(
              "-assumenosideeffects class * extends " + LibraryBase.class.getTypeName() + " {",
              "  throwing(...);",
              "  debug(...);",
              "}");
        case RULE_WITH_EXTENDS_PRG_BASE:
          return StringUtils.lines(
              "-assumenosideeffects class * extends " + ProgramBase.class.getTypeName() + " {",
              "  throwing(...);",
              "  debug(...);",
              "}");
      }
      throw new Unreachable();
    }

    public String expectedOutput() {
      switch (this) {
        case RULE_THAT_REFERS_LIB_BASE:
          return StringUtils.lines(
              "Unless LibraryBase is explicitly mentioned",
              // TODO(b/137938214): Should not mark Program*'s methods.
              // "Nah; no more throwing."
              // "[ProgramBase]: as long as ProgramBase is specified"
              // "No, no, no; no more throwing."
              // "[ProgramSub]: as long as ProgramSub is specified"
              "The end");
        case RULE_THAT_REFERS_PRG_BASE:
          return StringUtils.lines(
              "Expect to catch RuntimeException",
              "[LibraryBase]: as long as LibraryBase is specified",
              // TODO(b/137938214): Should we mark ProgramSub's methods?
              //   Those are clearly an instance of ProgramSub type, and invocations with it.
              //   One caveat: ProgramSub#throwing doesn't exist, so ProgramBase#throwing will be
              //   used after the resolution, which is specified as side effect free.
              // "[ProgramSub]: as long as ProgramSub is specified"
              "The end");
        case RULE_THAT_REFERS_PRG_SUB:
          return StringUtils.lines(
              "Expect to catch RuntimeException",
              "[LibraryBase]: as long as LibraryBase is specified",
              "[ProgramBase]: as long as ProgramBase is specified",
              "The end");
        case RULE_WITH_EXTENDS_LIB_BASE:
          return StringUtils.lines(
              "Expect to catch RuntimeException",
              "[LibraryBase]: as long as LibraryBase is specified",
              "The end");
        case RULE_WITH_EXTENDS_PRG_BASE:
          return StringUtils.lines(
              "Expect to catch RuntimeException",
              "[LibraryBase]: as long as LibraryBase is specified",
              "[ProgramBase]: as long as ProgramBase is specified",
              "The end");
      }
      throw new Unreachable();
    }
  }

  @Parameterized.Parameters(name = "{0} {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().withAllApiLevelsAlsoForCf().build(),
        TestConfig.values());
  }

  private final TestParameters parameters;
  private final TestConfig config;

  public AssumenosideeffectsVisibleMethodsTest(TestParameters parameters, TestConfig config) {
    this.parameters = parameters;
    this.config = config;
  }

  private static Path libJarPath;
  private static Path libDexPath;

  @BeforeClass
  public static void prepareLib() throws Exception {
    libJarPath = getStaticTemp().newFile("lib.jar").toPath().toAbsolutePath();
    writeClassesToJar(libJarPath, ImmutableList.of(LibraryBase.class));
    libDexPath = getStaticTemp().newFile("lib.dex").toPath().toAbsolutePath();
    testForD8(getStaticTemp())
        .addProgramFiles(libJarPath)
        .setMinApi(AndroidApiLevel.B)
        .setProgramConsumer(new ArchiveConsumer(libDexPath))
        .compile();
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addLibraryFiles(libJarPath)
        .addLibraryFiles(ToolHelper.getFirstSupportedAndroidJar(parameters.getApiLevel()))
        .addProgramClasses(ProgramBase.class, ProgramSub.class, MAIN)
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRule())
        .addDontObfuscate()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(parameters.isDexRuntime() ? libDexPath : libJarPath)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(config.expectedOutput());
  }

  static class LibraryBase {
    protected void throwing(String message) {
      throw new RuntimeException(message);
    }

    protected void debug(String message) {
      System.out.println("[LibraryBase]: " + message);
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class ProgramBase extends LibraryBase {
    @NeverInline
    @Override
    protected void throwing(String message) {
      System.out.println(message + "; no more throwing.");
    }

    @NeverInline
    @Override
    protected void debug(String message) {
      System.out.println("[ProgramBase]: " + message);
    }
  }

  @NeverClassInline
  static class ProgramSub extends ProgramBase {
    @NeverInline
    @Override
    protected void debug(String message) {
      System.out.println("[ProgramSub]: " + message);
    }
  }

  static class TestClass {
    @NeverInline
    static LibraryBase createLibraryInstance() {
      return System.currentTimeMillis() > 0 ? new LibraryBase() : new ProgramBase();
    }

    @NeverInline
    static ProgramBase createProgramInstance() {
      return System.currentTimeMillis() > 0 ? new ProgramBase() : new ProgramSub();
    }

    public static void main(String... args) {
      LibraryBase libInstance = createLibraryInstance();
      try {
        libInstance.throwing("Throwing!");
        System.out.println("Unless LibraryBase is explicitly mentioned");
      } catch (RuntimeException e) {
        System.out.println("Expect to catch RuntimeException");
      }
      libInstance.debug("as long as LibraryBase is specified");

      ProgramBase prgInstance = createProgramInstance();
      try {
        prgInstance.throwing("Nah");
      } catch (RuntimeException e) {
        System.out.println("Won't be any RuntimeException");
      }
      prgInstance.debug("as long as ProgramBase is specified");

      ProgramSub subInstance = new ProgramSub();
      try {
        subInstance.throwing("No, no, no");
      } catch (RuntimeException e) {
        System.out.println("Won't be any RuntimeException");
      }
      subInstance.debug("as long as ProgramSub is specified");
      System.out.println("The end");
    }
  }
}
