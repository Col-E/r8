// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MethodHandleTestRunner {
  static final Class<?> CLASS = MethodHandleTest.class;

  private boolean ldc = false;

  @Rule public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void testMethodHandlesLookup() throws Exception {
    // Run test with dynamic method lookups, i.e. using MethodHandles.lookup().find*()
    ldc = false;
    test();
  }

  @Test
  public void testLdcMethodHandle() throws Exception {
    // Run test with LDC methods, i.e. without java.lang.invoke.MethodHandles
    ldc = true;
    test();
  }

  private final Class[] inputClasses = {
    MethodHandleTest.class,
    MethodHandleTest.C.class,
    MethodHandleTest.I.class,
    MethodHandleTest.Impl.class,
    MethodHandleTest.D.class,
  };

  private void test() throws Exception {
    ProcessResult runInput = runInput();
    Path outCf = temp.getRoot().toPath().resolve("cf.jar");
    build(new ClassFileConsumer.ArchiveConsumer(outCf));
    Path outDex = temp.getRoot().toPath().resolve("dex.zip");
    build(new DexIndexedConsumer.ArchiveConsumer(outDex));

    ProcessResult runCf =
        ToolHelper.runJava(outCf, CLASS.getCanonicalName(), ldc ? "error" : "exception");
    assertEquals(runInput.toString(), runCf.toString());
    // TODO(mathiasr): Once we include a P runtime, change this to "P and above".
    if (ToolHelper.getDexVm() != DexVm.ART_DEFAULT) {
      return;
    }
    ProcessResult runDex =
        ToolHelper.runArtRaw(
            outDex.toString(),
            CLASS.getCanonicalName(),
            cmd -> cmd.appendProgramArgument(ldc ? "pass" : "exception"));
    // Only compare stdout and exitCode since dex2oat prints to stderr.
    if (runInput.exitCode != runDex.exitCode) {
      System.out.println(runDex.stderr);
    }
    assertEquals(runInput.stdout, runDex.stdout);
    assertEquals(runInput.exitCode, runDex.exitCode);
  }

  private void build(ProgramConsumer programConsumer) throws Exception {
    // MethodHandle.invoke() only supported from Android O
    // ConstMethodHandle only supported from Android P
    AndroidApiLevel apiLevel = AndroidApiLevel.P;
    Builder cfBuilder =
        R8Command.builder()
            .setMinApiLevel(apiLevel.getLevel())
            .setMode(CompilationMode.DEBUG)
            .addLibraryFiles(ToolHelper.getAndroidJar(apiLevel))
            .setProgramConsumer(programConsumer);
    for (Class<?> c : inputClasses) {
      byte[] classAsBytes = getClassAsBytes(c);
      cfBuilder.addClassProgramData(classAsBytes, Origin.unknown());
    }
    R8.run(cfBuilder.build());
  }

  private ProcessResult runInput() throws Exception {
    Path out = temp.getRoot().toPath().resolve("input.jar");
    ClassFileConsumer.ArchiveConsumer archiveConsumer = new ClassFileConsumer.ArchiveConsumer(out);
    for (Class<?> c : inputClasses) {
      archiveConsumer.accept(
          getClassAsBytes(c), DescriptorUtils.javaTypeToDescriptor(c.getName()), null);
    }
    archiveConsumer.finished(null);
    ProcessResult runInput = ToolHelper.runJava(out, CLASS.getName(), ldc ? "error" : "exception");
    if (runInput.exitCode != 0) {
      System.out.println(runInput);
    }
    assertEquals(0, runInput.exitCode);
    return runInput;
  }

  private byte[] getClassAsBytes(Class<?> clazz) throws Exception {
    if (ldc) {
      if (clazz == MethodHandleTest.D.class) {
        return MethodHandleDump.dumpD();
      } else if (clazz == MethodHandleTest.class) {
        return MethodHandleDump.transform(ToolHelper.getClassAsBytes(clazz));
      }
    }
    return ToolHelper.getClassAsBytes(clazz);
  }
}
