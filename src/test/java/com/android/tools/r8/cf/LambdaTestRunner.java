// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.JarCode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.DexInspector;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class LambdaTestRunner {

  private static final Class<?> CLASS = LambdaTest.class;
  private static final String METHOD = "main";

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void test() throws Exception {
    // Test that the InvokeDynamic instruction in LambdaTest.main()
    // is not modified by the R8 compilation.
    // First, extract the InvokeDynamic instruction from the input class.
    byte[] inputClass = ToolHelper.getClassAsBytes(CLASS);
    int opcode = Opcodes.INVOKEDYNAMIC;
    InvokeDynamicInsnNode insnInput = findFirstInMethod(inputClass, opcode);
    // Compile with R8 and extract the InvokeDynamic instruction from the output class.
    AndroidAppConsumers appBuilder = new AndroidAppConsumers();
    Path outPath = temp.getRoot().toPath().resolve("out.jar");
    R8.run(
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()))
            .setProgramConsumer(appBuilder.wrapClassFileConsumer(new ArchiveConsumer(outPath)))
            .addClassProgramData(inputClass, Origin.unknown())
            .build());
    AndroidApp app = appBuilder.build();
    InvokeDynamicInsnNode insnOutput = findFirstInMethod(app, opcode);
    // Check that the InvokeDynamic instruction is not modified.
    assertEquals(insnInput.name, insnOutput.name);
    assertEquals(insnInput.desc, insnOutput.desc);
    assertEquals(insnInput.bsm, insnOutput.bsm);
    assertArrayEquals(insnInput.bsmArgs, insnOutput.bsmArgs);
    // Check that execution gives the same output.
    ProcessResult inputResult =
        ToolHelper.runJava(ToolHelper.getClassPathForTests(), CLASS.getName());
    ProcessResult outputResult = ToolHelper.runJava(outPath, CLASS.getName());
    assertEquals(inputResult.toString(), outputResult.toString());
  }

  private InvokeDynamicInsnNode findFirstInMethod(AndroidApp app, int opcode) throws Exception {
    String returnType = "void";
    DexInspector inspector = new DexInspector(app);
    List<String> args = Collections.singletonList(String[].class.getTypeName());
    DexEncodedMethod method = inspector.clazz(CLASS).method(returnType, METHOD, args).getMethod();
    JarCode jarCode = method.getCode().asJarCode();
    MethodNode outputMethod = jarCode.getNode();
    return (InvokeDynamicInsnNode) findFirstInstruction(outputMethod, opcode);
  }

  private InvokeDynamicInsnNode findFirstInMethod(byte[] clazz, int opcode) {
    MethodNode[] method = {null};
    new ClassReader(clazz)
        .accept(
            new ClassNode(Opcodes.ASM6) {
              @Override
              public MethodVisitor visitMethod(
                  int access, String name, String desc, String signature, String[] exceptions) {
                if (name.equals(METHOD)) {
                  method[0] = new MethodNode(access, name, desc, signature, exceptions);
                  return method[0];
                } else {
                  return null;
                }
              }
            },
            0);
    return (InvokeDynamicInsnNode) findFirstInstruction(method[0], opcode);
  }

  private AbstractInsnNode findFirstInstruction(MethodNode node, int opcode) {
    assert node != null;
    InsnList asmInsns = node.instructions;
    for (ListIterator<AbstractInsnNode> it = asmInsns.iterator(); it.hasNext(); ) {
      AbstractInsnNode insn = it.next();
      if (insn.getOpcode() == opcode) {
        return insn;
      }
    }
    throw new RuntimeException("Instruction not found");
  }
}
