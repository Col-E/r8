// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlineCmpDoubleTestRunner {
  static final Class CLASS = InlineCmpDoubleTest.class;
  final boolean enableInlining;
  @Rule public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  public InlineCmpDoubleTestRunner(boolean enableInlining) {
    this.enableInlining = enableInlining;
  }

  @Parameters(name = "inlining={0}")
  public static Boolean[] data() {
    return new Boolean[]{true, false};
  }

  @Test
  public void test() throws Exception {
    byte[] inputClass = ToolHelper.getClassAsBytes(CLASS);
    AndroidAppConsumers appBuilder = new AndroidAppConsumers();
    Path outPath = temp.getRoot().toPath().resolve("out.jar");
    List<String> proguardKeepMain = ImmutableList.of(TestBase.keepMainProguardConfiguration(CLASS));
    R8Command command =  R8Command.builder()
        .setMode(CompilationMode.RELEASE)
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .setProgramConsumer(appBuilder.wrapClassFileConsumer(new ArchiveConsumer(outPath)))
        .addProguardConfiguration(proguardKeepMain, Origin.unknown())
        .addClassProgramData(inputClass, Origin.unknown())
        .build();

    AndroidApp app = ToolHelper.runR8(command, options -> {
          options.enableCfFrontend = true;
          options.enableInlining = enableInlining;
    });


    assert ToolHelper.runJava(outPath, CLASS.getCanonicalName()).exitCode == 0;
    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(CLASS);
    MethodSubject method = clazz
        .method(new MethodSignature("inlineMe", "int", ImmutableList.of("int")));
    assertEquals(enableInlining, !method.isPresent());
  }
}
