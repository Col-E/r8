// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.TypeLatticeElement.fromDexType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.NonNull;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.NonNullTracker;
import com.android.tools.r8.ir.optimize.NonNullTrackerTestBase;
import com.android.tools.r8.ir.optimize.nonnull.FieldAccessTest;
import com.android.tools.r8.ir.optimize.nonnull.NonNullAfterArrayAccess;
import com.android.tools.r8.ir.optimize.nonnull.NonNullAfterFieldAccess;
import com.android.tools.r8.ir.optimize.nonnull.NonNullAfterInvoke;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.Test;

public class NullabilityTest extends NonNullTrackerTestBase {
  private void buildAndTest(
      Class<?> mainClass,
      MethodSignature signature,
      boolean npeCaught,
      BiConsumer<AppInfo, IRCode> inspector)
      throws Exception {
    AppInfo appInfo = build(mainClass);
    CodeInspector codeInspector = new CodeInspector(appInfo.app);
    DexEncodedMethod foo = codeInspector.clazz(mainClass.getName()).method(signature).getMethod();
    IRCode irCode =
        foo.buildIR(appInfo, GraphLense.getIdentityLense(), TEST_OPTIONS, Origin.unknown());
    NonNullTracker nonNullTracker = new NonNullTracker();
    nonNullTracker.addNonNull(irCode);
    TypeAnalysis analysis = new TypeAnalysis(appInfo, foo);
    analysis.widening(foo, irCode);
    inspector.accept(appInfo, irCode);
    verifyLastInvoke(irCode, npeCaught);
  }

  private static void verifyClassTypeLattice(
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices,
      DexType receiverType,
      Value v,
      TypeLatticeElement l) {
    // Due to the last invocation that will check nullability of the argument,
    // there is one exceptional mapping to PRIMITIVE.
    if (l.isPrimitive()) {
      return;
    }
    assertTrue(l.isClassType());
    ClassTypeLatticeElement lattice = l.asClassTypeLatticeElement();
    // Receiver
    if (lattice.getClassType().equals(receiverType)) {
      assertFalse(l.isNullable());
    } else {
      Instruction definition = v.definition;
      if (definition != null) {
        TypeLatticeElement expected = expectedLattices.get(v.definition.getClass());
        if (expected != null) {
          assertEquals(expected, l);
        }
      }
    }
  }

  private void verifyLastInvoke(IRCode code, boolean npeCaught) {
    InstructionIterator it = code.instructionIterator();
    boolean metInvokeVirtual = false;
    while (it.hasNext()) {
      Instruction instruction = it.next();
      if (instruction.isInvokeMethodWithReceiver()) {
        InvokeMethodWithReceiver invoke = instruction.asInvokeMethodWithReceiver();
        if (invoke.getInvokedMethod().name.toString().contains("hash")) {
          metInvokeVirtual = true;
          TypeLatticeElement l = invoke.getReceiver().getTypeLattice();
          assertEquals(npeCaught, l.isNullable());
        }
      }
    }
    assertTrue(metInvokeVirtual);
  }

  private void forEachOutValue(IRCode irCode, BiConsumer<Value, TypeLatticeElement> consumer) {
    irCode.instructionIterator().forEachRemaining(instruction -> {
      Value outValue = instruction.outValue();
      if (outValue != null) {
        TypeLatticeElement element = outValue.getTypeLattice();
        consumer.accept(outValue, element);
      }
    });
  }

  @Test
  public void nonNullAfterSafeInvokes() throws Exception {
    MethodSignature signature =
        new MethodSignature("foo", "int", new String[]{"java.lang.String"});
    buildAndTest(NonNullAfterInvoke.class, signature, false, (appInfo, irCode) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType mainClass = appInfo.dexItemFactory.createType(
          DescriptorUtils.javaTypeToDescriptor(NonNullAfterInvoke.class.getCanonicalName()));
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          InvokeVirtual.class, fromDexType(appInfo, appInfo.dexItemFactory.stringType, true),
          NonNull.class, fromDexType(appInfo, appInfo.dexItemFactory.stringType, false),
          NewInstance.class, fromDexType(appInfo, assertionErrorType, false));
      forEachOutValue(irCode, (v, l) -> verifyClassTypeLattice(expectedLattices, mainClass, v, l));
    });
  }

  @Test
  public void stillNullAfterExceptionCatch_invoke() throws Exception {
    MethodSignature signature =
        new MethodSignature("bar", "int", new String[]{"java.lang.String"});
    buildAndTest(NonNullAfterInvoke.class, signature, true, (appInfo, irCode) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType mainClass = appInfo.dexItemFactory.createType(
          DescriptorUtils.javaTypeToDescriptor(NonNullAfterInvoke.class.getCanonicalName()));
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          InvokeVirtual.class, fromDexType(appInfo, appInfo.dexItemFactory.stringType, true),
          NonNull.class, fromDexType(appInfo, appInfo.dexItemFactory.stringType, false),
          NewInstance.class, fromDexType(appInfo, assertionErrorType, false));
      forEachOutValue(irCode, (v, l) -> verifyClassTypeLattice(expectedLattices, mainClass, v, l));
    });
  }

  @Test
  public void nonNullAfterSafeArrayAccess() throws Exception {
    MethodSignature signature =
        new MethodSignature("foo", "int", new String[]{"java.lang.String[]"});
    buildAndTest(NonNullAfterArrayAccess.class, signature, false, (appInfo, irCode) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType mainClass = appInfo.dexItemFactory.createType(
          DescriptorUtils.javaTypeToDescriptor(NonNullAfterArrayAccess.class.getCanonicalName()));
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          // An element inside a non-null array could be null.
          ArrayGet.class, fromDexType(appInfo, appInfo.dexItemFactory.stringType, true),
          NewInstance.class, fromDexType(appInfo, assertionErrorType, false));
      forEachOutValue(irCode, (v, l) -> {
        if (l.isArrayType()) {
          ArrayTypeLatticeElement lattice = l.asArrayTypeLatticeElement();
          assertEquals(
              appInfo.dexItemFactory.stringType,
              lattice.getArrayElementType(appInfo.dexItemFactory));
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
    buildAndTest(NonNullAfterArrayAccess.class, signature, true, (appInfo, irCode) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType mainClass = appInfo.dexItemFactory.createType(
          DescriptorUtils.javaTypeToDescriptor(NonNullAfterArrayAccess.class.getCanonicalName()));
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          // An element inside a non-null array could be null.
          ArrayGet.class, fromDexType(appInfo, appInfo.dexItemFactory.stringType, true),
          NewInstance.class, fromDexType(appInfo, assertionErrorType, false));
      forEachOutValue(irCode, (v, l) -> {
        if (l.isArrayType()) {
          ArrayTypeLatticeElement lattice = l.asArrayTypeLatticeElement();
          assertEquals(
              appInfo.dexItemFactory.stringType,
              lattice.getArrayElementType(appInfo.dexItemFactory));
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
    buildAndTest(NonNullAfterFieldAccess.class, signature, false, (appInfo, irCode) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType mainClass = appInfo.dexItemFactory.createType(
          DescriptorUtils.javaTypeToDescriptor(NonNullAfterFieldAccess.class.getCanonicalName()));
      DexType testClass = appInfo.dexItemFactory.createType(
          DescriptorUtils.javaTypeToDescriptor(FieldAccessTest.class.getCanonicalName()));
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          Argument.class, fromDexType(appInfo, testClass, true),
          NonNull.class, fromDexType(appInfo, testClass, false),
          // instance may not be initialized.
          InstanceGet.class, fromDexType(appInfo, appInfo.dexItemFactory.stringType, true),
          NewInstance.class, fromDexType(appInfo, assertionErrorType, false));
      forEachOutValue(irCode, (v, l) -> verifyClassTypeLattice(expectedLattices, mainClass, v, l));
    });
  }

  @Test
  public void stillNullAfterExceptionCatch_iget() throws Exception {
    MethodSignature signature = new MethodSignature("bar", "int",
        new String[]{FieldAccessTest.class.getCanonicalName()});
    buildAndTest(NonNullAfterFieldAccess.class, signature, true, (appInfo, irCode) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType mainClass = appInfo.dexItemFactory.createType(
          DescriptorUtils.javaTypeToDescriptor(NonNullAfterFieldAccess.class.getCanonicalName()));
      DexType testClass = appInfo.dexItemFactory.createType(
          DescriptorUtils.javaTypeToDescriptor(FieldAccessTest.class.getCanonicalName()));
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          Argument.class, fromDexType(appInfo, testClass, true),
          NonNull.class, fromDexType(appInfo, testClass, false),
          // instance may not be initialized.
          InstanceGet.class, fromDexType(appInfo, appInfo.dexItemFactory.stringType, true),
          NewInstance.class, fromDexType(appInfo, assertionErrorType, false));
      forEachOutValue(irCode, (v, l) -> verifyClassTypeLattice(expectedLattices, mainClass, v, l));
    });
  }
}
