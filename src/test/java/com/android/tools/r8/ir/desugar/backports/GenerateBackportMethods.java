// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.TestDataSourceSet;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
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
public class GenerateBackportMethods extends MethodGenerationBase {

  private final DexType GENERATED_TYPE =
      factory.createType("Lcom/android/tools/r8/ir/desugar/backports/BackportedMethods;");
  private final List<Class<?>> METHOD_TEMPLATE_CLASSES =
      ImmutableList.of(
          AssertionErrorMethods.class,
          AtomicReferenceArrayMethods.class,
          AtomicReferenceFieldUpdaterMethods.class,
          AtomicReferenceMethods.class,
          BigDecimalMethods.class,
          BooleanMethods.class,
          ByteMethods.class,
          CharSequenceMethods.class,
          CharacterMethods.class,
          CloseResourceMethod.class,
          CollectionMethods.class,
          CollectionsMethods.class,
          DoubleMethods.class,
          FloatMethods.class,
          IntegerMethods.class,
          LongMethods.class,
          MathMethods.class,
          MethodMethods.class,
          ObjectsMethods.class,
          OptionalMethods.class,
          PredicateMethods.class,
          ShortMethods.class,
          StreamMethods.class,
          StringMethods.class,
          ThrowableMethods.class,
          UnsafeMethods.class);

  protected final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntime(CfVm.JDK9).build();
  }

  public GenerateBackportMethods(TestParameters parameters) {
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
    return 2021;
  }

  private static CfInstruction rewriteToJava9API(
      DexItemFactory itemFactory, CfInstruction instruction) {
    // Rewrite static invoke of javaUtilLongParseUnsignedLongStub to j.l.Long.parseUnsignedLong.
    if (instruction.isInvoke()
        && instruction
            .asInvoke()
            .getMethod()
            .getName()
            .toString()
            .equals("javaLangLongParseUnsignedLongStub")) {
      CfInvoke invoke = instruction.asInvoke();
      return new CfInvoke(
          invoke.getOpcode(),
          itemFactory.createMethod(
              itemFactory.createType("Ljava/lang/Long;"),
              invoke.getMethod().getProto(),
              itemFactory.createString("parseUnsignedLong")),
          invoke.isInterface());
    } else {
      return instruction;
    }
  }

  private static CfInstruction rewriteToUnsafe(
      DexItemFactory itemFactory, CfInstruction instruction) {
    // Rewrite references to UnsafeStub to sun.misc.Unsafe.
    if (instruction.isInvoke()) {
      String name = instruction.asInvoke().getMethod().getName().toString();
      if (name.equals("compareAndSwapObject") || name.equals("getObject")) {
        CfInvoke invoke = instruction.asInvoke();
        return new CfInvoke(
            invoke.getOpcode(),
            itemFactory.createMethod(
                itemFactory.createType("Lsun/misc/Unsafe;"),
                invoke.getMethod().getProto(),
                itemFactory.createString(name)),
            invoke.isInterface());
      }
    }
    if (instruction.isFrame()) {
      return instruction
          .asFrame()
          .mapReferenceTypes(
              type ->
                  (type.getTypeName().endsWith("$UnsafeStub"))
                      ? itemFactory.createType("Lsun/misc/Unsafe;")
                      : type);
    }
    return instruction;
  }

  @Override
  protected CfCode getCode(String holderName, String methodName, CfCode code) {
    if (methodName.endsWith("Stub")) {
      // Don't include stubs targeted only for rewriting in the generated code.
      return null;
    }
    if (holderName.equals("LongMethods") && methodName.equals("parseUnsignedLongWithRadix")) {
      code.setInstructions(
          code.getInstructions().stream()
              .map(instruction -> rewriteToJava9API(factory, instruction))
              .collect(Collectors.toList()));
    }
    if (holderName.equals("UnsafeMethods") && methodName.equals("compareAndSwapObject")) {
      code.setInstructions(
          code.getInstructions().stream()
              .map(instruction -> rewriteToUnsafe(factory, instruction))
              .collect(Collectors.toList()));
    }
    return code;
  }

  @Test
  public void testBackportsGenerated() throws Exception {
    ArrayList<Class<?>> sorted = new ArrayList<>(getMethodTemplateClasses());
    sorted.sort(Comparator.comparing(Class::getTypeName));
    assertEquals("Classes should be listed in sorted order", sorted, getMethodTemplateClasses());
    assertEquals(
        FileUtils.readTextFile(getGeneratedFile(), StandardCharsets.UTF_8), generateMethods());
  }

  public static void main(String[] args) throws Exception {
    setUpSystemPropertiesForMain(TestDataSourceSet.TESTS_JAVA_8);
    new GenerateBackportMethods(null).generateMethodsAndWriteThemToFile();
  }
}
