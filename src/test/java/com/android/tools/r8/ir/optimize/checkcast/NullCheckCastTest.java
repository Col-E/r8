// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.checkcast;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class NullCheckCastTestMain {
  public static void main(String[] args) {
    System.out.println((SubClass) null);
  }
}

class Base {
}

class SubClass extends Base {
}

@RunWith(Parameterized.class)
public class NullCheckCastTest extends TestBase {
  private final Backend backend;

  @Parameterized.Parameters(name = "backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public NullCheckCastTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void runTest() throws Exception {
    AndroidApp app = readClasses(Base.class, SubClass.class, NullCheckCastTestMain.class);
    AndroidApp processedApp = compileWithR8(
        app, keepMainProguardConfiguration(NullCheckCastTestMain.class), null, backend);

    List<byte[]> classBytes = ImmutableList.of(
        ToolHelper.getClassAsBytes(Base.class),
        ToolHelper.getClassAsBytes(SubClass.class),
        ToolHelper.getClassAsBytes(NullCheckCastTestMain.class)
    );
    String main = NullCheckCastTestMain.class.getCanonicalName();
    ProcessResult javaOutput = runOnJavaRaw(main, classBytes, ImmutableList.of());
    assertEquals(0, javaOutput.exitCode);
    ProcessResult output = runOnVMRaw(processedApp, main, backend);
    assertEquals(0, output.exitCode);
    assertEquals(javaOutput.stdout.trim(), output.stdout.trim());

    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject mainSubject = inspector.clazz(NullCheckCastTestMain.class);
    assertThat(mainSubject, isPresent());
    MethodSubject mainMethod = mainSubject.mainMethod();
    assertThat(mainMethod, isPresent());

    // Check if the check-cast is gone.
    assertTrue(Streams.stream(mainMethod.iterateInstructions())
        .noneMatch(InstructionSubject::isCheckCast));

    // As check-cast is gone, other types can be discarded, too.
    ClassSubject classSubject = inspector.clazz(Base.class);
    assertThat(classSubject, not(isPresent()));
    classSubject = inspector.clazz(SubClass.class);
    assertThat(classSubject, not(isPresent()));
  }

}
