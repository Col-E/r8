// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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

@RunWith(Parameterized.class)
public class CompositionalLenseTest extends TestBase {
  private final static List<Class> CLASSES =
      ImmutableList.of(Base.class, Sub.class, TestMain.class);

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public CompositionalLenseTest(Backend backend) {
    this.backend = backend;
  }

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
    R8Command.Builder builder = ToolHelper.prepareR8CommandBuilder(app, emptyConsumer(backend));
    builder
        .addProguardConfiguration(
            ImmutableList.of(
                keepMainProguardConfiguration(TestMain.class),
                "-applymapping " + mapPath,
                "-dontobfuscate"), // to use the renamed names in test-mapping.txt
            Origin.unknown())
        .addLibraryFiles(runtimeJar(backend));
    AndroidApp processedApp = ToolHelper.runR8(builder.build(), options -> {
      options.enableInlining = false;
      options.enableClassMerging = false;
    });
    CodeInspector codeInspector = new CodeInspector(processedApp, o -> o.enableCfFrontend = true);
    ClassSubject classSubject = codeInspector.clazz(TestMain.class);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(CodeInspector.MAIN);
    assertThat(methodSubject, isPresent());
    Iterator<InstructionSubject> iterator = methodSubject.iterateInstructions();

    InstructionSubject instruction = null;
    boolean found = false;
    while (iterator.hasNext()) {
      instruction = iterator.next();
      if (instruction.isInvokeVirtual()) {
        found = true;
        break;
      }
    }
    assertTrue(found);

    DexMethod invokedMethod = ((InvokeInstructionSubject) instruction).invokedMethod();
    assertEquals("bar", invokedMethod.name.toString());
    assertEquals("X", invokedMethod.getHolder().getName());
  }
}
