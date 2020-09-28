// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class SwitchDebugLocalsConflictTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public SwitchDebugLocalsConflictTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws CompilationFailedException {
    testForD8()
        .addProgramClassFileData(Dump.dump())
        .noDesugaring()
        .compileWithExpectedDiagnostics(
            diagnotics -> {
              diagnotics.assertNoErrors();
              diagnotics.assertInfoThatMatches(
                  diagnosticMessage(containsString("invalid locals information")));
            });
  }

  // Dump of class file from b/138952302, which is produced with Java 7 and contains too large a
  // local scope for the local variable 'valueDiv'. The larger scope causes a debug read of an
  // uninitialized value, thus the value can be of either type long or of type double depending on
  // which execution path leads up to the local start. More recent javac compilers emit the smaller
  // correct scope for the local variable.
  public static class Dump implements Opcodes {

    public static byte[] dump() {

      ClassWriter cw = new ClassWriter(0);
      FieldVisitor fv;
      MethodVisitor mv;
      cw.visit(V1_7, ACC_PUBLIC | ACC_SUPER, "TestClass", null, "java/lang/Object", null);
      cw.visitSource("TestClass.java", null);
      {
        fv = cw.visitField(ACC_PUBLIC | ACC_STATIC, "DEFAULT_VALUE", "J", null, null);
        fv.visitEnd();
      }
      {
        mv =
            cw.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                "foo",
                "([Ljava/lang/Object;I)Ljava/lang/Object;",
                null,
                null);
        mv.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        mv.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
        Label label3 = new Label();
        Label label4 = new Label();
        mv.visitTryCatchBlock(label3, label4, label2, "java/lang/Throwable");
        Label label5 = new Label();
        Label label6 = new Label();
        mv.visitTryCatchBlock(label5, label6, label2, "java/lang/Throwable");
        Label label7 = new Label();
        Label label8 = new Label();
        mv.visitTryCatchBlock(label7, label8, label2, "java/lang/Throwable");
        Label label9 = new Label();
        Label label10 = new Label();
        mv.visitTryCatchBlock(label9, label10, label2, "java/lang/Throwable");
        Label label11 = new Label();
        Label label12 = new Label();
        mv.visitTryCatchBlock(label11, label12, label2, "java/lang/Throwable");
        Label label13 = new Label();
        Label label14 = new Label();
        mv.visitTryCatchBlock(label13, label14, label2, "java/lang/Throwable");
        Label label15 = new Label();
        Label label16 = new Label();
        mv.visitTryCatchBlock(label15, label16, label2, "java/lang/Throwable");
        mv.visitLabel(label0);
        mv.visitLineNumber(23, label0);
        mv.visitVarInsn(ALOAD, 0);
        Label label17 = new Label();
        mv.visitJumpInsn(IFNULL, label17);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ARRAYLENGTH);
        mv.visitJumpInsn(IFNE, label3);
        mv.visitLabel(label17);
        mv.visitLineNumber(24, label17);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitFieldInsn(GETSTATIC, "TestClass", "DEFAULT_VALUE", "J");
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        mv.visitLabel(label1);
        mv.visitInsn(ARETURN);
        mv.visitLabel(label3);
        mv.visitLineNumber(26, label3);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ARRAYLENGTH);
        mv.visitVarInsn(ISTORE, 2);
        Label label18 = new Label();
        mv.visitLabel(label18);
        mv.visitLineNumber(27, label18);
        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ISTORE, 3);
        Label label19 = new Label();
        mv.visitLabel(label19);
        mv.visitLineNumber(28, label19);
        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ISTORE, 4);
        Label label20 = new Label();
        mv.visitLabel(label20);
        mv.visitLineNumber(29, label20);
        mv.visitInsn(DCONST_0);
        mv.visitVarInsn(DSTORE, 5);
        Label label21 = new Label();
        mv.visitLabel(label21);
        mv.visitLineNumber(30, label21);
        mv.visitInsn(LCONST_0);
        mv.visitVarInsn(LSTORE, 7);
        Label label22 = new Label();
        mv.visitLabel(label22);
        mv.visitLineNumber(31, label22);
        mv.visitFrame(
            Opcodes.F_FULL,
            7,
            new Object[] {
              "[Ljava/lang/Object;",
              Opcodes.INTEGER,
              Opcodes.INTEGER,
              Opcodes.INTEGER,
              Opcodes.INTEGER,
              Opcodes.DOUBLE,
              Opcodes.LONG
            },
            0,
            new Object[] {});
        mv.visitVarInsn(ILOAD, 3);
        mv.visitVarInsn(ILOAD, 2);
        Label label23 = new Label();
        mv.visitJumpInsn(IF_ICMPGE, label23);
        Label label24 = new Label();
        mv.visitLabel(label24);
        mv.visitLineNumber(32, label24);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ILOAD, 3);
        mv.visitInsn(AALOAD);
        mv.visitVarInsn(ASTORE, 9);
        Label label25 = new Label();
        mv.visitLabel(label25);
        mv.visitLineNumber(33, label25);
        mv.visitInsn(DCONST_0);
        mv.visitVarInsn(DSTORE, 10);
        Label label26 = new Label();
        mv.visitLabel(label26);
        mv.visitLineNumber(34, label26);
        mv.visitInsn(LCONST_0);
        mv.visitVarInsn(LSTORE, 12);
        Label label27 = new Label();
        mv.visitLabel(label27);
        mv.visitLineNumber(36, label27);
        mv.visitVarInsn(ALOAD, 9);
        mv.visitTypeInsn(INSTANCEOF, "java/lang/String");
        Label label28 = new Label();
        mv.visitJumpInsn(IFEQ, label28);
        Label label29 = new Label();
        mv.visitLabel(label29);
        mv.visitLineNumber(37, label29);
        mv.visitVarInsn(ALOAD, 9);
        mv.visitTypeInsn(CHECKCAST, "java/lang/String");
        mv.visitVarInsn(ASTORE, 14);
        Label label30 = new Label();
        mv.visitLabel(label30);
        mv.visitLineNumber(38, label30);
        mv.visitVarInsn(ILOAD, 4);
        Label label31 = new Label();
        mv.visitJumpInsn(IFNE, label31);
        mv.visitVarInsn(ALOAD, 14);
        mv.visitMethodInsn(
            INVOKESTATIC,
            "NumberUtil",
            "hasDigit",
            "(Ljava/lang/String;)Z",
            false);
        Label label32 = new Label();
        mv.visitJumpInsn(IFEQ, label32);
        mv.visitLabel(label31);
        mv.visitLineNumber(39, label31);
        mv.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "[Ljava/lang/Object;",
              Opcodes.INTEGER,
              Opcodes.INTEGER,
              Opcodes.INTEGER,
              Opcodes.INTEGER,
              Opcodes.DOUBLE,
              Opcodes.LONG,
              "java/lang/Object",
              Opcodes.DOUBLE,
              Opcodes.LONG,
              "java/lang/String"
            },
            0,
            new Object[] {});
        mv.visitVarInsn(ALOAD, 14);
        mv.visitMethodInsn(
            INVOKESTATIC,
            "NumberUtil",
            "parseDouble",
            "(Ljava/lang/String;)D",
            false);
        mv.visitVarInsn(DSTORE, 10);
        Label label33 = new Label();
        mv.visitLabel(label33);
        mv.visitLineNumber(40, label33);
        mv.visitInsn(ICONST_1);
        mv.visitVarInsn(ISTORE, 4);
        Label label34 = new Label();
        mv.visitJumpInsn(GOTO, label34);
        mv.visitLabel(label32);
        mv.visitLineNumber(42, label32);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ALOAD, 14);
        mv.visitMethodInsn(
            INVOKESTATIC,
            "NumberUtil",
            "parseLong",
            "(Ljava/lang/String;)J",
            false);
        mv.visitVarInsn(LSTORE, 12);
        mv.visitLabel(label34);
        mv.visitLineNumber(44, label34);
        mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
        Label label35 = new Label();
        mv.visitJumpInsn(GOTO, label35);
        mv.visitLabel(label28);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ILOAD, 4);
        Label label36 = new Label();
        mv.visitJumpInsn(IFNE, label36);
        mv.visitVarInsn(ALOAD, 9);
        mv.visitMethodInsn(
            INVOKESTATIC,
            "NumberUtil",
            "isFloatPointNum",
            "(Ljava/lang/Object;)Z",
            false);
        Label label37 = new Label();
        mv.visitJumpInsn(IFEQ, label37);
        mv.visitLabel(label36);
        mv.visitLineNumber(45, label36);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ALOAD, 9);
        mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false);
        mv.visitVarInsn(DSTORE, 10);
        Label label38 = new Label();
        mv.visitLabel(label38);
        mv.visitLineNumber(46, label38);
        mv.visitInsn(ICONST_1);
        mv.visitVarInsn(ISTORE, 4);
        mv.visitJumpInsn(GOTO, label35);
        mv.visitLabel(label37);
        mv.visitLineNumber(47, label37);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ALOAD, 9);
        mv.visitTypeInsn(INSTANCEOF, "java/lang/Integer");
        Label label39 = new Label();
        mv.visitJumpInsn(IFNE, label39);
        mv.visitVarInsn(ALOAD, 9);
        mv.visitTypeInsn(INSTANCEOF, "java/lang/Long");
        Label label40 = new Label();
        mv.visitJumpInsn(IFEQ, label40);
        mv.visitLabel(label39);
        mv.visitLineNumber(48, label39);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ALOAD, 9);
        mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "longValue", "()J", false);
        mv.visitVarInsn(LSTORE, 12);
        mv.visitJumpInsn(GOTO, label35);
        mv.visitLabel(label40);
        mv.visitLineNumber(50, label40);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitFieldInsn(GETSTATIC, "TestClass", "DEFAULT_VALUE", "J");
        mv.visitVarInsn(LSTORE, 12);
        mv.visitLabel(label35);
        mv.visitLineNumber(53, label35);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ILOAD, 4);
        Label label41 = new Label();
        mv.visitJumpInsn(IFEQ, label41);
        Label label42 = new Label();
        mv.visitLabel(label42);
        mv.visitLineNumber(54, label42);
        mv.visitVarInsn(LLOAD, 7);
        mv.visitInsn(LCONST_0);
        mv.visitInsn(LCMP);
        Label label43 = new Label();
        mv.visitJumpInsn(IFEQ, label43);
        Label label44 = new Label();
        mv.visitLabel(label44);
        mv.visitLineNumber(55, label44);
        mv.visitVarInsn(LLOAD, 7);
        mv.visitInsn(L2D);
        mv.visitVarInsn(DSTORE, 5);
        Label label45 = new Label();
        mv.visitLabel(label45);
        mv.visitLineNumber(56, label45);
        mv.visitInsn(LCONST_0);
        mv.visitVarInsn(LSTORE, 7);
        mv.visitLabel(label43);
        mv.visitLineNumber(58, label43);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ILOAD, 3);
        Label label46 = new Label();
        mv.visitJumpInsn(IFNE, label46);
        Label label47 = new Label();
        mv.visitLabel(label47);
        mv.visitLineNumber(59, label47);
        mv.visitVarInsn(DLOAD, 10);
        mv.visitVarInsn(DSTORE, 5);
        Label label48 = new Label();
        mv.visitJumpInsn(GOTO, label48);
        mv.visitLabel(label46);
        mv.visitLineNumber(61, label46);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ILOAD, 1);
        Label label49 = new Label();
        Label label50 = new Label();
        Label label51 = new Label();
        Label label52 = new Label();
        Label label53 = new Label();
        Label label54 = new Label();
        mv.visitTableSwitchInsn(
            1, 5, label54, new Label[] {label49, label50, label51, label52, label53});
        mv.visitLabel(label49);
        mv.visitLineNumber(63, label49);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(DLOAD, 5);
        mv.visitVarInsn(DLOAD, 10);
        mv.visitInsn(DADD);
        mv.visitVarInsn(DSTORE, 5);
        Label label55 = new Label();
        mv.visitLabel(label55);
        mv.visitLineNumber(64, label55);
        mv.visitJumpInsn(GOTO, label54);
        mv.visitLabel(label50);
        mv.visitLineNumber(66, label50);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(DLOAD, 5);
        mv.visitVarInsn(DLOAD, 10);
        mv.visitInsn(DSUB);
        mv.visitVarInsn(DSTORE, 5);
        Label label56 = new Label();
        mv.visitLabel(label56);
        mv.visitLineNumber(67, label56);
        mv.visitJumpInsn(GOTO, label54);
        mv.visitLabel(label51);
        mv.visitLineNumber(69, label51);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(DLOAD, 5);
        mv.visitVarInsn(DLOAD, 10);
        mv.visitInsn(DMUL);
        mv.visitVarInsn(DSTORE, 5);
        Label label57 = new Label();
        mv.visitLabel(label57);
        mv.visitLineNumber(70, label57);
        mv.visitJumpInsn(GOTO, label54);
        mv.visitLabel(label52);
        mv.visitLineNumber(72, label52);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(DLOAD, 10);
        mv.visitVarInsn(DSTORE, 14);
        Label label58 = new Label();
        mv.visitLabel(label58);
        mv.visitLineNumber(73, label58);
        // start of 'valueDiv' ====================================
        mv.visitVarInsn(DLOAD, 14);
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DCMPL);
        mv.visitJumpInsn(IFNE, label5);
        Label label59 = new Label();
        mv.visitLabel(label59);
        mv.visitLineNumber(74, label59);
        mv.visitFieldInsn(GETSTATIC, "TestClass", "DEFAULT_VALUE", "J");
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        mv.visitLabel(label4);
        mv.visitInsn(ARETURN);
        mv.visitLabel(label5);
        mv.visitLineNumber(76, label5);
        mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.DOUBLE}, 0, null);
        mv.visitVarInsn(DLOAD, 5);
        mv.visitVarInsn(DLOAD, 14);
        mv.visitInsn(DDIV);
        mv.visitVarInsn(DSTORE, 5);
        Label label60 = new Label();
        mv.visitLabel(label60);
        mv.visitLineNumber(77, label60);
        mv.visitJumpInsn(GOTO, label54);
        mv.visitLabel(label53);
        mv.visitLineNumber(79, label53);
        mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
        // **********************
        // Entry to switch case for which 'valueDiv' has not been initialized.
        // Reading slot 14 at this point will create a phi with operands of type long and double.
        // **********************
        mv.visitVarInsn(DLOAD, 10);
        mv.visitVarInsn(DSTORE, 16);
        Label label61 = new Label();
        mv.visitLabel(label61);
        mv.visitLineNumber(80, label61);
        mv.visitVarInsn(DLOAD, 16);
        mv.visitInsn(DCONST_0);
        mv.visitInsn(DCMPL);
        mv.visitJumpInsn(IFNE, label7);
        Label label62 = new Label();
        mv.visitLabel(label62);
        mv.visitLineNumber(81, label62);
        mv.visitFieldInsn(GETSTATIC, "TestClass", "DEFAULT_VALUE", "J");
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        mv.visitLabel(label6);
        mv.visitInsn(ARETURN);
        mv.visitLabel(label7);
        mv.visitLineNumber(83, label7);
        mv.visitFrame(
            Opcodes.F_APPEND, 3, new Object[] {Opcodes.TOP, Opcodes.TOP, Opcodes.DOUBLE}, 0, null);
        mv.visitVarInsn(DLOAD, 5);
        mv.visitVarInsn(DLOAD, 16);
        mv.visitInsn(DREM);
        mv.visitVarInsn(DSTORE, 5);
        // end of local 'valueDiv' ====================================
        mv.visitLabel(label54);
        mv.visitLineNumber(85, label54);
        mv.visitFrame(Opcodes.F_CHOP, 3, null, 0, null);
        mv.visitJumpInsn(GOTO, label48);
        mv.visitLabel(label41);
        mv.visitLineNumber(89, label41);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ILOAD, 3);
        Label label63 = new Label();
        mv.visitJumpInsn(IFNE, label63);
        Label label64 = new Label();
        mv.visitLabel(label64);
        mv.visitLineNumber(90, label64);
        mv.visitVarInsn(LLOAD, 12);
        mv.visitVarInsn(LSTORE, 7);
        mv.visitJumpInsn(GOTO, label48);
        mv.visitLabel(label63);
        mv.visitLineNumber(92, label63);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(ILOAD, 1);
        Label label65 = new Label();
        Label label66 = new Label();
        Label label67 = new Label();
        Label label68 = new Label();
        Label label69 = new Label();
        mv.visitTableSwitchInsn(
            1, 5, label48, new Label[] {label65, label66, label67, label68, label69});
        mv.visitLabel(label65);
        mv.visitLineNumber(94, label65);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(LLOAD, 7);
        mv.visitVarInsn(LLOAD, 12);
        mv.visitInsn(LADD);
        mv.visitVarInsn(LSTORE, 7);
        Label label70 = new Label();
        mv.visitLabel(label70);
        mv.visitLineNumber(95, label70);
        mv.visitJumpInsn(GOTO, label48);
        mv.visitLabel(label66);
        mv.visitLineNumber(97, label66);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(LLOAD, 7);
        mv.visitVarInsn(LLOAD, 12);
        mv.visitInsn(LSUB);
        mv.visitVarInsn(LSTORE, 7);
        Label label71 = new Label();
        mv.visitLabel(label71);
        mv.visitLineNumber(98, label71);
        mv.visitJumpInsn(GOTO, label48);
        mv.visitLabel(label67);
        mv.visitLineNumber(100, label67);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(LLOAD, 7);
        mv.visitVarInsn(LLOAD, 12);
        mv.visitInsn(LMUL);
        mv.visitVarInsn(LSTORE, 7);
        Label label72 = new Label();
        mv.visitLabel(label72);
        mv.visitLineNumber(101, label72);
        mv.visitJumpInsn(GOTO, label48);
        mv.visitLabel(label68);
        mv.visitLineNumber(103, label68);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(LLOAD, 12);
        mv.visitInsn(LCONST_0);
        mv.visitInsn(LCMP);
        mv.visitJumpInsn(IFNE, label9);
        Label label73 = new Label();
        mv.visitLabel(label73);
        mv.visitLineNumber(104, label73);
        mv.visitFieldInsn(GETSTATIC, "TestClass", "DEFAULT_VALUE", "J");
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        mv.visitLabel(label8);
        mv.visitInsn(ARETURN);
        mv.visitLabel(label9);
        mv.visitLineNumber(106, label9);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(LLOAD, 7);
        mv.visitVarInsn(LLOAD, 12);
        mv.visitInsn(LDIV);
        mv.visitVarInsn(LSTORE, 7);
        Label label74 = new Label();
        mv.visitLabel(label74);
        mv.visitLineNumber(107, label74);
        mv.visitJumpInsn(GOTO, label48);
        mv.visitLabel(label69);
        mv.visitLineNumber(109, label69);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(LLOAD, 12);
        mv.visitVarInsn(LSTORE, 14);
        Label label75 = new Label();
        mv.visitLabel(label75);
        mv.visitLineNumber(110, label75);
        mv.visitVarInsn(LLOAD, 14);
        mv.visitInsn(LCONST_0);
        mv.visitInsn(LCMP);
        mv.visitJumpInsn(IFNE, label11);
        Label label76 = new Label();
        mv.visitLabel(label76);
        mv.visitLineNumber(111, label76);
        mv.visitFieldInsn(GETSTATIC, "TestClass", "DEFAULT_VALUE", "J");
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        mv.visitLabel(label10);
        mv.visitInsn(ARETURN);
        mv.visitLabel(label11);
        mv.visitLineNumber(113, label11);
        mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.LONG}, 0, null);
        mv.visitVarInsn(LLOAD, 7);
        mv.visitVarInsn(LLOAD, 14);
        mv.visitInsn(LREM);
        mv.visitVarInsn(LSTORE, 7);
        mv.visitLabel(label48);
        mv.visitLineNumber(118, label48);
        mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
        mv.visitVarInsn(ILOAD, 4);
        mv.visitJumpInsn(IFEQ, label13);
        mv.visitVarInsn(DLOAD, 5);
        mv.visitLdcInsn(new Double("Infinity"));
        mv.visitInsn(DCMPL);
        Label label77 = new Label();
        mv.visitJumpInsn(IFEQ, label77);
        mv.visitVarInsn(DLOAD, 5);
        mv.visitLdcInsn(new Double("-Infinity"));
        mv.visitInsn(DCMPL);
        mv.visitJumpInsn(IFEQ, label77);
        mv.visitLdcInsn(new Double("NaN"));
        mv.visitVarInsn(DLOAD, 5);
        mv.visitInsn(DCMPL);
        mv.visitJumpInsn(IFNE, label13);
        mv.visitLabel(label77);
        mv.visitLineNumber(120, label77);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitInsn(DCONST_0);
        mv.visitMethodInsn(
            INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        mv.visitLabel(label12);
        mv.visitInsn(ARETURN);
        mv.visitLabel(label13);
        mv.visitLineNumber(122, label13);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitIincInsn(3, 1);
        Label label78 = new Label();
        mv.visitLabel(label78);
        mv.visitLineNumber(123, label78);
        mv.visitJumpInsn(GOTO, label22);
        mv.visitLabel(label23);
        mv.visitLineNumber(125, label23);
        mv.visitFrame(Opcodes.F_CHOP, 3, null, 0, null);
        mv.visitVarInsn(ILOAD, 4);
        mv.visitJumpInsn(IFEQ, label15);
        Label label79 = new Label();
        mv.visitLabel(label79);
        mv.visitLineNumber(126, label79);
        mv.visitVarInsn(DLOAD, 5);
        mv.visitMethodInsn(
            INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        mv.visitLabel(label14);
        mv.visitInsn(ARETURN);
        mv.visitLabel(label15);
        mv.visitLineNumber(128, label15);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(LLOAD, 7);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        mv.visitLabel(label16);
        mv.visitInsn(ARETURN);
        mv.visitLabel(label2);
        mv.visitLineNumber(130, label2);
        mv.visitFrame(
            Opcodes.F_FULL,
            2,
            new Object[] {"[Ljava/lang/Object;", Opcodes.INTEGER},
            1,
            new Object[] {"java/lang/Throwable"});
        mv.visitVarInsn(ASTORE, 2);
        Label label80 = new Label();
        mv.visitLabel(label80);
        mv.visitLineNumber(132, label80);
        mv.visitFieldInsn(GETSTATIC, "TestClass", "DEFAULT_VALUE", "J");
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        mv.visitInsn(ARETURN);
        Label label81 = new Label();
        mv.visitLabel(label81);
        mv.visitLocalVariable("numStr", "Ljava/lang/String;", null, label30, label34, 14);
        mv.visitLocalVariable("valueMod", "D", null, label61, label54, 16);
        mv.visitLocalVariable("valueDiv", "D", null, label58, label54, 14);
        mv.visitLocalVariable("valueMod", "J", null, label75, label48, 14);
        mv.visitLocalVariable("num", "Ljava/lang/Object;", null, label25, label78, 9);
        mv.visitLocalVariable("dTmp", "D", null, label26, label78, 10);
        mv.visitLocalVariable("lTmp", "J", null, label27, label78, 12);
        mv.visitLocalVariable("len", "I", null, label18, label2, 2);
        mv.visitLocalVariable("i", "I", null, label19, label2, 3);
        mv.visitLocalVariable("needDouble", "Z", null, label20, label2, 4);
        mv.visitLocalVariable("douRet", "D", null, label21, label2, 5);
        mv.visitLocalVariable("lonRet", "J", null, label22, label2, 7);
        mv.visitLocalVariable("e", "Ljava/lang/Throwable;", null, label80, label80, 2);
        mv.visitLocalVariable("operationList", "[Ljava/lang/Object;", null, label0, label81, 0);
        mv.visitLocalVariable("type", "I", null, label0, label81, 1);
        mv.visitMaxs(4, 18);
        mv.visitEnd();
      }
      {
        mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        Label label0 = new Label();
        mv.visitLabel(label0);
        mv.visitLineNumber(19, label0);
        mv.visitInsn(LCONST_0);
        mv.visitFieldInsn(PUTSTATIC, "TestClass", "DEFAULT_VALUE", "J");
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();
      }
      cw.visitEnd();
      return cw.toByteArray();
    }
  }
}
