// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class EnumMinification extends TestBase {

  private Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public EnumMinification(Backend backend) {
    this.backend = backend;
  }

  private AndroidApp buildApp(Class<?> mainClass, byte[] enumClassFile) throws Exception {
    return ToolHelper.runR8(
        R8Command.builder()
            .addClassProgramData(ToolHelper.getClassAsBytes(mainClass), Origin.unknown())
            .addClassProgramData(enumClassFile, Origin.unknown())
            .addProguardConfiguration(
                ImmutableList.of(keepMainProguardConfiguration(mainClass)), Origin.unknown())
            .setProgramConsumer(emptyConsumer(backend))
            .build());
  }

  public void runTest(
      Class<?> mainClass, byte[] enumClass, String enumTypeName, boolean valueOfKept)
      throws Exception {
    AndroidApp output = buildApp(mainClass, enumClass);

    CodeInspector inspector = new CodeInspector(output);
    ClassSubject clazz = inspector.clazz(enumTypeName);
    // The class and fields - including field $VALUES and method valueOf - can be renamed. Only
    // the values() method needs to be
    assertThat(clazz, isRenamed());
    assertThat(clazz.field(enumTypeName, "VALUE1"), isRenamed());
    assertThat(clazz.field(enumTypeName, "VALUE2"), isRenamed());
    assertThat(clazz.field(enumTypeName + "[]", "$VALUES"), isRenamed());
    assertThat(
        clazz.method(enumTypeName, "valueOf", ImmutableList.of("java.lang.String")),
        valueOfKept ? isRenamed() : not(isPresent()));
    assertThat(clazz.method(enumTypeName + "[]", "values", ImmutableList.of()), not(isRenamed()));

    assertEquals("VALUE1", runOnVM(output, mainClass, backend));
  }

  @Test
  public void test() throws Exception {
    runTest(Main.class, ToolHelper.getClassAsBytes(Enum.class), Enum.class.getTypeName(), true);
  }

  @Test
  public void testAsmDump() throws Exception {
    runTest(Main.class, EnumDump.dump(true), "com.android.tools.r8.naming.Enum", true);
  }

  @Test
  public void testWithoutValuesMethod() throws Exception {
    // This should not fail even if the values method is not present.
    buildApp(Main.class, EnumDump.dump(false));
  }

  @Test
  public void testJavaLangEnumValueOf() throws Exception {
    runTest(Main2.class, ToolHelper.getClassAsBytes(Enum.class), Enum.class.getTypeName(), false);
  }
}

class Main {

  public static void main(String[] args) {
    Enum e = Enum.valueOf("VALUE1");
    System.out.print(e);
  }
}

enum Enum {
  VALUE1,
  VALUE2
}

class Main2 {
  public static void main(String[] args) {
    // Use java.lang.Enum.valueOf instead of com.android.tools.r8.naming.Enum.valueOf.
    System.out.print(java.lang.Enum.valueOf(Enum.class, "VALUE1"));
  }
}
/* Dump of javac generated code from the following enum class (the one just above):
 *
 *  package com.android.tools.r8.naming;
 *
 *  enum Enum {
 *    VALUE1,
 *    VALUE2
 *  }
 *
 */
class EnumDump implements Opcodes {

  public static byte[] dump(boolean includeValuesMethod) {
    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;

    classWriter.visit(
        V1_8,
        ACC_FINAL | ACC_SUPER | ACC_ENUM,
        "com/android/tools/r8/naming/Enum",
        "Ljava/lang/Enum<Lcom/android/tools/r8/naming/Enum;>;",
        "java/lang/Enum",
        null);

    classWriter.visitSource("EnumMinification.java", null);

    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC | ACC_ENUM,
              "VALUE1",
              "Lcom/android/tools/r8/naming/Enum;",
              null,
              null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC | ACC_ENUM,
              "VALUE2",
              "Lcom/android/tools/r8/naming/Enum;",
              null,
              null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PRIVATE | ACC_FINAL | ACC_STATIC | ACC_SYNTHETIC,
              "$VALUES",
              "[Lcom/android/tools/r8/naming/Enum;",
              null,
              null);
      fieldVisitor.visitEnd();
    }
    if (includeValuesMethod) {
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                "values",
                "()[Lcom/android/tools/r8/naming/Enum;",
                null,
                null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(72, label0);
        methodVisitor.visitFieldInsn(
            GETSTATIC,
            "com/android/tools/r8/naming/Enum",
            "$VALUES",
            "[Lcom/android/tools/r8/naming/Enum;");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "[Lcom/android/tools/r8/naming/Enum;",
            "clone",
            "()Ljava/lang/Object;",
            false);
        methodVisitor.visitTypeInsn(CHECKCAST, "[Lcom/android/tools/r8/naming/Enum;");
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitMaxs(1, 0);
        methodVisitor.visitEnd();
      }
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC,
              "valueOf",
              "(Ljava/lang/String;)Lcom/android/tools/r8/naming/Enum;",
              null,
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(72, label0);
      methodVisitor.visitLdcInsn(Type.getType("Lcom/android/tools/r8/naming/Enum;"));
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "java/lang/Enum",
          "valueOf",
          "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;",
          false);
      methodVisitor.visitTypeInsn(CHECKCAST, "com/android/tools/r8/naming/Enum");
      methodVisitor.visitInsn(ARETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label1, 0);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", "()V", null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(72, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
      methodVisitor.visitInsn(RETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable(
          "this", "Lcom/android/tools/r8/naming/Enum;", null, label0, label1, 0);
      methodVisitor.visitMaxs(3, 3);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(73, label0);
      methodVisitor.visitTypeInsn(NEW, "com/android/tools/r8/naming/Enum");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitLdcInsn("VALUE1");
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "com/android/tools/r8/naming/Enum",
          "<init>",
          "(Ljava/lang/String;I)V",
          false);
      methodVisitor.visitFieldInsn(
          PUTSTATIC,
          "com/android/tools/r8/naming/Enum",
          "VALUE1",
          "Lcom/android/tools/r8/naming/Enum;");
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(74, label1);
      methodVisitor.visitTypeInsn(NEW, "com/android/tools/r8/naming/Enum");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitLdcInsn("VALUE2");
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "com/android/tools/r8/naming/Enum",
          "<init>",
          "(Ljava/lang/String;I)V",
          false);
      methodVisitor.visitFieldInsn(
          PUTSTATIC,
          "com/android/tools/r8/naming/Enum",
          "VALUE2",
          "Lcom/android/tools/r8/naming/Enum;");
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(72, label2);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitTypeInsn(ANEWARRAY, "com/android/tools/r8/naming/Enum");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          "com/android/tools/r8/naming/Enum",
          "VALUE1",
          "Lcom/android/tools/r8/naming/Enum;");
      methodVisitor.visitInsn(AASTORE);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          "com/android/tools/r8/naming/Enum",
          "VALUE2",
          "Lcom/android/tools/r8/naming/Enum;");
      methodVisitor.visitInsn(AASTORE);
      methodVisitor.visitFieldInsn(
          PUTSTATIC,
          "com/android/tools/r8/naming/Enum",
          "$VALUES",
          "[Lcom/android/tools/r8/naming/Enum;");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(4, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
