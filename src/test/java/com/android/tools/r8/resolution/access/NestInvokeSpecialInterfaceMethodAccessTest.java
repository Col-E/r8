// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.access;

import static com.android.tools.r8.TestRuntime.CfVm.JDK11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.NoSuchMethodResult;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.OptionalBool;
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

/** Tests the behavior of invoke-special on interfaces with a direct private definition. */
@RunWith(Parameterized.class)
public class NestInvokeSpecialInterfaceMethodAccessTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("I::bar");

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

  public NestInvokeSpecialInterfaceMethodAccessTest(
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
        withNest(I.class).setPrivate(I.class.getDeclaredMethod("bar")).transform(),
        withNest(A.class)
            .transformMethodInsnInMethod(
                "foo",
                (opcode, owner, name, descriptor, isInterface, continuation) -> {
                  assertEquals(Opcodes.INVOKEVIRTUAL, opcode);
                  assertEquals(DescriptorUtils.getBinaryNameFromJavaType(A.class.getName()), owner);
                  String newOwner =
                      symbolicReferenceIsDefiningType
                          ? DescriptorUtils.getBinaryNameFromJavaType(I.class.getName())
                          : DescriptorUtils.getBinaryNameFromJavaType(A.class.getName());
                  boolean newIsInterface = symbolicReferenceIsDefiningType;
                  continuation.visitMethodInsn(
                      Opcodes.INVOKESPECIAL, newOwner, name, descriptor, newIsInterface);
                })
            .transform());
  }

  private ClassFileTransformer withNest(Class<?> clazz) throws Exception {
    if (inSameNest) {
      // If in the same nest make A host and B a member.
      return transformer(clazz).setNest(I.class, A.class);
    }
    // Otherwise, set the class to be its own host and no additional members.
    return transformer(clazz).setNest(clazz);
  }

  @Test
  public void testResolutionAccess() throws Exception {
    // White-box test of the R8 resolution and lookup methods.
    Class<?> definingClass = I.class;
    Class<?> declaredClass = symbolicReferenceIsDefiningType ? definingClass : A.class;
    Class<?> callerClass = A.class;

    AppView<AppInfoWithClassHierarchy> appView = getAppView();
    AppInfoWithClassHierarchy appInfo = appView.appInfo();

    DexProgramClass definingClassDefinition = getDexProgramClass(definingClass, appView);
    DexProgramClass declaredClassDefinition = getDexProgramClass(declaredClass, appView);
    DexProgramClass callerClassDefinition = getDexProgramClass(callerClass, appView);

    DexMethod method = getTargetMethodSignature(declaredClass, appView.dexItemFactory());
    assertCallingClassCallsTarget(callerClass, appView, method);

    // Resolve the method from the point of the declared holder.
    assertEquals(method.holder, declaredClassDefinition.type);
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodOnLegacy(declaredClassDefinition, method);

    if (!symbolicReferenceIsDefiningType) {
      // The targeted method is a private interface method and thus not a maximally specific method.
      assertTrue(resolutionResult instanceof NoSuchMethodResult);
      return;
    }

    assertEquals(
        OptionalBool.of(inSameNest),
        resolutionResult.isAccessibleFrom(callerClassDefinition, appView));
    DexClassAndMethod targetSpecial =
        resolutionResult.lookupInvokeSpecialTarget(callerClassDefinition, appView);
    DexClassAndMethod targetSuper =
        resolutionResult.lookupInvokeSuperTarget(callerClassDefinition, appView);
    if (inSameNest) {
      assertEquals(definingClassDefinition.getType(), targetSpecial.getHolderType());
      assertEquals(targetSpecial.getReference(), targetSuper.getReference());
    } else {
      assertNull(targetSpecial);
      assertNull(targetSuper);
    }
  }

  private void assertCallingClassCallsTarget(
      Class<?> callerClass, AppView<?> appView, DexMethod target) {
    CodeInspector inspector = new CodeInspector(appView.appInfo().app());
    MethodSubject foo = inspector.clazz(callerClass).uniqueMethodWithOriginalName("foo");
    assertTrue(
        foo.streamInstructions()
            .anyMatch(i -> i.asCfInstruction().isInvokeSpecial() && i.getMethod() == target));
  }

  private DexMethod getTargetMethodSignature(
      Class<?> declaredClass, DexItemFactory dexItemFactory) {
    return buildMethod(
        Reference.method(Reference.classFromClass(declaredClass), "bar", ImmutableList.of(), null),
        dexItemFactory);
  }

  private DexProgramClass getDexProgramClass(Class<?> clazz, AppView<?> appView) {
    return appView.definitionFor(buildType(clazz, appView.dexItemFactory())).asProgramClass();
  }

  private AppView<AppInfoWithClassHierarchy> getAppView() throws Exception {
    return computeAppViewWithClassHierarchy(
        buildClasses(getClasses())
            .addClassProgramData(getTransformedClasses())
            .addLibraryFile(parameters.getDefaultRuntimeLibrary())
            .build());
  }

  @Test
  public void test() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkExpectedResult);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkExpectedResult);
  }

  private void checkExpectedResult(TestRunResult<?> result) {
    if (!symbolicReferenceIsDefiningType) {
      result.assertFailureWithErrorThatThrows(NoSuchMethodError.class);
      return;
    }

    if (!inSameNest) {
      // TODO(b/145775365): Desugaring causes change to reported error.
      // The default method desugar will target $default$bar, but the definition is $private$bar.
      result.assertFailureWithErrorThatThrows(
          isDesugaring() ? NoSuchMethodError.class : IllegalAccessError.class);
      return;
    }

    result.assertSuccessWithOutput(EXPECTED);
  }

  private boolean isDesugaring() {
    return parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.N);
  }

  interface I {
    /* will be private */ default void bar() {
      System.out.println("I::bar");
    }
  }

  static class A implements I {
    public void foo() {
      // Rewritten to invoke-special A.bar or I.bar which resolves to private method A.bar
      // When targeting A.bar => throws NoSuchMethodError.
      // When targeting I.bar:
      //   - in same nest => success.
      //   - not in nest => throws IllegalAccessError.
      bar();
    }
  }

  static class Main {
    public static void main(String[] args) {
      new A().foo();
    }
  }
}
