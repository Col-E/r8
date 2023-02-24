// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.methodhandles.fields;

import static org.hamcrest.CoreMatchers.containsString;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.H_GETFIELD;
import static org.objectweb.asm.Opcodes.H_GETSTATIC;
import static org.objectweb.asm.Opcodes.H_PUTFIELD;
import static org.objectweb.asm.Opcodes.H_PUTSTATIC;

import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.UnsupportedFeatureDiagnostic;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableMap;
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
public class ClassFieldMethodHandleTest extends TestBase {

  enum LookupType {
    DYNAMIC,
    CONSTANT,
  }

  private final TestParameters parameters;
  private final LookupType lookupType;

  @Parameters(name = "{0}, lookup:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        TestParameters.builder().withAllRuntimesAndApiLevels().build(), LookupType.values());
  }

  public ClassFieldMethodHandleTest(TestParameters parameters, LookupType lookupType) {
    this.parameters = parameters;
    this.lookupType = lookupType;
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(C.class)
        .addProgramClassFileData(getTransformedMain())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpected());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addProgramClasses(C.class)
        .addProgramClassFileData(getTransformedMain())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(this::checkDiagnostics)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkResult);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepClassAndMembersRules(C.class, Main.class)
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addProgramClasses(C.class)
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

  private boolean hasMethodHandlesRuntimeSupport() {
    return parameters.isCfRuntime()
        || parameters
            .asDexRuntime()
            .maxSupportedApiLevel()
            .isGreaterThanOrEqualTo(AndroidApiLevel.O);
  }

  private void checkDiagnostics(TestDiagnosticMessages diagnostics) {
    if ((lookupType == LookupType.DYNAMIC && !hasInvokePolymorphicCompileSupport())
        || lookupType == LookupType.CONSTANT && !hasConstMethodCompileSupport()) {
      diagnostics
          .assertAllWarningsMatch(
              DiagnosticsMatcher.diagnosticType(UnsupportedFeatureDiagnostic.class))
          .assertOnlyWarnings();
    } else {
      diagnostics.assertNoMessages();
    }
  }

  private void checkResult(TestRunResult<?> result) {
    if (lookupType == LookupType.DYNAMIC && hasInvokePolymorphicCompileSupport()) {
      result.assertSuccessWithOutput(getExpected());
    } else if (hasConstMethodCompileSupport()) {
      result.assertSuccessWithOutput(getExpected());
    } else if (lookupType == LookupType.DYNAMIC && !hasMethodHandlesRuntimeSupport()) {
      result.assertFailureWithErrorThatThrows(NoClassDefFoundError.class);
    } else {
      result.assertFailureWithErrorThatMatches(
          containsString(
              lookupType == LookupType.DYNAMIC ? "invoke-polymorphic" : "const-method-handle"));
    }
  }

  private String getExpected() {
    return StringUtils.lines("AOK");
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
                  String fieldName = name.startsWith("sci") ? "si" : "vi";
                  int type =
                      ImmutableMap.<String, Integer>builder()
                          .put("sciSetField", H_PUTSTATIC)
                          .put("sciGetField", H_GETSTATIC)
                          .put("vciSetField", H_PUTFIELD)
                          .put("vciGetField", H_GETFIELD)
                          .build()
                          .get(name);
                  mv.visitCode();
                  mv.visitLdcInsn(new Handle(type, binaryName(C.class), fieldName, "I", false));
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

  public static class Main {

    public static MethodHandle vciSetField() {
      try {
        return MethodHandles.lookup().findSetter(C.class, "vi", int.class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public static MethodHandle vciGetField() {
      try {
        return MethodHandles.lookup().findGetter(C.class, "vi", int.class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public static MethodHandle sciSetField() {
      try {
        return MethodHandles.lookup().findStaticSetter(C.class, "si", int.class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public static MethodHandle sciGetField() {
      try {
        return MethodHandles.lookup().findStaticGetter(C.class, "si", int.class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public static void assertEquals(int x, int y) {
      if (x != y) {
        throw new AssertionError("failed!");
      }
    }

    public static void main(String[] args) throws Throwable {
      C c = new C();
      vciSetField().invoke(c, 17);
      assertEquals(17, (int) vciGetField().invoke(c));
      sciSetField().invoke(18);
      assertEquals(18, (int) sciGetField().invoke());
      System.out.println("AOK");
    }
  }
}
