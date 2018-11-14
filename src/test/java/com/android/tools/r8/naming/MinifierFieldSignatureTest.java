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
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
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
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class MinifierFieldSignatureTest extends TestBase {
  /*

  class Fields<X extends String> {
    class Inner {
    }
    public X anX;
    public X[] anArrayOfX;
    public Fields<X> aFieldsOfX;
    public Fields<X>.Inner aFieldsOfXInner;
  }

  */

  private String anXSignature = "TX;";
  private String anArrayOfXSignature = "[TX;";
  private String aFieldsOfXSignature = "LFields<TX;>;";
  private String aFieldsOfXInnerSignature = "LFields<TX;>.Inner;";
  private Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public MinifierFieldSignatureTest(Backend backend) {
    this.backend = backend;
  }

  public byte[] dumpFields(Map<String, String> signatures) throws Exception {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    String signature;

    cw.visit(V1_8, ACC_SUPER, "Fields", "<X:Ljava/lang/String;>Ljava/lang/Object;",
        "java/lang/Object", null);

    cw.visitInnerClass("Fields$Inner", "Fields", "Inner", 0);

    {
      signature = signatures.get("anX");
      signature = signature == null ? anXSignature : signature;
      fv = cw.visitField(ACC_PUBLIC, "anX", "Ljava/lang/String;", signature, null);
      fv.visitEnd();
    }
    {
      signature = signatures.get("anArrayOfX");
      signature = signature == null ? anArrayOfXSignature : signature;
      fv = cw.visitField(
          ACC_PUBLIC, "anArrayOfX", "[Ljava/lang/String;", signature, null);
      fv.visitEnd();
    }
    {
      signature = signatures.get("aFieldsOfX");
      signature = signature == null ? aFieldsOfXSignature : signature;
      fv = cw.visitField(ACC_PUBLIC, "aFieldsOfX", "LFields;", signature, null);
      fv.visitEnd();
    }
    {
      signature = signatures.get("aFieldsOfXInner");
      signature = signature == null ? aFieldsOfXInnerSignature : signature;
      fv = cw.visitField(
          ACC_PUBLIC, "aFieldsOfXInner", "LFields$Inner;", signature, null);
      fv.visitEnd();
    }
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

  public byte[] dumpInner() throws Exception {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;

    cw.visit(V1_8, ACC_SUPER, "Fields$Inner", null, "java/lang/Object", null);

    cw.visitInnerClass("Fields$Inner", "Fields", "Inner", 0);

    {
      fv = cw.visitField(ACC_FINAL + ACC_SYNTHETIC, "this$0", "LFields;", null, null);
      fv.visitEnd();
    }
    {
      mv = cw.visitMethod(0, "<init>", "(LFields;)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitFieldInsn(PUTFIELD, "Fields$Inner", "this$0", "LFields;");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 2);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  private FieldSubject lookupAnX(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("Fields");
    return clazz.field("java.lang.String", "anX");
  }

  private FieldSubject lookupAnArrayOfX(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("Fields");
    return clazz.field("java.lang.String[]", "anArrayOfX");
  }

  private FieldSubject lookupAFieldsOfX(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("Fields");
    return clazz.field("Fields", "aFieldsOfX");
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
                    .addClassProgramData(dumpFields(signatures), Origin.unknown())
                    .addClassProgramData(dumpInner(), Origin.unknown())
                    .addProguardConfiguration(
                        ImmutableList.of(
                            "-keepattributes InnerClasses,EnclosingMethod,Signature",
                            "-keep,allowobfuscation class ** { *; }"),
                        Origin.unknown())
                    .setProgramConsumer(emptyConsumer(backend))
                    .addLibraryFiles(runtimeJar(backend))
                    .setProguardMapConsumer(StringConsumer.emptyConsumer())
                    .build(),
                options -> {
                  options.testing.suppressExperimentalCfBackendWarning = true;
                }));
    // All classes are kept, and renamed.
    ClassSubject clazz = inspector.clazz("Fields");
    assertThat(clazz, isRenamed());
    assertThat(inspector.clazz("Fields$Inner"), isRenamed());

    FieldSubject anX = lookupAnX(inspector);
    FieldSubject anArrayOfX = lookupAnArrayOfX(inspector);
    FieldSubject aFieldsOfX =lookupAFieldsOfX(inspector);
    FieldSubject aFieldsOfXInner = clazz.field("Fields$Inner", "aFieldsOfXInner");

    // Check that all fields have been renamed
    assertThat(anX, isRenamed());
    assertThat(anArrayOfX, isRenamed());
    assertThat(aFieldsOfX, isRenamed());
    assertThat(aFieldsOfXInner, isRenamed());

    //System.out.println(generic.getFinalSignatureAttribute());
    //System.out.println(generic.getOriginalSignatureAttribute());

    // Test that methods have their original signature if the default was provided.
    if (!signatures.containsKey("anX")) {
      assertEquals(anXSignature, anX.getOriginalSignatureAttribute());
    }
    if (!signatures.containsKey("anArrayOfX")) {
      assertEquals(anArrayOfXSignature, anArrayOfX.getOriginalSignatureAttribute());
    }
    if (!signatures.containsKey("aFieldsOfX")) {
      assertEquals(
          aFieldsOfXSignature, aFieldsOfX.getOriginalSignatureAttribute());
    }
    if (!signatures.containsKey("aFieldsOfXInner")) {
      assertEquals(
          aFieldsOfXInnerSignature, aFieldsOfXInner.getOriginalSignatureAttribute());
    }

    diagnostics.accept(checker);
    inspect.accept(inspector);
  }

  private void testSingleField(String name, String signature,
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

  private void noSignatureAttribute(FieldSubject field) {
    assertThat(field, isPresent());
    assertNull(field.getFinalSignatureAttribute());
    assertNull(field.getOriginalSignatureAttribute());
  }

  @Test
  public void originalJavacSignatures() throws Exception {
    // Test using the signatures generated by javac.
    runTest(ImmutableMap.of(), this::noWarnings, this::noInspection);
  }

  @Test
  public void signatureEmpty() throws Exception {
    testSingleField("anX", "", this::noWarnings,
        inspector -> noSignatureAttribute(lookupAnX(inspector)));
  }

  @Test
  public void signatureInvalid() throws Exception {
    testSingleField("anX", "X", diagnostics -> {
      assertEquals(1, diagnostics.warnings.size());
      // TODO(sgjesse): The position 2 reported here is one off.
      DiagnosticsChecker.checkDiagnostic(diagnostics.warnings.get(0), this::isOriginUnknown,
          "Invalid signature 'X' for field",
          "java.lang.String Fields.anX",
          "Expected L, [ or T at position 2");
    }, inspector -> noSignatureAttribute(lookupAnX(inspector)));
  }

  @Test
  public void classNotFound() throws Exception {
    String signature = "LNotFound<TX;>.InnerNotFound.InnerAlsoNotFound;";
    testSingleField("anX", signature, this::noWarnings, inspector -> {
      assertThat(inspector.clazz("NotFound"), not(isPresent()));
      assertEquals(signature, lookupAnX(inspector).getOriginalSignatureAttribute());
    });
  }

  @Test
  public void multipleWarnings() throws Exception {
    runTest(ImmutableMap.of(
        "anX", "X",
        "anArrayOfX", "X",
        "aFieldsOfX", "X"
    ), diagnostics -> {
      assertEquals(3, diagnostics.warnings.size());
    }, inspector -> {
      noSignatureAttribute(lookupAnX(inspector));
      noSignatureAttribute(lookupAnArrayOfX(inspector));
      noSignatureAttribute(lookupAFieldsOfX(inspector));
    });
  }
}
