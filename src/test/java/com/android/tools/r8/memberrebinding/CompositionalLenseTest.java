// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

// Base -> X:
class Base {
  // foo() -> bar
  void foo() {
    System.out.println("Base#foo");
  }
}

// Sub -> Y:
class Sub extends Base {
  // foo() -> bar
  // Sub#foo ~> Base#foo by member rebinding analysis
}

class TestMain {
  public static void main(String[] args) {
    // Without regard to the order of member rebinding and apply mapping,
    // this call should be mapped to X.bar(), not Y.bar() nor Base.foo().
    new Sub().foo();
  }
}

public class CompositionalLenseTest extends TestBase {
  private final static List<Class> CLASSES =
      ImmutableList.of(Base.class, Sub.class, TestMain.class);

  @Test
  public void test() throws Exception {
    Path mapPath = temp.newFile("test-mapping.txt").toPath();
    List<String> pgMap = ImmutableList.of(
        "com.android.tools.r8.memberrebinding.Base -> X:",
        "  void foo() -> bar",
        "com.android.tools.r8.memberrebinding.Sub -> Y:",
        "  void foo() -> bar"
    );
    FileUtils.writeTextFile(mapPath, pgMap);

    AndroidApp app = readClasses(CLASSES);
    R8Command.Builder builder = ToolHelper.prepareR8CommandBuilder(app);
    builder.addProguardConfiguration(
        ImmutableList.of(
            keepMainProguardConfiguration(TestMain.class),
            "-applymapping " + mapPath,
            "-dontobfuscate"),  // to use the renamed names in test-mapping.txt
        Origin.unknown());
    AndroidApp processedApp = ToolHelper.runR8(builder.build(), options -> {
      options.enableInlining = false;
      options.enableClassMerging = false;
    });
    DexInspector dexInspector = new DexInspector(processedApp);
    ClassSubject classSubject = dexInspector.clazz(TestMain.class);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(DexInspector.MAIN);
    assertThat(methodSubject, isPresent());
    DexCode dexCode = methodSubject.getMethod().getCode().asDexCode();
    assertTrue(dexCode.instructions[2] instanceof InvokeVirtual);
    InvokeVirtual invoke = (InvokeVirtual) dexCode.instructions[2];
    DexMethod invokedMethod = invoke.getMethod();
    assertEquals("bar", invokedMethod.name.toString());
    assertEquals("X", invokedMethod.getHolder().getName());
  }
}
