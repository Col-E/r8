// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

class B112517039ReturnType extends Exception {
}

interface B112517039I {
  B112517039ReturnType m();
  void flaf(Exception e);
}

class B112517039Caller {
  public void call(B112517039I i) {
    System.out.println("Ewwo!");
    i.flaf(i.m());
  }
}

class B112517039Main {
  public static void main(String[] args) {
    try {
      B112517039Caller caller = new B112517039Caller();
      caller.call(null);
    } catch (NullPointerException e) {
      System.out.println("NullPointerException");
    }
  }
}

@RunWith(VmTestRunner.class)
public class ReturnTypeTest extends TestBase {
  @Test
  public void testFromJavac() throws Exception {
    String mainName = B112517039Main.class.getCanonicalName();
    ProcessResult javaResult = ToolHelper.runJava(ToolHelper.getClassPathForTests(), mainName);
    assertEquals(0, javaResult.exitCode);
    assertThat(javaResult.stdout, containsString("Ewwo!"));
    assertThat(javaResult.stdout, containsString("NullPointerException"));

    List<String> config = ImmutableList.of(
        "-printmapping",
        "-keep class " + mainName + " {",
        "  public static void main(...);",
        "}"
    );
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFilesForTestDirectory(
        ToolHelper.getPackageDirectoryForTestPackage(B112517039Main.class.getPackage()),
        path -> path.getFileName().toString().startsWith("B112517039")));
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm().getLevel());
    builder.addProguardConfiguration(config, Origin.unknown());
    AndroidApp processedApp = ToolHelper.runR8(builder.build(), options -> {
      options.enableInlining = false;
    });

    Path outDex = temp.getRoot().toPath().resolve("dex.zip");
    processedApp.writeToZip(outDex, OutputMode.DexIndexed);
    ProcessResult artResult = ToolHelper.runArtNoVerificationErrorsRaw(outDex.toString(), mainName);
    assertEquals(0, artResult.exitCode);
    assertThat(javaResult.stdout, containsString("Ewwo!"));
    assertThat(javaResult.stdout, containsString("NullPointerException"));

    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject returnType = inspector.clazz(B112517039ReturnType.class);
    assertThat(returnType, isRenamed());
  }
}
