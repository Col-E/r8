// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.interfacetargets;

import static com.android.tools.r8.resolution.interfacetargets.ProgramAndLibraryDefinitionTest.ClassTestParam.DEFINED_WITH_METHOD;
import static com.android.tools.r8.resolution.interfacetargets.ProgramAndLibraryDefinitionTest.ClassTestParam.NOT_DEFINED;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProgramAndLibraryDefinitionTest extends TestBase {

  public enum ClassTestParam {
    NOT_DEFINED,
    DEFINED_NO_METHOD,
    DEFINED_WITH_METHOD
  }

  private final TestParameters parameters;
  private final ClassTestParam aInLibrary;
  private final ClassTestParam bInLibrary;
  private final ClassTestParam aInProgram;
  private final ClassTestParam bInProgram;

  @Parameters(name = "{0}, aInLibrary: {1}, bInLibrary: {2}, aInProgram: {3}, bInProgram: {4}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDefaultDexRuntime()
            .withDefaultCfRuntime()
            .apiLevelWithDefaultMethodsSupport()
            .build(),
        ClassTestParam.values(),
        ClassTestParam.values(),
        ClassTestParam.values(),
        ClassTestParam.values());
  }

  public ProgramAndLibraryDefinitionTest(
      TestParameters parameters,
      ClassTestParam aInLibrary,
      ClassTestParam bInLibrary,
      ClassTestParam aInProgram,
      ClassTestParam bInProgram) {
    this.parameters = parameters;
    this.aInLibrary = aInLibrary;
    this.bInLibrary = bInLibrary;
    this.aInProgram = aInProgram;
    this.bInProgram = bInProgram;
  }

  @Test
  public void testR8() throws Exception {
    R8FullTestBuilder testBuilder =
        testForR8(parameters.getBackend())
            .addDefaultRuntimeLibrary(parameters)
            .addProgramClasses(Main.class, Implementer.class, I.class)
            .setMinApi(parameters)
            .addDontWarn(A.class, B.class)
            .addKeepMainRule(Main.class)
            .addOptionsModification(options -> options.loadAllClassDefinitions = true)
            .addDontObfuscate()
            .allowUnusedDontWarnPatterns();
    byte[] libraryA = getAFromClassTestParam(this.aInLibrary);
    addIfNotNull(libraryA, testBuilder::addLibraryClassFileData);
    byte[] libraryB = getBFromClassTestParam(bInLibrary);
    addIfNotNull(libraryB, testBuilder::addLibraryClassFileData);
    addIfNotNull(getAFromClassTestParam(aInProgram), testBuilder::addProgramClassFileData);
    addIfNotNull(getBFromClassTestParam(bInProgram), testBuilder::addProgramClassFileData);
    R8TestCompileResult compileResult = testBuilder.compile();
    if (libraryA != null) {
      compileResult.addRunClasspathClassFileData(libraryA);
    }
    if (libraryB != null) {
      compileResult.addRunClasspathClassFileData(libraryB);
    }
    R8TestRunResult runResult = compileResult.run(parameters.getRuntime(), Main.class);
    if (isExpectedToFailWithNoClassDefError()) {
      runResult.assertFailureWithErrorThatThrows(NoClassDefFoundError.class);
    } else if (isExpectedToFailWithICCE()) {
      runResult.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
    } else if (isExpectedToFailWithNoSuchMethodError()) {
      runResult.assertFailureWithErrorThatThrows(NoSuchMethodError.class);
    } else if (isDefinedOnAProgram() || (isDefinedOnALibrary() && !isAInProgram())) {
      runResult.assertSuccessWithOutputLines("A::foo");
    } else {
      assert isDefinedOnBProgram() || (isDefinedOnBLibrary() && !isBInProgram());
      runResult.assertSuccessWithOutputLines("B::foo");
    }
  }

  private boolean isAInProgram() {
    return aInProgram != NOT_DEFINED;
  }

  private boolean isBInProgram() {
    return bInProgram != NOT_DEFINED;
  }

  private boolean isAInLibrary() {
    return aInLibrary != NOT_DEFINED;
  }

  private boolean isBInLibrary() {
    return bInLibrary != NOT_DEFINED;
  }

  private boolean isDefinedOnAProgram() {
    return aInProgram == DEFINED_WITH_METHOD;
  }

  private boolean isDefinedOnBProgram() {
    return bInProgram == DEFINED_WITH_METHOD;
  }

  private boolean isDefinedOnALibrary() {
    return aInLibrary == DEFINED_WITH_METHOD;
  }

  private boolean isDefinedOnBLibrary() {
    return bInLibrary == DEFINED_WITH_METHOD;
  }

  private boolean isExpectedToFailWithNoClassDefError() {
    return (!isAInLibrary() && !isAInProgram()) || (!isBInLibrary() && !isBInProgram());
  }

  private boolean isExpectedToFailWithNoSuchMethodError() {
    boolean notDefinedInProgram = !isDefinedOnAProgram() && !isDefinedOnBProgram();
    boolean notDefinedInLibrary = !isDefinedOnALibrary() && !isDefinedOnBLibrary();
    if (notDefinedInLibrary && notDefinedInProgram) {
      return true;
    }
    if (notDefinedInProgram) {
      // TODO(b/230289235): Currently, a program definition will shadow the library definition and
      //  R8 will optimize the interfaces away.
      if (isDefinedOnALibrary() && isDefinedOnBLibrary()) {
        return isAInProgram() && isBInProgram();
      } else if (isDefinedOnALibrary()) {
        return isAInProgram();
      } else {
        assert isDefinedOnBLibrary();
        return isBInProgram();
      }
    } else {
      // if the library definition is overriding the program definition and there is no definition
      // then we also fail.
      if (isDefinedOnAProgram() && isAInLibrary() && !isDefinedOnALibrary()) {
        return true;
      }
      if (isDefinedOnBProgram() && isBInLibrary() && !isDefinedOnBLibrary()) {
        return true;
      }
    }
    return false;
  }

  private boolean isExpectedToFailWithICCE() {
    if (isDefinedOnAProgram() && isDefinedOnBProgram()) {
      return true;
    }
    if (!isAInProgram() && !isBInProgram()) {
      return isDefinedOnALibrary() && isDefinedOnBLibrary();
    }
    if (isDefinedOnALibrary() && !isAInProgram() && isDefinedOnBProgram()) {
      return true;
    }
    if (isDefinedOnBLibrary() && !isBInProgram() && isDefinedOnAProgram()) {
      return true;
    }
    return false;
  }

  private void addIfNotNull(byte[] clazz, Consumer<byte[]> consumer) {
    if (clazz != null) {
      consumer.accept(clazz);
    }
  }

  private byte[] getAFromClassTestParam(ClassTestParam param) throws Exception {
    switch (param) {
      case NOT_DEFINED:
        return null;
      case DEFINED_NO_METHOD:
        return transformer(A.class).removeMethods(MethodPredicate.onName("foo")).transform();
      default:
        assert param == DEFINED_WITH_METHOD;
        return transformer(A.class).transform();
    }
  }

  private byte[] getBFromClassTestParam(ClassTestParam param) throws Exception {
    switch (param) {
      case NOT_DEFINED:
        return null;
      case DEFINED_NO_METHOD:
        return transformer(B.class).removeMethods(MethodPredicate.onName("bar")).transform();
      default:
        assert param == DEFINED_WITH_METHOD;
        return transformer(B.class).renameMethod(MethodPredicate.onName("bar"), "foo").transform();
    }
  }

  public interface A {

    default void foo() {
      System.out.println("A::foo");
    }
  }

  public interface B {

    default void /* foo */ bar() { // Will be renamed to foo.
      System.out.println("B::foo");
    }
  }

  public interface I extends A {}

  public static class Implementer implements I, B {}

  public static class Main {

    public static void main(String[] args) {
      new Implementer().foo();
    }
  }
}
