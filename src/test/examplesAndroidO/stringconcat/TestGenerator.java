// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package stringconcat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class TestGenerator {
  private static final String RECIPE_PREFIX = "RECIPE:";

  private static final String STRING_CONCAT_FACTORY = "java/lang/invoke/StringConcatFactory";

  private static final Handle MAKE_CONCAT_WITH_CONSTANTS = new Handle(
      Opcodes.H_INVOKESTATIC, STRING_CONCAT_FACTORY, "makeConcatWithConstants",
      MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
          MethodType.class, String.class, Object[].class).toMethodDescriptorString(),
      false);

  private static final Handle MAKE_CONCAT = new Handle(
      Opcodes.H_INVOKESTATIC, STRING_CONCAT_FACTORY, "makeConcat",
      MethodType.methodType(CallSite.class, MethodHandles.Lookup.class,
          String.class, MethodType.class).toMethodDescriptorString(),
      false);

  public static void main(String[] args) throws IOException {
    assert args.length == 1;
    generateTests(Paths.get(args[0],
        TestGenerator.class.getPackage().getName(),
        StringConcat.class.getSimpleName() + ".class"));
  }

  private static void generateTests(Path classNamePath) throws IOException {
    ClassReader cr = new ClassReader(new FileInputStream(classNamePath.toFile()));
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cr.accept(
        new ClassVisitor(Opcodes.ASM6, cw) {
          @Override
          public MethodVisitor visitMethod(int access,
              final String methodName, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, methodName, desc, signature, exceptions);
            return new MethodVisitor(Opcodes.ASM6, mv) {
              private List<Object> recentConstants = new ArrayList<>();

              @Override
              public void visitLdcInsn(Object cst) {
                if (!recentConstants.isEmpty() ||
                    (cst instanceof String && ((String) cst).startsWith(RECIPE_PREFIX))) {
                  // Add the constant, don't push anything on stack.
                  recentConstants.add(cst);
                  return;
                }
                super.visitLdcInsn(cst);
              }

              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String desc, boolean itf) {
                // Replace calls to 'makeConcat(...)' with appropriate `invokedynamic`.
                if (opcode == Opcodes.INVOKESTATIC && name.equals("makeConcat")) {
                  mv.visitInvokeDynamicInsn(MAKE_CONCAT.getName(), desc, MAKE_CONCAT);
                  recentConstants.clear();
                  return;
                }

                // Replace calls to 'makeConcat(...)' with appropriate `invokedynamic`.
                if (opcode == Opcodes.INVOKESTATIC && name.equals("makeConcatWithConstants")) {
                  if (recentConstants.isEmpty()) {
                    throw new AssertionError("No constants detected in `" +
                        methodName + "`: call to " + name + desc);
                  }
                  recentConstants.set(0,
                      ((String) recentConstants.get(0)).substring(RECIPE_PREFIX.length()));

                  mv.visitInvokeDynamicInsn(MAKE_CONCAT_WITH_CONSTANTS.getName(),
                      removeLastParams(desc, recentConstants.size()), MAKE_CONCAT_WITH_CONSTANTS,
                      recentConstants.toArray(new Object[recentConstants.size()]));
                  recentConstants.clear();
                  return;
                }

                // Otherwise fall back to default implementation.
                super.visitMethodInsn(opcode, owner, name, desc, itf);
              }

              private String removeLastParams(String descr, int paramsToRemove) {
                MethodType methodType =
                    MethodType.fromMethodDescriptorString(
                        descr, this.getClass().getClassLoader());
                return methodType
                    .dropParameterTypes(
                        methodType.parameterCount() - paramsToRemove,
                        methodType.parameterCount())
                    .toMethodDescriptorString();
              }

              @Override
              public void visitInsn(int opcode) {
                switch (opcode) {
                  case Opcodes.ICONST_0:
                    if (!recentConstants.isEmpty()) {
                      recentConstants.add(0);
                      return;
                    }
                    break;
                  case Opcodes.ICONST_1:
                    if (!recentConstants.isEmpty()) {
                      recentConstants.add(1);
                      return;
                    }
                    break;
                  case Opcodes.ICONST_2:
                    if (!recentConstants.isEmpty()) {
                      recentConstants.add(2);
                      return;
                    }
                    break;
                  case Opcodes.ICONST_3:
                    if (!recentConstants.isEmpty()) {
                      recentConstants.add(3);
                      return;
                    }
                    break;
                  case Opcodes.ICONST_4:
                    if (!recentConstants.isEmpty()) {
                      recentConstants.add(4);
                      return;
                    }
                    break;
                  case Opcodes.ICONST_5:
                    if (!recentConstants.isEmpty()) {
                      recentConstants.add(5);
                      return;
                    }
                    break;
                  case Opcodes.ICONST_M1:
                    if (!recentConstants.isEmpty()) {
                      recentConstants.add(-1);
                      return;
                    }
                    break;
                  default:
                    recentConstants.clear();
                    break;
                }
                super.visitInsn(opcode);
              }

              @Override
              public void visitIntInsn(int opcode, int operand) {
                recentConstants.clear();
                super.visitIntInsn(opcode, operand);
              }

              @Override
              public void visitVarInsn(int opcode, int var) {
                recentConstants.clear();
                super.visitVarInsn(opcode, var);
              }

              @Override
              public void visitTypeInsn(int opcode, String type) {
                recentConstants.clear();
                super.visitTypeInsn(opcode, type);
              }

              @Override
              public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                recentConstants.clear();
                super.visitFieldInsn(opcode, owner, name, desc);
              }

              @Override
              public void visitJumpInsn(int opcode, Label label) {
                recentConstants.clear();
                super.visitJumpInsn(opcode, label);
              }

              @Override
              public void visitIincInsn(int var, int increment) {
                recentConstants.clear();
                super.visitIincInsn(var, increment);
              }

              @Override
              public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                recentConstants.clear();
                super.visitTableSwitchInsn(min, max, dflt, labels);
              }

              @Override
              public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                recentConstants.clear();
                super.visitLookupSwitchInsn(dflt, keys, labels);
              }

              @Override
              public void visitMultiANewArrayInsn(String desc, int dims) {
                recentConstants.clear();
                super.visitMultiANewArrayInsn(desc, dims);
              }
            };
          }
        }, 0);
    new FileOutputStream(classNamePath.toFile()).write(cw.toByteArray());
  }
}
