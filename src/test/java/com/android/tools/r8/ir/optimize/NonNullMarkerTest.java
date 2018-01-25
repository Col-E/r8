// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.NonNull;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

// TODO(jsjeon): switch to JasminTestBase or AsmTestBase.
public class NonNullMarkerTest extends SmaliTestBase {
  private final String CLASS_NAME = "Example";
  private static final InternalOptions TEST_OPTIONS = new InternalOptions();

  private void buildAndTest(
      SmaliBuilder builder,
      MethodSignature signature,
      int expectedNumberOfNonNull)
      throws Exception {
    AndroidApp app = builder.build();
    DexApplication dexApplication =
        new ApplicationReader(app, TEST_OPTIONS, new Timing("NonNullMarkerTest.appReader"))
            .read().toDirect();
    AppInfo appInfo = new AppInfo(dexApplication);
    DexInspector dexInspector = new DexInspector(appInfo.app);
    DexEncodedMethod foo = dexInspector.clazz(CLASS_NAME).method(signature).getMethod();
    IRCode irCode = foo.buildIR(TEST_OPTIONS);
    checkCountOfNonNull(irCode, 0);

    NonNullMarker nonNullMarker = new NonNullMarker();

    nonNullMarker.addNonNull(irCode);
    assertTrue(irCode.isConsistentSSA());
    checkCountOfNonNull(irCode, expectedNumberOfNonNull);

    nonNullMarker.cleanupNonNull(irCode);
    assertTrue(irCode.isConsistentSSA());
    checkCountOfNonNull(irCode, 0);
  }

  private static void checkCountOfNonNull(IRCode code, int expectedOccurrences) {
    int count = 0;
    Instruction prev = null, curr = null;
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      prev = curr != null && !curr.isGoto() ? curr : prev;
      curr = it.next();
      if (curr instanceof NonNull) {
        // Make sure non-null is added to the right place.
        assertTrue(prev == null || NonNullMarker.throwsOnNullInput(prev));
        count++;
      }
    }
    assertEquals(count, expectedOccurrences);
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
    buildAndTest(builder, signature, 1);
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
    buildAndTest(builder, signature, 1);
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
    buildAndTest(builder, signature, 1);
  }

  @Test
  public void avoidRedundantNonNull() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    MethodSignature signature =
        builder.addInstanceMethod("void", "foo", ImmutableList.of("java.lang.String"), 1,
            "invoke-virtual {p1}, Ljava/lang/String;->toString()Ljava/lang/String;",
            // Successful invocation above means p1 is not null.
            "if-nez p1, :not_null",
            "iget-object v0, p0, LTest;->bar:Ljava/lang/String;",
            // Successful field access above means p0 is not null.
            // But! the receiver is known to be non-null already.
            "if-nez p0, :not_null",
            "new-instance v0, Ljava/lang/AssertionError;",
            "throw v0",
            ":not_null",
            "return-void"
        );
    builder.addClass("Test");
    builder.addInstanceField("bar", "Ljava/lang/String;");
    buildAndTest(builder, signature, 1);
  }

}
