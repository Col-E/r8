// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cfmethodgeneration.CfClassGenerator;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenerateInstrumentationServerClassesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntime(CfVm.JDK9).build();
  }

  @Test
  public void testInstrumentationServerClassesGenerated() throws IOException {
    for (CfClassGenerator classGenerator : getClassGenerators()) {
      assertEquals(
          FileUtils.readTextFile(classGenerator.getGeneratedFile(), StandardCharsets.UTF_8),
          classGenerator.generateClass());
    }
  }

  public static void main(String[] args) throws IOException {
    for (CfClassGenerator classGenerator : getClassGenerators()) {
      classGenerator.writeClassToFile();
    }
  }

  private static List<CfClassGenerator> getClassGenerators() {
    return ImmutableList.of(
        new InstrumentationServerClassGenerator(InstrumentationServer.class),
        new InstrumentationServerClassGenerator(InstrumentationServerImpl.class));
  }

  private static class InstrumentationServerClassGenerator extends CfClassGenerator {

    private final Class<?> clazz;

    InstrumentationServerClassGenerator(Class<?> clazz) {
      this.clazz = clazz;
    }

    @Override
    protected DexType getGeneratedType() {
      return factory.createType(
          "Lcom/android/tools/r8/startup/generated/" + clazz.getSimpleName() + "Factory;");
    }

    @Override
    public Class<?> getImplementation() {
      return clazz;
    }

    @Override
    protected int getYear() {
      return 2022;
    }
  }
}
