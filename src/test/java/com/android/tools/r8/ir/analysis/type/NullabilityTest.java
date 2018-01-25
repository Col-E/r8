// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.NonNull;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.NonNullMarker;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.Test;

public class NullabilityTest extends SmaliTestBase {
  private final String CLASS_NAME = "Example";
  private static final InternalOptions TEST_OPTIONS = new InternalOptions();

  private void buildAndTest(
      SmaliBuilder builder,
      MethodSignature signature,
      boolean npeCaught,
      BiConsumer<AppInfo, TypeAnalysis> inspector)
      throws Exception {
    AndroidApp app = builder.build();
    DexApplication dexApplication =
        new ApplicationReader(app, TEST_OPTIONS, new Timing("NullabilityTest.appReader"))
            .read().toDirect();
    AppInfo appInfo = new AppInfo(dexApplication);
    DexInspector dexInspector = new DexInspector(appInfo.app);
    DexEncodedMethod foo = dexInspector.clazz(CLASS_NAME).method(signature).getMethod();
    IRCode irCode = foo.buildIR(TEST_OPTIONS);
    NonNullMarker nonNullMarker = new NonNullMarker();
    nonNullMarker.addNonNull(irCode);
    TypeAnalysis analysis = new TypeAnalysis(appInfo, foo, irCode);
    inspector.accept(appInfo, analysis);
    verifyLastInvoke(irCode, analysis, npeCaught);
  }

  private static void verifyClassTypeLattice(
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices,
      DexType receiverType,
      Value v,
      TypeLatticeElement l) {
    assertTrue(l.isClassTypeLatticeElement());
    ClassTypeLatticeElement lattice = l.asClassTypeLatticeElement();
    // Receiver
    if (lattice.getClassType().equals(receiverType)) {
      assertFalse(l.isNullable());
    } else {
      TypeLatticeElement expected = expectedLattices.get(v.definition.getClass());
      if (expected != null) {
        assertEquals(expected, l);
      }
    }
  }

  private void verifyLastInvoke(IRCode code, TypeAnalysis analysis, boolean npeCaught) {
    InstructionIterator it = code.instructionIterator();
    boolean metInvokeVirtual = false;
    while (it.hasNext()) {
      Instruction instruction = it.next();
      if (instruction instanceof InvokeVirtual) {
        InvokeVirtual invokeVirtual = instruction.asInvokeVirtual();
        if (invokeVirtual.getInvokedMethod().name.toString().contains("hash")) {
          metInvokeVirtual = true;
          TypeLatticeElement l = analysis.getLatticeElement(invokeVirtual.getReceiver());
          assertEquals(npeCaught, l.isNullable());
        }
      }
    }
    assertTrue(metInvokeVirtual);
  }

  @Test
  public void nonNullAfterSafeInvokes() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    MethodSignature signature =
        builder.addInstanceMethod("void", "foo", ImmutableList.of("java.lang.String"), 1,
            "invoke-virtual {p1}, Ljava/lang/String;->toString()Ljava/lang/String;",
            // Successful invocation above means p1 is not null.
            "if-nez p1, :not_null",
            "new-instance v0, Ljava/lang/AssertionError;",
            "throw v0",
            ":not_null",
            // And thus the call below uses the same, non-null value.
            "invoke-virtual {p1}, Ljava/lang/String;->hashCode()I",
            "return-void"
        );
    buildAndTest(builder, signature, false, (appInfo, typeAnalysis) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType example = appInfo.dexItemFactory.createType("LExample;");
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          InvokeVirtual.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, true),
          NonNull.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, false),
          NewInstance.class, new ClassTypeLatticeElement(assertionErrorType, false));
      typeAnalysis.forEach((v, l) -> verifyClassTypeLattice(expectedLattices, example, v, l));
    });
  }

  @Test
  public void stillNullAfterExceptionCatch_invoke() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    MethodSignature signature =
        builder.addInstanceMethod("void", "foo", ImmutableList.of("java.lang.String"), 1,
            ":try_start",
            "invoke-virtual {p1}, Ljava/lang/String;->toString()Ljava/lang/String;",
            "if-nez p1, :return",
            "new-instance v0, Ljava/lang/AssertionError;",
            "throw v0",
            ":try_end",
            ".catch Ljava/lang/Throwable; {:try_start .. :try_end} :return",
            ":return",
            // p1 could be still null at the outside of try-catch.
            "invoke-virtual {p1}, Ljava/lang/String;->hashCode()I",
            "return-void"
        );
    buildAndTest(builder, signature, true, (appInfo, typeAnalysis) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType example = appInfo.dexItemFactory.createType("LExample;");
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          InvokeVirtual.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, true),
          NonNull.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, false),
          NewInstance.class, new ClassTypeLatticeElement(assertionErrorType, false));
      typeAnalysis.forEach((v, l) -> verifyClassTypeLattice(expectedLattices, example, v, l));
    });
  }

  @Test
  public void nonNullAfterSafeArrayAccess() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    MethodSignature signature =
        builder.addInstanceMethod("void", "foo", ImmutableList.of("java.lang.String[]"), 1,
            "const/4 v0, 0",
            "aget-object v0, p1, v0",
            // Successful array access above means p1 is not null.
            "if-nez p1, :not_null",
            "new-instance v0, Ljava/lang/AssertionError;",
            "throw v0",
            ":not_null",
            // And thus the call below uses the same, non-null value.
            "invoke-virtual {p1}, [Ljava/lang/String;->hashCode()I",
            "return-void"
        );
    buildAndTest(builder, signature, false, (appInfo, typeAnalysis) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType example = appInfo.dexItemFactory.createType("LExample;");
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          // An element inside a non-null array could be null.
          ArrayGet.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, true),
          NewInstance.class, new ClassTypeLatticeElement(assertionErrorType, false));
      typeAnalysis.forEach((v, l) -> {
        if (l.isArrayTypeLatticeElement()) {
          ArrayTypeLatticeElement lattice = l.asArrayTypeLatticeElement();
          assertEquals(
              appInfo.dexItemFactory.stringType,
              lattice.getArrayElementType(appInfo.dexItemFactory));
          assertEquals(v.definition.isArgument(), l.isNullable());
        } else if (l.isClassTypeLatticeElement()) {
          verifyClassTypeLattice(expectedLattices, example, v, l);
        }
      });
    });
  }

  @Test
  public void stillNullAfterExceptionCatch_aget() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    MethodSignature signature =
        builder.addInstanceMethod("void", "foo", ImmutableList.of("java.lang.String[]"), 1,
            ":try_start",
            "const/4 v0, 0",
            "aget-object v0, p1, v0",
            "if-nez p1, :return",
            "new-instance v0, Ljava/lang/AssertionError;",
            "throw v0",
            ":try_end",
            ".catch Ljava/lang/Throwable; {:try_start .. :try_end} :return",
            ":return",
            // p1 could be still null at the outside of try-catch.
            "invoke-virtual {p1}, [Ljava/lang/String;->hashCode()I",
            "return-void"
        );
    buildAndTest(builder, signature, true, (appInfo, typeAnalysis) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType example = appInfo.dexItemFactory.createType("LExample;");
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          // An element inside a non-null array could be null.
          ArrayGet.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, true),
          NewInstance.class, new ClassTypeLatticeElement(assertionErrorType, false));
      typeAnalysis.forEach((v, l) -> {
        if (l.isArrayTypeLatticeElement()) {
          ArrayTypeLatticeElement lattice = l.asArrayTypeLatticeElement();
          assertEquals(
              appInfo.dexItemFactory.stringType,
              lattice.getArrayElementType(appInfo.dexItemFactory));
          assertEquals(v.definition.isArgument(), l.isNullable());
        } else if (l.isClassTypeLatticeElement()) {
          verifyClassTypeLattice(expectedLattices, example, v, l);
        }
      });
    });
  }

  @Test
  public void nonNullAfterSafeFieldAccess() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    MethodSignature signature =
        builder.addStaticMethod("void", "foo", ImmutableList.of("Test"), 1,
            "iget-object v0, p0, LTest;->bar:Ljava/lang/String;",
            // Successful field access above means p0 is not null.
            "if-nez p0, :not_null",
            "new-instance v0, Ljava/lang/AssertionError;",
            "throw v0",
            ":not_null",
            // And thus the call below uses the same, non-null value.
            "invoke-virtual {p0}, LTest;->hashCode()I",
            "return-void"
        );
    builder.addClass("Test");
    builder.addInstanceField("bar", "Ljava/lang/String;");
    buildAndTest(builder, signature, false, (appInfo, typeAnalysis) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType example = appInfo.dexItemFactory.createType("LExample;");
      DexType testType = appInfo.dexItemFactory.createType("LTest;");
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          Argument.class, new ClassTypeLatticeElement(testType, true),
          NonNull.class, new ClassTypeLatticeElement(testType, false),
          // instance may not be initialized.
          InstanceGet.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, true),
          NewInstance.class, new ClassTypeLatticeElement(assertionErrorType, false));
      typeAnalysis.forEach((v, l) -> verifyClassTypeLattice(expectedLattices, example, v, l));
    });
  }

  @Test
  public void stillNullAfterExceptionCatch_iget() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    MethodSignature signature =
        builder.addStaticMethod("void", "foo", ImmutableList.of("Test"), 1,
            ":try_start",
            "iget-object v0, p0, LTest;->bar:Ljava/lang/String;",
            "if-nez p0, :return",
            "new-instance v0, Ljava/lang/AssertionError;",
            "throw v0",
            ":try_end",
            ".catch Ljava/lang/Throwable; {:try_start .. :try_end} :return",
            ":return",
            // p0 could be still null at the outside of try-catch.
            "invoke-virtual {p0}, LTest;->hashCode()I",
            "return-void"
        );
    builder.addClass("Test");
    builder.addInstanceField("bar", "Ljava/lang/String;");
    buildAndTest(builder, signature, true, (appInfo, typeAnalysis) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType example = appInfo.dexItemFactory.createType("LExample;");
      DexType testType = appInfo.dexItemFactory.createType("LTest;");
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          Argument.class, new ClassTypeLatticeElement(testType, true),
          NonNull.class, new ClassTypeLatticeElement(testType, false),
          // instance may not be initialized.
          InstanceGet.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, true),
          NewInstance.class, new ClassTypeLatticeElement(assertionErrorType, false));
      typeAnalysis.forEach((v, l) -> verifyClassTypeLattice(expectedLattices, example, v, l));
    });
  }
}
