// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardRuleParserException;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.CompilationFailedException;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OffOrAuto;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.harmony.jpda.tests.framework.TestErrorException;
import org.apache.harmony.jpda.tests.framework.jdwp.CommandPacket;
import org.apache.harmony.jpda.tests.framework.jdwp.Event;
import org.apache.harmony.jpda.tests.framework.jdwp.EventBuilder;
import org.apache.harmony.jpda.tests.framework.jdwp.EventPacket;
import org.apache.harmony.jpda.tests.framework.jdwp.Frame.Variable;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPCommands;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPCommands.ReferenceTypeCommandSet;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPCommands.StackFrameCommandSet;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPConstants;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPConstants.Error;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPConstants.EventKind;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPConstants.StepDepth;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPConstants.StepSize;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPConstants.SuspendPolicy;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPConstants.TypeTag;
import org.apache.harmony.jpda.tests.framework.jdwp.Location;
import org.apache.harmony.jpda.tests.framework.jdwp.Method;
import org.apache.harmony.jpda.tests.framework.jdwp.ParsedEvent;
import org.apache.harmony.jpda.tests.framework.jdwp.ParsedEvent.EventThread;
import org.apache.harmony.jpda.tests.framework.jdwp.ReplyPacket;
import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.apache.harmony.jpda.tests.framework.jdwp.VmMirror;
import org.apache.harmony.jpda.tests.jdwp.share.JDWPTestCase;
import org.apache.harmony.jpda.tests.share.JPDATestOptions;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

/**
 * Base class for debugging tests.
 *
 * The protocol messages are described here:
 * https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp/jdwp-protocol.html
 */
public abstract class DebugTestBase {

  public static final StepFilter NO_FILTER = new StepFilter.NoStepFilter();
  public static final StepFilter INTELLIJ_FILTER = new StepFilter.IntelliJStepFilter();
  private static final StepFilter DEFAULT_FILTER = NO_FILTER;

  enum DexCompilerKind {
    DX,
    D8
  }

  enum BinaryKind {
    CF,
    DEX
  }

  protected static class DebuggeePath {
    public final BinaryKind kind;
    public final Path path;

    public static DebuggeePath makeDex(Path path) {
      return new DebuggeePath(BinaryKind.DEX, path);
    }

    public static DebuggeePath makeClassFile(Path path) {
      return new DebuggeePath(BinaryKind.CF, path);
    }

    public DebuggeePath(BinaryKind kind, Path path) {
      this.kind = kind;
      this.path = path;
    }
  }

  private static final DexCompilerKind DEX_COMPILER_KIND = DexCompilerKind.D8;

  private static final int FIRST_LINE = -1;

  // Set to true to enable verbose logs
  private static final boolean DEBUG_TESTS = false;

  private static final Path JDWP_JAR = ToolHelper
      .getJdwpTestsJarPath(ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm()));
  protected static final Path DEBUGGEE_JAR =
      Paths.get(ToolHelper.BUILD_DIR, "test", "debug_test_resources.jar");
  private static final Path DEBUGGEE_JAVA8_JAR = Paths
      .get(ToolHelper.BUILD_DIR, "test", "debug_test_resources_java8.jar");
  private static final Path DEBUGGEE_KOTLIN_JAR = Paths
      .get(ToolHelper.BUILD_DIR, "test", "debug_test_resources_kotlin.jar");
  private static final String PROGUARD_MAP_FILENAME = "proguard.map";

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  // TODO(tamaskenez): Separate test setup from test runner.
  private static Path jdwpDexD8;
  private static Path debuggeeDexD8;
  private static Path debuggeeJava8DexD8;
  private static Path debuggeeKotlinDexD8;

  @Rule
  public TestName testName = new TestName();

  @BeforeClass
  public static void setUp() throws Exception {
    jdwpDexD8 = compileToDex(JDWP_JAR, null);
    debuggeeDexD8 = null;
    debuggeeJava8DexD8 = null;
    debuggeeKotlinDexD8 = null;
  }

  protected static synchronized Path getDebuggeeDexD8()
      throws IOException, CompilationException, CompilationFailedException {
    if (debuggeeDexD8 == null) {
      debuggeeDexD8 = compileToDex(DEBUGGEE_JAR, null);
    }
    return debuggeeDexD8;
  }

  private static synchronized Path getDebuggeeJava8DexD8()
      throws IOException, CompilationException, CompilationFailedException {
    if (debuggeeJava8DexD8 == null) {
      debuggeeJava8DexD8 =
          compileToDex(
              DEBUGGEE_JAVA8_JAR,
              options -> {
                // Enable desugaring for preN runtimes
                options.interfaceMethodDesugaring = OffOrAuto.Auto;
              });
    }
    return debuggeeJava8DexD8;
  }

  private static synchronized Path getDebuggeeKotlinDexD8()
      throws IOException, CompilationException, CompilationFailedException {
    if (debuggeeKotlinDexD8 == null) {
      debuggeeKotlinDexD8 = compileToDex(DEBUGGEE_KOTLIN_JAR, null);
    }
    return debuggeeKotlinDexD8;
  }

  protected static DebuggeePath getDebuggeeDexD8OrCf(boolean cf)
      throws IOException, CompilationException, CompilationFailedException {
    return cf ? DebuggeePath.makeClassFile(DEBUGGEE_JAR) : DebuggeePath.makeDex(getDebuggeeDexD8());
  }

  protected static DebuggeePath getDebuggeeJava8DexD8OrCf(boolean cf)
      throws IOException, CompilationException, CompilationFailedException {
    return cf
        ? DebuggeePath.makeClassFile(DEBUGGEE_JAVA8_JAR)
        : DebuggeePath.makeDex(getDebuggeeJava8DexD8());
  }

  protected static Path compileToDex(Path jarToCompile, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationException, CompilationFailedException {
    return compileToDex(DEX_COMPILER_KIND, jarToCompile, optionsConsumer);
  }

  static Path compileToDex(
      DexCompilerKind compiler, Path jarToCompile, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationException, CompilationFailedException {
    int minSdk = ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm());
    assert jarToCompile.toFile().exists();
    Path dexOutputDir = temp.newFolder().toPath();
    switch (compiler) {
      case D8:
        {
          ToolHelper.runD8(
              D8Command.builder()
                  .addProgramFiles(jarToCompile)
                  .setOutputPath(dexOutputDir)
                  .setMinApiLevel(minSdk)
                  .setMode(CompilationMode.DEBUG)
                  .addLibraryFiles(Paths.get(ToolHelper.getAndroidJar(minSdk)))
                  .build(),
              optionsConsumer);
          break;
        }
      case DX:
        {
          ProcessResult result =
              ToolHelper.runDX(
                  new String[] {
                    "--output=" + dexOutputDir,
                    "--min-sdk-version=" + minSdk,
                    jarToCompile.toString()
                  });
          Assert.assertEquals(result.stderr, 0, result.exitCode);
          break;
        }
      default:
        throw new Unreachable();
    }
    return dexOutputDir.resolve("classes.dex");
  }

  public static Path compileToDexViaR8(
      Consumer<InternalOptions> optionsConsumer,
      Consumer<ProguardConfiguration.Builder> pgConsumer,
      Path jarToCompile,
      List<String> proguardConfigurations,
      boolean writeProguardMap,
      CompilationMode compilationMode)
      throws IOException, CompilationException, ExecutionException, ProguardRuleParserException,
      CompilationFailedException {
    int minSdk = ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm());
    assert jarToCompile.toFile().exists();
    Path dexOutputDir = temp.newFolder().toPath();
    R8Command.Builder builder =
        R8Command.builder()
            .addProgramFiles(jarToCompile)
            .setOutputPath(dexOutputDir)
            .setMinApiLevel(minSdk)
            .setMode(CompilationMode.DEBUG)
            .addLibraryFiles(Paths.get(ToolHelper.getAndroidJar(minSdk)))
            .setMode(compilationMode);
    if (writeProguardMap) {
      builder.setProguardMapOutput(dexOutputDir.resolve(PROGUARD_MAP_FILENAME));
    }
    if (!proguardConfigurations.isEmpty()) {
      builder.addProguardConfiguration(proguardConfigurations);
    }
    if (pgConsumer != null) {
      builder.addProguardConfigurationConsumer(pgConsumer);
    }
    ToolHelper.runR8(builder.build(), optionsConsumer);
    return dexOutputDir.resolve("classes.dex");
  }

  private BinaryKind currentlyRunningBinaryKind = null;

  protected final BinaryKind getCurrentlyRunningBinaryKind() {
    if (currentlyRunningBinaryKind == null) {
      throw new RuntimeException("Nothing is running currently.");
    }
    return currentlyRunningBinaryKind;
  }

  protected static final boolean supportsDefaultMethod(boolean isRunningJava) {
    return isRunningJava
        || ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm()) >= AndroidApiLevel.N.getLevel();
  }

  protected final boolean isRunningJava() {
    return getCurrentlyRunningBinaryKind() == BinaryKind.CF;
  }

  protected final boolean isRunningArt() {
    return getCurrentlyRunningBinaryKind() == BinaryKind.DEX;
  }

  protected final void runDebugTest(String debuggeeClass, JUnit3Wrapper.Command... commands)
      throws Throwable {
    runDebugTest(
        DebuggeePath.makeDex(getDebuggeeDexD8()),
        Collections.<Path>emptyList(),
        debuggeeClass,
        Arrays.asList(commands));
  }

  protected final void runDebugTest(List<Path> extraPaths, String debuggeeClass,
      JUnit3Wrapper.Command... commands) throws Throwable {
    runDebugTest(
        DebuggeePath.makeDex(getDebuggeeDexD8()),
        extraPaths,
        debuggeeClass,
        Arrays.asList(commands));
  }

  protected final void runDebugTest(String debuggeeClass, List<JUnit3Wrapper.Command> commands)
      throws Throwable {
    runDebugTest(
        DebuggeePath.makeDex(getDebuggeeDexD8()),
        Collections.<Path>emptyList(),
        debuggeeClass,
        commands);
  }

  protected final void runDebugTestJava8(String debuggeeClass, JUnit3Wrapper.Command... commands)
      throws Throwable {
    runDebugTest(
        DebuggeePath.makeDex(getDebuggeeJava8DexD8()),
        Collections.<Path>emptyList(),
        debuggeeClass,
        Arrays.asList(commands));
  }

  protected final void runDebugTestJava8(String debuggeeClass, List<JUnit3Wrapper.Command> commands)
      throws Throwable {
    runDebugTest(
        DebuggeePath.makeDex(getDebuggeeJava8DexD8()),
        Collections.<Path>emptyList(),
        debuggeeClass,
        commands);
  }

  protected final void runDebugTestKotlin(String debuggeeClass, JUnit3Wrapper.Command... commands)
      throws Throwable {
    runDebugTest(
        DebuggeePath.makeDex(getDebuggeeKotlinDexD8()),
        Collections.<Path>emptyList(),
        debuggeeClass,
        Arrays.asList(commands));
  }

  protected void runDebugTest(
      DebuggeePath debuggeePath, String debuggeeClass, JUnit3Wrapper.Command... commands)
      throws Throwable {
    runDebugTest(
        debuggeePath, Collections.<Path>emptyList(), debuggeeClass, Arrays.asList(commands));
  }

  protected void runDebugTest(
      DebuggeePath debuggeePath, String debuggeeClass, List<JUnit3Wrapper.Command> commands)
      throws Throwable {
    runDebugTest(debuggeePath, Collections.<Path>emptyList(), debuggeeClass, commands);
  }

  protected void runDebugTest(
      DebuggeePath debuggeePath,
      List<Path> extraPaths,
      String debuggeeClass,
      JUnit3Wrapper.Command... commands)
      throws Throwable {
    runDebugTest(debuggeePath, extraPaths, debuggeeClass, Arrays.asList(commands));
  }

  protected void runDebugTest(
      DebuggeePath debuggeePath,
      List<Path> extraPaths,
      String debuggeeClass,
      List<JUnit3Wrapper.Command> commands)
      throws Throwable {
    // Skip test due to unsupported runtime.
    Assume.assumeTrue("Skipping test " + testName.getMethodName() + " because ART is not supported",
        ToolHelper.artSupported());
    Assume.assumeTrue("Skipping test " + testName.getMethodName()
            + " because debug tests are not yet supported on Windows",
        !ToolHelper.isWindows());

    String[] paths = new String[extraPaths.size() + 2];
    int indexPath = 0;
    ClassNameMapper classNameMapper = null;
    if (debuggeePath.kind == BinaryKind.CF) {
      paths[indexPath++] = JDWP_JAR.toString();
    } else {
      paths[indexPath++] = jdwpDexD8.toString();
      Path proguardMapPath = debuggeePath.path.resolveSibling(PROGUARD_MAP_FILENAME);
      if (Files.exists(proguardMapPath)) {
        classNameMapper = ClassNameMapper.mapperFromFile(proguardMapPath);
      }
    }
    paths[indexPath++] = debuggeePath.path.toString();
    for (Path extraPath : extraPaths) {
      paths[indexPath++] = extraPath.toString();
    }

    currentlyRunningBinaryKind = debuggeePath.kind;
    new JUnit3Wrapper(debuggeeClass, paths, commands, classNameMapper, isRunningArt()).runBare();
  }

  protected final JUnit3Wrapper.Command run() {
    return new JUnit3Wrapper.Command.RunCommand();
  }

  protected final JUnit3Wrapper.Command breakpoint(String className, String methodName) {
    return breakpoint(className, methodName, null);
  }

  protected final JUnit3Wrapper.Command breakpoint(String className, String methodName, int line) {
    return breakpoint(className, methodName, null, line);
  }

  protected final JUnit3Wrapper.Command breakpoint(String className, String methodName,
      String methodSignature) {
    return breakpoint(className, methodName, methodSignature, FIRST_LINE);
  }

  protected final JUnit3Wrapper.Command breakpoint(String className, String methodName,
      String methodSignature, int line) {
    return new JUnit3Wrapper.Command.BreakpointCommand(className, methodName, methodSignature, line);
  }

  protected final JUnit3Wrapper.Command stepOver() {
    return stepOver(DEFAULT_FILTER);
  }

  protected final JUnit3Wrapper.Command stepOver(StepFilter stepFilter) {
    return step(StepKind.OVER, stepFilter);
  }

  protected final JUnit3Wrapper.Command stepOut() {
    return stepOut(DEFAULT_FILTER);
  }

  protected final JUnit3Wrapper.Command stepOut(StepFilter stepFilter) {
    return step(StepKind.OUT, stepFilter);
  }

  protected final JUnit3Wrapper.Command stepInto() {
    return stepInto(DEFAULT_FILTER);
  }

  protected final JUnit3Wrapper.Command stepInto(StepFilter stepFilter) {
    return step(StepKind.INTO, stepFilter);
  }

  protected static List<Variable> getVisibleKotlinInlineVariables(
      JUnit3Wrapper.DebuggeeState debuggeeState) {
    return debuggeeState.getVisibleVariables().stream()
        .filter(v -> v.getName().matches("^\\$i\\$f\\$.*$")).collect(Collectors.toList());
  }

  public enum StepKind {
    INTO(StepDepth.INTO),
    OVER(StepDepth.OVER),
    OUT(StepDepth.OUT);

    private final byte jdwpValue;

    StepKind(byte jdwpValue) {
      this.jdwpValue = jdwpValue;
    }
  }

  public enum StepLevel {
    LINE(StepSize.LINE),
    INSTRUCTION(StepSize.MIN);

    private final byte jdwpValue;

    StepLevel(byte jdwpValue) {
      this.jdwpValue = jdwpValue;
    }
  }

  private JUnit3Wrapper.Command step(StepKind stepKind, StepFilter stepFilter) {
    return step(stepKind, StepLevel.LINE, stepFilter);
  }

  private JUnit3Wrapper.Command step(StepKind stepKind, StepLevel stepLevel,
      StepFilter stepFilter) {
    return new JUnit3Wrapper.Command.StepCommand(stepKind, stepLevel, stepFilter);
  }

  protected JUnit3Wrapper.Command stepUntil(StepKind stepKind, StepLevel stepLevel,
      Function<JUnit3Wrapper.DebuggeeState, Boolean> stepUntil) {
    return stepUntil(stepKind, stepLevel, stepUntil, DEFAULT_FILTER);
  }

  protected JUnit3Wrapper.Command stepUntil(StepKind stepKind, StepLevel stepLevel,
      Function<JUnit3Wrapper.DebuggeeState, Boolean> stepUntil, StepFilter stepFilter) {
    // We create an extension to the given step filter which will also check whether we need to
    // step again according to the given stepUntil function.
    StepFilter stepUntilFilter = new StepFilter() {
      @Override
      public List<String> getExcludedClasses() {
        return stepFilter.getExcludedClasses();
      }

      @Override
      public boolean skipLocation(JUnit3Wrapper.DebuggeeState debuggeeState, JUnit3Wrapper wrapper,
          JUnit3Wrapper.Command.StepCommand stepCommand) {
        if (stepFilter.skipLocation(debuggeeState, wrapper, stepCommand)) {
          return true;
        }
        if (stepUntil.apply(debuggeeState) == Boolean.FALSE) {
          // We did not reach the expected location so step again.
          wrapper.enqueueCommandFirst(stepCommand);
          return true;
        }
        return false;
      }
    };
    return new JUnit3Wrapper.Command.StepCommand(stepKind, stepLevel, stepUntilFilter);
  }

  protected final JUnit3Wrapper.Command checkLocal(String localName) {
    return inspect(t -> t.checkLocal(localName));
  }

  protected final JUnit3Wrapper.Command checkLocal(String localName, Value expectedValue) {
    return inspect(t -> t.checkLocal(localName, expectedValue));
  }

  protected final JUnit3Wrapper.Command checkNoLocal(String localName) {
    return inspect(t -> t.checkNoLocal(localName));
  }

  protected final JUnit3Wrapper.Command checkNoLocal() {
    return inspect(t -> {
      List<String> localNames = t.getLocalNames();
      Assert.assertTrue("Local variables: " + String.join(",", localNames), localNames.isEmpty());
    });
  }

  protected final JUnit3Wrapper.Command checkLine(String sourceFile, int line) {
    return inspect(t -> {
      Assert.assertEquals(sourceFile, t.getSourceFile());
      Assert.assertEquals(line, t.getLineNumber());
    });
  }

  protected final JUnit3Wrapper.Command checkMethod(String className, String methodName) {
    return checkMethod(className, methodName, null);
  }

  protected final JUnit3Wrapper.Command checkMethod(String className, String methodName,
      String methodSignature) {
    return inspect(t -> {
      Assert.assertEquals("Incorrect class name", className, t.getClassName());
      Assert.assertEquals("Incorrect method name", methodName, t.getMethodName());
      if (methodSignature != null) {
        Assert.assertEquals("Incorrect method signature", methodSignature,
            t.getMethodSignature());
      }
    });
  }

  protected final JUnit3Wrapper.Command checkStaticFieldClinitSafe(
      String className, String fieldName, String fieldSignature, Value expectedValue) {
    return inspect(t -> {
      // TODO(65148874): The current Art from AOSP master hangs when requesting static fields
      // when breaking in <clinit>. Last known good version is 7.0.0.
      Assume.assumeTrue(
          "Skipping test " + testName.getMethodName() + " because ART version is not supported",
          isRunningJava() || ToolHelper.getDexVm().getVersion().isOlderThanOrEqual(Version.V7_0_0));
      checkStaticField(className, fieldName, fieldSignature, expectedValue);
    });
  }

  protected final JUnit3Wrapper.Command checkStaticField(
      String className, String fieldName, String fieldSignature, Value expectedValue) {
    return inspect(t -> {
      Value value = t.getStaticField(className, fieldName, fieldSignature);
      Assert.assertEquals("Incorrect value for static '" + className + "." + fieldName + "'",
          expectedValue, value);
    });
  }

  protected final JUnit3Wrapper.Command inspect(Consumer<JUnit3Wrapper.DebuggeeState> inspector) {
    return t -> inspector.accept(t.debuggeeState);
  }

  protected final JUnit3Wrapper.Command setLocal(String localName, Value newValue) {
    return new JUnit3Wrapper.Command.SetLocalCommand(localName, newValue);
  }

  protected final JUnit3Wrapper.Command getLocal(String localName, Consumer<Value> inspector) {
    return t -> inspector.accept(t.debuggeeState.getLocalValues().get(localName));
  }

  @Ignore("Prevents Gradle from running the wrapper as a test.")
  static class JUnit3Wrapper extends JDWPTestCase {

    private final String debuggeeClassName;

    private final String[] debuggeePath;

    // Initially, the runtime is suspended so we're ready to process commands.
    private State state = State.ProcessCommand;

    /**
     * Represents the context of the debuggee suspension. This is {@code null} when the debuggee is
     * not suspended.
     */
    private DebuggeeState debuggeeState = null;

    private final Deque<Command> commandsQueue;
    private final Translator translator;

    // Active event requests.
    private final Map<Integer, EventHandler> events = new TreeMap<>();

    private final boolean isRunningArt;

    /**
     * The Translator interface provides mapping between the class and method names and line numbers
     * found in the binary file and their original forms.
     *
     * <p>Terminology:
     *
     * <p>The term 'original' refers to the names and line numbers found in the original source
     * code. The term 'obfuscated' refers to the names and line numbers in the binary. Note that
     * they may not actually be obfuscated:
     *
     * <p>- The obfuscated class and method names can be identical to the original ones if
     * minification is disabled or they are 'keep' classes/methods. - The obfuscated line numbers
     * can be identical to the original ones if neither inlining nor line number remapping took
     * place.
     */
    private interface Translator {
      public String getOriginalClassName(String obfuscatedClassName);

      public String getOriginalMethodName(
          String obfuscatedClassName, String obfuscatedMethodName, String methodSignature);

      public int getOriginalLineNumber(int obfuscatedLineNumber);

      public String getObfuscatedClassName(String originalClassName);

      public String getObfuscatedMethodName(
          String originalClassName, String originalMethodName, String methodSignature);
    }

    private class IdentityTranslator implements Translator {

      @Override
      public String getOriginalClassName(String obfuscatedClassName) {
        return obfuscatedClassName;
      }

      @Override
      public String getOriginalMethodName(
          String obfuscatedClassName, String obfuscatedMethodName, String methodSignature) {
        return obfuscatedMethodName;
      }

      @Override
      public int getOriginalLineNumber(int obfuscatedLineNumber) {
        return obfuscatedLineNumber;
      }

      @Override
      public String getObfuscatedClassName(String originalClassName) {
        return originalClassName;
      }

      @Override
      public String getObfuscatedMethodName(
          String originalClassName, String originalMethodName, String methodSignature) {
        return originalMethodName;
      }
    }

    private class ClassNameMapperTranslator extends IdentityTranslator {
      private final ClassNameMapper classNameMapper;

      public ClassNameMapperTranslator(ClassNameMapper classNameMapper) {
        this.classNameMapper = classNameMapper;
      }

      @Override
      public String getOriginalClassName(String obfuscatedClassName) {
        // TODO(tamaskenez) Watch for inline methods (we can be in a different class).
        return classNameMapper.deobfuscateClassName(obfuscatedClassName);
      }

      @Override
      public String getOriginalMethodName(
          String obfuscatedClassName, String obfuscatedMethodName, String methodSignature) {
        MemberNaming memberNaming =
            getMemberNaming(obfuscatedClassName, obfuscatedMethodName, methodSignature);
        if (memberNaming == null) {
          return obfuscatedMethodName;
        }

        Signature originalSignature = memberNaming.getOriginalSignature();
        return originalSignature.name;
      }

      @Override
      public int getOriginalLineNumber(int obfuscatedLineNumber) {
        return obfuscatedLineNumber;
        // TODO(tamaskenez) Map possibly reassigned line number to original, watch for inline.
        // methods
      }

      @Override
      public String getObfuscatedClassName(String originalClassName) {
        // TODO(tamaskenez) Watch for inline methods (we can be in a different class).
        String obfuscatedClassName =
            classNameMapper.getObfuscatedToOriginalMapping().inverse().get(originalClassName);
        return obfuscatedClassName == null ? originalClassName : obfuscatedClassName;
      }

      @Override
      public String getObfuscatedMethodName(
          String originalClassName, String originalMethodName, String methodSignatureOrNull) {
        ClassNamingForNameMapper naming;
        String obfuscatedClassName =
            classNameMapper.getObfuscatedToOriginalMapping().inverse().get(originalClassName);
        if (obfuscatedClassName != null) {
          naming = classNameMapper.getClassNaming(obfuscatedClassName);
        } else {
          return originalMethodName;
        }

        if (methodSignatureOrNull == null) {
          List<MemberNaming> memberNamings = naming.lookupByOriginalName(originalMethodName);
          if (memberNamings.isEmpty()) {
            return originalMethodName;
          } else if (memberNamings.size() == 1) {
            return memberNamings.get(0).getRenamedName();
          } else
            throw new RuntimeException(
                String.format(
                    "Looking up method %s.%s without signature is ambiguous (%d candidates).",
                    originalClassName, originalMethodName, memberNamings.size()));
        } else {
          MethodSignature originalSignature =
              MethodSignature.fromSignature(originalMethodName, methodSignatureOrNull);
          MemberNaming memberNaming = naming.lookupByOriginalSignature(originalSignature);
          if (memberNaming == null) {
            return originalMethodName;
          }

          return memberNaming.getRenamedName();
        }
      }

      /** Assumes classNameMapper is valid. Return null if no member naming found. */
      private MemberNaming getMemberNaming(
          String obfuscatedClassName, String obfuscatedMethodName, String genericMethodSignature) {
        ClassNamingForNameMapper classNaming = classNameMapper.getClassNaming(obfuscatedClassName);
        if (classNaming == null) {
          return null;
        }

        MethodSignature renamedSignature =
            MethodSignature.fromSignature(obfuscatedMethodName, genericMethodSignature);
        return classNaming.lookup(renamedSignature);
      }
    }

    JUnit3Wrapper(
        String debuggeeClassName,
        String[] debuggeePath,
        List<Command> commands,
        ClassNameMapper classNameMapper,
        boolean isRunningArt) {
      this.debuggeeClassName = debuggeeClassName;
      this.debuggeePath = debuggeePath;
      this.commandsQueue = new ArrayDeque<>(commands);
      if (classNameMapper == null) {
        this.translator = new IdentityTranslator();
      } else {
        this.translator = new ClassNameMapperTranslator(classNameMapper);
      }
      this.isRunningArt = isRunningArt;
    }

    @Override
    protected void runTest() throws Throwable {
      if (DEBUG_TESTS) {
        logWriter.println("Starts loop with " + commandsQueue.size() + " command(s) to process");
      }

      boolean exited = false;
      while (!exited) {
        if (DEBUG_TESTS) {
          logWriter.println("Loop on state " + state.name());
        }
        switch (state) {
          case ProcessCommand: {
            Command command = commandsQueue.poll();
            assert command != null;
            if (DEBUG_TESTS) {
              logWriter.println("Process command " + command.toString());
            }
            try {
              command.perform(this);
            } catch (TestErrorException e) {
              boolean ignoreException = false;
              if (ToolHelper.getDexVm().getVersion() == Version.V4_4_4) {
                // Dalvik has flaky synchronization issue on shutdown. The workaround is to ignore
                // the exception if and only if we know that it's the final resume command.
                if (debuggeeState == null && commandsQueue.isEmpty()) {
                  // We should receive the VMDeath event and transition to the Exit state here.
                  processEvents();
                  assert state == State.Exit;
                  ignoreException = true;
                }
              }
              if (!ignoreException) {
                throw e;
              }
            }
            break;
          }
          case WaitForEvent:
            processEvents();
            break;
          case Exit:
            exited = true;
            break;
          default:
            throw new AssertionError();
        }
      }

      assertTrue("All commands have NOT been processed", commandsQueue.isEmpty());

      logWriter.println("Finish loop");
    }

    @Override
    protected String getDebuggeeClassName() {
      return debuggeeClassName;
    }

    private enum State {
      /**
       * Process next command
       */
      ProcessCommand,
      /**
       * Wait for the next event
       */
      WaitForEvent,
      /**
       * The debuggee has exited
       */
      Exit
    }

    private void processEvents() {
      EventPacket eventPacket = getMirror().receiveEvent();
      ParsedEvent[] parsedEvents = ParsedEvent.parseEventPacket(eventPacket);
      if (DEBUG_TESTS) {
        logWriter.println("Received " + parsedEvents.length + " event(s)");
        for (int i = 0; i < parsedEvents.length; ++i) {
          String msg = String.format("#%d: %s (id=%d)", Integer.valueOf(i),
              JDWPConstants.EventKind.getName(parsedEvents[i].getEventKind()),
              Integer.valueOf(parsedEvents[i].getRequestID()));
          logWriter.println(msg);
        }
      }
      // We only expect one event at a time.
      assertEquals(1, parsedEvents.length);
      ParsedEvent parsedEvent = parsedEvents[0];
      byte eventKind = parsedEvent.getEventKind();
      int requestID = parsedEvent.getRequestID();

      if (eventKind == JDWPConstants.EventKind.VM_DEATH) {
        // Special event when debuggee is about to terminate.
        assertEquals(0, requestID);
        setState(State.Exit);
      } else {
        assert parsedEvent.getSuspendPolicy() == SuspendPolicy.ALL;

        // Capture the context of the event suspension.
        updateEventContext((EventThread) parsedEvent);

        if (DEBUG_TESTS && debuggeeState.getLocation() != null) {
          // Dump location
          String classSig = getMirror().getClassSignature(debuggeeState.getLocation().classID);
          String methodName = VmMirrorUtils
              .getMethodName(getMirror(), debuggeeState.getLocation().classID,
                  debuggeeState.getLocation().methodID);
          String methodSig = VmMirrorUtils
              .getMethodSignature(getMirror(), debuggeeState.getLocation().classID,
                  debuggeeState.getLocation().methodID);
          String msg = String
              .format("Suspended in %s#%s%s@0x%x", classSig, methodName, methodSig,
                  Long.valueOf(debuggeeState.getLocation().index));
          if (debuggeeState.getLocation().index >= 0) {
            msg += " (line " + debuggeeState.getLineNumber() + ")";
          }
          System.out.println(msg);
        }

        // Handle event.
        EventHandler eh = events.get(requestID);
        assert eh != null;
        eh.handle(this);
      }
    }

    @Override
    protected JPDATestOptions createTestOptions() {
      // Override properties to run debuggee with ART/Dalvik.
      class ArtTestOptions extends JPDATestOptions {

        ArtTestOptions(String[] debuggeePath) {
          // Set debuggee command-line.
          if (isRunningArt) {
            ArtCommandBuilder artCommandBuilder = new ArtCommandBuilder(ToolHelper.getDexVm());
            if (ToolHelper.getDexVm().getVersion().isNewerThan(DexVm.Version.V5_1_1)) {
              artCommandBuilder.appendArtOption("-Xcompiler-option");
              artCommandBuilder.appendArtOption("--debuggable");
            }
            if (DEBUG_TESTS && ToolHelper.getDexVm().getVersion().isNewerThan(Version.V4_4_4)) {
              artCommandBuilder.appendArtOption("-verbose:jdwp");
            }
            setProperty("jpda.settings.debuggeeJavaPath", artCommandBuilder.build());
          }

          // Set debuggee classpath
          String debuggeeClassPath = String.join(File.pathSeparator, debuggeePath);
          setProperty("jpda.settings.debuggeeClasspath", debuggeeClassPath);

          // Force to localhost (required for continuous testing configuration). Use port '0'
          // for automatic selection (required when tests are executed in parallel).
          setProperty("jpda.settings.transportAddress", "127.0.0.1:0");

          // Set verbosity
          setProperty("jpda.settings.verbose", Boolean.toString(DEBUG_TESTS));
        }
      }
      return new ArtTestOptions(debuggeePath);
    }

    public void enqueueCommandFirst(Command command) {
      commandsQueue.addFirst(command);
    }

    public void enqueueCommandsFirst(List<Command> commands) {
      for (int i = commands.size() - 1; i >= 0; --i) {
        enqueueCommandFirst(commands.get(i));
      }
    }

    //
    // Inspection
    //

    public interface FrameInspector {
      long getFrameId();
      Location getLocation();

      int getLineNumber();
      String getSourceFile();
      String getClassName();
      String getClassSignature();
      String getMethodName();
      String getMethodSignature();

      // Locals

      List<Variable> getVisibleVariables();

      /**
       * Returns the names of all local variables visible at the current location
       */
      List<String> getLocalNames();

      /**
       * Returns the values of all locals visible at the current location.
       */
      Map<String, Value> getLocalValues();
      void checkNoLocal(String localName);
      void checkLocal(String localName);
      void checkLocal(String localName, Value expectedValue);
    }

    public static class DebuggeeState implements FrameInspector {

      private class DebuggeeFrame implements FrameInspector {

        private final long frameId;
        private final Location location;
        private final Translator translator;

        public DebuggeeFrame(long frameId, Location location, Translator translator) {
          this.frameId = frameId;
          this.location = location;
          this.translator = translator;
        }

        public long getFrameId() {
          return frameId;
        }

        public Location getLocation() {
          return location;
        }

        private int getObfuscatedLineNumber() {
          Location location = getLocation();
          ReplyPacket reply = getMirror().getLineTable(location.classID, location.methodID);
          if (reply.getErrorCode() != 0) {
            return -1;
          }

          long startCodeIndex = reply.getNextValueAsLong();
          long endCodeIndex = reply.getNextValueAsLong();
          int lines = reply.getNextValueAsInt();
          int line = -1;
          long previousLineCodeIndex = -1;
          for (int i = 0; i < lines; ++i) {
            long currentLineCodeIndex = reply.getNextValueAsLong();
            int currentLineNumber = reply.getNextValueAsInt();

            // Code indices are in ascending order.
            assert currentLineCodeIndex >= startCodeIndex;
            assert currentLineCodeIndex <= endCodeIndex;
            assert currentLineCodeIndex >= previousLineCodeIndex;
            previousLineCodeIndex = currentLineCodeIndex;

            if (location.index >= currentLineCodeIndex) {
              line = currentLineNumber;
            } else {
              break;
            }
          }
          return line;
        }

        public int getLineNumber() {
          return translator.getOriginalLineNumber(getObfuscatedLineNumber());
        }

        public String getSourceFile() {
          // TODO(shertz) support JSR-45
          Location location = getLocation();
          CommandPacket sourceFileCommand = new CommandPacket(
              JDWPCommands.ReferenceTypeCommandSet.CommandSetID,
              JDWPCommands.ReferenceTypeCommandSet.SourceFileCommand);
          sourceFileCommand.setNextValueAsReferenceTypeID(location.classID);
          ReplyPacket replyPacket = getMirror().performCommand(sourceFileCommand);
          if (replyPacket.getErrorCode() != 0) {
            return null;
          } else {
            return replyPacket.getNextValueAsString();
          }
        }

        @Override
        public List<Variable> getVisibleVariables() {
          // Get variable table and keep only variables visible at this location.
          Location frameLocation = getLocation();
          return getVariables(getMirror(), frameLocation.classID, frameLocation.methodID).stream()
              .filter(v -> inScope(frameLocation.index, v))
              .collect(Collectors.toList());
        }

        public List<String> getLocalNames() {
          return getVisibleVariables().stream().map(v -> v.getName()).collect(Collectors.toList());
        }

        @Override
        public Map<String, Value> getLocalValues() {
          return JUnit3Wrapper.getVariablesAt(mirror, location).stream()
              .collect(Collectors.toMap(
                  v -> v.getName(),
                  v -> {
                    // Get local value
                    CommandPacket commandPacket = new CommandPacket(
                        JDWPCommands.StackFrameCommandSet.CommandSetID,
                        JDWPCommands.StackFrameCommandSet.GetValuesCommand);
                    commandPacket.setNextValueAsThreadID(getThreadId());
                    commandPacket.setNextValueAsFrameID(getFrameId());
                    commandPacket.setNextValueAsInt(1);
                    commandPacket.setNextValueAsInt(v.getSlot());
                    commandPacket.setNextValueAsByte(v.getTag());
                    ReplyPacket replyPacket = getMirror().performCommand(commandPacket);
                    int valuesCount = replyPacket.getNextValueAsInt();
                    assert valuesCount == 1;
                    return replyPacket.getNextValueAsValue();
                  }
              ));
        }

        private void failNoLocal(String localName) {
          Assert.fail(
              "line " + getLineNumber() + ": Expected local '" + localName + "' not present");
        }

        @Override
        public void checkNoLocal(String localName) {
          Optional<Variable> localVar = JUnit3Wrapper
              .getVariableAt(mirror, getLocation(), localName);
          Assert.assertFalse("Unexpected local: " + localName, localVar.isPresent());
        }

        public void checkLocal(String localName) {
          Optional<Variable> localVar = JUnit3Wrapper
              .getVariableAt(mirror, getLocation(), localName);
          if (!localVar.isPresent()) {
            failNoLocal(localName);
          }
        }

        public void checkLocal(String localName, Value expectedValue) {
          Optional<Variable> localVar = getVariableAt(mirror, getLocation(), localName);
          if (!localVar.isPresent()) {
            failNoLocal(localName);
          }

          // Get value
          CommandPacket commandPacket = new CommandPacket(
              JDWPCommands.StackFrameCommandSet.CommandSetID,
              JDWPCommands.StackFrameCommandSet.GetValuesCommand);
          commandPacket.setNextValueAsThreadID(getThreadId());
          commandPacket.setNextValueAsFrameID(getFrameId());
          commandPacket.setNextValueAsInt(1);
          commandPacket.setNextValueAsInt(localVar.get().getSlot());
          commandPacket.setNextValueAsByte(localVar.get().getTag());
          ReplyPacket replyPacket = getMirror().performCommand(commandPacket);
          int valuesCount = replyPacket.getNextValueAsInt();
          assert valuesCount == 1;
          Value localValue = replyPacket.getNextValueAsValue();

          Assert.assertEquals("Incorrect value for local '" + localName + "'",
              expectedValue, localValue);
        }

        /**
         * Return class name, as found in the binary. If it has not been obfuscated (minified) it's
         * identical to the original class name. Otherwise, it's the obfuscated one.
         */
        private String getObfuscatedClassName() {
          String classSignature = getClassSignature();
          assert classSignature.charAt(0) == 'L';
          // Remove leading 'L' and trailing ';'
          classSignature = classSignature.substring(1, classSignature.length() - 1);
          // Return fully qualified name
          return classSignature.replace('/', '.');
        }

        public String getClassName() {
          return translator.getOriginalClassName(getObfuscatedClassName());
        }

        public String getClassSignature() {
          Location location = getLocation();
          return getMirror().getClassSignature(location.classID);
        }

        // Return method name as found in the binary. Can be obfuscated (minified).
        private String getObfuscatedMethodName() {
          Location location = getLocation();
          return getMirror().getMethodName(location.classID, location.methodID);
        }

        // Return original method name.
        public String getMethodName() {
          return translator.getOriginalMethodName(
              getObfuscatedClassName(), getObfuscatedMethodName(), getMethodSignature());
        }

        public String getMethodSignature() {
          Location location = getLocation();
          CommandPacket command = new CommandPacket(ReferenceTypeCommandSet.CommandSetID,
              ReferenceTypeCommandSet.MethodsWithGenericCommand);
          command.setNextValueAsReferenceTypeID(location.classID);

          ReplyPacket reply = getMirror().performCommand(command);
          assert reply.getErrorCode() == Error.NONE;
          int methods = reply.getNextValueAsInt();

          for (int i = 0; i < methods; ++i) {
            long methodId = reply.getNextValueAsMethodID();
            reply.getNextValueAsString(); // skip name
            String methodSignature = reply.getNextValueAsString();
            reply.getNextValueAsString(); // skip generic signature
            reply.getNextValueAsInt();  // skip modifiers
            if (methodId == location.methodID) {
              return methodSignature;
            }
          }
          throw new AssertionError("No method info for the current location");
        }
      }

      private final VmMirror mirror;
      private final long threadId;
      private final List<DebuggeeFrame> frames;

      public DebuggeeState(VmMirror mirror, long threadId, List<DebuggeeFrame> frames) {
        this.mirror = mirror;
        this.threadId = threadId;
        this.frames = frames;
      }

      public VmMirror getMirror() {
        return mirror;
      }

      public long getThreadId() {
        return threadId;
      }

      public int getFrameDepth() {
        return frames.size();
      }

      public FrameInspector getFrame(int index) {
        return frames.get(index);
      }

      public FrameInspector getTopFrame() {
        return getFrame(0);
      }

      @Override
      public long getFrameId() {
        return getTopFrame().getFrameId();
      }

      @Override
      public Location getLocation() {
        return frames.isEmpty() ? null : getTopFrame().getLocation();
      }

      @Override
      public void checkNoLocal(String localName) {
        getTopFrame().checkNoLocal(localName);
      }

      @Override
      public void checkLocal(String localName) {
        getTopFrame().checkLocal(localName);
      }

      @Override
      public void checkLocal(String localName, Value expectedValue) {
        getTopFrame().checkLocal(localName, expectedValue);
      }

      @Override
      public int getLineNumber() {
        return getTopFrame().getLineNumber();
      }

      @Override
      public String getSourceFile() {
        return getTopFrame().getSourceFile();
      }

      @Override
      public List<String> getLocalNames() {
        return getTopFrame().getLocalNames();
      }

      @Override
      public Map<String, Value> getLocalValues() {
        return getTopFrame().getLocalValues();
      }

      @Override
      public String getClassName() {
        return getTopFrame().getClassName();
      }

      @Override
      public String getClassSignature() {
        return getTopFrame().getClassSignature();
      }

      @Override
      public String getMethodName() {
        return getTopFrame().getMethodName();
      }

      @Override
      public String getMethodSignature() {
        return getTopFrame().getMethodSignature();
      }

      @Override
      public List<Variable> getVisibleVariables() {
        return getTopFrame().getVisibleVariables();
      }

      public Value getStaticField(String className, String fieldName, String fieldSignature) {
        String classSignature = DescriptorUtils.javaTypeToDescriptor(className);
        byte typeTag = TypeTag.CLASS;
        long classId = getMirror().getClassID(classSignature);
        Assert.assertFalse("No class named " + className + " found", classId == -1);

        // The class is available, lookup and read the field.
        long fieldId = findField(getMirror(), classId, fieldName, fieldSignature);
        return getField(getMirror(), classId, fieldId);
      }

      private long findField(VmMirror mirror, long classId, String fieldName,
          String fieldSignature) {

        boolean withGenericSignature = true;
        CommandPacket commandPacket = new CommandPacket(ReferenceTypeCommandSet.CommandSetID,
            ReferenceTypeCommandSet.FieldsWithGenericCommand);
        commandPacket.setNextValueAsReferenceTypeID(classId);
        ReplyPacket replyPacket = mirror.performCommand(commandPacket);
        if (replyPacket.getErrorCode() != Error.NONE) {
          // Retry with older command ReferenceType.Fields.
          withGenericSignature = false;
          commandPacket.setCommand(ReferenceTypeCommandSet.FieldsCommand);
          replyPacket = mirror.performCommand(commandPacket);
          assert replyPacket.getErrorCode() == Error.NONE;
        }

        int fieldsCount = replyPacket.getNextValueAsInt();
        LongList matchingFieldIds = new LongArrayList();
        for (int i = 0; i < fieldsCount; ++i) {
          long currentFieldId = replyPacket.getNextValueAsFieldID();
          String currentFieldName = replyPacket.getNextValueAsString();
          String currentFieldSignature = replyPacket.getNextValueAsString();
          if (withGenericSignature) {
            replyPacket.getNextValueAsString(); // Skip generic signature.
          }
          replyPacket.getNextValueAsInt(); // Skip modifiers.

          // Filter fields based on name (and signature if there is).
          if (fieldName.equals(currentFieldName)) {
            if (fieldSignature == null || fieldSignature.equals(currentFieldSignature)) {
              matchingFieldIds.add(currentFieldId);
            }
          }
        }
        Assert.assertTrue(replyPacket.isAllDataRead());

        Assert.assertFalse("No field named " + fieldName + " found", matchingFieldIds.isEmpty());
        // There must be only one matching field.
        Assert.assertEquals("More than 1 field found: please specify a signature", 1,
            matchingFieldIds.size());
        return matchingFieldIds.getLong(0);
      }

      private Value getField(VmMirror mirror, long classId, long fieldId) {

        CommandPacket commandPacket = new CommandPacket(ReferenceTypeCommandSet.CommandSetID,
            ReferenceTypeCommandSet.GetValuesCommand);
        commandPacket.setNextValueAsReferenceTypeID(classId);
        commandPacket.setNextValueAsInt(1);
        commandPacket.setNextValueAsFieldID(fieldId);
        ReplyPacket replyPacket = mirror.performCommand(commandPacket);
        assert replyPacket.getErrorCode() == Error.NONE;

        int fieldsCount = replyPacket.getNextValueAsInt();
        assert fieldsCount == 1;
        Value result = replyPacket.getNextValueAsValue();
        Assert.assertTrue(replyPacket.isAllDataRead());
        return result;
      }
    }

    public static Optional<Variable> getVariableAt(VmMirror mirror, Location location,
        String localName) {
      return getVariablesAt(mirror, location).stream()
          .filter(v -> localName.equals(v.getName()))
          .findFirst();
    }

    protected static boolean inScope(long index, Variable var) {
      long varStart = var.getCodeIndex();
      long varEnd = varStart + var.getLength();
      return index >= varStart && index < varEnd;
    }

    private static List<Variable> getVariablesAt(VmMirror mirror, Location location) {
      // Get variable table and keep only variables visible at this location.
      return getVariables(mirror, location.classID, location.methodID).stream()
          .filter(v -> inScope(location.index, v))
          .collect(Collectors.toList());
    }

    private static List<Variable> getVariables(VmMirror mirror, long classID, long methodID) {
      List<Variable> list = mirror.getVariableTable(classID, methodID);
      return list != null ? list : Collections.emptyList();
    }

    private void setState(State state) {
      this.state = state;
    }

    public DebuggeeState getDebuggeeState() {
      return debuggeeState;
    }

    private void updateEventContext(EventThread event) {
      final long threadId = event.getThreadID();
      final List<JUnit3Wrapper.DebuggeeState.DebuggeeFrame> frames = new ArrayList<>();
      debuggeeState = new DebuggeeState(getMirror(), threadId, frames);

      // ART returns an error if we ask for frames when there is none. Workaround by asking the
      // frame count first.
      int frameCount = getMirror().getFrameCount(threadId);
      if (frameCount > 0) {
        ReplyPacket replyPacket = getMirror().getThreadFrames(threadId, 0, frameCount);
        int number = replyPacket.getNextValueAsInt();
        assertEquals(frameCount, number);

        for (int i = 0; i < frameCount; ++i) {
          long frameId = replyPacket.getNextValueAsFrameID();
          Location location = replyPacket.getNextValueAsLocation();
          frames.add(debuggeeState.new DebuggeeFrame(frameId, location, translator));
        }
        assertAllDataRead(replyPacket);
      }
    }

    private VmMirror getMirror() {
      return debuggeeWrapper.vmMirror;
    }

    private void resume() {
      debuggeeState = null;
      getMirror().resume();
      setState(State.WaitForEvent);
    }

    private LongList getMethodCodeIndex(long classId, long breakpointMethodId, int lineToSearch) {
      LongList pcs = new LongArrayList();
      ReplyPacket replyPacket = getMirror().getLineTable(classId, breakpointMethodId);
      checkReplyPacket(replyPacket, "Failed to get method line table");
      long start = replyPacket.getNextValueAsLong(); // start
      long end = replyPacket.getNextValueAsLong(); // end
      int linesCount = replyPacket.getNextValueAsInt();
      if (linesCount == 0) {
        pcs.add(-1L);
      } else {
        if (lineToSearch == FIRST_LINE) {
          // Read only the 1st line because code indices are in ascending order
          pcs.add(replyPacket.getNextValueAsLong());
        } else {
          for (int entry = 0; entry < linesCount; entry++) {
            long pc = replyPacket.getNextValueAsLong();
            long lineNumber = replyPacket.getNextValueAsInt();
            if (lineNumber == lineToSearch) {
              pcs.add(pc);
            }
          }
        }
      }
      return pcs;
    }

    //
    // Command processing
    //
    public interface Command {

      void perform(JUnit3Wrapper testBase);

      class RunCommand implements Command {

        @Override
        public void perform(JUnit3Wrapper testBase) {
          testBase.resume();
        }

        @Override
        public String toString() {
          return "run";
        }
      }

      class BreakpointCommand implements Command {

        private final String className;
        private final String methodName;
        private final String methodSignature;
        private boolean requestedClassPrepare = false;
        private int line;

        public BreakpointCommand(String className, String methodName,
            String methodSignature, int line) {
          assert className != null;
          assert methodName != null;
          this.className = className;
          this.methodName = methodName;
          this.methodSignature = methodSignature;
          this.line = line;
        }

        @Override
        public void perform(JUnit3Wrapper testBase) {
          VmMirror mirror = testBase.getMirror();
          String obfuscatedClassName = testBase.translator.getObfuscatedClassName(className);
          String classSignature = getClassSignature(obfuscatedClassName);
          byte typeTag = TypeTag.CLASS;
          long classId = mirror.getClassID(classSignature);
          if (classId == -1) {
            // Is it an interface ?
            classId = mirror.getInterfaceID(classSignature);
            typeTag = TypeTag.INTERFACE;
          }
          if (classId == -1) {
            // The class is not ready yet. Request a CLASS_PREPARE to delay the installation of the
            // breakpoint.
            assert requestedClassPrepare == false : "Already requested class prepare";
            requestedClassPrepare = true;
            ReplyPacket replyPacket = mirror.setClassPrepared(obfuscatedClassName);
            final int classPrepareRequestId = replyPacket.getNextValueAsInt();
            testBase.events.put(Integer.valueOf(classPrepareRequestId), wrapper -> {
              // Remove the CLASS_PREPARE
              wrapper.events.remove(Integer.valueOf(classPrepareRequestId));
              wrapper.getMirror().clearEvent(JDWPConstants.EventKind.CLASS_PREPARE,
                  classPrepareRequestId);

              // Breakpoint then resume.
              wrapper.enqueueCommandsFirst(
                  Arrays.asList(BreakpointCommand.this, new JUnit3Wrapper.Command.RunCommand()));

              // Set wrapper ready to process next command.
              wrapper.setState(State.ProcessCommand);
            });
          } else {
            // The class is available: lookup the method then set the breakpoint.
            String obfuscatedMethodName =
                testBase.translator.getObfuscatedMethodName(className, methodName, methodSignature);
            long breakpointMethodId =
                findMethod(mirror, classId, obfuscatedMethodName, methodSignature);
            LongList pcs = testBase.getMethodCodeIndex(classId, breakpointMethodId, line);
            for (long pc : pcs) {
              Assert.assertTrue("No code in method", pc >= 0);
              ReplyPacket replyPacket = testBase.getMirror().setBreakpoint(
                  new Location(typeTag, classId, breakpointMethodId, pc), SuspendPolicy.ALL);
              assert replyPacket.getErrorCode() == Error.NONE;
              int breakpointId = replyPacket.getNextValueAsInt();
              testBase.events.put(Integer.valueOf(breakpointId), new DefaultEventHandler());
            }
          }
        }

        private static long findMethod(VmMirror mirror, long classId, String methodName,
            String methodSignature) {

          boolean withGenericSignature = true;
          CommandPacket commandPacket = new CommandPacket(ReferenceTypeCommandSet.CommandSetID,
              ReferenceTypeCommandSet.MethodsWithGenericCommand);
          commandPacket.setNextValueAsReferenceTypeID(classId);
          ReplyPacket replyPacket = mirror.performCommand(commandPacket);
          if (replyPacket.getErrorCode() != Error.NONE) {
            // Retry with older command ReferenceType.Methods
            withGenericSignature = false;
            commandPacket.setCommand(ReferenceTypeCommandSet.MethodsCommand);
            replyPacket = mirror.performCommand(commandPacket);
            assert replyPacket.getErrorCode() == Error.NONE;
          }

          int methodsCount = replyPacket.getNextValueAsInt();
          List<Long> matchingMethodIds = new ArrayList<>();
          for (int i = 0; i < methodsCount; ++i) {
            long currentMethodId = replyPacket.getNextValueAsMethodID();
            String currentMethodName = replyPacket.getNextValueAsString();
            String currentMethodSignature = replyPacket.getNextValueAsString();
            if (withGenericSignature) {
              replyPacket.getNextValueAsString(); // skip generic signature
            }
            replyPacket.getNextValueAsInt(); // skip modifiers

            // Filter methods based on name (and signature if there is).
            if (methodName.equals(currentMethodName)) {
              if (methodSignature == null || methodSignature.equals(currentMethodSignature)) {
                matchingMethodIds.add(Long.valueOf(currentMethodId));
              }
            }
          }
          Assert.assertTrue(replyPacket.isAllDataRead());

          Assert
              .assertFalse("No method named " + methodName + " found", matchingMethodIds.isEmpty());
          // There must be only one matching method
          Assert.assertEquals("More than 1 method found: please specify a signature", 1,
              matchingMethodIds.size());
          return matchingMethodIds.get(0);
        }

        @Override
        public String toString() {
          StringBuilder sb = new StringBuilder();
          sb.append("breakpoint");
          sb.append(" class=");
          sb.append(className);
          sb.append(" method=");
          sb.append(methodName);
          sb.append(" signature=");
          sb.append(methodSignature);
          sb.append(" line=");
          sb.append(line);
          return sb.toString();
        }
      }

      class StepCommand implements Command {

        private final StepKind stepDepth;
        private final StepLevel stepSize;
        private final StepFilter stepFilter;

        public StepCommand(StepKind stepDepth, StepLevel stepSize, StepFilter stepFilter) {
          this.stepDepth = stepDepth;
          this.stepSize = stepSize;
          this.stepFilter = stepFilter;
        }

        @Override
        public void perform(JUnit3Wrapper testBase) {
          long threadId = testBase.getDebuggeeState().getThreadId();
          int stepRequestID;
          {
            EventBuilder eventBuilder = Event.builder(EventKind.SINGLE_STEP, SuspendPolicy.ALL);
            eventBuilder.setStep(threadId, stepSize.jdwpValue, stepDepth.jdwpValue);
            stepFilter.getExcludedClasses().stream().forEach(s -> eventBuilder.setClassExclude(s));
            ReplyPacket replyPacket = testBase.getMirror().setEvent(eventBuilder.build());
            stepRequestID = replyPacket.getNextValueAsInt();
            testBase.assertAllDataRead(replyPacket);
          }
          testBase.events.put(stepRequestID, new StepEventHandler(this, stepRequestID, stepFilter));

          // Resume all threads.
          testBase.resume();
        }

        @Override
        public String toString() {
          return String.format("step %s/%s", JDWPConstants.StepDepth.getName(stepDepth.jdwpValue),
              JDWPConstants.StepSize.getName(stepSize.jdwpValue));
        }
      }

      class SetLocalCommand implements Command {

        private final String localName;
        private final Value newValue;

        public SetLocalCommand(String localName, Value newValue) {
          this.localName = localName;
          this.newValue = newValue;
        }

        @Override
        public void perform(JUnit3Wrapper testBase) {
          Optional<Variable> localVar =
              getVariableAt(testBase.getMirror(), testBase.debuggeeState.getLocation(), localName);
          Assert.assertTrue("No local '" + localName + "'", localVar.isPresent());

          CommandPacket setValues = new CommandPacket(StackFrameCommandSet.CommandSetID,
              StackFrameCommandSet.SetValuesCommand);
          setValues.setNextValueAsThreadID(testBase.getDebuggeeState().getThreadId());
          setValues.setNextValueAsFrameID(testBase.getDebuggeeState().getFrameId());
          setValues.setNextValueAsInt(1);
          setValues.setNextValueAsInt(localVar.get().getSlot());
          setValues.setNextValueAsValue(newValue);
          ReplyPacket replyPacket = testBase.getMirror().performCommand(setValues);
          testBase.checkReplyPacket(replyPacket, "StackFrame.SetValues");
        }
      }
    }

    //
    // Event handling
    //
    private interface EventHandler {

      void handle(JUnit3Wrapper testBase);
    }

    private static class DefaultEventHandler implements EventHandler {

      @Override
      public void handle(JUnit3Wrapper testBase) {
        testBase.setState(State.ProcessCommand);
      }
    }

    private static class StepEventHandler extends DefaultEventHandler {

      private final JUnit3Wrapper.Command.StepCommand stepCommand;
      private final int stepRequestID;
      private final StepFilter stepFilter;

      private StepEventHandler(
          JUnit3Wrapper.Command.StepCommand stepCommand,
          int stepRequestID,
          StepFilter stepFilter) {
        this.stepCommand = stepCommand;
        this.stepRequestID = stepRequestID;
        this.stepFilter = stepFilter;
      }

      @Override
      public void handle(JUnit3Wrapper testBase) {
        // Clear step event.
        testBase.getMirror().clearEvent(EventKind.SINGLE_STEP, stepRequestID);
        testBase.events.remove(Integer.valueOf(stepRequestID));

        // Let the filtering happen.
        // Note: we don't need to know whether the location was skipped or not because we are
        // going to process the next command(s) in the queue anyway.
        stepFilter.skipLocation(testBase.getDebuggeeState(), testBase, stepCommand);

        super.handle(testBase);
      }
    }

  }

  //
  // Step filtering
  //

  interface StepFilter {

    /**
     * Provides a list of class name to be skipped when single stepping. This can be a fully
     * qualified name (like java.lang.String) or a subpackage (like java.util.*).
     */
    List<String> getExcludedClasses();

    /**
     * Indicates whether the given location must be skipped.
     */
    boolean skipLocation(JUnit3Wrapper.DebuggeeState debuggeeState, JUnit3Wrapper wrapper,
        JUnit3Wrapper.Command.StepCommand stepCommand);

    /**
     * A {@link StepFilter} that does not filter anything.
     */
    class NoStepFilter implements StepFilter {

      @Override
      public List<String> getExcludedClasses() {
        return Collections.emptyList();
      }

      @Override
      public boolean skipLocation(JUnit3Wrapper.DebuggeeState debuggeeState, JUnit3Wrapper wrapper,
          JUnit3Wrapper.Command.StepCommand stepCommand) {
        return false;
      }
    }

    /**
     * A {@link StepFilter} that matches the default behavior of IntelliJ regarding single
     * stepping.
     */
    class IntelliJStepFilter implements StepFilter {
      // This is the value specified by JDWP in documentation of ReferenceType.Methods command.
      private static final int SYNTHETIC_FLAG = 0xF0000000;

      @Override
      public List<String> getExcludedClasses() {
        return ImmutableList.of(
            "com.sun.*",
            "java.*",
            "javax.*",
            "org.omg.*",
            "sun.*",
            "jdk.internal.*",
            "junit.*",
            "com.intellij.rt.*",
            "com.yourkit.runtime.*",
            "com.springsource.loaded.*",
            "org.springsource.loaded.*",
            "javassist.*",
            "org.apache.webbeans.*",
            "com.ibm.ws.*",
            "kotlin.*"
        );
      }

      @Override
      public boolean skipLocation(JUnit3Wrapper.DebuggeeState debuggeeState, JUnit3Wrapper wrapper,
          JUnit3Wrapper.Command.StepCommand stepCommand) {
        VmMirror mirror = debuggeeState.getMirror();
        Location location = debuggeeState.getLocation();
        // Skip synthetic methods.
        if (isLambdaMethod(mirror, location)) {
          // Lambda methods are synthetic but we do want to stop there.
          if (DEBUG_TESTS) {
            System.out.println("NOT skipping lambda implementation method");
          }
          return false;
        }
        if (isInLambdaClass(mirror, location)) {
          // Lambda classes must be skipped since they are only wrappers around lambda code.
          if (DEBUG_TESTS) {
            System.out.println("Skipping lambda class wrapper method");
          }
          wrapper.enqueueCommandFirst(stepCommand);
          return true;
        }
        if (isSyntheticMethod(mirror, location)) {
          if (DEBUG_TESTS) {
            System.out.println("Skipping synthetic method");
          }
          wrapper.enqueueCommandFirst(stepCommand);
          return true;
        }
        if (isClassLoader(mirror, location)) {
          if (DEBUG_TESTS) {
            System.out.println("Skipping class loader");
          }
          wrapper.enqueueCommandFirst(
              new JUnit3Wrapper.Command.StepCommand(StepKind.OUT, StepLevel.LINE, this));
          return true;
        }
        return false;
      }

      private static boolean isClassLoader(VmMirror mirror, Location location) {
        final long classLoaderClassID = mirror.getClassID("Ljava/lang/ClassLoader;");
        assert classLoaderClassID != -1;
        long classID = location.classID;
        while (classID != 0) {
          if (classID == classLoaderClassID) {
            return true;
          }
          classID = mirror.getSuperclassId(classID);
        }
        return false;
      }

      private static boolean isSyntheticMethod(VmMirror mirror, Location location) {
        // We must gather the modifiers of the method. This is only possible using
        // ReferenceType.Methods command which gather information about all methods in a class.
        Method[] methods = mirror.getMethods(location.classID);
        for (Method method : methods) {
          if (method.getMethodID() == location.methodID &&
              ((method.getModBits() & SYNTHETIC_FLAG) != 0)) {
            return true;
          }
        }
        return false;
      }

      private static boolean isInLambdaClass(VmMirror mirror, Location location) {
        String classSig = mirror.getClassSignature(location.classID);
        return classSig.contains("$$Lambda$");
      }

      private static boolean isLambdaMethod(VmMirror mirror, Location location) {
        String methodName = mirror.getMethodName(location.classID, location.methodID);
        return methodName.startsWith("lambda$");
      }
    }
  }

}
