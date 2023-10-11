// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.TestDataSourceSet;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenerateEnumUnboxingMethods extends MethodGenerationBase {

  private final DexType GENERATED_TYPE =
      factory.createType("Lcom/android/tools/r8/ir/optimize/enums/EnumUnboxingCfMethods;");
  private final List<Class<?>> METHOD_TEMPLATE_CLASSES =
      ImmutableList.of(EnumUnboxingMethods.class);

  protected final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntime(CfVm.JDK9).build();
  }

  public GenerateEnumUnboxingMethods(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Override
  protected DexType getGeneratedType() {
    return GENERATED_TYPE;
  }

  @Override
  protected List<Class<?>> getMethodTemplateClasses() {
    return METHOD_TEMPLATE_CLASSES;
  }

  @Override
  protected int getYear() {
    return 2020;
  }

  @Test
  public void testEnumUtilityMethodsGenerated() throws Exception {
    ArrayList<Class<?>> sorted = new ArrayList<>(getMethodTemplateClasses());
    sorted.sort(Comparator.comparing(Class::getTypeName));
    assertEquals("Classes should be listed in sorted order", sorted, getMethodTemplateClasses());
    assertEquals(
        FileUtils.readTextFile(getGeneratedFile(), StandardCharsets.UTF_8), generateMethods());
  }

  public static void main(String[] args) throws Exception {
    setUpSystemPropertiesForMain(TestDataSourceSet.TESTS_JAVA_8);
    new GenerateEnumUnboxingMethods(null).generateMethodsAndWriteThemToFile();
  }
}
