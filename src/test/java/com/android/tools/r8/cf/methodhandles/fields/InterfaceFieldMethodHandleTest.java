// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.methodhandles.fields;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.containsString;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.H_GETSTATIC;
import static org.objectweb.asm.Opcodes.H_PUTSTATIC;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.errors.UnsupportedFeatureDiagnostic;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.StringUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class InterfaceFieldMethodHandleTest extends TestBase {

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

  public InterfaceFieldMethodHandleTest(TestParameters parameters, LookupType lookupType) {
    this.parameters = parameters;
    this.lookupType = lookupType;
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(I.class)
        .addProgramClassFileData(getTransformedMain())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpected());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addProgramClasses(I.class)
        .addProgramClassFileData(getTransformedMain())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(this::checkDiagnostics)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkResult);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepClassAndMembersRules(I.class, Main.class)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addProgramClasses(I.class)
        .addProgramClassFileData(getTransformedMain())
        .setMinApi(parameters)
        .allowDiagnosticMessages()
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
        || lookupType == LookupType.CONSTANT && !hasConstMethodCompileSupport()) {
      diagnostics
          .assertAllWarningsMatch(diagnosticType(UnsupportedFeatureDiagnostic.class))
          .assertOnlyWarnings();
    } else {
      diagnostics.assertNoMessages();
    }
  }

  private void checkResult(TestRunResult<?> result) {
    if (parameters.isDexRuntimeVersion(Version.V13_0_0)
        && lookupType == LookupType.CONSTANT
        && hasConstMethodCompileSupport()) {
      // TODO(b/235576668): VM 13 throws an escaping IAE outside the guarded range.
      result
          .assertFailureWithErrorThatThrows(IllegalAccessError.class)
          .assertStderrMatches(containsString("Main.main"));
      return;
    }
    if (lookupType == LookupType.DYNAMIC && hasInvokePolymorphicCompileSupport()) {
      result.assertSuccessWithOutput(getExpected());
    } else if (hasConstMethodCompileSupport()) {
      result.assertSuccessWithOutput(getExpected());
    } else {
      result.assertFailureWithErrorThatMatches(
          containsString(
              lookupType == LookupType.DYNAMIC ? "invoke-polymorphic" : "const-method-handle"));
    }
  }

  private String getExpected() {
    if (lookupType == LookupType.CONSTANT && parameters.isDexRuntimeVersion(Version.V9_0_0)) {
      // VM 9 will assign the value in the setter in contrast to RI.
      return StringUtils.lines("42", "pass", "19");
    }
    return StringUtils.lines("42", lookupType == LookupType.DYNAMIC ? "exception" : "error", "42");
  }

  byte[] getTransformedMain() throws Exception {
    return transformer(Main.class)
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
                if (lookupType == LookupType.CONSTANT && name.endsWith("Field")) {
                  int type = name.equals("iiSetField") ? H_PUTSTATIC : H_GETSTATIC;
                  mv.visitCode();
                  mv.visitLdcInsn(new Handle(type, binaryName(I.class), "ii", "I", true));
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

  public interface I {
    int ii = 42;
  }

  public static class Main {

    public static MethodHandle iiSetField() {
      try {
        return MethodHandles.lookup().findStaticSetter(I.class, "ii", int.class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public static MethodHandle iiGetField() {
      try {
        return MethodHandles.lookup().findStaticGetter(I.class, "ii", int.class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public static void read() throws Throwable {
      System.out.println(iiGetField().invoke());
    }

    public static void main(String[] args) throws Throwable {
      read();
      // Note: having the try-catch inlined here hits ART issue b/235576668.
      try {
        iiSetField().invoke(19);
        System.out.println("pass");
      } catch (IllegalAccessError e) {
        System.out.println("error");
      } catch (RuntimeException e) {
        System.out.println("exception");
      }
      read();
    }
  }
}
