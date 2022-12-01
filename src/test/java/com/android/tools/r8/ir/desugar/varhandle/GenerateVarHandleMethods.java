// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.varhandle;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenerateVarHandleMethods extends MethodGenerationBase {

  private final DexType GENERATED_TYPE =
      factory.createType("Lcom/android/tools/r8/ir/desugar/varhandle/VarHandleDesugaringMethods;");
  private final List<Class<?>> METHOD_TEMPLATE_CLASSES = ImmutableList.of(DesugarVarHandle.class);

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
    return ImmutableList.of(DesugarVarHandle.class);
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

  private static CfInstruction rewriteToUnsafe(DexItemFactory factory, CfInstruction instruction) {
    DexType unsafe = factory.unsafeType;
    DexType unsafeStub =
        factory.createType(
            "L" + DesugarVarHandle.class.getTypeName().replace('.', '/') + "$UnsafeStub;");
    // Rewrite references to UnsafeStub to sun.misc.Unsafe
    if (instruction.isTypeInstruction()
        && instruction.asTypeInstruction().getType() == unsafeStub) {
      return instruction.asTypeInstruction().withType(unsafe);
    }
    if (instruction.isFieldInstruction()) {
      CfFieldInstruction fieldInstruction = instruction.asFieldInstruction();
      if (fieldInstruction.getField().getType() == unsafeStub) {
        return fieldInstruction.createWithField(
            factory.createField(
                fieldInstruction.getField().getHolderType(),
                unsafe,
                fieldInstruction.getField().name));
      }
    }
    if (instruction.isInvoke()) {
      CfInvoke invoke = instruction.asInvoke();
      DexMethod method = invoke.getMethod();
      String name = method.getName().toString();
      if (invoke.getMethod().getHolderType() == unsafeStub) {
        return new CfInvoke(
            invoke.getOpcode(),
            factory.createMethod(unsafe, invoke.getMethod().getProto(), factory.createString(name)),
            invoke.isInterface());
      }
    }
    if (instruction.isFrame()) {
      return instruction.asFrame().mapReferenceTypes(type -> (type == unsafeStub) ? unsafe : type);
    }
    return instruction;
  }

  private static CfInstruction rewriteDesugarVarHandle(
      DexItemFactory factory, CfInstruction instruction) {
    // Rewrite references to com.android.tools.r8.ir.desugar.varhandle.DesugarVarHandle to
    // com.android.tools.r8.DesugarVarHandle.
    DexType desugarVarHandle = factory.desugarVarHandleType;
    DexType desugarVarHandleStub =
        factory.createType("L" + DesugarVarHandle.class.getTypeName().replace('.', '/') + ";");
    if (instruction.isFieldInstruction()) {
      CfFieldInstruction fieldInstruction = instruction.asFieldInstruction();
      if (fieldInstruction.getField().getHolderType() == desugarVarHandleStub) {
        return fieldInstruction.createWithField(
            factory.createField(
                desugarVarHandle,
                fieldInstruction.getField().getType(),
                fieldInstruction.getField().name));
      }
    }
    return instruction;
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

  @Override
  protected CfCode getCode(String holderName, String methodName, CfCode code) {
    if (methodName.endsWith("Stub")) {
      // Don't include stubs targeted only for rewriting in the generated code.
      return null;
    }
    if (!holderName.equals("DesugarVarHandle")) {
      throw new RuntimeException("Unexpected");
    }
    code.setInstructions(
        code.getInstructions().stream()
            .map(instruction -> rewriteToUnsafe(factory, instruction))
            .map(instruction -> rewriteDesugarVarHandle(factory, instruction))
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
