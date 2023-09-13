// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations.b137392797;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_3_72;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class B137392797 extends TestBase implements Opcodes {

  private final TestParameters parameters;
  private final boolean defaultEnumValueInAnnotation;

  @Parameterized.Parameters(name = "{0}, default value in annotation: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public B137392797(TestParameters parameters, boolean defaultEnumValueInAnnotation) {
    this.parameters = parameters;
    this.defaultEnumValueInAnnotation = defaultEnumValueInAnnotation;
  }

  private void checkEnumUses(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz("com.squareup.wire.WireField$Label");
    assertThat(classSubject, isPresent());
    // Only 2 of the 5 enum values are actually used:
    //   * REQUIRED: annotation for Test.field1
    //   * OPTIONAL: default value of WireField.label
    // When generating class file the field values[] is also present as values() is kept.
    assertEquals(parameters.isCfRuntime() ? 3 : 2, classSubject.allFields().size());
    // Methods <clinit>, <init> always present. values() present if generating class file.
    assertEquals(
        (parameters.isCfRuntime() ? 3 : 2)
            - BooleanUtils.intValue(parameters.canInitNewInstanceUsingSuperclassConstructor()),
        classSubject.allMethods().size());
  }

  @Test
  public void testR8() throws Exception {
    KotlinCompiler compiler = KOTLINC_1_3_72.getCompiler();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            classWireField(defaultEnumValueInAnnotation),
            classWireFieldLabel(),
            classTest(defaultEnumValueInAnnotation))
        .addProgramClasses(TestClass.class)
        .addClasspathFiles(compiler.getKotlinStdlibJar(), compiler.getKotlinAnnotationJar())
        .addKeepClassAndMembersRules(
            "com.squareup.wire.WireField", "com.squareup.demo.myapplication.Test")
        .addKeepMainRule(TestClass.class)
        .addKeepAttributes("*Annotation*")
        .applyIf(
            parameters.isCfRuntime(),
            builder ->
                // When parsing the enum default value, the JVM tries to find the enum with the
                // given name, but after shrinking the enum field names and the enum instance names
                // no longer match.
                builder.addKeepRules(
                    "-keepclassmembers,allowshrinking class com.squareup.wire.WireField$Label {",
                    "  static com.squareup.wire.WireField$Label OPTIONAL;",
                    "}"))
        .setMinApi(parameters)
        .addOptionsModification(
            options -> {
              // The default limit for LIR is 2 at time of writing.
              // The constructor inlining check needs a limit of 4 to trigger.
              options.inlinerOptions().simpleInliningInstructionLimit = 4;
            })
        .compile()
        .inspect(this::checkEnumUses)
        .run(parameters.getRuntime(), TestClass.class, "com.squareup.demo.myapplication.Test")
        .assertSuccessWithOutputLines(
            "1", "com.squareup.wire.WireField", "1", "com.squareup.wire.WireField");
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      // Create an instance of the Test data class.
      Class.forName(args[0]).getConstructor(String.class, String.class).newInstance("Test", "Test");
      // Check the annotations on the fields can be read.
      System.out.println(Class.forName(args[0]).getField("field1").getAnnotations().length);
      System.out.println(
          Class.forName(args[0]).getField("field1").getAnnotations()[0].annotationType().getName());
      System.out.println(Class.forName(args[0]).getField("field2").getAnnotations().length);
      System.out.println(
          Class.forName(args[0]).getField("field2").getAnnotations()[0].annotationType().getName());
    }
  }

  /*
   ASM code below is for the following Kotlin code - slightly modified as described in comments.

   @Target(AnnotationTarget.FIELD)
   @Retention(AnnotationRetention.RUNTIME)
   annotation class WireField(
     val tag: Int,
     val keyAdapter: String = "",
     val adapter: String,
     val label: Label = Label.OPTIONAL,
     val redacted: Boolean = false) {

     enum class Label {
       REQUIRED,
       OPTIONAL,
       REPEATED,
       ONE_OF,
       PACKED;

       val isRepeated: Boolean
           @JvmName("isRepeated") get() = this == REPEATED || this == PACKED

       val isPacked: Boolean
           @JvmName("isPacked") get() = this == PACKED

       val isOneOf: Boolean
           @JvmName("isOneOf") get() = this == ONE_OF
       }
   }

   data class Test(
     @field:WireField(
       tag = 1,
       adapter = "com.squareup.wire.ProtoAdapter#STRING",
       label = WireField.Label.REQUIRED)
     @JvmField
     val field1: String,
     @field:WireField(
       tag = 1,
       adapter = "com.squareup.wire.ProtoAdapter#STRING")
     @JvmField
     val field2: String
   )

  */
  public static byte[] classWireField(boolean defaultEnumValueInAnnotation) {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_6,
        ACC_PUBLIC | ACC_ANNOTATION | ACC_ABSTRACT | ACC_INTERFACE,
        "com/squareup/wire/WireField",
        null,
        "java/lang/Object",
        new String[] {"java/lang/annotation/Annotation"});

    classWriter.visitSource("WireField.kt", null);

    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/annotation/Target;", true);
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("allowedTargets");
        annotationVisitor1.visitEnum(null, "Lkotlin/annotation/AnnotationTarget;", "FIELD");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/annotation/Retention;", true);
      annotationVisitor0.visitEnum("value", "Lkotlin/annotation/AnnotationRetention;", "RUNTIME");
      annotationVisitor0.visitEnd();
    }
    {
      annotationVisitor0 = classWriter.visitAnnotation("Ljava/lang/annotation/Retention;", true);
      annotationVisitor0.visitEnum("value", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME");
      annotationVisitor0.visitEnd();
    }
    {
      annotationVisitor0 = classWriter.visitAnnotation("Ljava/lang/annotation/Target;", true);
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("value");
        annotationVisitor1.visitEnum(null, "Ljava/lang/annotation/ElementType;", "FIELD");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    /*
    Annotation kotlin.Metadata removed.
    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      ...
    }
    */
    classWriter.visitInnerClass(
        "com/squareup/wire/WireField$Label",
        "com/squareup/wire/WireField",
        "Label",
        ACC_PUBLIC | ACC_FINAL | ACC_STATIC | ACC_ENUM);

    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, "tag", "()I", null, null);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_ABSTRACT, "keyAdapter", "()Ljava/lang/String;", null, null);
      {
        annotationVisitor0 = methodVisitor.visitAnnotationDefault();
        annotationVisitor0.visit(null, "");
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_ABSTRACT, "adapter", "()Ljava/lang/String;", null, null);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_ABSTRACT,
              "label",
              "()Lcom/squareup/wire/WireField$Label;",
              null,
              null);
      {
        if (defaultEnumValueInAnnotation) {
          annotationVisitor0 = methodVisitor.visitAnnotationDefault();
          annotationVisitor0.visitEnum(null, "Lcom/squareup/wire/WireField$Label;", "OPTIONAL");
          annotationVisitor0.visitEnd();
        }
      }
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, "redacted", "()Z", null, null);
      {
        annotationVisitor0 = methodVisitor.visitAnnotationDefault();
        annotationVisitor0.visit(null, Boolean.FALSE);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] classWireFieldLabel() {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_6,
        ACC_PUBLIC | ACC_FINAL | ACC_SUPER | ACC_ENUM,
        "com/squareup/wire/WireField$Label",
        "Ljava/lang/Enum<Lcom/squareup/wire/WireField$Label;>;",
        "java/lang/Enum",
        null);

    classWriter.visitSource("WireField.kt", null);

    /*
    Annotation kotlin.Metadata removed.
    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      ...
    }
    */
    classWriter.visitInnerClass(
        "com/squareup/wire/WireField$Label",
        "com/squareup/wire/WireField",
        "Label",
        ACC_PUBLIC | ACC_FINAL | ACC_STATIC | ACC_ENUM);

    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC | ACC_ENUM,
              "REQUIRED",
              "Lcom/squareup/wire/WireField$Label;",
              null,
              null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC | ACC_ENUM,
              "OPTIONAL",
              "Lcom/squareup/wire/WireField$Label;",
              null,
              null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC | ACC_ENUM,
              "REPEATED",
              "Lcom/squareup/wire/WireField$Label;",
              null,
              null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC | ACC_ENUM,
              "ONE_OF",
              "Lcom/squareup/wire/WireField$Label;",
              null,
              null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC | ACC_ENUM,
              "PACKED",
              "Lcom/squareup/wire/WireField$Label;",
              null,
              null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PRIVATE | ACC_FINAL | ACC_STATIC | ACC_SYNTHETIC,
              "$VALUES",
              "[Lcom/squareup/wire/WireField$Label;",
              null,
              null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitInsn(ICONST_5);
      methodVisitor.visitTypeInsn(ANEWARRAY, "com/squareup/wire/WireField$Label");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitTypeInsn(NEW, "com/squareup/wire/WireField$Label");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitLdcInsn("REQUIRED");
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "com/squareup/wire/WireField$Label",
          "<init>",
          "(Ljava/lang/String;I)V",
          false);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitFieldInsn(
          PUTSTATIC,
          "com/squareup/wire/WireField$Label",
          "REQUIRED",
          "Lcom/squareup/wire/WireField$Label;");
      methodVisitor.visitInsn(AASTORE);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitTypeInsn(NEW, "com/squareup/wire/WireField$Label");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitLdcInsn("OPTIONAL");
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "com/squareup/wire/WireField$Label",
          "<init>",
          "(Ljava/lang/String;I)V",
          false);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitFieldInsn(
          PUTSTATIC,
          "com/squareup/wire/WireField$Label",
          "OPTIONAL",
          "Lcom/squareup/wire/WireField$Label;");
      methodVisitor.visitInsn(AASTORE);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitTypeInsn(NEW, "com/squareup/wire/WireField$Label");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitLdcInsn("REPEATED");
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "com/squareup/wire/WireField$Label",
          "<init>",
          "(Ljava/lang/String;I)V",
          false);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitFieldInsn(
          PUTSTATIC,
          "com/squareup/wire/WireField$Label",
          "REPEATED",
          "Lcom/squareup/wire/WireField$Label;");
      methodVisitor.visitInsn(AASTORE);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitInsn(ICONST_3);
      methodVisitor.visitTypeInsn(NEW, "com/squareup/wire/WireField$Label");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitLdcInsn("ONE_OF");
      methodVisitor.visitInsn(ICONST_3);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "com/squareup/wire/WireField$Label",
          "<init>",
          "(Ljava/lang/String;I)V",
          false);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitFieldInsn(
          PUTSTATIC,
          "com/squareup/wire/WireField$Label",
          "ONE_OF",
          "Lcom/squareup/wire/WireField$Label;");
      methodVisitor.visitInsn(AASTORE);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitInsn(ICONST_4);
      methodVisitor.visitTypeInsn(NEW, "com/squareup/wire/WireField$Label");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitLdcInsn("PACKED");
      methodVisitor.visitInsn(ICONST_4);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "com/squareup/wire/WireField$Label",
          "<init>",
          "(Ljava/lang/String;I)V",
          false);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitFieldInsn(
          PUTSTATIC,
          "com/squareup/wire/WireField$Label",
          "PACKED",
          "Lcom/squareup/wire/WireField$Label;");
      methodVisitor.visitInsn(AASTORE);
      methodVisitor.visitFieldInsn(
          PUTSTATIC,
          "com/squareup/wire/WireField$Label",
          "$VALUES",
          "[Lcom/squareup/wire/WireField$Label;");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(8, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "isRepeated", "()Z", null, null);
      {
        annotationVisitor0 = methodVisitor.visitAnnotation("Lkotlin/jvm/JvmName;", false);
        annotationVisitor0.visit("name", "isRepeated");
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(60, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitTypeInsn(CHECKCAST, "com/squareup/wire/WireField$Label");
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          "com/squareup/wire/WireField$Label",
          "REPEATED",
          "Lcom/squareup/wire/WireField$Label;");
      Label label1 = new Label();
      methodVisitor.visitJumpInsn(IF_ACMPEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitTypeInsn(CHECKCAST, "com/squareup/wire/WireField$Label");
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          "com/squareup/wire/WireField$Label",
          "PACKED",
          "Lcom/squareup/wire/WireField$Label;");
      Label label2 = new Label();
      methodVisitor.visitJumpInsn(IF_ACMPNE, label2);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(ICONST_1);
      Label label3 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label3);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {Opcodes.INTEGER});
      methodVisitor.visitInsn(IRETURN);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLocalVariable(
          "this", "Lcom/squareup/wire/WireField$Label;", null, label0, label4, 0);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "isPacked", "()Z", null, null);
      {
        annotationVisitor0 = methodVisitor.visitAnnotation("Lkotlin/jvm/JvmName;", false);
        annotationVisitor0.visit("name", "isPacked");
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(63, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitTypeInsn(CHECKCAST, "com/squareup/wire/WireField$Label");
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          "com/squareup/wire/WireField$Label",
          "PACKED",
          "Lcom/squareup/wire/WireField$Label;");
      Label label1 = new Label();
      methodVisitor.visitJumpInsn(IF_ACMPNE, label1);
      methodVisitor.visitInsn(ICONST_1);
      Label label2 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label2);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {Opcodes.INTEGER});
      methodVisitor.visitInsn(IRETURN);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLocalVariable(
          "this", "Lcom/squareup/wire/WireField$Label;", null, label0, label3, 0);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "isOneOf", "()Z", null, null);
      {
        annotationVisitor0 = methodVisitor.visitAnnotation("Lkotlin/jvm/JvmName;", false);
        annotationVisitor0.visit("name", "isOneOf");
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(66, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitTypeInsn(CHECKCAST, "com/squareup/wire/WireField$Label");
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          "com/squareup/wire/WireField$Label",
          "ONE_OF",
          "Lcom/squareup/wire/WireField$Label;");
      Label label1 = new Label();
      methodVisitor.visitJumpInsn(IF_ACMPNE, label1);
      methodVisitor.visitInsn(ICONST_1);
      Label label2 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label2);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {Opcodes.INTEGER});
      methodVisitor.visitInsn(IRETURN);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLocalVariable(
          "this", "Lcom/squareup/wire/WireField$Label;", null, label0, label3, 0);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", "()V", null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(51, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
      methodVisitor.visitInsn(RETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable(
          "this", "Lcom/squareup/wire/WireField$Label;", null, label0, label1, 0);
      methodVisitor.visitLocalVariable(
          "$enum_name_or_ordinal$0", "Ljava/lang/String;", null, label0, label1, 1);
      methodVisitor.visitLocalVariable("$enum_name_or_ordinal$1", "I", null, label0, label1, 2);
      methodVisitor.visitMaxs(3, 3);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC,
              "values",
              "()[Lcom/squareup/wire/WireField$Label;",
              null,
              null);
      methodVisitor.visitCode();
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          "com/squareup/wire/WireField$Label",
          "$VALUES",
          "[Lcom/squareup/wire/WireField$Label;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "[Lcom/squareup/wire/WireField$Label;",
          "clone",
          "()Ljava/lang/Object;",
          false);
      methodVisitor.visitTypeInsn(CHECKCAST, "[Lcom/squareup/wire/WireField$Label;");
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC,
              "valueOf",
              "(Ljava/lang/String;)Lcom/squareup/wire/WireField$Label;",
              null,
              null);
      methodVisitor.visitCode();
      methodVisitor.visitLdcInsn(Type.getType("Lcom/squareup/wire/WireField$Label;"));
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "java/lang/Enum",
          "valueOf",
          "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;",
          false);
      methodVisitor.visitTypeInsn(CHECKCAST, "com/squareup/wire/WireField$Label");
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] classTest(boolean defaultEnumValueInAnnotation) {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_6,
        ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
        "com/squareup/demo/myapplication/Test",
        null,
        "java/lang/Object",
        null);

    classWriter.visitSource("Test.kt", null);

    /*
    Annotation kotlin.Metadata removed.
    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      ...
    }
    */
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_FINAL, "field1", "Ljava/lang/String;", null, null);
      {
        annotationVisitor0 = fieldVisitor.visitAnnotation("Lcom/squareup/wire/WireField;", true);
        annotationVisitor0.visit("tag", new Integer(1));
        annotationVisitor0.visit("adapter", "com.squareup.wire.ProtoAdapter#STRING");
        annotationVisitor0.visitEnum("label", "Lcom/squareup/wire/WireField$Label;", "REQUIRED");
        annotationVisitor0.visitEnd();
      }
      {
        annotationVisitor0 = fieldVisitor.visitAnnotation("Lkotlin/jvm/JvmField;", false);
        annotationVisitor0.visitEnd();
      }
      {
        annotationVisitor0 =
            fieldVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_FINAL, "field2", "Ljava/lang/String;", null, null);
      {
        annotationVisitor0 = fieldVisitor.visitAnnotation("Lcom/squareup/wire/WireField;", true);
        annotationVisitor0.visit("tag", new Integer(1));
        annotationVisitor0.visit("adapter", "com.squareup.wire.ProtoAdapter#STRING");
        if (!defaultEnumValueInAnnotation) {
          annotationVisitor0.visitEnum("label", "Lcom/squareup/wire/WireField$Label;", "OPTIONAL");
        }
        annotationVisitor0.visitEnd();
      }
      {
        annotationVisitor0 = fieldVisitor.visitAnnotation("Lkotlin/jvm/JvmField;", false);
        annotationVisitor0.visitEnd();
      }
      {
        annotationVisitor0 =
            fieldVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", null, null);
      methodVisitor.visitAnnotableParameterCount(2, false);
      {
        annotationVisitor0 =
            methodVisitor.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      {
        annotationVisitor0 =
            methodVisitor.visitParameterAnnotation(1, "Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      // Removed use of kotlin.jvm.internal.Intrinsics.
      // methodVisitor.visitLabel(label0);
      // methodVisitor.visitVarInsn(ALOAD, 1);
      // methodVisitor.visitLdcInsn("field1");
      // methodVisitor.visitMethodInsn(INVOKESTATIC, "kotlin/jvm/internal/Intrinsics",
      //     "checkParameterIsNotNull", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
      // methodVisitor.visitVarInsn(ALOAD, 2);
      // methodVisitor.visitLdcInsn("field2");
      // methodVisitor.visitMethodInsn(INVOKESTATIC, "kotlin/jvm/internal/Intrinsics",
      //     "checkParameterIsNotNull", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(5, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitFieldInsn(
          PUTFIELD, "com/squareup/demo/myapplication/Test", "field1", "Ljava/lang/String;");
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitFieldInsn(
          PUTFIELD, "com/squareup/demo/myapplication/Test", "field2", "Ljava/lang/String;");
      methodVisitor.visitInsn(RETURN);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLocalVariable(
          "this", "Lcom/squareup/demo/myapplication/Test;", null, label0, label2, 0);
      methodVisitor.visitLocalVariable("field1", "Ljava/lang/String;", null, label0, label2, 1);
      methodVisitor.visitLocalVariable("field2", "Ljava/lang/String;", null, label0, label2, 2);
      methodVisitor.visitMaxs(2, 3);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_FINAL, "component1", "()Ljava/lang/String;", null, null);
      {
        annotationVisitor0 =
            methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/squareup/demo/myapplication/Test", "field1", "Ljava/lang/String;");
      methodVisitor.visitInsn(ARETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable(
          "this", "Lcom/squareup/demo/myapplication/Test;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_FINAL, "component2", "()Ljava/lang/String;", null, null);
      {
        annotationVisitor0 =
            methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/squareup/demo/myapplication/Test", "field2", "Ljava/lang/String;");
      methodVisitor.visitInsn(ARETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable(
          "this", "Lcom/squareup/demo/myapplication/Test;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_FINAL,
              "copy",
              "(Ljava/lang/String;Ljava/lang/String;)Lcom/squareup/demo/myapplication/Test;",
              null,
              null);
      {
        annotationVisitor0 =
            methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitAnnotableParameterCount(2, false);
      {
        annotationVisitor0 =
            methodVisitor.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      {
        annotationVisitor0 =
            methodVisitor.visitParameterAnnotation(1, "Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitLdcInsn("field1");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "kotlin/jvm/internal/Intrinsics",
          "checkParameterIsNotNull",
          "(Ljava/lang/Object;Ljava/lang/String;)V",
          false);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitLdcInsn("field2");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "kotlin/jvm/internal/Intrinsics",
          "checkParameterIsNotNull",
          "(Ljava/lang/Object;Ljava/lang/String;)V",
          false);
      methodVisitor.visitTypeInsn(NEW, "com/squareup/demo/myapplication/Test");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "com/squareup/demo/myapplication/Test",
          "<init>",
          "(Ljava/lang/String;Ljava/lang/String;)V",
          false);
      methodVisitor.visitInsn(ARETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable(
          "this", "Lcom/squareup/demo/myapplication/Test;", null, label0, label1, 0);
      methodVisitor.visitLocalVariable("field1", "Ljava/lang/String;", null, label0, label1, 1);
      methodVisitor.visitLocalVariable("field2", "Ljava/lang/String;", null, label0, label1, 2);
      methodVisitor.visitMaxs(4, 3);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC,
              "copy$default",
              "(Lcom/squareup/demo/myapplication/Test;"
                  + "Ljava/lang/String;Ljava/lang/String;"
                  + "ILjava/lang/Object;)Lcom/squareup/demo/myapplication/Test;",
              null,
              null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ILOAD, 3);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitInsn(IAND);
      Label label0 = new Label();
      methodVisitor.visitJumpInsn(IFEQ, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/squareup/demo/myapplication/Test", "field1", "Ljava/lang/String;");
      methodVisitor.visitVarInsn(ASTORE, 1);
      methodVisitor.visitLabel(label0);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ILOAD, 3);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(IAND);
      Label label1 = new Label();
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/squareup/demo/myapplication/Test", "field2", "Ljava/lang/String;");
      methodVisitor.visitVarInsn(ASTORE, 2);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "com/squareup/demo/myapplication/Test",
          "copy",
          "(Ljava/lang/String;Ljava/lang/String;)Lcom/squareup/demo/myapplication/Test;",
          false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(3, 5);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
      {
        annotationVisitor0 =
            methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      methodVisitor.visitLdcInsn("Test(field1=");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/squareup/demo/myapplication/Test", "field1", "Ljava/lang/String;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitLdcInsn(", field2=");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/squareup/demo/myapplication/Test", "field2", "Ljava/lang/String;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitLdcInsn(")");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "hashCode", "()I", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/squareup/demo/myapplication/Test", "field1", "Ljava/lang/String;");
      methodVisitor.visitInsn(DUP);
      Label label0 = new Label();
      methodVisitor.visitJumpInsn(IFNULL, label0);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "hashCode", "()I", false);
      Label label1 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label1);
      methodVisitor.visitLabel(label0);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/String"});
      methodVisitor.visitInsn(POP);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {Opcodes.INTEGER});
      methodVisitor.visitIntInsn(BIPUSH, 31);
      methodVisitor.visitInsn(IMUL);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/squareup/demo/myapplication/Test", "field2", "Ljava/lang/String;");
      methodVisitor.visitInsn(DUP);
      Label label2 = new Label();
      methodVisitor.visitJumpInsn(IFNULL, label2);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "hashCode", "()I", false);
      Label label3 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label3);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"com/squareup/demo/myapplication/Test"},
          2,
          new Object[] {Opcodes.INTEGER, "java/lang/String"});
      methodVisitor.visitInsn(POP);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"com/squareup/demo/myapplication/Test"},
          2,
          new Object[] {Opcodes.INTEGER, Opcodes.INTEGER});
      methodVisitor.visitInsn(IADD);
      methodVisitor.visitInsn(IRETURN);
      methodVisitor.visitMaxs(3, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC, "equals", "(Ljava/lang/Object;)Z", null, null);
      methodVisitor.visitAnnotableParameterCount(1, false);
      {
        annotationVisitor0 =
            methodVisitor.visitParameterAnnotation(
                0, "Lorg/jetbrains/annotations/Nullable;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      Label label0 = new Label();
      methodVisitor.visitJumpInsn(IF_ACMPEQ, label0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitTypeInsn(INSTANCEOF, "com/squareup/demo/myapplication/Test");
      Label label1 = new Label();
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitTypeInsn(CHECKCAST, "com/squareup/demo/myapplication/Test");
      methodVisitor.visitVarInsn(ASTORE, 2);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/squareup/demo/myapplication/Test", "field1", "Ljava/lang/String;");
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/squareup/demo/myapplication/Test", "field1", "Ljava/lang/String;");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "kotlin/jvm/internal/Intrinsics",
          "areEqual",
          "(Ljava/lang/Object;Ljava/lang/Object;)Z",
          false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/squareup/demo/myapplication/Test", "field2", "Ljava/lang/String;");
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/squareup/demo/myapplication/Test", "field2", "Ljava/lang/String;");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "kotlin/jvm/internal/Intrinsics",
          "areEqual",
          "(Ljava/lang/Object;Ljava/lang/Object;)Z",
          false);
      methodVisitor.visitJumpInsn(IFEQ, label1);
      methodVisitor.visitLabel(label0);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitInsn(IRETURN);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitInsn(IRETURN);
      methodVisitor.visitMaxs(2, 3);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
