// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DefaultDiagnosticsHandler;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MethodHandleTestRunner extends TestBase {
  static final Class<?> CLASS = MethodHandleTest.class;

  enum LookupType {
    DYNAMIC,
    CONSTANT,
  }

  enum MinifyMode {
    NONE,
    MINIFY,
  }

  enum Frontend {
    JAR,
    CF,
  }

  private CompilationMode compilationMode;
  private LookupType lookupType;
  private Frontend frontend;
  private ProcessResult runInput;
  private MinifyMode minifyMode;

  @Parameters(name = "{0}_{1}_{2}_{3}")
  public static List<String[]> data() {
    List<String[]> res = new ArrayList<>();
    for (LookupType lookupType : LookupType.values()) {
      for (Frontend frontend : Frontend.values()) {
        for (MinifyMode minifyMode : MinifyMode.values()) {
          if (lookupType == LookupType.DYNAMIC && minifyMode == MinifyMode.MINIFY) {
            // Skip because we don't keep the members looked up dynamically.
            continue;
          }
          for (CompilationMode compilationMode : CompilationMode.values()) {
            res.add(
                new String[] {
                  lookupType.name(), frontend.name(), minifyMode.name(), compilationMode.name()
                });
          }
        }
      }
    }
    return res;
  }

  public MethodHandleTestRunner(
      String lookupType, String frontend, String minifyMode, String compilationMode) {
    this.lookupType = LookupType.valueOf(lookupType);
    this.frontend = Frontend.valueOf(frontend);
    this.minifyMode = MinifyMode.valueOf(minifyMode);
    this.compilationMode = CompilationMode.valueOf(compilationMode);
  }

  @Test
  public void test() throws Exception {
    runInput();
    runCf();
    // TODO(mathiasr): Once we include a P runtime, change this to "P and above".
    if (ToolHelper.getDexVm() == DexVm.ART_DEFAULT && ToolHelper.artSupported()) {
      runDex();
    }
  }

  private final Class[] inputClasses = {
    MethodHandleTest.class,
    MethodHandleTest.C.class,
    MethodHandleTest.I.class,
    MethodHandleTest.Impl.class,
    MethodHandleTest.D.class,
    MethodHandleTest.E.class,
    MethodHandleTest.F.class,
  };

  private void runInput() throws Exception {
    Path out = temp.getRoot().toPath().resolve("input.jar");
    ClassFileConsumer.ArchiveConsumer archiveConsumer = new ClassFileConsumer.ArchiveConsumer(out);
    for (Class<?> c : inputClasses) {
      archiveConsumer.accept(
          ByteDataView.of(getClassAsBytes(c)),
          DescriptorUtils.javaTypeToDescriptor(c.getName()),
          null);
    }
    archiveConsumer.finished(null);
    String expected = lookupType == LookupType.CONSTANT ? "error" : "exception";
    runInput = ToolHelper.runJava(out, CLASS.getName(), expected);
    if (runInput.exitCode != 0) {
      System.out.println(runInput);
    }
    assertEquals(0, runInput.exitCode);
  }

  private void runCf() throws Exception {
    Path outCf = temp.getRoot().toPath().resolve("cf.jar");
    build(new ClassFileConsumer.ArchiveConsumer(outCf));
    String expected = lookupType == LookupType.CONSTANT ? "error" : "exception";
    ProcessResult runCf = ToolHelper.runJava(outCf, CLASS.getCanonicalName(), expected);
    assertEquals(runInput.toString(), runCf.toString());
  }

  private void runDex() throws Exception {
    Path outDex = temp.getRoot().toPath().resolve("dex.zip");
    build(new DexIndexedConsumer.ArchiveConsumer(outDex));
    String expected = lookupType == LookupType.CONSTANT ? "pass" : "exception";
    ProcessResult runDex =
        ToolHelper.runArtRaw(
            outDex.toString(),
            CLASS.getCanonicalName(),
            cmd -> cmd.appendProgramArgument(expected));
    // Only compare stdout and exitCode since dex2oat prints to stderr.
    if (runInput.exitCode != runDex.exitCode) {
      System.out.println(runDex.stderr);
    }
    assertEquals(runInput.exitCode, runDex.exitCode);
    assertEquals(runInput.stdout, runDex.stdout);
  }

  private void build(ProgramConsumer programConsumer) throws Exception {
    // MethodHandle.invoke() only supported from Android O
    // ConstMethodHandle only supported from Android P
    AndroidApiLevel apiLevel = AndroidApiLevel.P;
    Builder command =
        R8Command.builder()
            .setMode(compilationMode)
            .addLibraryFiles(ToolHelper.getAndroidJar(apiLevel))
            .setProgramConsumer(programConsumer);
    if (!(programConsumer instanceof ClassFileConsumer)) {
      command.setMinApiLevel(apiLevel.getLevel());
    }
    for (Class<?> c : inputClasses) {
      byte[] classAsBytes = getClassAsBytes(c);
      command.addClassProgramData(classAsBytes, Origin.unknown());
    }
    if (minifyMode == MinifyMode.MINIFY) {
      command.addProguardConfiguration(
          Arrays.asList(
              "-keep public class com.android.tools.r8.cf.MethodHandleTest {",
              "  public static void main(...);",
              "}",
              // Disallow merging MethodHandleTest$I into MethodHandleTest$Impl
              "-keep public interface com.android.tools.r8.cf.MethodHandleTest$I"),
          Origin.unknown());
    }
    try {
      ToolHelper.runR8(
          command.build(), options -> options.enableCfFrontend = frontend == Frontend.CF);
    } catch (CompilationError e) {
      if (frontend == Frontend.CF && compilationMode == CompilationMode.DEBUG) {
        // TODO(b/79725635): Investigate why these tests fail on the buildbot.
        // Use a Reporter to extract origin info to standard error.
        new Reporter(new DefaultDiagnosticsHandler()).error(e);
        // Print the stack trace since this is not always printed by JUnit.
        e.printStackTrace();
        Assume.assumeNoException(
            "TODO(b/79725635): Investigate why these tests fail on the buildbot.", e);
      }
      throw e;
    }
  }

  private byte[] getClassAsBytes(Class<?> clazz) throws Exception {
    if (lookupType == LookupType.CONSTANT) {
      if (clazz == MethodHandleTest.D.class) {
        return MethodHandleDump.dumpD();
      } else if (clazz == MethodHandleTest.class) {
        return MethodHandleDump.transform(ToolHelper.getClassAsBytes(clazz));
      }
    }
    return ToolHelper.getClassAsBytes(clazz);
  }
}
