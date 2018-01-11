// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
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
      BiConsumer<AppInfoWithSubtyping, TypeAnalysis> inspector)
      throws Exception {
    AndroidApp app = builder.build();
    DexApplication dexApplication =
        new ApplicationReader(app, TEST_OPTIONS, new Timing("NullabilityTest.appReader"))
            .read().toDirect();
    AppInfoWithSubtyping appInfo = new AppInfoWithSubtyping(dexApplication);
    DexInspector dexInspector = new DexInspector(appInfo.app);
    DexEncodedMethod foo = dexInspector.clazz(CLASS_NAME).method(signature).getMethod();
    IRCode irCode = foo.buildIR(TEST_OPTIONS);
    TypeAnalysis analysis = new TypeAnalysis(appInfo, foo, irCode);
    analysis.run();
    inspector.accept(appInfo, analysis);
  }

  private static void verifyClassTypeLattice(
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices,
      DexType receiverType,
      Value v,
      TypeLatticeElement l) {
    assertTrue(l instanceof ClassTypeLatticeElement);
    ClassTypeLatticeElement lattice = (ClassTypeLatticeElement) l;
    // Receiver
    if (lattice.classType.equals(receiverType)) {
      assertFalse(l.isNullable());
    } else {
      TypeLatticeElement expected = expectedLattices.get(v.definition.getClass());
      if (expected != null) {
        assertEquals(expected, l);
      }
    }
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
            "return-void"
        );
    buildAndTest(builder, signature, (appInfo, typeAnalysis) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType example = appInfo.dexItemFactory.createType("LExample;");
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          // TODO(b/70795205): Can be refined by using control-flow info.
          InvokeVirtual.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, true),
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
    buildAndTest(builder, signature, (appInfo, typeAnalysis) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType example = appInfo.dexItemFactory.createType("LExample;");
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          InvokeVirtual.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, true),
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
            "return-void"
        );
    buildAndTest(builder, signature, (appInfo, typeAnalysis) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType example = appInfo.dexItemFactory.createType("LExample;");
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          // An element inside a non-null array could be null.
          ArrayGet.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, true),
          NewInstance.class, new ClassTypeLatticeElement(assertionErrorType, false));
      typeAnalysis.forEach((v, l) -> {
        if (l instanceof ArrayTypeLatticeElement) {
          ArrayTypeLatticeElement lattice = (ArrayTypeLatticeElement) l;
          assertEquals(
              appInfo.dexItemFactory.stringType,
              lattice.getArrayElementType(appInfo.dexItemFactory));
          // TODO(b/70795205): Can be refined by using control-flow info.
          assertTrue(l.isNullable());
        } else if (l instanceof ClassTypeLatticeElement) {
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
    buildAndTest(builder, signature, (appInfo, typeAnalysis) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType example = appInfo.dexItemFactory.createType("LExample;");
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          // An element inside a non-null array could be null.
          ArrayGet.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, true),
          NewInstance.class, new ClassTypeLatticeElement(assertionErrorType, false));
      typeAnalysis.forEach((v, l) -> {
        if (l instanceof ArrayTypeLatticeElement) {
          ArrayTypeLatticeElement lattice = (ArrayTypeLatticeElement) l;
          assertEquals(
              appInfo.dexItemFactory.stringType,
              lattice.getArrayElementType(appInfo.dexItemFactory));
          assertTrue(l.isNullable());
        } else if (l instanceof ClassTypeLatticeElement) {
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
            "return-void"
        );
    builder.addClass("Test");
    builder.addInstanceField("bar", "Ljava/lang/String;");
    buildAndTest(builder, signature, (appInfo, typeAnalysis) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType example = appInfo.dexItemFactory.createType("LExample;");
      DexType testType = appInfo.dexItemFactory.createType("LTest;");
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          // TODO(b/70795205): Can be refined by using control-flow info.
          Argument.class, new ClassTypeLatticeElement(testType, true),
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
    buildAndTest(builder, signature, (appInfo, typeAnalysis) -> {
      DexType assertionErrorType = appInfo.dexItemFactory.createType("Ljava/lang/AssertionError;");
      DexType example = appInfo.dexItemFactory.createType("LExample;");
      DexType testType = appInfo.dexItemFactory.createType("LTest;");
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
          Argument.class, new ClassTypeLatticeElement(testType, true),
          // instance may not be initialized.
          InstanceGet.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, true),
          NewInstance.class, new ClassTypeLatticeElement(assertionErrorType, false));
      typeAnalysis.forEach((v, l) -> verifyClassTypeLattice(expectedLattices, example, v, l));
    });
  }
}
