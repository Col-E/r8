// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.graph.JarClassFileReader;
import com.android.tools.r8.graph.LazyLoadedDexApplication.Builder;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.Timing;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LIRRoundtripTest extends TestBase {

  static class TestClass {
    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultCfRuntime().build();
  }

  private final TestParameters parameters;
  private AppView<?> appView;
  private ProgramMethod method;

  public LIRRoundtripTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Before
  public void setUp() throws Exception {
    DexItemFactory factory = new DexItemFactory();
    InternalOptions options = new InternalOptions(factory, new Reporter());
    options.programConsumer = ClassFileConsumer.emptyConsumer();
    Builder builder = DexApplication.builder(options, Timing.empty());
    JarClassFileReader<DexProgramClass> reader =
        new JarClassFileReader<>(
            new JarApplicationReader(options),
            clazz -> {
              builder.addProgramClass(clazz);
              clazz
                  .programMethods()
                  .forEach(
                      m -> {
                        if (m.getReference().qualifiedName().endsWith("main")) {
                          method = m;
                        }
                      });
            },
            ClassKind.PROGRAM);
    reader.read(Origin.unknown(), ToolHelper.getClassAsBytes(TestClass.class));
    appView =
        AppView.createForD8(
            AppInfo.createInitialAppInfo(
                builder.build(), GlobalSyntheticsStrategy.forNonSynthesizing()));
  }

  @Test
  public void testReference() throws Exception {
    testForJvm()
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testRoundtrip() throws Exception {
    IRCode irCode1 = translateCf2IR();
    LIRCode lirCode = translateIR2LIR(irCode1);
    IRCode irCode2 = translateLIR2IR(lirCode);
    translateIR2Cf(irCode2);
    Path out = writeToFile();
    testForJvm()
        .addProgramFiles(out)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private Path writeToFile() {
    Path out = temp.getRoot().toPath().resolve("out.jar");
    Marker fakeMarker = new Marker(Tool.D8);
    ArchiveConsumer consumer = new ArchiveConsumer(out);
    new CfApplicationWriter(appView, fakeMarker).write(consumer);
    consumer.finished(appView.reporter());
    return out;
  }

  private IRCode translateCf2IR() {
    CfCode cfCode = method.getDefinition().getCode().asCfCode();
    return cfCode.buildIR(method, appView, Origin.unknown());
  }

  private LIRCode translateIR2LIR(IRCode irCode) {
    Reference2IntMap<Value> values = new Reference2IntOpenHashMap<>();
    int index = 0;
    for (Instruction instruction : irCode.instructions()) {
      if (instruction.hasOutValue()) {
        values.put(instruction.outValue(), index);
      }
      index++;
    }
    LIRBuilder<Value> builder =
        new LIRBuilder<Value>(values::getInt).setMetadata(irCode.metadata());
    BasicBlockIterator blockIt = irCode.listIterator();
    while (blockIt.hasNext()) {
      BasicBlock block = blockIt.next();
      // TODO(b/225838009): Support control flow.
      assert !block.hasPhis();
      InstructionIterator it = block.iterator();
      while (it.hasNext()) {
        Instruction instruction = it.next();
        instruction.buildLIR(builder);
      }
    }
    return builder.build();
  }

  private IRCode translateLIR2IR(LIRCode lirCode) {
    return new LIR2IRBuilder(appView).translate(method, lirCode);
  }

  private void translateIR2Cf(IRCode irCode) {
    CodeRewriter codeRewriter = new CodeRewriter(appView);
    DeadCodeRemover deadCodeRemover = new DeadCodeRemover(appView, codeRewriter);
    CfCode cfCode =
        new CfBuilder(appView, method, irCode, BytecodeMetadataProvider.empty())
            .build(deadCodeRemover);
    method.setCode(cfCode, appView);
  }
}
