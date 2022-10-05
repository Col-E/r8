// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyTopLevelFlags;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class RetargetAndBackportTest extends DesugaredLibraryTestBase implements Opcodes {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntime(Version.DEFAULT).withCfRuntime(CfVm.JDK11).build(),
        LibraryDesugaringSpecification.getJdk8Jdk11());
  }

  public RetargetAndBackportTest(
      TestParameters parameters, LibraryDesugaringSpecification libraryDesugaringSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  /**
   * Add this library desugaring configuration:
   * "library_flags": [
   *  {
   *    "rewrite_prefix":{"java.time.": "j$.time."},
   *    "backport": {"java.lang.DesugarMath": "java.lang.Math"},
   *  }
   * ],
   */
  private static void specifyDesugaredLibrary(InternalOptions options) {
    LegacyRewritingFlags rewritingFlags =
        LegacyRewritingFlags.builder(options.itemFactory, options.reporter, Origin.unknown())
            .putRewritePrefix("java.time.", "j$.time.")
            .putBackportCoreLibraryMember("java.lang.DesugarMath", "java.lang.Math")
            .build();
    options.setDesugaredLibrarySpecification(
        new LegacyDesugaredLibrarySpecification(
            LegacyTopLevelFlags.testing(), rewritingFlags, true));
  }

  @Test
  public void test() throws Exception {
    testForL8(AndroidApiLevel.B, parameters.getBackend())
        .addProgramClassFileData(dump())
        .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
        .setDesugaredLibrarySpecification(libraryDesugaringSpecification.getSpecification())
        .addOptionsModifier(RetargetAndBackportTest::specifyDesugaredLibrary)
        .compile()
        .inspect(
            inspector -> {
              assertTrue(
                  inspector
                      .clazz("j$.time.Duration")
                      .uniqueMethodWithOriginalName("toMillis")
                      .streamInstructions()
                      .filter(InstructionSubject::isInvokeStatic)
                      .map(InstructionSubject::toString)
                      .allMatch(s -> s.contains("Backport")));
            });
  }

  // Dump of java.time.Duration from JDK 11 based desugared library input built from
  // https://github.com/google/desugar_jdk_libs/commit/a2a0a26c06cbee9144eceaefd8c65e1bae2b611c.
  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(
        V11,
        ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
        "java/time/Duration",
        "Ljava/lang/Object;Ljava/time/temporal/TemporalAmount;Ljava/lang/Comparable<Ljava/time/Duration;>;Ljava/io/Serializable;",
        "java/lang/Object",
        new String[] {
          "java/time/temporal/TemporalAmount", "java/lang/Comparable", "java/io/Serializable"
        });

    classWriter.visitSource("Duration.java", null);

    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "toMillis", "()J", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(1217, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD, "java/time/Duration", "seconds", "J");
      methodVisitor.visitVarInsn(LSTORE, 1);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(1218, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD, "java/time/Duration", "nanos", "I");
      methodVisitor.visitInsn(I2L);
      methodVisitor.visitVarInsn(LSTORE, 3);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(1219, label2);
      methodVisitor.visitVarInsn(LLOAD, 1);
      methodVisitor.visitInsn(LCONST_0);
      methodVisitor.visitInsn(LCMP);
      Label label3 = new Label();
      methodVisitor.visitJumpInsn(IFGE, label3);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(1222, label4);
      methodVisitor.visitVarInsn(LLOAD, 1);
      methodVisitor.visitInsn(LCONST_1);
      methodVisitor.visitInsn(LADD);
      methodVisitor.visitVarInsn(LSTORE, 1);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(1223, label5);
      methodVisitor.visitVarInsn(LLOAD, 3);
      methodVisitor.visitLdcInsn(new Long(1000000000L));
      methodVisitor.visitInsn(LSUB);
      methodVisitor.visitVarInsn(LSTORE, 3);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(1225, label3);
      methodVisitor.visitFrame(
          Opcodes.F_APPEND, 2, new Object[] {Opcodes.LONG, Opcodes.LONG}, 0, null);
      methodVisitor.visitVarInsn(LLOAD, 1);
      methodVisitor.visitIntInsn(SIPUSH, 1000);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/lang/DesugarMath", "multiplyExact", "(JI)J", false);
      methodVisitor.visitVarInsn(LSTORE, 5);
      Label label6 = new Label();
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(1226, label6);
      methodVisitor.visitVarInsn(LLOAD, 5);
      methodVisitor.visitVarInsn(LLOAD, 3);
      methodVisitor.visitLdcInsn(new Long(1000000L));
      methodVisitor.visitInsn(LDIV);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/lang/DesugarMath", "addExact", "(JJ)J", false);
      methodVisitor.visitVarInsn(LSTORE, 5);
      Label label7 = new Label();
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(1227, label7);
      methodVisitor.visitVarInsn(LLOAD, 5);
      methodVisitor.visitInsn(LRETURN);
      Label label8 = new Label();
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLocalVariable("this", "Ljava/time/Duration;", null, label0, label8, 0);
      methodVisitor.visitLocalVariable("tempSeconds", "J", null, label1, label8, 1);
      methodVisitor.visitLocalVariable("tempNanos", "J", null, label2, label8, 3);
      methodVisitor.visitLocalVariable("millis", "J", null, label6, label8, 5);
      methodVisitor.visitMaxs(6, 7);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
