// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.arraytypes;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class ArrayTypesTest extends TestBase {

  private final TestParameters parameters;

  private static String packageName;
  private static String arrayBaseTypeDescriptor;
  private static String arrayTypeDescriptor;
  private static String generatedTestClassName;
  private static String expectedOutput;

  public ArrayTypesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void setup() {
    packageName = ArrayTypesTest.class.getPackage().getName();
    arrayBaseTypeDescriptor =
        DescriptorUtils.getDescriptorFromClassBinaryName(
            DescriptorUtils.getBinaryNameFromJavaType(
                A.class.getTypeName()));
    arrayTypeDescriptor = "[" + arrayBaseTypeDescriptor;
    generatedTestClassName = packageName + "." + "GeneratedTestClass";
    expectedOutput = StringUtils.lines(
        "javac code:",
        "Length: EQ",
        "Hashcode: EQ",
        "toString: EQ",
        "ASM code:",
        "Clones: NE",
        "Hashcode: EQ",
        "Compare with Object: true",
        "Compare with array type: true",
        "toString: true",
        "Got expected exception",
        "Done");
  }

  private void runR8Test(boolean enableMinification) throws Exception {
    testForR8(parameters.getBackend())
        .minification(enableMinification)
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(generateTestClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep class " + generatedTestClassName + " { test(...); }")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void testR8() throws Exception {
    runR8Test(true);
  }

  @Test
  public void testR8NoMinification() throws Exception {
    runR8Test(false);
  }

  @Test
  public void testR8ApplyMapping() throws Exception {
    // Rename the array type (keep it in the same package for access reasons).
    Path mappingFile = temp.newFile("mapping.txt").toPath();
    FileUtils.writeTextFile(
        mappingFile,
        StringUtils.lines(
            A.class.getTypeName() + " -> " + packageName + ".a:"));

    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(generateTestClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-applymapping " + mappingFile.toAbsolutePath())
        .addDontObfuscate()
        .noTreeShaking()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(generateTestClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  public static byte[] generateTestClass() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor mv;

    classWriter.visit(
        Opcodes.V1_6,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM,
        DescriptorUtils.getBinaryNameFromJavaType(generatedTestClassName),
        null,
        "java/lang/Object",
        null);

    {
      mv = classWriter.visitMethod(
          Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
          "test", "(" + arrayTypeDescriptor + ")V",
          null,
          null);

      mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitLdcInsn("ASM code:");
      mv.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);

      // Invoke clone using both java.lang.Object and array type an holder.
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, "java/lang/Object", "clone", "()Ljava/lang/Object;", false);
      mv.visitVarInsn(Opcodes.ASTORE, 1);

      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, arrayTypeDescriptor, "clone", "()Ljava/lang/Object;", false);
      mv.visitVarInsn(Opcodes.ASTORE, 2);

      printCompareObjectsIdentical("Clones: ", mv, (mvCopy) -> {
        assert mv == mvCopy;
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
      });

      printCompareIntegers(
          "Hashcode: ",
          mv,
          (mvCopy) -> {
            assert mv == mvCopy;
            // Invoke hashCode using both java.lang.Object and array type an holder.
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "hashCode", "()I", false);

            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL, arrayTypeDescriptor, "hashCode", "()I", false);
          });

      printBoolean("Compare with Object: ", mv, (mvCopy) -> {
        assert mv == mvCopy;
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      });

      printBoolean("Compare with array type: ", mv, (mvCopy) -> {
        assert mv == mvCopy;
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL, arrayTypeDescriptor, "equals", "(Ljava/lang/Object;)Z", false);
      });

      printBoolean("toString: ", mv, (mvCopy) -> {
        assert mv == mvCopy;
        // Invoke toString using both java.lang.Object and array type an holder.
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL, arrayTypeDescriptor, "toString", "()Ljava/lang/String;", false);

        // Compare the toString strings.
        mv.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
      });

      // Finally invoke a method not present on an array type.
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, arrayTypeDescriptor, "notPresent", "()Ljava/lang/String;", false);

      mv.visitInsn(Opcodes.RETURN);
      mv.visitMaxs(10, 10);
      mv.visitEnd();
    }

    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static void printCompareIntegers(
      String header, MethodVisitor mv, Consumer<MethodVisitor> consumer) {
    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitLdcInsn(header);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);

    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");

    consumer.accept(mv);

    Label neLabel = new Label();
    Label printLabel = new Label();
    mv.visitJumpInsn(Opcodes.IF_ICMPNE, neLabel);
    mv.visitLdcInsn("EQ");
    mv.visitJumpInsn(Opcodes.GOTO, printLabel);
    mv.visitLabel(neLabel);
    mv.visitLdcInsn("NE");
    mv.visitLabel(printLabel);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
  }

  public static void printCompareObjectsIdentical(
      String header, MethodVisitor mv, Consumer<MethodVisitor> consumer) {
    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitLdcInsn(header);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);

    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");

    consumer.accept(mv);

    Label neLabel = new Label();
    Label printLabel = new Label();
    mv.visitJumpInsn(Opcodes.IF_ACMPNE, neLabel);
    mv.visitLdcInsn("EQ");
    mv.visitJumpInsn(Opcodes.GOTO, printLabel);
    mv.visitLabel(neLabel);
    mv.visitLdcInsn("NE");
    mv.visitLabel(printLabel);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
  }

  public static void printBoolean(
      String header, MethodVisitor mv, Consumer<MethodVisitor> consumer) {
    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitLdcInsn(header);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);

    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");

    consumer.accept(mv);

    Label neLabel = new Label();
    Label printLabel = new Label();
    mv.visitJumpInsn(Opcodes.IFNE, neLabel);
    mv.visitLdcInsn("false");
    mv.visitJumpInsn(Opcodes.GOTO, printLabel);
    mv.visitLabel(neLabel);
    mv.visitLdcInsn("true");
    mv.visitLabel(printLabel);
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
  }
}

class A {

}

class Main {

  public static void main(String[] args) throws Exception {
    A[] array = new A[2];
    A[] clone = array.clone();

    System.out.println("javac code:");

    if (array.length == clone.length) {
      System.out.println("Length: EQ");
    }

    if (array.hashCode() == array.hashCode()) {
      System.out.println("Hashcode: EQ");
    }

    if (array.toString().equals(array.toString())) {
      System.out.println("toString: EQ");
    }

    Class<?> generatedTestClass =
        Class.forName(Main.class.getPackage().getName() + "." + "GeneratedTestClass");
    Method testMethod = generatedTestClass.getDeclaredMethod("test", array.getClass());
    try {
      testMethod.invoke(null, new Object[]{array});
    } catch (java.lang.reflect.InvocationTargetException e) {
      System.out.println("Got expected exception");
    }
    System.out.println("Done");
  }
}
