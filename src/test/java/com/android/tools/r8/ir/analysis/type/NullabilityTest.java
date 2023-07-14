// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ToolHelper.getMostRecentAndroidJar;
import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.android.tools.r8.ir.analysis.type.TypeElement.fromDexType;
import static com.android.tools.r8.ir.analysis.type.TypeElement.stringClassType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AssumeInserter;
import com.android.tools.r8.ir.optimize.nonnull.FieldAccessTest;
import com.android.tools.r8.ir.optimize.nonnull.NonNullAfterArrayAccess;
import com.android.tools.r8.ir.optimize.nonnull.NonNullAfterFieldAccess;
import com.android.tools.r8.ir.optimize.nonnull.NonNullAfterInvoke;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NullabilityTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public NullabilityTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private void buildAndTest(
      Collection<Class<?>> classes,
      Class<?> mainClass,
      MethodSignature signature,
      boolean npeCaught,
      BiConsumer<AppView<?>, IRCode> inspector)
      throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(classes).addLibraryFile(getMostRecentAndroidJar()).build());
    CodeInspector codeInspector = new CodeInspector(appView.appInfo().app());
    MethodSubject fooSubject = codeInspector.clazz(mainClass.getName()).method(signature);
    IRCode irCode = fooSubject.buildIR(appView);
    new AssumeInserter(appView).insertAssumeInstructions(irCode, Timing.empty());
    inspector.accept(appView, irCode);
    verifyLastInvoke(irCode, npeCaught);
  }

  private static void verifyClassTypeLattice(
      Map<Class<? extends Instruction>, TypeElement> expectedLattices,
      DexType receiverType,
      Value v,
      TypeElement l) {
    // Due to the last invocation that will check nullability of the argument,
    // there is one exceptional mapping to PRIMITIVE.
    if (l.isPrimitiveType()) {
      return;
    }
    assertTrue(l.isClassType());
    ClassTypeElement lattice = l.asClassType();
    // Receiver
    if (lattice.getClassType().equals(receiverType)) {
      assertFalse(l.isNullable());
    } else {
      Instruction definition = v.definition;
      if (definition != null) {
        TypeElement expected = expectedLattices.get(v.definition.getClass());
        if (expected != null) {
          assertEquals(expected, l);
        }
      }
    }
  }

  private void verifyLastInvoke(IRCode code, boolean npeCaught) {
    boolean metInvokeVirtual = false;
    for (Instruction instruction : code.instructions()) {
      if (instruction.isInvokeMethodWithReceiver()) {
        InvokeMethodWithReceiver invoke = instruction.asInvokeMethodWithReceiver();
        if (invoke.getInvokedMethod().name.toString().contains("hash")) {
          metInvokeVirtual = true;
          TypeElement l = invoke.getReceiver().getType();
          assertEquals(npeCaught, l.isNullable());
        }
      }
    }
    assertTrue(metInvokeVirtual);
  }

  private void forEachOutValue(IRCode irCode, BiConsumer<Value, TypeElement> consumer) {
    irCode
        .instructionIterator()
        .forEachRemaining(
            instruction -> {
              Value outValue = instruction.outValue();
              if (outValue != null) {
                TypeElement element = outValue.getType();
                consumer.accept(outValue, element);
              }
            });
  }

  @Test
  public void nonNullAfterSafeInvokes() throws Exception {
    MethodSignature signature =
        new MethodSignature("foo", "int", new String[]{"java.lang.String"});
    buildAndTest(
        ImmutableList.of(NonNullAfterInvoke.class),
        NonNullAfterInvoke.class,
        signature,
        false,
        (appInfo, irCode) -> {
          DexType assertionErrorType =
              appInfo.dexItemFactory().createType("Ljava/lang/AssertionError;");
          DexType mainClass =
              appInfo
                  .dexItemFactory()
                  .createType(
                      DescriptorUtils.javaTypeToDescriptor(
                          NonNullAfterInvoke.class.getCanonicalName()));
          Map<Class<? extends Instruction>, TypeElement> expectedLattices =
              ImmutableMap.of(
                  InvokeVirtual.class, stringClassType(appInfo, maybeNull()),
                  Assume.class, stringClassType(appInfo, definitelyNotNull()),
                  NewInstance.class, fromDexType(assertionErrorType, definitelyNotNull(), appInfo));
          forEachOutValue(
              irCode, (v, l) -> verifyClassTypeLattice(expectedLattices, mainClass, v, l));
        });
  }

  @Test
  public void stillNullAfterExceptionCatch_invoke() throws Exception {
    MethodSignature signature =
        new MethodSignature("bar", "int", new String[]{"java.lang.String"});
    buildAndTest(
        ImmutableList.of(NonNullAfterInvoke.class),
        NonNullAfterInvoke.class,
        signature,
        true,
        (appInfo, irCode) -> {
          DexType assertionErrorType =
              appInfo.dexItemFactory().createType("Ljava/lang/AssertionError;");
          DexType mainClass =
              appInfo
                  .dexItemFactory()
                  .createType(
                      DescriptorUtils.javaTypeToDescriptor(
                          NonNullAfterInvoke.class.getCanonicalName()));
          Map<Class<? extends Instruction>, TypeElement> expectedLattices =
              ImmutableMap.of(
                  InvokeVirtual.class, stringClassType(appInfo, maybeNull()),
                  Assume.class, stringClassType(appInfo, definitelyNotNull()),
                  NewInstance.class, fromDexType(assertionErrorType, definitelyNotNull(), appInfo));
          forEachOutValue(
              irCode, (v, l) -> verifyClassTypeLattice(expectedLattices, mainClass, v, l));
        });
  }

  @Test
  public void nonNullAfterSafeArrayAccess() throws Exception {
    MethodSignature signature =
        new MethodSignature("foo", "int", new String[]{"java.lang.String[]"});
    buildAndTest(
        ImmutableList.of(NonNullAfterArrayAccess.class),
        NonNullAfterArrayAccess.class,
        signature,
        false,
        (appInfo, irCode) -> {
          DexType assertionErrorType =
              appInfo.dexItemFactory().createType("Ljava/lang/AssertionError;");
          DexType mainClass =
              appInfo
                  .dexItemFactory()
                  .createType(
                      DescriptorUtils.javaTypeToDescriptor(
                          NonNullAfterArrayAccess.class.getCanonicalName()));
          Map<Class<? extends Instruction>, TypeElement> expectedLattices =
              ImmutableMap.of(
                  // An element inside a non-null array could be null.
                  ArrayGet.class,
                      fromDexType(appInfo.dexItemFactory().stringType, maybeNull(), appInfo),
                  NewInstance.class, fromDexType(assertionErrorType, definitelyNotNull(), appInfo));
          forEachOutValue(
              irCode,
              (v, l) -> {
                if (l.isArrayType()) {
                  ArrayTypeElement lattice = l.asArrayType();
                  assertEquals(1, lattice.getNesting());
                  TypeElement elementType = lattice.getMemberType();
                  assertTrue(elementType.isClassType());
                  assertEquals(
                      appInfo.dexItemFactory().stringType,
                      elementType.asClassType().getClassType());
                  assertEquals(v.definition.isArgument(), l.isNullable());
                } else if (l.isClassType()) {
                  verifyClassTypeLattice(expectedLattices, mainClass, v, l);
                }
              });
        });
  }

  @Test
  public void stillNullAfterExceptionCatch_aget() throws Exception {
    MethodSignature signature =
        new MethodSignature("bar", "int", new String[]{"java.lang.String[]"});
    buildAndTest(
        ImmutableList.of(NonNullAfterArrayAccess.class),
        NonNullAfterArrayAccess.class,
        signature,
        true,
        (appInfo, irCode) -> {
          DexType assertionErrorType =
              appInfo.dexItemFactory().createType("Ljava/lang/AssertionError;");
          DexType mainClass =
              appInfo
                  .dexItemFactory()
                  .createType(
                      DescriptorUtils.javaTypeToDescriptor(
                          NonNullAfterArrayAccess.class.getCanonicalName()));
          Map<Class<? extends Instruction>, TypeElement> expectedLattices =
              ImmutableMap.of(
                  // An element inside a non-null array could be null.
                  ArrayGet.class,
                      fromDexType(appInfo.dexItemFactory().stringType, maybeNull(), appInfo),
                  NewInstance.class, fromDexType(assertionErrorType, definitelyNotNull(), appInfo));
          forEachOutValue(
              irCode,
              (v, l) -> {
                if (l.isArrayType()) {
                  ArrayTypeElement lattice = l.asArrayType();
                  assertEquals(1, lattice.getNesting());
                  TypeElement elementTypeLattice = lattice.getMemberType();
                  assertTrue(elementTypeLattice.isClassType());
                  assertEquals(
                      appInfo.dexItemFactory().stringType,
                      elementTypeLattice.asClassType().getClassType());
                  assertEquals(v.definition.isArgument(), l.isNullable());
                } else if (l.isClassType()) {
                  verifyClassTypeLattice(expectedLattices, mainClass, v, l);
                }
              });
        });
  }

  @Test
  public void nonNullAfterSafeFieldAccess() throws Exception {
    MethodSignature signature = new MethodSignature("foo", "int",
        new String[]{FieldAccessTest.class.getCanonicalName()});
    buildAndTest(
        ImmutableList.of(FieldAccessTest.class, NonNullAfterFieldAccess.class),
        NonNullAfterFieldAccess.class,
        signature,
        false,
        (appInfo, irCode) -> {
          DexType assertionErrorType =
              appInfo.dexItemFactory().createType("Ljava/lang/AssertionError;");
          DexType mainClass =
              appInfo
                  .dexItemFactory()
                  .createType(
                      DescriptorUtils.javaTypeToDescriptor(
                          NonNullAfterFieldAccess.class.getCanonicalName()));
          DexType testClass =
              appInfo
                  .dexItemFactory()
                  .createType(
                      DescriptorUtils.javaTypeToDescriptor(
                          FieldAccessTest.class.getCanonicalName()));
          Map<Class<? extends Instruction>, TypeElement> expectedLattices =
              ImmutableMap.of(
                  Argument.class, fromDexType(testClass, maybeNull(), appInfo),
                  Assume.class, fromDexType(testClass, definitelyNotNull(), appInfo),
                  // instance may not be initialized.
                  InstanceGet.class,
                      fromDexType(appInfo.dexItemFactory().stringType, maybeNull(), appInfo),
                  NewInstance.class, fromDexType(assertionErrorType, definitelyNotNull(), appInfo));
          forEachOutValue(
              irCode, (v, l) -> verifyClassTypeLattice(expectedLattices, mainClass, v, l));
        });
  }

  @Test
  public void stillNullAfterExceptionCatch_iget() throws Exception {
    MethodSignature signature = new MethodSignature("bar", "int",
        new String[]{FieldAccessTest.class.getCanonicalName()});
    buildAndTest(
        ImmutableList.of(FieldAccessTest.class, NonNullAfterFieldAccess.class),
        NonNullAfterFieldAccess.class,
        signature,
        true,
        (appInfo, irCode) -> {
          DexType assertionErrorType =
              appInfo.dexItemFactory().createType("Ljava/lang/AssertionError;");
          DexType mainClass =
              appInfo
                  .dexItemFactory()
                  .createType(
                      DescriptorUtils.javaTypeToDescriptor(
                          NonNullAfterFieldAccess.class.getCanonicalName()));
          DexType testClass =
              appInfo
                  .dexItemFactory()
                  .createType(
                      DescriptorUtils.javaTypeToDescriptor(
                          FieldAccessTest.class.getCanonicalName()));
          Map<Class<? extends Instruction>, TypeElement> expectedLattices =
              ImmutableMap.of(
                  Argument.class, fromDexType(testClass, maybeNull(), appInfo),
                  Assume.class, fromDexType(testClass, definitelyNotNull(), appInfo),
                  // instance may not be initialized.
                  InstanceGet.class,
                      fromDexType(appInfo.dexItemFactory().stringType, maybeNull(), appInfo),
                  NewInstance.class, fromDexType(assertionErrorType, definitelyNotNull(), appInfo));
          forEachOutValue(
              irCode, (v, l) -> verifyClassTypeLattice(expectedLattices, mainClass, v, l));
        });
  }
}
