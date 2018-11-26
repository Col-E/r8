// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.invokesuper.Consumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class MinifierClassSignatureTest extends TestBase {
  /*

  class Simple {
  }
  class Base<T> {
  }
  class Outer<T> {
    class Inner {
      class InnerInner {
      }
      class ExtendsInnerInner extends InnerInner {
      }
    }
    class ExtendsInner extends Inner {
    }
  }

  */

  String baseSignature = "<T:Ljava/lang/Object;>Ljava/lang/Object;";
  String outerSignature = "<T:Ljava/lang/Object;>Ljava/lang/Object;";
  String extendsInnerSignature = "LOuter<TT;>.Inner;";
  String extendsInnerInnerSignature = "LOuter<TT;>.Inner.InnerInner;";
  private Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public MinifierClassSignatureTest(Backend backend) {
    this.backend = backend;
  }

  private byte[] dumpSimple(String classSignature) throws Exception {

    ClassWriter cw = new ClassWriter(0);
    MethodVisitor mv;

    String signature = classSignature;
    cw.visit(V1_8, ACC_SUPER, "Simple", signature, "java/lang/Object", null);

    {
      mv = cw.visitMethod(0, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  private byte[] dumpBase(String classSignature) throws Exception {

    final String javacClassSignature = baseSignature;
    ClassWriter cw = new ClassWriter(0);
    MethodVisitor mv;

    String signature = classSignature != null ? classSignature : javacClassSignature;
    cw.visit(V1_8, ACC_SUPER, "Base", signature, "java/lang/Object", null);

    {
      mv = cw.visitMethod(0, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }


  private byte[] dumpOuter(String classSignature) {

    final String javacClassSignature = outerSignature;
    ClassWriter cw = new ClassWriter(0);
    MethodVisitor mv;

    String signature = classSignature != null ? classSignature : javacClassSignature;
    cw.visit(V1_8, ACC_SUPER, "Outer", signature, "java/lang/Object", null);

    cw.visitInnerClass("Outer$ExtendsInner", "Outer", "ExtendsInner", 0);

    cw.visitInnerClass("Outer$Inner", "Outer", "Inner", 0);

    {
      mv = cw.visitMethod(0, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  private byte[] dumpInner(String classSignature) {

    final String javacClassSignature = null;
    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;

    String signature = classSignature != null ? classSignature : javacClassSignature;
    cw.visit(V1_8, ACC_SUPER, "Outer$Inner", signature, "java/lang/Object", null);

    cw.visitInnerClass("Outer$Inner", "Outer", "Inner", 0);

    cw.visitInnerClass("Outer$Inner$ExtendsInnerInner", "Outer$Inner", "ExtendsInnerInner", 0);

    cw.visitInnerClass("Outer$Inner$InnerInner", "Outer$Inner", "InnerInner", 0);

    {
      fv = cw.visitField(ACC_FINAL + ACC_SYNTHETIC, "this$0", "LOuter;", null, null);
      fv.visitEnd();
    }
    {
      mv = cw.visitMethod(0, "<init>", "(LOuter;)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitFieldInsn(PUTFIELD, "Outer$Inner", "this$0", "LOuter;");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 2);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  private byte[] dumpExtendsInner(String classSignature) throws Exception {

    final String javacClassSignature = extendsInnerSignature;
    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;

    String signature = classSignature != null ? classSignature : javacClassSignature;
    cw.visit(V1_8, ACC_SUPER, "Outer$ExtendsInner", signature, "Outer$Inner", null);

    cw.visitInnerClass("Outer$Inner", "Outer", "Inner", 0);

    cw.visitInnerClass("Outer$ExtendsInner", "Outer", "ExtendsInner", 0);

    {
      fv = cw.visitField(ACC_FINAL + ACC_SYNTHETIC, "this$0", "LOuter;", null, null);
      fv.visitEnd();
    }
    {
      mv = cw.visitMethod(0, "<init>", "(LOuter;)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitFieldInsn(PUTFIELD, "Outer$ExtendsInner", "this$0", "LOuter;");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitMethodInsn(INVOKESPECIAL, "Outer$Inner", "<init>", "(LOuter;)V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 2);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  private byte[] dumpInnerInner(String classSignature) throws Exception {

    final String javacClassSignature = null;
    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;

    String signature = classSignature != null ? classSignature : javacClassSignature;
    cw.visit(V1_8, ACC_SUPER, "Outer$Inner$InnerInner", signature, "java/lang/Object", null);

    cw.visitInnerClass("Outer$Inner", "Outer", "Inner", 0);

    cw.visitInnerClass("Outer$Inner$InnerInner", "Outer$Inner", "InnerInner", 0);

    {
      fv = cw.visitField(ACC_FINAL + ACC_SYNTHETIC, "this$1", "LOuter$Inner;", null, null);
      fv.visitEnd();
    }
    {
      mv = cw.visitMethod(0, "<init>", "(LOuter$Inner;)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitFieldInsn(PUTFIELD, "Outer$Inner$InnerInner", "this$1", "LOuter$Inner;");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 2);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  private byte[] dumpExtendsInnerInner(String classSignature) throws Exception {

    final String javacClassSignature = extendsInnerInnerSignature;
    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;

    String signature = classSignature != null ? classSignature : javacClassSignature;
    cw.visit(V1_8, ACC_SUPER, "Outer$Inner$ExtendsInnerInner", signature, "Outer$Inner$InnerInner",
        null);

    cw.visitInnerClass("Outer$Inner", "Outer", "Inner", 0);

    cw.visitInnerClass("Outer$Inner$InnerInner", "Outer$Inner", "InnerInner", 0);

    cw.visitInnerClass("Outer$Inner$ExtendsInnerInner", "Outer$Inner", "ExtendsInnerInner", 0);

    {
      fv = cw.visitField(ACC_FINAL + ACC_SYNTHETIC, "this$1", "LOuter$Inner;", null, null);
      fv.visitEnd();
    }
    {
      mv = cw.visitMethod(0, "<init>", "(LOuter$Inner;)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitFieldInsn(PUTFIELD, "Outer$Inner$ExtendsInnerInner", "this$1", "LOuter$Inner;");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitMethodInsn(INVOKESPECIAL, "Outer$Inner$InnerInner", "<init>", "(LOuter$Inner;)V",
          false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 2);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  public void runTest(
      ImmutableMap<String, String> signatures,
      Consumer<DiagnosticsChecker> diagnostics,
      Consumer<CodeInspector> inspect)
      throws Exception {
    DiagnosticsChecker checker = new DiagnosticsChecker();
    CodeInspector inspector =
        new CodeInspector(
            ToolHelper.runR8(
                R8Command.builder(checker)
                    .addClassProgramData(dumpSimple(signatures.get("Simple")), Origin.unknown())
                    .addClassProgramData(dumpBase(signatures.get("Base")), Origin.unknown())
                    .addClassProgramData(dumpOuter(signatures.get("Outer")), Origin.unknown())
                    .addClassProgramData(dumpInner(signatures.get("Outer$Inner")), Origin.unknown())
                    .addClassProgramData(
                        dumpExtendsInner(signatures.get("Outer$ExtendsInner")), Origin.unknown())
                    .addClassProgramData(
                        dumpInnerInner(signatures.get("Outer$Inner$InnerInner")), Origin.unknown())
                    .addClassProgramData(
                        dumpExtendsInnerInner(signatures.get("Outer$Inner$ExtendsInnerInner")),
                        Origin.unknown())
                    .addProguardConfiguration(
                        ImmutableList.of(
                            "-keepattributes InnerClasses,EnclosingMethod,Signature",
                            "-keep,allowobfuscation class **"),
                        Origin.unknown())
                    .setProgramConsumer(emptyConsumer(backend))
                    .addLibraryFiles(runtimeJar(backend))
                    .setProguardMapConsumer(StringConsumer.emptyConsumer())
                    .build()));
    // All classes are kept, and renamed.
    assertThat(inspector.clazz("Simple"), isRenamed());
    assertThat(inspector.clazz("Base"), isRenamed());
    assertThat(inspector.clazz("Outer"), isRenamed());
    assertThat(inspector.clazz("Outer$Inner"), isRenamed());
    assertThat(inspector.clazz("Outer$ExtendsInner"), isRenamed());
    assertThat(inspector.clazz("Outer$Inner$InnerInner"), isRenamed());
    assertThat(inspector.clazz("Outer$Inner$ExtendsInnerInner"), isRenamed());

    // Test that classes with have their original signature if the default was provided.
    if (!signatures.containsKey("Simple")) {
      assertNull(inspector.clazz("Simple").getOriginalSignatureAttribute());
    }
    if (!signatures.containsKey("Base")) {
      assertEquals(baseSignature, inspector.clazz("Base").getOriginalSignatureAttribute());
    }
    if (!signatures.containsKey("Outer")) {
      assertEquals(outerSignature, inspector.clazz("Outer").getOriginalSignatureAttribute());
    }
    if (!signatures.containsKey("Outer$Inner")) {
      assertNull(inspector.clazz("Outer$Inner").getOriginalSignatureAttribute());
    }
    if (!signatures.containsKey("Outer$ExtendsInner")) {
      assertEquals(extendsInnerSignature,
          inspector.clazz("Outer$ExtendsInner").getOriginalSignatureAttribute());
    }
    if (!signatures.containsKey("Outer$Inner$InnerInner")) {
      assertNull(inspector.clazz("Outer$Inner$InnerInner").getOriginalSignatureAttribute());
    }
    if (!signatures.containsKey("Outer$Inner$ExtendsInnerInner")) {
      assertEquals(extendsInnerInnerSignature,
          inspector.clazz("Outer$Inner$ExtendsInnerInner").getOriginalSignatureAttribute());
    }

    diagnostics.accept(checker);
    inspect.accept(inspector);
  }

  private void testSingleClass(String name, String signature,
      Consumer<DiagnosticsChecker> diagnostics,
      Consumer<CodeInspector> inspector)
      throws Exception {
    ImmutableMap<String, String> signatures = ImmutableMap.of(name, signature);
    runTest(signatures, diagnostics, inspector);
  }

  private void isOriginUnknown(Origin origin) {
    assertSame(Origin.unknown(), origin);
  }

  private void noWarnings(DiagnosticsChecker checker) {
    assertEquals(0, checker.warnings.size());
  }

  private void noInspection(CodeInspector inspector) {
  }

  private void noSignatureAttribute(ClassSubject clazz) {
    assertNull(clazz.getFinalSignatureAttribute());
    assertNull(clazz.getOriginalSignatureAttribute());
  }

  @Test
  public void originalJavacSignatures() throws Exception {
    // Test using the signatures generated by javac.
    runTest(ImmutableMap.of(), this::noWarnings, this::noInspection);
  }

  @Test
  public void classSignature_empty() throws Exception {
    testSingleClass("Outer", "", this::noWarnings, inspector -> {
      ClassSubject outer = inspector.clazz("Outer");
      assertNull(outer.getFinalSignatureAttribute());
      assertNull(outer.getOriginalSignatureAttribute());
    });
  }

  @Test
  public void classSignatureOuter_valid() throws Exception {
    // class Outer<T extends Simple> extends Base<T>
    String signature = "<T:LSimple;>LBase<TT;>;";
    testSingleClass("Outer", signature, this::noWarnings, inspector -> {
      ClassSubject outer = inspector.clazz("Outer");
      ClassSubject simple = inspector.clazz("Simple");
      ClassSubject base = inspector.clazz("Base");
      String baseDescriptorWithoutSemicolon =
          base.getFinalDescriptor().substring(0, base.getFinalDescriptor().length() - 1);
      String minifiedSignature =
          "<T:" +  simple.getFinalDescriptor() + ">" + baseDescriptorWithoutSemicolon + "<TT;>;";
      assertEquals(minifiedSignature, outer.getFinalSignatureAttribute());
      assertEquals(signature, outer.getOriginalSignatureAttribute());
    });
  }

  @Test
  public void classSignatureExtendsInner_valid() throws Exception {
    String signature = "LOuter<TT;>.Inner;";
    testSingleClass("Outer$ExtendsInner", signature, this::noWarnings, inspector -> {
      ClassSubject extendsInner = inspector.clazz("Outer$ExtendsInner");
      ClassSubject outer = inspector.clazz("Outer");
      ClassSubject inner = inspector.clazz("Outer$Inner");
      String outerDescriptorWithoutSemicolon =
          outer.getFinalDescriptor().substring(0, outer.getFinalDescriptor().length() - 1);
      String innerFinalDescriptor = inner.getFinalDescriptor();
      String innerLastPart =
          innerFinalDescriptor.substring(innerFinalDescriptor.indexOf("$") + 1);
      String minifiedSignature = outerDescriptorWithoutSemicolon + "<TT;>." + innerLastPart;
      assertEquals(minifiedSignature, extendsInner.getFinalSignatureAttribute());
      assertEquals(signature, extendsInner.getOriginalSignatureAttribute());
    });
  }

  @Test
  public void classSignatureOuter_classNotFound() throws Exception {
    String signature = "<T:LNotFound;>LAlsoNotFound;";
    testSingleClass("Outer", signature, this::noWarnings, inspector -> {
      assertThat(inspector.clazz("NotFound"), not(isPresent()));
      ClassSubject outer = inspector.clazz("Outer");
      assertEquals(signature, outer.getOriginalSignatureAttribute());
    });
  }

  @Test
  public void classSignatureExtendsInner_innerClassNotFound() throws Exception {
    String signature = "LOuter<TT;>.NotFound;";
    testSingleClass("Outer$ExtendsInner", signature, this::noWarnings, inspector -> {
      assertThat(inspector.clazz("NotFound"), not(isPresent()));
      ClassSubject outer = inspector.clazz("Outer$ExtendsInner");
      assertEquals(signature, outer.getOriginalSignatureAttribute());
    });
  }

  @Test
  public void classSignatureExtendsInner_outerAndInnerClassNotFound() throws Exception {
    String signature = "LNotFound<TT;>.AlsoNotFound;";
    testSingleClass("Outer$ExtendsInner", signature, this::noWarnings, inspector -> {
      assertThat(inspector.clazz("NotFound"), not(isPresent()));
      ClassSubject outer = inspector.clazz("Outer$ExtendsInner");
      assertEquals(signature, outer.getOriginalSignatureAttribute());
    });
  }

  @Test
  public void classSignatureExtendsInner_nestedInnerClassNotFound() throws Exception {
    String signature = "LOuter<TT;>.Inner.NotFound;";
    testSingleClass("Outer$ExtendsInner", signature, this::noWarnings, inspector -> {
      assertThat(inspector.clazz("NotFound"), not(isPresent()));
      ClassSubject outer = inspector.clazz("Outer$ExtendsInner");
      assertEquals(signature, outer.getOriginalSignatureAttribute());
    });
  }

  @Test
  public void classSignatureExtendsInner_multipleMestedInnerClassesNotFound() throws Exception {
    String signature = "LOuter<TT;>.NotFound.AlsoNotFound;";
    testSingleClass("Outer$ExtendsInner", signature, this::noWarnings, inspector -> {
      assertThat(inspector.clazz("NotFound"), not(isPresent()));
      ClassSubject outer = inspector.clazz("Outer$ExtendsInner");
      assertEquals(signature, outer.getOriginalSignatureAttribute());
    });
  }

  @Test
  public void classSignatureOuter_invalid() throws Exception {
    testSingleClass("Outer", "X", diagnostics -> {
      assertEquals(1, diagnostics.warnings.size());
      DiagnosticsChecker.checkDiagnostic(diagnostics.warnings.get(0), this::isOriginUnknown,
          "Invalid signature 'X' for class Outer", "Expected L at position 1");
    }, inspector -> noSignatureAttribute(inspector.clazz("Outer")));
  }

  @Test
  public void classSignatureOuter_invalidEnd() throws Exception {
    testSingleClass("Outer", "<L", diagnostics -> {
      assertEquals(1, diagnostics.warnings.size());
      DiagnosticsChecker.checkDiagnostic(diagnostics.warnings.get(0), this::isOriginUnknown,
          "Invalid signature '<L' for class Outer", "Unexpected end of signature at position 3");
    }, inspector -> noSignatureAttribute(inspector.clazz("Outer")));
  }

  @Test
  public void classSignatureExtendsInner_invalid() throws Exception {
    testSingleClass("Outer$ExtendsInner", "X", diagnostics -> {
      assertEquals(1, diagnostics.warnings.size());
      DiagnosticsChecker.checkDiagnostic(diagnostics.warnings.get(0), this::isOriginUnknown,
          "Invalid signature 'X' for class Outer$ExtendsInner", "Expected L at position 1");
    }, inspector -> noSignatureAttribute(inspector.clazz("Outer$ExtendsInner")));
  }

  @Test
  public void classSignatureExtendsInnerInner_invalid() throws Exception {
    testSingleClass("Outer$Inner$ExtendsInnerInner", "X", diagnostics -> {
      assertEquals(1, diagnostics.warnings.size());
      DiagnosticsChecker.checkDiagnostic(diagnostics.warnings.get(0), this::isOriginUnknown,
          "Invalid signature 'X' for class Outer$Inner$ExtendsInnerInner",
          "Expected L at position 1");
    }, inspector -> noSignatureAttribute(inspector.clazz("Outer$Inner$ExtendsInnerInner")));
  }

  @Test
  public void multipleWarnings() throws Exception {
    runTest(ImmutableMap.of(
        "Outer", "X",
        "Outer$ExtendsInner", "X",
        "Outer$Inner$ExtendsInnerInner", "X"), diagnostics -> {
      assertEquals(3, diagnostics.warnings.size());
    }, inspector -> {
      noSignatureAttribute(inspector.clazz("Outer"));
      noSignatureAttribute(inspector.clazz("Outer$ExtendsInner"));
      noSignatureAttribute(inspector.clazz("Outer$Inner$ExtendsInnerInner"));
    });
  }
  @Test
  public void regress80029761() throws Exception {
    String signature = "LOuter<TT;>.com/example/Inner;";
    testSingleClass("Outer$ExtendsInner", signature, diagnostics -> {
      assertEquals(1, diagnostics.warnings.size());
      DiagnosticsChecker.checkDiagnostic(
          diagnostics.warnings.get(0),
          this::isOriginUnknown,
          "Invalid signature '" + signature + "' for class Outer$ExtendsInner",
          "Expected ; at position 16");
    }, inspector -> {
      noSignatureAttribute(inspector.clazz("Outer$ExtendsInner"));
    });
  }
}
