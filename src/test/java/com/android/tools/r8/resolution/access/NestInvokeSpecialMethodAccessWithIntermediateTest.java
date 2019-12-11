// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.access;

import static com.android.tools.r8.TestRuntime.CfVm.JDK11;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

/** Tests the behavior of invoke-special among related (non-interface) classes. */
@RunWith(Parameterized.class)
public class NestInvokeSpecialMethodAccessWithIntermediateTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A::bar");

  private final TestParameters parameters;

  // If true, all classes are in the same nest, otherwise each is in its own.
  private final boolean inSameNest;

  // If true, the invoke will reference the actual type defining the method.
  private final boolean symbolicReferenceIsDefiningType;

  @Parameterized.Parameters(name = "{0}, in-same-nest:{1}, sym-ref-is-def-type:{2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntimesStartingFromIncluding(JDK11)
            .withDexRuntimes()
            .withAllApiLevels()
            .build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public NestInvokeSpecialMethodAccessWithIntermediateTest(
      TestParameters parameters, boolean inSameNest, boolean symbolicReferenceIsDefiningType) {
    this.parameters = parameters;
    this.inSameNest = inSameNest;
    this.symbolicReferenceIsDefiningType = symbolicReferenceIsDefiningType;
  }

  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(Main.class);
  }

  public Collection<byte[]> getTransformedClasses() throws Exception {
    return ImmutableList.of(
        withNest(A.class).setPrivate(A.class.getDeclaredMethod("bar")).transform(),
        withNest(B.class).transform(),
        withNest(C.class)
            .transformMethodInsnInMethod(
                "foo",
                (opcode, owner, name, descriptor, isInterface, continuation) -> {
                  assertEquals(Opcodes.INVOKESPECIAL, opcode);
                  assertEquals(DescriptorUtils.getBinaryNameFromJavaType(B.class.getName()), owner);
                  String newOwner =
                      symbolicReferenceIsDefiningType
                          ? DescriptorUtils.getBinaryNameFromJavaType(A.class.getName())
                          : DescriptorUtils.getBinaryNameFromJavaType(B.class.getName());
                  continuation.apply(opcode, newOwner, name, descriptor, isInterface);
                })
            .transform());
  }

  private ClassFileTransformer withNest(Class<?> clazz) throws Exception {
    if (inSameNest) {
      // If in the same nest make A host and B a member.
      return transformer(clazz).setNest(A.class, B.class, C.class);
    }
    // Otherwise, set the class to be its own host and no additional members.
    return transformer(clazz).setNest(clazz);
  }

  @Test
  public void testResolutionAccess() throws Exception {
    // White-box test of the R8 resolution and lookup methods.
    Class<?> definingClass = A.class;
    Class<?> declaredClass = symbolicReferenceIsDefiningType ? definingClass : B.class;
    Class<?> callerClass = C.class;

    AppView<AppInfoWithLiveness> appView = getAppView();
    AppInfoWithLiveness appInfo = appView.appInfo();

    DexClass definingClassDefinition = getDexProgramClass(definingClass, appInfo);
    DexClass declaredClassDefinition = getDexProgramClass(declaredClass, appInfo);
    DexProgramClass callerClassDefinition = getDexProgramClass(callerClass, appInfo);

    DexMethod method = getTargetMethodSignature(declaredClass, appInfo);

    assertCallingClassCallsTarget(callerClass, appInfo, method);

    // Resolve the method from the point of the declared holder.
    assertEquals(method.holder, declaredClassDefinition.type);
    ResolutionResult resolutionResult = appInfo.resolveMethod(declaredClassDefinition, method);

    // Verify that the resolved method is on the defining class.
    assertEquals(
        definingClassDefinition, resolutionResult.asSingleResolution().getResolvedHolder());

    // Verify that the resolved method is accessible only when in the same nest.
    assertEquals(inSameNest, resolutionResult.isAccessibleFrom(callerClassDefinition, appInfo));

    // Verify that looking up the dispatch target returns a valid target
    // iff in the same nest and declaredHolder == definingHolder.
    DexEncodedMethod targetSpecial =
        resolutionResult.lookupInvokeSpecialTarget(callerClassDefinition, appInfo);
    DexEncodedMethod targetSuper =
        resolutionResult.lookupInvokeSuperTarget(callerClassDefinition, appInfo);
    if (inSameNest && symbolicReferenceIsDefiningType) {
      assertEquals(definingClassDefinition.type, targetSpecial.method.holder);
      assertEquals(targetSpecial, targetSuper);
    } else {
      assertNull(targetSpecial);
      // TODO(b/145775365): The current invoke-super will return the resolution target.
      assertNotNull(targetSuper);
    }
  }

  private void assertCallingClassCallsTarget(
      Class<?> callerClass, AppInfoWithLiveness appInfo, DexMethod target) {
    CodeInspector inspector = new CodeInspector(appInfo.app());
    MethodSubject foo = inspector.clazz(callerClass).uniqueMethodWithName("foo");
    assertTrue(
        foo.streamInstructions().anyMatch(i -> i.isInvokeSpecial() && i.getMethod() == target));
  }

  private DexMethod getTargetMethodSignature(Class<?> declaredClass, AppInfoWithLiveness appInfo) {
    return buildMethod(
        Reference.method(Reference.classFromClass(declaredClass), "bar", ImmutableList.of(), null),
        appInfo.dexItemFactory());
  }

  private DexProgramClass getDexProgramClass(Class<?> clazz, AppInfoWithLiveness appInfo) {
    return appInfo.definitionFor(buildType(clazz, appInfo.dexItemFactory())).asProgramClass();
  }

  private AppView<AppInfoWithLiveness> getAppView() throws Exception {
    return computeAppViewWithLiveness(
        buildClasses(getClasses()).addClassProgramData(getTransformedClasses()).build(),
        Main.class);
  }

  @Test
  public void test() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkExpectedResult(result, false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkExpectedResult(result, true));
  }

  private void checkExpectedResult(TestRunResult<?> result, boolean isR8) {
    if (inSameNest && parameters.isCfRuntime()) {
      if (isR8) {
        // TODO(b/145775365): R8 incorrectly compiles the input.
        result.assertSuccessWithOutput(EXPECTED);
      } else if (symbolicReferenceIsDefiningType) {
        result.assertSuccessWithOutput(EXPECTED);
      } else {
        result.assertFailureWithErrorThatMatches(containsString(NoSuchMethodError.class.getName()));
      }
    } else {
      result.assertFailureWithErrorThatMatches(containsString(IllegalAccessError.class.getName()));
    }
  }

  static class A {
    /* will be private */ void bar() {
      System.out.println("A::bar");
    }
  }

  static class B extends A {
    // Intentionally empty.
  }

  static class C extends B {
    public void foo() {
      // invoke-special A.bar or B.bar which resolves to private method A.bar
      // Without nests, results in an IllegalAccessError.
      // With nests and sym-ref B.bar, results in a NoSuchMethodError.
      // With nests and sym-ref A.bar runs without error.
      super.bar();
    }
  }

  static class Main {
    public static void main(String[] args) {
      new C().foo();
    }
  }
}
