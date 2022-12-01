// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.varhandle;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfTypeInstruction;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenerateVarHandleMethods extends MethodGenerationBase {

  private final DexType GENERATED_TYPE =
      factory.createType("Lcom/android/tools/r8/ir/desugar/varhandle/VarHandleDesugaringMethods;");
  private final List<Class<?>> METHOD_TEMPLATE_CLASSES =
      ImmutableList.of(DesugarVarHandle.class, DesugarMethodHandlesLookup.class);

  protected final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntime(CfVm.JDK9).build();
  }

  public GenerateVarHandleMethods(TestParameters parameters) {
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
  protected List<Class<?>> getClassesToGenerate() {
    return ImmutableList.of(DesugarVarHandle.class, DesugarMethodHandlesLookup.class);
  }

  @Override
  protected boolean includeMethod(DexEncodedMethod method) {
    // Include all methods, including constructors.
    return true;
  }

  @Override
  protected int getYear() {
    return 2022;
  }

  @Override
  protected DexEncodedField getField(DexEncodedField field) {
    if (field.getType().getTypeName().endsWith("$UnsafeStub")) {
      return DexEncodedField.builder(field)
          .setField(
              factory.createField(
                  field.getHolderType(), factory.createType("Lsun/misc/Unsafe;"), field.getName()))
          .disableAndroidApiLevelCheck()
          .build();
    }
    return field;
  }

  // TODO(b/261024278): Share this code.
  private class InstructionTypeMapper {
    private final Map<DexType, DexType> typeMap;

    InstructionTypeMapper(Map<DexType, DexType> typeMap) {
      this.typeMap = typeMap;
    }

    private CfInstruction rewriteInstruction(CfInstruction instruction) {
      if (instruction.isTypeInstruction()) {
        CfInstruction rewritten = rewriteTypeInstruction(instruction.asTypeInstruction());
        return rewritten == null ? instruction : rewritten;
      }
      if (instruction.isFieldInstruction()) {
        return rewriteFieldInstruction(instruction.asFieldInstruction());
      }
      if (instruction.isInvoke()) {
        return rewriteInvokeInstruction(instruction.asInvoke());
      }
      if (instruction.isFrame()) {
        return rewriteFrameInstruction(instruction.asFrame());
      }
      return instruction;
    }

    private CfInstruction rewriteInvokeInstruction(CfInvoke instruction) {
      CfInvoke invoke = instruction.asInvoke();
      DexMethod method = invoke.getMethod();
      String name = method.getName().toString();
      DexType holderType = invoke.getMethod().getHolderType();
      DexType rewrittenType = typeMap.getOrDefault(holderType, holderType);
      if (rewrittenType != holderType) {
        // TODO(b/261024278): If sharing this code also rewrite signature.
        return new CfInvoke(
            invoke.getOpcode(),
            factory.createMethod(
                rewrittenType, invoke.getMethod().getProto(), factory.createString(name)),
            invoke.isInterface());
      }
      return instruction;
    }

    private CfFieldInstruction rewriteFieldInstruction(CfFieldInstruction instruction) {
      DexType holderType = instruction.getField().getHolderType();
      DexType rewrittenHolderType = typeMap.getOrDefault(holderType, holderType);
      DexType fieldType = instruction.getField().getType();
      DexType rewrittenType = typeMap.getOrDefault(fieldType, fieldType);
      if (rewrittenHolderType != holderType || rewrittenType != fieldType) {
        return instruction.createWithField(
            factory.createField(rewrittenHolderType, rewrittenType, instruction.getField().name));
      }
      return instruction;
    }

    private CfInstruction rewriteTypeInstruction(CfTypeInstruction instruction) {
      DexType rewrittenType = typeMap.getOrDefault(instruction.getType(), instruction.getType());
      return rewrittenType != instruction.getType() ? instruction.withType(rewrittenType) : null;
    }

    private CfInstruction rewriteFrameInstruction(CfFrame instruction) {
      return instruction.asFrame().mapReferenceTypes(type -> typeMap.getOrDefault(type, type));
    }
  }

  @Override
  protected CfCode getCode(String holderName, String methodName, CfCode code) {
    if (methodName.endsWith("Stub")) {
      // Don't include stubs targeted only for rewriting in the generated code.
      return null;
    }
    if (!holderName.equals("DesugarVarHandle")
        && !holderName.equals("DesugarMethodHandlesLookup")) {
      throw new RuntimeException("Unexpected: " + holderName);
    }
    // Rewrite references to com.android.tools.r8.ir.desugar.varhandle.DesugarVarHandle to
    // com.android.tools.r8.DesugarVarHandle and rewrite references to UnsafeStub to
    // sun.misc.Unsafe.
    InstructionTypeMapper instructionTypeMapper =
        new InstructionTypeMapper(
            ImmutableMap.of(
                factory.createType(
                    "L" + DesugarVarHandle.class.getTypeName().replace('.', '/') + ";"),
                factory.desugarVarHandleType,
                factory.createType(
                    "L" + DesugarVarHandle.class.getTypeName().replace('.', '/') + "$UnsafeStub;"),
                factory.unsafeType));
    code.setInstructions(
        code.getInstructions().stream()
            .map(instructionTypeMapper::rewriteInstruction)
            .collect(Collectors.toList()));
    return code;
  }

  private DexEncodedMethod methodWithName(DexEncodedMethod method, String name) {
    DexType holder = method.getHolderType();
    DexType desugarVarHandle = factory.desugarVarHandleType;
    DexType desugarVarHandleStub =
        factory.createType("L" + DesugarVarHandle.class.getTypeName().replace('.', '/') + ";");
    // Map methods to be on the final DesugarVarHandle class.
    if (holder == desugarVarHandleStub) {
      holder = desugarVarHandle;
    }
    DexProto proto = method.getProto();
    if (proto.getReturnType() == desugarVarHandleStub) {
      proto = factory.createProto(desugarVarHandle, proto.parameters);
    }
    return DexEncodedMethod.syntheticBuilder(method)
        .setMethod(factory.createMethod(holder, proto, factory.createString(name)))
        .build();
  }

  @Override
  protected DexEncodedMethod mapMethod(DexEncodedMethod method) {
    // Map VarHandle access mode methods to not have the Int/Long postfix.
    for (String prefix : ImmutableList.of("get", "set", "compareAndSet")) {
      if (method.getName().startsWith(prefix)) {
        assert method.getName().toString().substring(prefix.length()).equals("Int")
            || method.getName().toString().substring(prefix.length()).equals("Long")
            || method.getName().toString().equals(prefix);
        return methodWithName(method, prefix);
      }
    }
    return methodWithName(method, method.getName().toString());
  }

  @Test
  public void testVarHandleDesugaringGenerated() throws Exception {
    ArrayList<Class<?>> sorted = new ArrayList<>(getMethodTemplateClasses());
    sorted.sort(Comparator.comparing(Class::getTypeName));
    assertEquals("Classes should be listed in sorted order", sorted, getMethodTemplateClasses());
    assertEquals(
        FileUtils.readTextFile(getGeneratedFile(), StandardCharsets.UTF_8), generateMethods());
  }

  public static void main(String[] args) throws Exception {
    new GenerateVarHandleMethods(null).generateMethodsAndWriteThemToFile();
  }
}
