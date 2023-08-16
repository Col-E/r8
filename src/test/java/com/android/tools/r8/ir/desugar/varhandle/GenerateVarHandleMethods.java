// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.varhandle;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cfmethodgeneration.InstructionTypeMapper;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenerateVarHandleMethods extends MethodGenerationBase {

  private final DexType GENERATED_TYPE =
      factory.createType(DescriptorUtils.javaClassToDescriptor(VarHandleDesugaringMethods.class));
  private final List<Class<?>> METHOD_TEMPLATE_CLASSES =
      ImmutableList.of(DesugarMethodHandlesLookup.class, DesugarVarHandle.class);

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
          .setField(factory.createField(field.getHolderType(), factory.unsafeType, field.getName()))
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
    if (!holderName.equals("DesugarVarHandle")
        && !holderName.equals("DesugarMethodHandlesLookup")) {
      throw new RuntimeException("Unexpected: " + holderName);
    }
    // Rewrite references to com.android.tools.r8.ir.desugar.varhandle.DesugarVarHandle to
    // com.android.tools.r8.DesugarVarHandle and rewrite references to UnsafeStub to
    // sun.misc.Unsafe.
    InstructionTypeMapper instructionTypeMapper =
        new InstructionTypeMapper(
            factory,
            ImmutableMap.of(
                factory.createType(
                    DescriptorUtils.javaClassToDescriptor(DesugarMethodHandlesLookup.class)),
                factory.lookupType,
                factory.createType(DescriptorUtils.javaClassToDescriptor(DesugarVarHandle.class)),
                factory.varHandleType,
                factory.createType(
                    DescriptorUtils.javaClassToDescriptor(DesugarVarHandle.UnsafeStub.class)),
                factory.unsafeType),
            GenerateVarHandleMethods::mapMethodName);
    code.setInstructions(
        code.getInstructions().stream()
            .map(instructionTypeMapper::rewriteInstruction)
            .collect(Collectors.toList()));
    return code;
  }

  private DexEncodedMethod methodWithName(DexEncodedMethod method, String name) {
    DexType holder = method.getHolderType();
    DexType varHandle = factory.varHandleType;
    DexType methodHandlesLookup = factory.lookupType;
    DexType desugarVarHandleStub =
        factory.createType(DescriptorUtils.javaClassToDescriptor(DesugarVarHandle.class));
    DexType desugarMethodHandlesLookupStub =
        factory.createType(DescriptorUtils.javaClassToDescriptor(DesugarMethodHandlesLookup.class));
    // Map methods to be on the final DesugarVarHandle class.
    if (holder == desugarVarHandleStub) {
      holder = varHandle;
    } else if (holder == desugarMethodHandlesLookupStub) {
      holder = methodHandlesLookup;
    }
    DexProto proto = method.getProto();
    if (proto.getReturnType() == desugarVarHandleStub) {
      proto = factory.createProto(varHandle, proto.parameters);
    } else if (proto.getReturnType() == desugarMethodHandlesLookupStub) {
      proto = factory.createProto(methodHandlesLookup, proto.parameters);
    }
    return DexEncodedMethod.syntheticBuilder(method)
        .setMethod(factory.createMethod(holder, proto, factory.createString(name)))
        .build();
  }

  @Override
  protected DexEncodedMethod mapMethod(DexEncodedMethod method) {
    // Map VarHandle access mode methods to not have the Int/Long postfix.
    return methodWithName(method, mapMethodName(method.getName().toString()));
  }

  private static String mapMethodName(String name) {
    Set<String> postfixes =
        ImmutableSet.of("InBox", "Int", "Long", "Array", "ArrayInBox", "ArrayInt", "ArrayLong");
    for (String prefix :
        ImmutableList.of(
            "get",
            "set",
            "compareAndSet",
            "weakCompareAndSet",
            "getVolatile",
            "setVolatile",
            "setRelease")) {
      if (name.startsWith(prefix)) {
        String postfix = name.substring(prefix.length());
        if (postfixes.contains(postfix)) {
          return prefix;
        }
      }
    }
    return name;
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
