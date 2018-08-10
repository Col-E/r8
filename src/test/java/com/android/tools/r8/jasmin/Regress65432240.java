// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.jasmin.JasminBuilder.ClassFileVersion;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CfInstructionSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress65432240 extends JasminTestBase {

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public Regress65432240(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testConstantNotIntoEntryBlock() throws Exception {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    MethodSignature signature = clazz.addStaticMethod("test1", ImmutableList.of("I"), "I",
        ".limit stack 3",
        ".limit locals 2",
        "  iload 0",
        "  ifne L2",
        "L1:",
        "  iconst_0",
        "  ireturn",
        "L2:",
        "  iload 0",
        "  iload 0",
        "  iconst_1",
        "  isub",
        "  invokestatic Test/test1(I)I",
        "  iadd",
        "  ireturn");

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_0",
        "  invokestatic Test/test1(I)I",
        "  invokestatic java/lang/Integer/toString(I)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_0",
        "  invokestatic Test/test1(I)I",
        "  invokestatic java/lang/Integer/toString(I)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  return");

    String expected = runOnJava(builder, clazz.name);

    AndroidApp originalApplication = builder.build();
    AndroidApp processedApplication =
        ToolHelper.runR8(
            R8Command.builder(originalApplication)
                .setProgramConsumer(emptyConsumer(backend))
                .addLibraryFiles(runtimeJar(backend))
                .build());

    CodeInspector inspector =
        new CodeInspector(processedApplication, o -> o.enableCfFrontend = true);
    ClassSubject inspectedClass = inspector.clazz(clazz.name);
    MethodSubject method = inspectedClass.method(signature);
    assertTrue(method.isPresent());
    Iterator<InstructionSubject> iterator = method.iterateInstructions();
    InstructionSubject instruction = null;
    boolean found = false;
    while (iterator.hasNext()) {
      instruction = iterator.next();
      if (!(instruction instanceof CfInstructionSubject)
          || !((CfInstructionSubject) instruction).isLoad()) {
        found = true;
        break;
      }
    }
    assertTrue(found && instruction.isIfNez());

    String result;
    if (backend == Backend.DEX) {
      result = runOnArt(processedApplication, clazz.name);
    } else {
      assert backend == Backend.CF;
      result = runOnJava(processedApplication, clazz.name, Collections.emptyList());
    }
    assertEquals(expected, result);
  }
}
