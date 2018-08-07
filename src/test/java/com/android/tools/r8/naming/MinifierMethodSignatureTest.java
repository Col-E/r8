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
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
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
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class MinifierMethodSignatureTest extends TestBase {
  /*

  class Methods<X extends Throwable> {
    class Inner {
    }
    public static <T extends Throwable> T generic(T a, Methods<T>.Inner b) { return null; }
    public Methods<X>.Inner parameterizedReturn() { return null; }
    public void parameterizedArguments(X a, Methods<X>.Inner b) { }
    public void parametrizedThrows() throws X { }
  }

  */

  private String genericSignature = "<T:Ljava/lang/Throwable;>(TT;LMethods<TT;>.Inner;)TT;";
  private String parameterizedReturnSignature = "()LMethods<TX;>.Inner;";
  private String parameterizedArgumentsSignature = "(TX;LMethods<TX;>.Inner;)V";
  private String parametrizedThrowsSignature = "()V^TX;";
  Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public MinifierMethodSignatureTest(Backend backend) {
    this.backend = backend;
  }

  private byte[] dumpMethods(Map<String, String> signatures) throws Exception {

    ClassWriter cw = new ClassWriter(0);
    MethodVisitor mv;
    String signature;

    cw.visit(V1_8, ACC_SUPER, "Methods", "<X:Ljava/lang/Throwable;>Ljava/lang/Object;",
        "java/lang/Object", null);

    cw.visitInnerClass("Methods$Inner", "Methods", "Inner", 0);

    {
      mv = cw.visitMethod(0, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      signature = signatures.get("generic");
      signature = signature == null ? genericSignature : signature;
      mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "generic",
          "(Ljava/lang/Throwable;LMethods$Inner;)Ljava/lang/Throwable;",
          signature, null);
      mv.visitCode();
      mv.visitInsn(ACONST_NULL);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(1, 2);
      mv.visitEnd();
    }
    {
      signature = signatures.get("parameterizedReturn");
      signature = signature == null ? parameterizedReturnSignature : signature;
      mv = cw.visitMethod(ACC_PUBLIC, "parameterizedReturn", "()LMethods$Inner;",
          signature, null);
      mv.visitCode();
      mv.visitInsn(ACONST_NULL);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      signature = signatures.get("parameterizedArguments");
      signature = signature == null ? parameterizedArgumentsSignature : signature;
      mv = cw.visitMethod(ACC_PUBLIC, "parameterizedArguments",
          "(Ljava/lang/Throwable;LMethods$Inner;)V", signature, null);
      mv.visitCode();
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 3);
      mv.visitEnd();
    }
    {
      signature = signatures.get("parametrizedThrows");
      signature = signature == null ? parametrizedThrowsSignature : signature;
      mv = cw.visitMethod(ACC_PUBLIC, "parametrizedThrows", "()V", signature,
          new String[] { "java/lang/Throwable" });
      mv.visitCode();
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 1);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  private byte[] dumpInner() throws Exception {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;

    cw.visit(V1_8, ACC_SUPER, "Methods$Inner", null, "java/lang/Object", null);

    cw.visitInnerClass("Methods$Inner", "Methods", "Inner", 0);

    {
      fv = cw.visitField(ACC_FINAL + ACC_SYNTHETIC, "this$0", "LMethods;", null, null);
      fv.visitEnd();
    }
    {
      mv = cw.visitMethod(0, "<init>", "(LMethods;)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitFieldInsn(PUTFIELD, "Methods$Inner", "this$0", "LMethods;");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 2);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  private MethodSubject lookupGeneric(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("Methods");
    return clazz.method(
        "java.lang.Throwable", "generic", ImmutableList.of("java.lang.Throwable", "Methods$Inner"));
  }

  private MethodSubject lookupParameterizedReturn(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("Methods");
    return clazz.method(
        "Methods$Inner", "parameterizedReturn", ImmutableList.of());
  }

  private MethodSubject lookupParameterizedArguments(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("Methods");
    return clazz.method(
        "void", "parameterizedArguments", ImmutableList.of("java.lang.Throwable", "Methods$Inner"));
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
                    .addClassProgramData(dumpMethods(signatures), Origin.unknown())
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
    ClassSubject clazz = inspector.clazz("Methods");
    assertThat(clazz, isRenamed());
    assertThat(inspector.clazz("Methods$Inner"), isRenamed());

    MethodSubject generic = lookupGeneric(inspector);
    MethodSubject parameterizedReturn = lookupParameterizedReturn(inspector);
    MethodSubject parameterizedArguments = lookupParameterizedArguments(inspector);
    MethodSubject parametrizedThrows =
        clazz.method("void", "parametrizedThrows", ImmutableList.of());

    // Check that all methods have been renamed
    assertThat(generic, isRenamed());
    assertThat(parameterizedReturn, isRenamed());
    assertThat(parameterizedArguments, isRenamed());
    assertThat(parametrizedThrows, isRenamed());

    // Test that methods have their original signature if the default was provided.
    if (!signatures.containsKey("generic")) {
      assertEquals(genericSignature, generic.getOriginalSignatureAttribute());
    }
    if (!signatures.containsKey("parameterizedReturn")) {
      assertEquals(
          parameterizedReturnSignature, parameterizedReturn.getOriginalSignatureAttribute());
    }
    if (!signatures.containsKey("parameterizedArguments")) {
      assertEquals(
          parameterizedArgumentsSignature, parameterizedArguments.getOriginalSignatureAttribute());
    }
    if (!signatures.containsKey("parametrizedThrows")) {
      assertEquals(
          parametrizedThrowsSignature, parametrizedThrows.getOriginalSignatureAttribute());
    }

    diagnostics.accept(checker);
    inspect.accept(inspector);
  }

  private void testSingleMethod(String name, String signature,
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

  private void noSignatureAttribute(MethodSubject method) {
    assertThat(method, isPresent());
    assertNull(method.getFinalSignatureAttribute());
    assertNull(method.getOriginalSignatureAttribute());
  }

  @Test
  public void originalJavacSignatures() throws Exception {
    // Test using the signatures generated by javac.
    runTest(ImmutableMap.of(), this::noWarnings, this::noInspection);
  }

  @Test
  public void signatureEmpty() throws Exception {
    testSingleMethod("generic", "", this::noWarnings, inspector -> {
      noSignatureAttribute(lookupGeneric(inspector));
    });
  }

  @Test
  public void signatureInvalid() throws Exception {
    testSingleMethod("generic", "X", diagnostics -> {
      assertEquals(1, diagnostics.warnings.size());
      DiagnosticsChecker.checkDiagnostic(diagnostics.warnings.get(0), this::isOriginUnknown,
          "Invalid signature for method",
          "java.lang.Throwable Methods.generic(java.lang.Throwable, Methods$Inner)",
          "Expected ( at position 1");
    }, inspector -> noSignatureAttribute(lookupGeneric(inspector)));
  }

  @Test
  public void classNotFound() throws Exception {
    String signature = "<T:LNotFound;>(TT;LAlsoNotFound<TT;>.InnerNotFound.InnerAlsoNotFound;)TT;";
    testSingleMethod("generic", signature, this::noWarnings,
        inspector -> {
          ClassSubject methods = inspector.clazz("Methods");
          MethodSubject method =
              methods.method("java.lang.Throwable", "generic",
                  ImmutableList.of("java.lang.Throwable", "Methods$Inner"));
          assertThat(inspector.clazz("NotFound"), not(isPresent()));
          assertEquals(signature, method.getOriginalSignatureAttribute());
        });
  }

  @Test
  public void multipleWarnings() throws Exception {
    runTest(ImmutableMap.of(
        "generic", "X",
        "parameterizedReturn", "X",
        "parameterizedArguments", "X"
    ), diagnostics -> {
      assertEquals(3, diagnostics.warnings.size());
    }, inspector -> {
      noSignatureAttribute(lookupGeneric(inspector));
      noSignatureAttribute(lookupParameterizedReturn(inspector));
      noSignatureAttribute(lookupParameterizedArguments(inspector));
    });
  }
}
