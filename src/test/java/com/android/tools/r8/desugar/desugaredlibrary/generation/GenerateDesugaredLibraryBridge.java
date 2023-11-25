// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.generation;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.TestDataSourceSet;
import com.android.tools.r8.cfmethodgeneration.InstructionTypeMapper;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.desugar.desugaredlibrary.generation.DesugaredLibraryBridge.NavType;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenerateDesugaredLibraryBridge extends MethodGenerationBase {

  private final DexType GENERATED_TYPE =
      factory.createType(
          "Lcom/android/tools/r8/ir/desugar/desugaredlibrary/retargeter/DesugaredLibraryCfMethods;");
  private final List<Class<?>> METHOD_TEMPLATE_CLASSES =
      ImmutableList.of(DesugaredLibraryBridge.class);

  protected final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntime(CfVm.JDK9).build();
  }

  public GenerateDesugaredLibraryBridge(TestParameters parameters) {
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
    return 2023;
  }

  @Test
  public void testDesugaredLibraryBridge() throws Exception {
    ArrayList<Class<?>> sorted = new ArrayList<>(getMethodTemplateClasses());
    sorted.sort(Comparator.comparing(Class::getTypeName));
    assertEquals("Classes should be listed in sorted order", sorted, getMethodTemplateClasses());
    assertEquals(
        FileUtils.readTextFile(getGeneratedFile(), StandardCharsets.UTF_8), generateMethods());
  }

  @Override
  protected CfCode getCode(String holderName, String methodName, CfCode code) {
    InstructionTypeMapper instructionTypeMapper =
        new InstructionTypeMapper(
            factory,
            ImmutableMap.of(
                factory.createType(DescriptorUtils.javaClassToDescriptor(NavType.class)),
                factory.createType("Landroidx/navigation/NavType;"),
                factory.createType(DescriptorUtils.javaClassToDescriptor(NavType.Companion.class)),
                factory.createType("Landroidx/navigation/NavType$Companion;")),
            Function.identity());
    code.setInstructions(
        code.getInstructions().stream()
            .map(instructionTypeMapper::rewriteInstruction)
            .collect(Collectors.toList()));
    return code;
  }

  public static void main(String[] args) throws Exception {
    setUpSystemPropertiesForMain(TestDataSourceSet.TESTS_JAVA_8);
    new GenerateDesugaredLibraryBridge(null).generateMethodsAndWriteThemToFile();
  }
}
