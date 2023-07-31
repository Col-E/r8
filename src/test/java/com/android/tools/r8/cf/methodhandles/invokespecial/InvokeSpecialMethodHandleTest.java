// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.methodhandles.invokespecial;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.containsString;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.H_INVOKESPECIAL;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.apimodel.ApiModelingTestHelper;
import com.android.tools.r8.errors.UnsupportedFeatureDiagnostic;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.StringUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class InvokeSpecialMethodHandleTest extends TestBase {

  enum LookupType {
    DYNAMIC,
    CONSTANT,
  }

  private final TestParameters parameters;
  private final LookupType lookupType;

  @Parameters(name = "{0}, lookup:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        TestParameters.builder()
            // Runtimes without Handle APIs fail in various ways. Start testing beyond that point.
            .withDexRuntimesStartingFromExcluding(Version.V7_0_0)
            .withAllApiLevels()
            .withCfRuntimes()
            .build(),
        LookupType.values());
  }

  public InvokeSpecialMethodHandleTest(TestParameters parameters, LookupType lookupType) {
    this.parameters = parameters;
    this.lookupType = lookupType;
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(C.class, Main.class)
        .addProgramClassFileData(getTransformedD())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpected());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addProgramClasses(C.class, Main.class)
        .addProgramClassFileData(getTransformedD())
        .setMinApi(parameters)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .compileWithExpectedDiagnostics(this::checkDiagnostics)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkResult);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepClassAndMembersRules(C.class, D.class, Main.class)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addProgramClasses(C.class, Main.class)
        .addProgramClassFileData(getTransformedD())
        .setMinApi(parameters)
        .allowDiagnosticMessages()
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .compileWithExpectedDiagnostics(this::checkDiagnostics)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkResult);
  }

  private boolean hasConstMethodCompileSupport() {
    return parameters.isCfRuntime()
        || parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithConstMethodHandleSupport());
  }

  private boolean hasInvokePolymorphicCompileSupport() {
    return parameters.isCfRuntime()
        || parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithInvokePolymorphicSupport());
  }

  private void checkDiagnostics(TestDiagnosticMessages diagnostics) {
    if ((lookupType == LookupType.DYNAMIC && !hasInvokePolymorphicCompileSupport())
        || (lookupType == LookupType.CONSTANT && !hasConstMethodCompileSupport())) {
      diagnostics
          .assertAllWarningsMatch(diagnosticType(UnsupportedFeatureDiagnostic.class))
          .assertOnlyWarnings();
    } else {
      diagnostics.assertNoMessages();
    }
  }

  private void checkResult(TestRunResult<?> result) {
    if (lookupType == LookupType.DYNAMIC && hasInvokePolymorphicCompileSupport()) {
      result.assertSuccessWithOutput(getExpected());
    } else if (lookupType == LookupType.CONSTANT && hasConstMethodCompileSupport()) {
      if (parameters.isDexRuntimeVersion(Version.V9_0_0)) {
        // VM 9 incorrectly prints out the overridden method despite the direct target.
        result.assertSuccessWithOutput("");
      } else if (parameters.isDexRuntimeVersion(Version.V13_0_0)) {
        // TODO(b/235807678): Subsequent ART VMs incorrectly throw IAE.
        result.assertFailureWithErrorThatThrows(IllegalAccessError.class);
      } else if (parameters.isDexRuntime()
          && parameters.asDexRuntime().getVersion().isNewerThan(Version.V9_0_0)) {
        // VMs between 9 and 13 segfault.
        if (parameters.asDexRuntime().getVersion().isEqualTo(Version.V14_0_0)) {
          result.assertFailureWithOutputThatMatches(
              containsString("reverting to SIG_DFL handler for signal 11"));
        } else {
          result.assertFailureWithErrorThatMatches(containsString("HandleUnexpectedSignal"));
        }
      } else {
        result.assertSuccessWithOutput(getExpected());
      }
    } else {
      result.assertFailureWithErrorThatMatches(
          containsString(
              lookupType == LookupType.DYNAMIC ? "invoke-polymorphic" : "const-method-handle"));
    }
  }

  private String getExpected() {
    return StringUtils.lines("vvi 20");
  }

  byte[] getTransformedD() throws Exception {
    return transformer(D.class)
        .addClassTransformer(
            new ClassTransformer() {
              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                MethodVisitor mv =
                    super.visitMethod(access, name, descriptor, signature, exceptions);
                if (lookupType == LookupType.CONSTANT && name.equals("vcviSpecialMethod")) {
                  mv.visitCode();
                  mv.visitLdcInsn(
                      new Handle(H_INVOKESPECIAL, binaryName(C.class), "vvi", "(I)V", false));
                  mv.visitInsn(ARETURN);
                  mv.visitMaxs(-1, -1);
                  mv.visitEnd();
                  return null;
                }
                return mv;
              }
            })
        .transform();
  }

  public static class C {

    public void vvi(int i) {
      System.out.println("vvi " + i);
    }
  }

  public static class D extends C {

    public void vvi(int i) {
      // Overridden to output nothing.
    }

    public static MethodHandle vcviSpecialMethod() {
      try {
        return MethodHandles.lookup()
            .findSpecial(C.class, "vvi", MethodType.methodType(void.class, int.class), D.class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static class Main {

    public static void main(String[] args) throws Throwable {
      MethodHandle methodHandle = D.vcviSpecialMethod();
      methodHandle.invoke(new D(), 20);
    }
  }
}
