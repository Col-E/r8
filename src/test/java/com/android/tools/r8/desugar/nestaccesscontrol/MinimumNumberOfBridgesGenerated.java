// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.getMainClass;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.Jdk9TestUtils;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaring;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MinimumNumberOfBridgesGenerated extends TestBase {

  public MinimumNumberOfBridgesGenerated(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntime(DexVm.Version.first())
        .withDexRuntime(DexVm.Version.last())
        .withApiLevelsStartingAtIncluding(apiLevelWithInvokeCustomSupport())
        .enableApiLevelsForCf()
        .build();
  }

  @Test
  public void testOnlyRequiredBridges() throws Exception {
    if (parameters.isDexRuntime()) {
      FullNestOnProgramPathTest.d8CompilationResult
          .apply(parameters.getApiLevel())
          .inspect(this::assertOnlyRequiredBridges);
    }
    FullNestOnProgramPathTest.compileAllNestsR8(
            parameters.getBackend(),
            parameters.getApiLevel(),
            builder ->
                builder.applyIf(parameters.isCfRuntime(), Jdk9TestUtils.addJdk9LibraryFiles(temp)))
        .inspect(this::assertOnlyRequiredBridges);
  }

  private void assertOnlyRequiredBridges(CodeInspector inspector) {
    // The following 2 classes have an extra private member which does not require a bridge.

    // Two bridges for method and staticMethod.
    int methodNumBridges = parameters.isCfRuntime() ? 0 : 2;
    ClassSubject methodMainClass = inspector.clazz(getMainClass("methods"));
    assertEquals(
        methodNumBridges, methodMainClass.allMethods(this::isNestBridge).size());

    // Two bridges for method and staticMethod.
    int constructorNumBridges = parameters.isCfRuntime() ? 0 : 1;
    ClassSubject constructorMainClass = inspector.clazz(getMainClass("constructors"));
    assertEquals(
        constructorNumBridges,
        constructorMainClass.allMethods(this::isNestBridge).size());

    // Four bridges for field and staticField, both get & set.
    int fieldNumBridges = parameters.isCfRuntime() ? 0 : 4;
    ClassSubject fieldMainClass = inspector.clazz(getMainClass("fields"));
    assertEquals(
        fieldNumBridges, fieldMainClass.allMethods(this::isNestBridge).size());
  }

  private boolean isNestBridge(FoundMethodSubject methodSubject) {
    DexEncodedMethod method = methodSubject.getMethod();
    if (method.isInstanceInitializer()) {
      if (method.getReference().proto.parameters.isEmpty()) {
        return false;
      }
      DexType[] formals = method.getReference().proto.parameters.values;
      DexType lastFormal = formals[formals.length - 1];
      return lastFormal.isClassType()
          && SyntheticItemsTestUtils.isInitializerTypeArgument(
              Reference.classFromDescriptor(lastFormal.toDescriptorString()));
    }
    return method
        .getReference()
        .name
        .toString()
        .startsWith(NestBasedAccessDesugaring.NEST_ACCESS_NAME_PREFIX);
  }
}
