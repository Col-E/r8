// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bootstrap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.Marker.Backend;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceCommand;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StackTraceUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.MethodVisitor;

@Ignore("b/288117378")
@RunWith(Parameterized.class)
public class RetraceStackTraceFunctionalCompositionTest extends TestBase {

  @Parameter() public TestParameters parameters;

  private Path rewrittenR8Jar;
  private static final int SAMPLING_SIZE = 50000;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    assumeTrue(ToolHelper.isLinux());
    return getTestParameters().withDefaultCfRuntime().build();
  }

  private void insertPrintingOfStacktraces(Path path, Path outputPath) throws Exception {
    String entryNameForClass = ZipUtils.zipEntryNameForClass(StackTraceUtils.class);
    Box<Long> idBox = new Box<>(0L);
    ZipUtils.map(
        path,
        outputPath,
        (zipEntry, bytes) -> {
          String entryName = zipEntry.getName();
          // Only insert into the R8 namespace, this will provide a better sampling than inserting
          // into all calls due to more inlining/outlining could have happened.
          if (ZipUtils.isClassFile(entryName)
              && !entryNameForClass.equals(entryName)
              && entryName.contains("com/android/tools/r8/")) {
            return transformer(bytes, null)
                .addClassTransformer(
                    new ClassTransformer() {
                      @Override
                      public MethodVisitor visitMethod(
                          int access,
                          String name,
                          String descriptor,
                          String signature,
                          String[] exceptions) {
                        MethodVisitor sub =
                            super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (name.equals("<clinit>")) {
                          return sub;
                        } else {
                          return new InsertStackTraceCallTransformer(
                              sub, () -> idBox.getAndCompute(x -> x + 1));
                        }
                      }
                      ;
                    })
                .transform();
          }
          return bytes;
        });
  }

  private static class InsertStackTraceCallTransformer extends MethodTransformer {

    private boolean insertedStackTraceCall = false;
    private final Supplier<Long> idGenerator;

    private InsertStackTraceCallTransformer(MethodVisitor visitor, Supplier<Long> idGenerator) {
      this.mv = visitor;
      this.idGenerator = idGenerator;
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
      insertPrintIfFirstInstruction();
      super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitInsn(int opcode) {
      insertPrintIfFirstInstruction();
      super.visitInsn(opcode);
    }

    @Override
    public void visitLdcInsn(Object value) {
      insertPrintIfFirstInstruction();
      super.visitLdcInsn(value);
    }

    @Override
    public void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface) {
      insertPrintIfFirstInstruction();
      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    private void insertPrintIfFirstInstruction() {
      if (!insertedStackTraceCall) {
        super.visitLdcInsn(idGenerator.get());
        super.visitMethodInsn(
            INVOKESTATIC, binaryName(StackTraceUtils.class), "printCurrentStack", "(J)V", false);
        insertedStackTraceCall = true;
      }
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
      super.visitMaxs(maxStack + 2, maxLocals);
    }
  }

  private Path getRewrittenR8Jar() throws Exception {
    if (rewrittenR8Jar != null) {
      return rewrittenR8Jar;
    }
    rewrittenR8Jar = temp.newFolder().toPath().resolve("r8_with_deps_with_prints.jar");
    insertPrintingOfStacktraces(ToolHelper.getR8WithRelocatedDeps(), rewrittenR8Jar);
    return rewrittenR8Jar;
  }

  @Test
  public void testR8RetraceAndComposition() throws Exception {
    Path rewrittenR8Jar = getRewrittenR8Jar();
    List<String> originalStackTraces = generateStackTraces(rewrittenR8Jar);
    Map<String, List<String>> originalPartitions = partitionStacktraces(originalStackTraces);

    List<String> originalStackTracesDeterministicCheck = generateStackTraces(rewrittenR8Jar);
    Map<String, List<String>> deterministicPartitions =
        partitionStacktraces(originalStackTracesDeterministicCheck);
    comparePartitionedStackTraces(originalPartitions, deterministicPartitions);

    // Compile rewritten R8 with R8 to obtain first level
    Pair<Path, Path> r8OfR8 = compileR8WithR8(rewrittenR8Jar);

    // If we retrace the entire file we should get the same result as the original.
    List<String> retracedFirstLevelStackTraces =
        retrace(r8OfR8.getSecond(), generateStackTraces(r8OfR8.getFirst()));
    Map<String, List<String>> firstRoundPartitions =
        partitionStacktraces(retracedFirstLevelStackTraces);
    comparePartitionedStackTraces(originalPartitions, firstRoundPartitions);

    Pair<Path, Path> d8OfR8ofR8 = compileWithD8(r8OfR8.getFirst(), r8OfR8.getSecond());
    List<String> d8StackTraces = generateStackTraces(d8OfR8ofR8.getFirst());
    List<String> secondRoundStackTraces = retrace(d8OfR8ofR8.getSecond(), d8StackTraces);
    Map<String, List<String>> secondRoundPartitions = partitionStacktraces(secondRoundStackTraces);
    comparePartitionedStackTraces(originalPartitions, secondRoundPartitions);
  }

  private List<String> retrace(Path mappingFile, List<String> stacktraces) {
    List<String> retracedLines = new ArrayList<>();
    Retrace.run(
        RetraceCommand.builder()
            .setRetracedStackTraceConsumer(retracedLines::addAll)
            .setStackTrace(stacktraces)
            .setMappingSupplier(
                ProguardMappingSupplier.builder()
                    .setProguardMapProducer(ProguardMapProducer.fromPath(mappingFile))
                    .build())
            .build());
    return retracedLines;
  }

  private void comparePartitionedStackTraces(
      Map<String, List<String>> one, Map<String, List<String>> other) {
    for (Entry<String, List<String>> keyStackTraceEntry : one.entrySet()) {
      String oneAsString = StringUtils.lines(keyStackTraceEntry.getValue());
      String otherAsString = StringUtils.lines(other.get(keyStackTraceEntry.getKey()));
      assertEquals(oneAsString, otherAsString);
    }
    assertEquals(one.keySet(), other.keySet());
  }

  private Map<String, List<String>> partitionStacktraces(List<String> allStacktraces) {
    Map<String, List<String>> partitions = new LinkedHashMap<>();
    int lastIndex = 0;
    for (int i = 0; i < allStacktraces.size(); i++) {
      if (allStacktraces.get(i).contains("@@@@")) {
        List<String> stackTrace = allStacktraces.subList(lastIndex, i);
        String keyForStackTrace = getKeyForStackTrace(stackTrace);
        List<String> existing = partitions.put(keyForStackTrace, stackTrace);
        assertNull(existing);
        lastIndex = i + 1;
        i++;
      }
    }
    return partitions;
  }

  private String getKeyForStackTrace(List<String> stackTrace) {
    String identifier = "java.lang.RuntimeException: ------(";
    String firstLine = stackTrace.get(0);
    int index = firstLine.indexOf(identifier);
    assertEquals(0, index);
    String endIdentifier = ")------";
    int endIndex = firstLine.indexOf(endIdentifier);
    assertTrue(endIndex > 0);
    return firstLine.substring(index + identifier.length(), endIndex);
  }

  private Pair<Path, Path> compileR8WithR8(Path r8Input) throws Exception {
    Path MAIN_KEEP = Paths.get("src/main/keep.txt");
    Path jar = temp.newFolder().toPath().resolve("out.jar");
    Path map = temp.newFolder().toPath().resolve("out.map");
    testForR8(Backend.CF)
        .setMode(CompilationMode.RELEASE)
        .addProgramFiles(r8Input)
        .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
        .addKeepRuleFiles(MAIN_KEEP)
        .allowUnusedProguardConfigurationRules()
        .addDontObfuscate()
        .compile()
        .apply(c -> FileUtils.writeTextFile(map, c.getProguardMap()))
        .writeToZip(jar);
    return Pair.create(jar, map);
  }

  private Pair<Path, Path> compileWithD8(Path r8Input, Path previousMappingFile) throws Exception {
    StringBuilder mappingComposed = new StringBuilder();
    Path jar = temp.newFolder().toPath().resolve("out.jar");
    Path map = temp.newFolder().toPath().resolve("out.map");
    testForD8(Backend.CF)
        .setMode(CompilationMode.RELEASE)
        .addProgramFiles(r8Input)
        .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
        .addOptionsModification(
            options -> {
              assertTrue(options.mappingComposeOptions().enableExperimentalMappingComposition);
            })
        .apply(
            b ->
                b.getBuilder()
                    .setProguardMapInputFile(previousMappingFile)
                    .setProguardMapConsumer((string, handler) -> mappingComposed.append(string)))
        .compile()
        .writeToZip(jar);
    FileUtils.writeTextFile(map, mappingComposed.toString());
    return Pair.create(jar, map);
  }

  private List<String> generateStackTraces(Path r8Jar) throws Exception {
    File stacktraceOutput = new File(temp.newFolder(), "stacktraces.txt");
    stacktraceOutput.createNewFile();
    testForExternalR8(Backend.DEX, parameters.getRuntime())
        .useProvidedR8(r8Jar)
        .addProgramClasses(HelloWorld.class)
        .addKeepMainRule(HelloWorld.class)
        .setMinApi(AndroidApiLevel.B)
        .addJvmFlag("-Dcom.android.tools.r8.deterministicdebugging=true")
        .addJvmFlag("-Dcom.android.tools.r8.internalStackTraceSamplingInterval=" + SAMPLING_SIZE)
        .addJvmFlag("-Dcom.android.tools.r8.internalPathToStacktraces=" + stacktraceOutput.toPath())
        .compile();
    return Files.readAllLines(stacktraceOutput.toPath(), StandardCharsets.UTF_8);
  }

  public static class HelloWorld {

    public static void main(String[] args) {
      System.out.println("Hello World");
    }
  }
}
