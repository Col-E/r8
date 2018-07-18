// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
abstract class AccessRelaxationTestBase extends TestBase {

  static R8Command.Builder loadProgramFiles(Iterable<Class> classes) {
    R8Command.Builder builder = R8Command.builder();
    for (Class clazz : classes) {
      builder.addProgramFiles(ToolHelper.getClassFileForTestClass(clazz));
    }
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm().getLevel());
    return builder;
  }

  static R8Command.Builder loadProgramFiles(Package p, Class... classes) throws Exception {
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFilesForTestPackage(p));
    for (Class clazz : classes) {
      builder.addProgramFiles(ToolHelper.getClassFileForTestClass(clazz));
    }
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm().getLevel());
    return builder;
  }

  void compareJvmAndArt(AndroidApp app, Class mainClass) throws Exception {
    // Run on Jvm.
    String jvmOutput = runOnJava(mainClass);

    // Run on Art to check generated code against verifier.
    String artOutput = runOnArt(app, mainClass);

    String adjustedArtOutput = artOutput.replace(
        "java.lang.IncompatibleClassChangeError", "java.lang.IllegalAccessError");
    assertEquals(jvmOutput, adjustedArtOutput);
  }

  static void assertPublic(CodeInspector codeInspector, Class clazz, MethodSignature signature) {
    ClassSubject classSubject = codeInspector.clazz(clazz);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(signature);
    assertThat(methodSubject, isPublic());
  }

  static void assertNotPublic(CodeInspector codeInspector, Class clazz, MethodSignature signature) {
    ClassSubject classSubject = codeInspector.clazz(clazz);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(signature);
    assertThat(methodSubject, not(isPublic()));
  }

}
