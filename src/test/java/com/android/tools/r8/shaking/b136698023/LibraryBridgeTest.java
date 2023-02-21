// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.b136698023;

import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryBridgeTest extends TestBase {

  private final TestParameters parameters;

  // Reproduction of b/136698023 using a the standard library.
  public interface ICloneable {

    Object clone();
  }

  public abstract static class BaseOrdinaryClone implements ICloneable {

    @Override
    public abstract Object clone();
  }

  public static class MainWithOrdinaryClone {

    public static void main(String[] args) {
      System.out.println(BaseOrdinaryClone.class.toString());
    }
  }

  // Reproduction of b/136698023 using a custom library object.
  public static class Base {

    protected Object xClone() {
      return null;
    }
  }

  public interface XCloneable {

    Object xClone();
  }

  public abstract static class Model extends Base implements XCloneable {

    @Override
    public abstract Object xClone();
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(Model.class.toString());
    }
  }

  // Reproduction of b/136698023 with an implementation as well.

  public static class ModelImpl extends Base implements XCloneable {

    @Override
    public Object xClone() {
      return new ModelImpl();
    }
  }

  public static class MainImpl {

    public static void main(String[] args) {
      System.out.println(ModelImpl.class.toString());
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public LibraryBridgeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRemovingClone()
      throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(ICloneable.class, BaseOrdinaryClone.class, MainWithOrdinaryClone.class)
        .addKeepClassAndMembersRules(ICloneable.class)
        .addKeepMainRule(MainWithOrdinaryClone.class)
        .addDontObfuscate()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MainWithOrdinaryClone.class)
        .assertSuccessWithOutputThatMatches(containsString(BaseOrdinaryClone.class.getTypeName()));
  }

  @Test
  public void testRemovingXClone()
      throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(XCloneable.class, Model.class, Main.class, Base.class)
        .addKeepClassAndMembersRules(XCloneable.class, Base.class)
        .addKeepMainRule(Main.class)
        .addDontObfuscate()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputThatMatches(containsString(Model.class.getTypeName()));
  }

  @Test
  public void testRemovingXCloneImpl()
      throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(XCloneable.class, ModelImpl.class, MainImpl.class, Base.class)
        .addKeepClassAndMembersRules(XCloneable.class, Base.class)
        .addKeepMainRule(MainImpl.class)
        .addDontObfuscate()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MainImpl.class)
        .assertSuccessWithOutputThatMatches(containsString(Model.class.getTypeName()));
  }

  @Test
  public void testRemovingXCloneWithDefinitionInLibrary()
      throws ExecutionException, CompilationFailedException, IOException {
    R8TestCompileResult library =
        testForR8(parameters.getBackend())
            .addProgramClasses(Base.class)
            .addKeepAllClassesRule()
            .setMinApi(parameters)
            .compile();
    testForR8(parameters.getBackend())
        .addProgramClasses(XCloneable.class, Model.class, Main.class)
        .addLibraryClasses(Base.class)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepClassAndMembersRules(XCloneable.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .addDontObfuscate()
        .compile()
        .addRunClasspathFiles(library.writeToZip())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputThatMatches(containsString(Model.class.getTypeName()));
  }
}
