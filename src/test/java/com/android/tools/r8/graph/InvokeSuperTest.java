// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class InvokeSuperTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  final TestParameters parameters;

  public InvokeSuperTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  static final String EXPECTED =
      StringUtils.lines(
          "superMethod in SubLevel2",
          "superMethod in SubLevel2",
          "superMethod in SubLevel2",
          "java.lang.NoSuchMethodError",
          "subLevel1Method in SubLevel2",
          "subLevel1Method in SubLevel2",
          "subLevel2Method in SubLevel2",
          "From SubLevel1: otherSuperMethod in Super");

  static final String UNEXPECTED_DEX_5_AND_6_OUTPUT =
      StringUtils.lines(
          "superMethod in Super",
          "superMethod in SubLevel1",
          "superMethod in SubLevel2",
          "java.lang.NoSuchMethodError",
          "subLevel1Method in SubLevel1",
          "subLevel1Method in SubLevel2",
          "subLevel2Method in SubLevel2",
          "From SubLevel1: otherSuperMethod in Super");

  String getExpectedOutput() {
    if (parameters.isDexRuntime()) {
      Version version = parameters.getRuntime().asDex().getVm().getVersion();
      if (version.isNewerThanOrEqual(Version.V5_1_1)
          && version.isOlderThanOrEqual(Version.V6_0_1)) {
        return UNEXPECTED_DEX_5_AND_6_OUTPUT;
      }
    }
    return EXPECTED;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(
            MainClass.class,
            Consumer.class,
            Super.class,
            SubLevel1.class,
            SubLevel2.class,
            SubClassOfInvokerClass.class)
        .addProgramClassFileData(InvokerClassDump.dumpVerifying())
        .run(parameters.getRuntime(), MainClass.class)
        .assertSuccessWithOutput(getExpectedOutput());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            MainClass.class,
            Consumer.class,
            Super.class,
            SubLevel1.class,
            SubLevel2.class,
            SubClassOfInvokerClass.class)
        .addProgramClassFileData(InvokerClassDump.dumpVerifying())
        .setMinApi(parameters)
        .addKeepMainRule(MainClass.class)
        .run(parameters.getRuntime(), MainClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testReferenceNonVerifying() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(
            MainClassFailing.class,
            Consumer.class,
            Super.class,
            SubLevel1.class,
            SubLevel2.class,
            SubClassOfInvokerClass.class)
        .addProgramClassFileData(InvokerClassDump.dumpNonVerifying())
        .run(parameters.getRuntime(), MainClassFailing.class)
        .apply(r -> checkNonVerifyingResult(r, false));
  }

  private void checkNonVerifyingResult(TestRunResult<?> result, boolean isR8) {
    // The input is invalid and any JVM will fail at verification time.
    if (parameters.isCfRuntime()) {
      result.assertFailureWithErrorThatThrows(VerifyError.class);
      return;
    }
    // Dex results vary wildly...
    Version version = parameters.getRuntime().asDex().getVm().getVersion();
    if (!isR8 && version.isOlderThanOrEqual(Version.V4_4_4)) {
      result.assertFailureWithErrorThatThrows(VerifyError.class);
    } else if (version == Version.V5_1_1 || version == Version.V6_0_1) {
      result.assertFailure();
    } else {
      result.assertSuccessWithOutputThatMatches(containsString(NoSuchMethodError.class.getName()));
    }
  }

  @Test
  public void testR8NonVerifying() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            MainClassFailing.class,
            Consumer.class,
            Super.class,
            SubLevel1.class,
            SubLevel2.class,
            SubClassOfInvokerClass.class)
        .addProgramClassFileData(InvokerClassDump.dumpNonVerifying())
        .setMinApi(parameters)
        .addKeepMainRule(MainClassFailing.class)
        .addOptionsModification(o -> o.testing.allowTypeErrors = true)
        .allowDiagnosticWarningMessages()
        .enableNoMethodStaticizingAnnotations()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertWarningsMatch(
                    allOf(
                        diagnosticType(UnverifiableCfCodeDiagnostic.class),
                        diagnosticMessage(
                            containsString(
                                "Unverifiable code in `void "
                                    + InvokerClass.class.getTypeName()
                                    + ".invokeSubLevel2MethodOnSubClassOfInvokerClass()`")))))
        .run(parameters.getRuntime(), MainClassFailing.class)
        .apply(r -> checkNonVerifyingResult(r, true));
  }

  /** Copy of {@ref java.util.function.Consumer} to allow tests to run on early versions of art. */
  interface Consumer<T> {

    void accept(T item);
  }

  static class Super {

    public void superMethod() {
      System.out.println("superMethod in Super");
    }

    public void otherSuperMethod() {
      System.out.println("otherSuperMethod in Super");
    }
  }

  static class SubLevel1 extends Super {

    @Override
    public void superMethod() {
      System.out.println("superMethod in SubLevel1");
    }

    public void subLevel1Method() {
      System.out.println("subLevel1Method in SubLevel1");
    }

    public void otherSuperMethod() {
      System.out.println("otherSuperMethod in SubLevel1");
    }

    public void callOtherSuperMethod() {
      System.out.print("From SubLevel1: ");
      super.otherSuperMethod();
    }
  }

  static class SubClassOfInvokerClass extends InvokerClass {

    @NoMethodStaticizing
    public void subLevel2Method() {
      System.out.println("subLevel2Method in SubClassOfInvokerClass");
    }
  }

  static class SubLevel2 extends SubLevel1 {

    @Override
    public void superMethod() {
      System.out.println("superMethod in SubLevel2");
    }

    @Override
    public void subLevel1Method() {
      System.out.println("subLevel1Method in SubLevel2");
    }

    public void subLevel2Method() {
      System.out.println("subLevel2Method in SubLevel2");
    }

    public void otherSuperMethod() {
      System.out.println("otherSuperMethod in SubLevel2");
    }

    public void callOtherSuperMethodIndirect() {
      callOtherSuperMethod();
    }
  }

  static class MainClass {

    private static void tryInvoke(Consumer<InvokerClass> function) {
      InvokerClass invoker = new InvokerClass();
      try {
        function.accept(invoker);
      } catch (Throwable e) {
        System.out.println(e.getClass().getCanonicalName());
      }
    }

    public static void main(String... args) {
      tryInvoke(InvokerClass::invokeSuperMethodOnSuper);
      tryInvoke(InvokerClass::invokeSuperMethodOnSubLevel1);
      tryInvoke(InvokerClass::invokeSuperMethodOnSubLevel2);
      tryInvoke(InvokerClass::invokeSubLevel1MethodOnSuper);
      tryInvoke(InvokerClass::invokeSubLevel1MethodOnSubLevel1);
      tryInvoke(InvokerClass::invokeSubLevel1MethodOnSubLevel2);
      tryInvoke(InvokerClass::invokeSubLevel2MethodOnSubLevel2);
      tryInvoke(InvokerClass::callOtherSuperMethodIndirect);
    }
  }

  static class MainClassFailing {

    private static void tryInvoke(Consumer<InvokerClass> function) {
      InvokerClass invoker = new InvokerClass();
      try {
        function.accept(invoker);
      } catch (Throwable e) {
        System.out.println(e.getClass().getCanonicalName());
      }
    }

    public static void main(String... args) {
      tryInvoke(InvokerClass::invokeSubLevel2MethodOnSubClassOfInvokerClass);
    }
  }

  /**
   * This class is a stub class needed to compile the dependent Java classes. The actual
   * implementation that will be used at runtime is generated by {@link InvokerClassDump}.
   */
  static class InvokerClass extends SubLevel2 {

    public void invokeSuperMethodOnSubLevel2() {
      stubIsUnreachable();
    }

    public void invokeSuperMethodOnSubLevel1() {
      stubIsUnreachable();
    }

    public void invokeSuperMethodOnSuper() {
      stubIsUnreachable();
    }

    public void invokeSubLevel1MethodOnSubLevel2() {
      stubIsUnreachable();
    }

    public void invokeSubLevel1MethodOnSubLevel1() {
      stubIsUnreachable();
    }

    public void invokeSubLevel1MethodOnSuper() {
      stubIsUnreachable();
    }

    public void invokeSubLevel2MethodOnSubLevel2() {
      stubIsUnreachable();
    }

    public void invokeSubLevel2MethodOnSubClassOfInvokerClass() {
      stubIsUnreachable();
    }

    private static void stubIsUnreachable() {
      throw new RuntimeException("Stub should never be called.");
    }
  }

  // This modifies the above {@link InvokerClass} with invoke-special for the corresponding methods.
  static class InvokerClassDump implements Opcodes {

    public static byte[] dumpVerifying() throws Exception {
      return dump(true);
    }

    public static byte[] dumpNonVerifying() throws Exception {
      return dump(false);
    }

    static byte[] dump(boolean verifying) throws Exception {
      return transformer(InvokerClass.class)
          .addClassTransformer(
              new ClassTransformer() {
                @Override
                public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                  // Keep the constructor as is.
                  if (name.equals("<init>")) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                  }
                  // Remove all methods not in the form of invokeXonY.
                  if (!name.startsWith("invoke")) {
                    return null;
                  }
                  // If dumping valid methods drop the invoke on a subclass, otherwise drop all
                  // others.
                  if (verifying == name.equals("invokeSubLevel2MethodOnSubClassOfInvokerClass")) {
                    return null;
                  }
                  // Replace the body of invokeXonY methods by invoke of X on class Y.
                  MethodVisitor mv =
                      super.visitMethod(access, name, descriptor, signature, exceptions);
                  int split = name.indexOf("On");
                  assertTrue(split > 0);
                  String targetMethodRaw = name.substring("invoke".length(), split);
                  String targetMethod =
                      StringUtils.toLowerCase(targetMethodRaw.substring(0, 1))
                          + targetMethodRaw.substring(1);
                  String targetHolderRaw = name.substring(split + 2);
                  String targetHolderType =
                      InvokeSuperTest.class.getTypeName() + "$" + targetHolderRaw;
                  String targetHolderName =
                      DescriptorUtils.getBinaryNameFromJavaType(targetHolderType);
                  mv.visitCode();
                  mv.visitVarInsn(ALOAD, 0);
                  mv.visitMethodInsn(INVOKESPECIAL, targetHolderName, targetMethod, "()V", false);
                  mv.visitInsn(RETURN);
                  mv.visitMaxs(1, 1);
                  mv.visitEnd();
                  return null;
                }
              })
          .transform();
    }
  }
}
