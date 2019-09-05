// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FieldBitAccessInfoTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public FieldBitAccessInfoTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getRuntime())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "get()=false", "set()", "get()=true", "clear()", "get()=false");
  }

  @Test
  public void testOptimizationInfo() throws Exception {
    AppView<AppInfoWithSubtyping> appView = buildApp();
    OptimizationFeedbackMock feedback = new OptimizationFeedbackMock();
    FieldBitAccessAnalysis fieldBitAccessAnalysis = new FieldBitAccessAnalysis(appView);

    DexProgramClass clazz = appView.appInfo().classes().iterator().next();
    assertEquals(TestClass.class.getTypeName(), clazz.type.toSourceString());

    for (DexEncodedMethod method : clazz.methods()) {
      IRCode code = method.buildIR(appView, Origin.unknown());
      fieldBitAccessAnalysis.recordFieldAccesses(code, feedback);
    }

    // TODO(b/140540714): Should have precise bit access info for `bitField`.
    assertEquals(
        ImmutableSet.of("bitField"),
        feedback.fieldsWithUnknownAccesses.stream()
            .map(field -> field.field.name.toSourceString())
            .collect(Collectors.toSet()));
  }

  private AppView<AppInfoWithSubtyping> buildApp() throws IOException, ExecutionException {
    DexItemFactory dexItemFactory = new DexItemFactory();
    InternalOptions options = new InternalOptions(dexItemFactory, new Reporter());
    options.programConsumer =
        parameters.isCfRuntime()
            ? ClassFileConsumer.emptyConsumer()
            : DexIndexedConsumer.emptyConsumer();
    Timing timing = new Timing("FieldBitAccessInfoTest");
    DexApplication application =
        new ApplicationReader(
                AndroidApp.builder()
                    .addClassProgramData(
                        ToolHelper.getClassAsBytes(TestClass.class), Origin.unknown())
                    .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
                    .build(),
                options,
                timing)
            .read()
            .toDirect();
    return AppView.createForR8(new AppInfoWithSubtyping(application), options);
  }

  static class TestClass {

    static int bitField = 0;

    public static void main(String[] args) {
      System.out.println("get()=" + get());
      System.out.println("set()");
      set();
      System.out.println("get()=" + get());
      System.out.println("clear()");
      clear();
      System.out.println("get()=" + get());
    }

    static boolean get() {
      return (bitField & 0x00000001) != 0;
    }

    static void set() {
      bitField |= 0x00000001;
    }

    static void clear() {
      bitField &= ~0x00000001;
    }
  }

  static class OptimizationFeedbackMock extends OptimizationFeedbackIgnore {

    Set<DexEncodedField> fieldsWithUnknownAccesses = Sets.newIdentityHashSet();

    @Override
    public void markFieldHasUnknownAccess(DexEncodedField field) {
      fieldsWithUnknownAccesses.add(field);
    }
  }
}
