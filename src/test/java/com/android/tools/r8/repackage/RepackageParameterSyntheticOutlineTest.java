// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageParameterSyntheticOutlineTest extends RepackageTestBase {

  private final String NEW_DESCRIPTOR = "Lfoo/ClassWithCodeToBeOutlined;";
  private final String[] EXPECTED =
      new String[] {"Param::testParam", "Return::print", "Param::testParam", "Return::print"};

  @Parameters(name = "{1}, kind: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(REPACKAGE_CLASSES), // Repackage will use foo as the package name.
        getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public RepackageParameterSyntheticOutlineTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Param.class, Return.class)
        .addProgramClassFileData(
            rewrittenPackageForClassWithCodeToBeOutlined(),
            rewrittenMainWithMethodReferencesToCodeToBeOutlined())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Param.class, Return.class)
        .addProgramClassFileData(
            rewrittenPackageForClassWithCodeToBeOutlined(),
            rewrittenMainWithMethodReferencesToCodeToBeOutlined())
        .addKeepMainRule(Main.class)
        .addKeepClassRulesWithAllowObfuscation(Param.class, Return.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .addOptionsModification(
            options -> {
              options.outline.minSize = 2;
              options.outline.threshold = 2;
            })
        .apply(this::configureRepackaging)
        .addKeepPackageNamesRule("bar**")
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private byte[] rewrittenPackageForClassWithCodeToBeOutlined() throws Exception {
    return transformer(ClassWithCodeToBeOutlined.class)
        .setClassDescriptor(NEW_DESCRIPTOR)
        .transform();
  }

  private byte[] rewrittenMainWithMethodReferencesToCodeToBeOutlined() throws Exception {
    return transformer(Main.class)
        .replaceClassDescriptorInMethodInstructions(
            DescriptorUtils.javaTypeToDescriptor(ClassWithCodeToBeOutlined.class.getTypeName()),
            NEW_DESCRIPTOR)
        .transform();
  }

  // Will be renamed by repackaging
  public static class Param {

    @NeverInline
    public void testParam() {
      System.out.println("Param::testParam");
    }

    @NeverInline
    public Return getReturn() {
      return new Return();
    }
  }

  // Will be renamed by repackaging
  public static class Return {

    public void print() {
      System.out.println("Return::print");
    }
  }

  // Renamed to baz.ClassWithCodeToBeOutlined such that we can keep this package name.
  public static class ClassWithCodeToBeOutlined {

    @NeverInline
    public static Return foo(Param param) {
      param.testParam();
      return param.getReturn();
    }

    @NeverInline
    public static Return bar(Param param) {
      param.testParam();
      return param.getReturn();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Param param = new Param();
      ClassWithCodeToBeOutlined.foo(param).print();
      ClassWithCodeToBeOutlined.bar(param).print();
    }
  }
}
