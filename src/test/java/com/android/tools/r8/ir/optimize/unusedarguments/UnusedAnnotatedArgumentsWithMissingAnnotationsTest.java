// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class UnusedAnnotatedArgumentsWithMissingAnnotationsTest extends TestBase
    implements Opcodes {

  private final boolean enableProguardCompatibilityMode;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, compat: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public UnusedAnnotatedArgumentsWithMissingAnnotationsTest(
      boolean enableProguardCompatibilityMode, TestParameters parameters) {
    this.enableProguardCompatibilityMode = enableProguardCompatibilityMode;
    this.parameters = parameters;
  }

  private void checkClass(ClassSubject clazz, String expectedAnnotationClass) {
    assertThat(clazz, isPresent());
    MethodSubject init = clazz.init("Test", "java.lang.String");
    assertThat(init, isPresent());
    if (enableProguardCompatibilityMode) {
      assertEquals(2, init.getMethod().getParameterAnnotations().size());
      assertTrue(init.getMethod().getParameterAnnotation(0).isEmpty());
      assertEquals(1, init.getMethod().getParameterAnnotation(1).size());
      assertEquals(
          "L" + expectedAnnotationClass + ";",
          init.getMethod()
              .getParameterAnnotation(1)
              .annotations[0]
              .annotation
              .type
              .toDescriptorString());
    } else {
      assertTrue(init.getMethod().getParameterAnnotations().isEmpty());
    }
  }

  @Test
  public void test() throws Exception {
    String expectedOutput =
        StringUtils.lines("In Inner1() used", "In Inner2() used", "In Inner3() used", "In main()");
    testForR8Compat(parameters.getBackend(), enableProguardCompatibilityMode)
        .addProgramClassFileData(
            dumpTest(),
            dumpInner1(),
            dumpInner2(),
            dumpInner3(),
            dumpAnnotation1(),
            dumpAnnotation2(),
            dumpAnnotation3())
        .addKeepMainRule("Test")
        .addKeepRules(
            "-keep @interface Annotation?",
            "-neverclassinline class *",
            "-nohorizontalclassmerging class Test$Inner?",
            "-keepclassmembers class Test$Inner? { synthetic <fields>; }",
            "-keepconstantarguments class Test$Inner? { void <init>(...); }")
        .addKeepRuntimeVisibleParameterAnnotations()
        .enableProguardTestOptions()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              checkClass(inspector.clazz("Test$Inner1"), "Annotation1");
              checkClass(inspector.clazz("Test$Inner2"), "Annotation2");
              checkClass(inspector.clazz("Test$Inner2"), "Annotation2");
            })
        .run(parameters.getRuntime(), "Test")
        .assertSuccessWithOutput(expectedOutput);
  }

  /*
   The dump is from Java 8 javac, and the dump is used to ensure that the input has parameter
   annotations, where the number of annotations are less than the actual number of arguments.
   Newer version of javac might change to include the synthetic argument in the in the list of
   parameter annotations, so just using a java class could end up making the test no longer test
   this case.

   Dump of the following:

   File Test.java:

     public class Test {
       public class Inner1 {
         Inner1(@Annotation1 String used, @Annotation2 String unused) {
             System.out.println("In Inner1() " + used);
         }

       }

       public class Inner2 {
         Inner2(@Annotation1 String unused, @Annotation2 String used) {
             System.out.println("In Inner2() " + used);
         }

       }

       public class Inner3 {
         Inner3(
             @Annotation1 String unused1, @Annotation2 String used, @Annotation3 String unused2) {
             System.out.println("In Inner3() " + used);
         }

       }

       public Test() {
         new Inner1("used", null);
         new Inner2(null, "used");
         new Inner3(null, "used", null);
       }

       public static void main(String[] args) {
         new Test();
         System.out.println("In main()");
       }
     }

   File Annotation1.java:

     import java.lang.annotation.ElementType;
     import java.lang.annotation.Retention;
     import java.lang.annotation.RetentionPolicy;
     import java.lang.annotation.Target;

     @Retention(RetentionPolicy.RUNTIME)
     @Target(ElementType.PARAMETER)
     public @interface Annotation1 {}

   File Annotation2.java:

     import java.lang.annotation.ElementType;
     import java.lang.annotation.Retention;
     import java.lang.annotation.RetentionPolicy;
     import java.lang.annotation.Target;

     @Retention(RetentionPolicy.RUNTIME)
     @Target(ElementType.PARAMETER)
     public @interface Annotation2 {}

   File Annotation3.java:

     import java.lang.annotation.ElementType;
     import java.lang.annotation.Retention;
     import java.lang.annotation.RetentionPolicy;
     import java.lang.annotation.Target;

     @Retention(RetentionPolicy.RUNTIME)
     @Target(ElementType.PARAMETER)
     public @interface Annotation3 {}
  */

  static byte[] dumpTest() {
    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "Test", null, "java/lang/Object", null);

    classWriter.visitSource("Test.java", null);

    classWriter.visitInnerClass("Test$Inner3", "Test", "Inner3", ACC_PUBLIC);

    classWriter.visitInnerClass("Test$Inner2", "Test", "Inner2", ACC_PUBLIC);

    classWriter.visitInnerClass("Test$Inner1", "Test", "Inner1", ACC_PUBLIC);

    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitTypeInsn(NEW, "Test$Inner1");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitLdcInsn("used");
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "Test$Inner1",
          "<init>",
          "(LTest;Ljava/lang/String;Ljava/lang/String;)V",
          false);
      methodVisitor.visitInsn(POP);
      methodVisitor.visitTypeInsn(NEW, "Test$Inner2");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitLdcInsn("used");
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "Test$Inner2",
          "<init>",
          "(LTest;Ljava/lang/String;Ljava/lang/String;)V",
          false);
      methodVisitor.visitInsn(POP);
      methodVisitor.visitTypeInsn(NEW, "Test$Inner3");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitLdcInsn("used");
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "Test$Inner3",
          "<init>",
          "(LTest;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
          false);
      methodVisitor.visitInsn(POP);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(6, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitTypeInsn(NEW, "Test");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Test", "<init>", "()V", false);
      methodVisitor.visitInsn(POP);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("In main()");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  static byte[] dumpInner1() {
    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "Test$Inner1", null, "java/lang/Object", null);

    classWriter.visitSource("Test.java", null);

    classWriter.visitInnerClass("Test$Inner1", "Test", "Inner1", ACC_PUBLIC);

    {
      fieldVisitor =
          classWriter.visitField(ACC_FINAL | ACC_SYNTHETIC, "this$0", "LTest;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              0, "<init>", "(LTest;Ljava/lang/String;Ljava/lang/String;)V", null, null);
      methodVisitor.visitAnnotableParameterCount(2, true);
      {
        annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "LAnnotation1;", true);
        annotationVisitor0.visitEnd();
      }
      {
        annotationVisitor0 = methodVisitor.visitParameterAnnotation(1, "LAnnotation2;", true);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitFieldInsn(PUTFIELD, "Test$Inner1", "this$0", "LTest;");
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      methodVisitor.visitLdcInsn("In Inner1() ");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 4);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  static byte[] dumpInner2() {
    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "Test$Inner2", null, "java/lang/Object", null);

    classWriter.visitSource("Test.java", null);

    classWriter.visitInnerClass("Test$Inner2", "Test", "Inner2", ACC_PUBLIC);

    {
      fieldVisitor =
          classWriter.visitField(ACC_FINAL | ACC_SYNTHETIC, "this$0", "LTest;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              0, "<init>", "(LTest;Ljava/lang/String;Ljava/lang/String;)V", null, null);
      methodVisitor.visitAnnotableParameterCount(2, true);
      {
        annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "LAnnotation1;", true);
        annotationVisitor0.visitEnd();
      }
      {
        annotationVisitor0 = methodVisitor.visitParameterAnnotation(1, "LAnnotation2;", true);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitFieldInsn(PUTFIELD, "Test$Inner2", "this$0", "LTest;");
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      methodVisitor.visitLdcInsn("In Inner2() ");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 4);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  static byte[] dumpInner3() {
    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "Test$Inner3", null, "java/lang/Object", null);

    classWriter.visitSource("Test.java", null);

    classWriter.visitInnerClass("Test$Inner3", "Test", "Inner3", ACC_PUBLIC);

    {
      fieldVisitor =
          classWriter.visitField(ACC_FINAL | ACC_SYNTHETIC, "this$0", "LTest;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              0,
              "<init>",
              "(LTest;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
              null,
              null);
      methodVisitor.visitAnnotableParameterCount(3, true);
      {
        annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "LAnnotation1;", true);
        annotationVisitor0.visitEnd();
      }
      {
        annotationVisitor0 = methodVisitor.visitParameterAnnotation(1, "LAnnotation2;", true);
        annotationVisitor0.visitEnd();
      }
      {
        annotationVisitor0 = methodVisitor.visitParameterAnnotation(2, "LAnnotation3;", true);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitFieldInsn(PUTFIELD, "Test$Inner3", "this$0", "LTest;");
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      methodVisitor.visitLdcInsn("In Inner3() ");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 5);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  static byte[] dumpAnnotation1() {
    ClassWriter classWriter = new ClassWriter(0);
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_ANNOTATION | ACC_ABSTRACT | ACC_INTERFACE,
        "Annotation1",
        null,
        "java/lang/Object",
        new String[] {"java/lang/annotation/Annotation"});

    classWriter.visitSource("Annotation1.java", null);

    {
      annotationVisitor0 = classWriter.visitAnnotation("Ljava/lang/annotation/Retention;", true);
      annotationVisitor0.visitEnum("value", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME");
      annotationVisitor0.visitEnd();
    }
    {
      annotationVisitor0 = classWriter.visitAnnotation("Ljava/lang/annotation/Target;", true);
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("value");
        annotationVisitor1.visitEnum(null, "Ljava/lang/annotation/ElementType;", "PARAMETER");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  static byte[] dumpAnnotation2() {
    ClassWriter classWriter = new ClassWriter(0);
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_ANNOTATION | ACC_ABSTRACT | ACC_INTERFACE,
        "Annotation2",
        null,
        "java/lang/Object",
        new String[] {"java/lang/annotation/Annotation"});

    classWriter.visitSource("Annotation2.java", null);

    {
      annotationVisitor0 = classWriter.visitAnnotation("Ljava/lang/annotation/Retention;", true);
      annotationVisitor0.visitEnum("value", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME");
      annotationVisitor0.visitEnd();
    }
    {
      annotationVisitor0 = classWriter.visitAnnotation("Ljava/lang/annotation/Target;", true);
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("value");
        annotationVisitor1.visitEnum(null, "Ljava/lang/annotation/ElementType;", "PARAMETER");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  static byte[] dumpAnnotation3() {
    ClassWriter classWriter = new ClassWriter(0);
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_ANNOTATION | ACC_ABSTRACT | ACC_INTERFACE,
        "Annotation3",
        null,
        "java/lang/Object",
        new String[] {"java/lang/annotation/Annotation"});

    classWriter.visitSource("Annotation3.java", null);

    {
      annotationVisitor0 = classWriter.visitAnnotation("Ljava/lang/annotation/Retention;", true);
      annotationVisitor0.visitEnum("value", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME");
      annotationVisitor0.visitEnd();
    }
    {
      annotationVisitor0 = classWriter.visitAnnotation("Ljava/lang/annotation/Target;", true);
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("value");
        annotationVisitor1.visitEnum(null, "Ljava/lang/annotation/ElementType;", "PARAMETER");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
