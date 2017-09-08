// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OffOrAuto;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.File;
import java.io.IOException;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  enum RuntimeKind {
    JAVA,
    ART
  }

  // Set to JAVA to run tests with java
  private static final RuntimeKind RUNTIME_KIND = RuntimeKind.ART;

  // Set to true to enable verbose logs
  private static final boolean DEBUG_TESTS = false;

  // Dalvik does not support command ReferenceType.Methods which is used to set breakpoint.
  // TODO(shertz) use command ReferenceType.MethodsWithGeneric instead
  private static final List<DexVm> UNSUPPORTED_ART_VERSIONS = ImmutableList.of(DexVm.ART_4_4_4);

  private static final Path JDWP_JAR = ToolHelper
      .getJdwpTestsJarPath(ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm()));
  private static final Path DEBUGGEE_JAR = Paths
      .get(ToolHelper.BUILD_DIR, "test", "debug_test_resources.jar");
  private static final Path DEBUGGEE_JAVA8_JAR = Paths
      .get(ToolHelper.BUILD_DIR, "test", "debug_test_resources_java8.jar");
  private static final Path DEBUGGEE_KOTLIN_JAR = Paths
      .get(ToolHelper.BUILD_DIR, "test", "debug_test_resources_kotlin.jar");

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static Path jdwpDexD8 = null;
  private static Path debuggeeDexD8 = null;
  private static Path debuggeeJava8DexD8 = null;
  private static Path debuggeeKotlinDexD8 = null;

  @Rule
  public TestName testName = new TestName();

  @BeforeClass
  public static void setUp() throws Exception {
    // Convert jar to dex with d8 with debug info
    jdwpDexD8 = compileToDex(null, JDWP_JAR);
    debuggeeDexD8 = compileToDex(null, DEBUGGEE_JAR);
    debuggeeJava8DexD8 = compileToDex(options -> {
          // Enable desugaring for preN runtimes
          options.interfaceMethodDesugaring = OffOrAuto.Auto;
        }, DEBUGGEE_JAVA8_JAR);
    debuggeeKotlinDexD8 = compileToDex(null, DEBUGGEE_KOTLIN_JAR);
  }

  protected static Path compileToDex(Consumer<InternalOptions> optionsConsumer,
      Path jarToCompile) throws IOException, CompilationException {
    int minSdk = ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm());
    assert jarToCompile.toFile().exists();
    Path dexOutputDir = temp.newFolder().toPath();
    ToolHelper.runD8(
        D8Command.builder()
            .addProgramFiles(jarToCompile)
            .setOutputPath(dexOutputDir)
            .setMinApiLevel(minSdk)
            .setMode(CompilationMode.DEBUG)
            .addLibraryFiles(Paths.get(ToolHelper.getAndroidJar(minSdk)))
            .build(),
        optionsConsumer);
    return dexOutputDir.resolve("classes.dex");

  }

  protected final boolean supportsDefaultMethod() {
    return RUNTIME_KIND == RuntimeKind.JAVA ||
        ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm()) >= Constants.ANDROID_N_API;
  }

  protected final boolean isRunningJava() {
    return RUNTIME_KIND == RuntimeKind.JAVA;
  }

  protected final void runDebugTest(String debuggeeClass, JUnit3Wrapper.Command... commands)
      throws Throwable {
    runDebugTest(Collections.emptyList(), debuggeeClass, Arrays.asList(commands));
  }

  protected final void runDebugTest(List<Path> extraPaths, String debuggeeClass,
      JUnit3Wrapper.Command... commands) throws Throwable {
    runDebugTest(extraPaths, debuggeeClass, Arrays.asList(commands));
  }

  protected final void runDebugTest(String debuggeeClass, List<JUnit3Wrapper.Command> commands)
      throws Throwable {
    runDebugTest(Collections.emptyList(), LanguageFeatures.JAVA_7, debuggeeClass, commands);
  }

  protected final void runDebugTest(List<Path> extraPaths, String debuggeeClass,
      List<JUnit3Wrapper.Command> commands) throws Throwable {
    runDebugTest(extraPaths, LanguageFeatures.JAVA_7, debuggeeClass, commands);
  }

  protected final void runDebugTestJava8(String debuggeeClass, JUnit3Wrapper.Command... commands)
      throws Throwable {
    runDebugTestJava8(debuggeeClass, Arrays.asList(commands));
  }

  protected final void runDebugTestJava8(String debuggeeClass, List<JUnit3Wrapper.Command> commands)
      throws Throwable {
    runDebugTest(Collections.emptyList(), LanguageFeatures.JAVA_8, debuggeeClass, commands);
  }

  protected final void runDebugTestKotlin(String debuggeeClass, JUnit3Wrapper.Command... commands)
      throws Throwable {
    runDebugTestKotlin(debuggeeClass, Arrays.asList(commands));
  }

  protected final void runDebugTestKotlin(String debuggeeClass,
      List<JUnit3Wrapper.Command> commands) throws Throwable {
    runDebugTest(Collections.emptyList(), LanguageFeatures.KOTLIN, debuggeeClass, commands);
  }

  protected enum LanguageFeatures {
    JAVA_7(DEBUGGEE_JAR) {
      @Override
      public Path getDexPath() {
        return debuggeeDexD8;
      }
    },
    JAVA_8(DEBUGGEE_JAVA8_JAR) {
      @Override
      public Path getDexPath() {
        return debuggeeJava8DexD8;
      }
    },
    KOTLIN(DEBUGGEE_KOTLIN_JAR) {
      @Override
      public Path getDexPath() {
        return debuggeeKotlinDexD8;
      }
    };

    private final Path jarPath;

    LanguageFeatures(Path jarPath) {
      this.jarPath = jarPath;
    }

    public Path getJarPath() {
      return jarPath;
    }

    public abstract Path getDexPath();
  }

  private void runDebugTest(List<Path> extraPaths, LanguageFeatures languageFeatures,
      String debuggeeClass, List<JUnit3Wrapper.Command> commands) throws Throwable {
    // Skip test due to unsupported runtime.
    Assume.assumeTrue("Skipping test " + testName.getMethodName() + " because ART is not supported",
        ToolHelper.artSupported());
    Assume.assumeFalse(
        "Skipping failing test " + testName.getMethodName() + " for runtime " + ToolHelper
            .getDexVm(), UNSUPPORTED_ART_VERSIONS.contains(ToolHelper.getDexVm()));

    String[] paths = new String[extraPaths.size() + 2];
    int indexPath = 0;
    if (RUNTIME_KIND == RuntimeKind.JAVA) {
      paths[indexPath++] = JDWP_JAR.toString();
      paths[indexPath++] = languageFeatures.getJarPath().toString();
    } else {
      paths[indexPath++] = jdwpDexD8.toString();
      paths[indexPath++] = languageFeatures.getDexPath().toString();
    }
    for (Path extraPath : extraPaths) {
      paths[indexPath++] = extraPath.toString();
    }
    new JUnit3Wrapper(debuggeeClass, paths, commands).runBare();
  }

  protected final JUnit3Wrapper.Command run() {
    return new JUnit3Wrapper.Command.RunCommand();
  }

  protected final JUnit3Wrapper.Command breakpoint(String className, String methodName) {
    return breakpoint(className, methodName, null);
  }

  protected final JUnit3Wrapper.Command breakpoint(String className, String methodName,
      String methodSignature) {
    return new JUnit3Wrapper.Command.BreakpointCommand(className, methodName, methodSignature);
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
    return new JUnit3Wrapper.Command.StepCommand(stepKind.jdwpValue, stepLevel.jdwpValue,
        stepFilter, state -> true);
  }

  protected JUnit3Wrapper.Command stepUntil(StepKind stepKind, StepLevel stepLevel,
      Function<JUnit3Wrapper.DebuggeeState, Boolean> stepUntil) {
    return new JUnit3Wrapper.Command.StepCommand(stepKind.jdwpValue, stepLevel.jdwpValue, NO_FILTER,
        stepUntil);
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
          isRunningJava() || ToolHelper.getDexVm().isOlderThanOrEqual(DexVm.ART_7_0_0));
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

    // Active event requests.
    private final Map<Integer, EventHandler> events = new TreeMap<>();

    JUnit3Wrapper(String debuggeeClassName, String[] debuggeePath, List<Command> commands) {
      this.debuggeeClassName = debuggeeClassName;
      this.debuggeePath = debuggeePath;
      this.commandsQueue = new ArrayDeque<>(commands);
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
            command.perform(this);
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
          String methodName = getMirror()
              .getMethodName(debuggeeState.getLocation().classID,
                  debuggeeState.getLocation().methodID);
          String methodSig = getMirror()
              .getMethodSignature(debuggeeState.getLocation().classID,
                  debuggeeState.getLocation().methodID);
          System.out.println(String
              .format("Suspended in %s#%s%s@0x%x (line=%d)", classSig, methodName, methodSig,
                  Long.valueOf(debuggeeState.getLocation().index),
                  Integer.valueOf(debuggeeState.getLineNumber())));
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
          if (RUNTIME_KIND == RuntimeKind.ART) {
            ArtCommandBuilder artCommandBuilder = new ArtCommandBuilder(ToolHelper.getDexVm());
            if (ToolHelper.getDexVm().isNewerThan(DexVm.ART_5_1_1)) {
              artCommandBuilder.appendArtOption("-Xcompiler-option");
              artCommandBuilder.appendArtOption("--debuggable");
            }
            if (DEBUG_TESTS) {
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

        public DebuggeeFrame(long frameId, Location location) {
          this.frameId = frameId;
          this.location = location;
        }

        public long getFrameId() {
          return frameId;
        }

        public Location getLocation() {
          return location;
        }

        public int getLineNumber() {
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

        public String getClassName() {
          String classSignature = getClassSignature();
          assert classSignature.charAt(0) == 'L';
          // Remove leading 'L' and trailing ';'
          classSignature = classSignature.substring(1, classSignature.length() - 1);
          // Return fully qualified name
          return classSignature.replace('/', '.');
        }

        public String getClassSignature() {
          Location location = getLocation();
          return getMirror().getClassSignature(location.classID);
        }

        public String getMethodName() {
          Location location = getLocation();
          return getMirror().getMethodName(location.classID, location.methodID);
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
          frames.add(debuggeeState.new DebuggeeFrame(frameId, location));
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

    private long getMethodFirstCodeIndex(long classId, long breakpointMethodId) {
      ReplyPacket replyPacket = getMirror().getLineTable(classId, breakpointMethodId);
      checkReplyPacket(replyPacket, "Failed to get method line table");
      replyPacket.getNextValueAsLong(); // start
      replyPacket.getNextValueAsLong(); // end
      int linesCount = replyPacket.getNextValueAsInt();
      if (linesCount == 0) {
        return -1;
      } else {
        // Read only the 1st line because code indices are in ascending order
        return replyPacket.getNextValueAsLong();
      }
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

        public BreakpointCommand(String className, String methodName,
            String methodSignature) {
          assert className != null;
          assert methodName != null;
          this.className = className;
          this.methodName = methodName;
          this.methodSignature = methodSignature;
        }

        @Override
        public void perform(JUnit3Wrapper testBase) {
          VmMirror mirror = testBase.getMirror();
          String classSignature = getClassSignature(className);
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
            ReplyPacket replyPacket = mirror.setClassPrepared(className);
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
            long breakpointMethodId = findMethod(mirror, classId, methodName, methodSignature);
            long index = testBase.getMethodFirstCodeIndex(classId, breakpointMethodId);
            Assert.assertTrue("No code in method", index >= 0);
            ReplyPacket replyPacket = testBase.getMirror().setBreakpoint(
                new Location(typeTag, classId, breakpointMethodId, index), SuspendPolicy.ALL);
            assert replyPacket.getErrorCode() == Error.NONE;
            int breakpointId = replyPacket.getNextValueAsInt();
            testBase.events.put(Integer.valueOf(breakpointId), new DefaultEventHandler());
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
          return sb.toString();
        }
      }

      class StepCommand implements Command {

        private final byte stepDepth;
        private final byte stepSize;
        private final StepFilter stepFilter;

        /**
         * A {@link Function} taking a {@link DebuggeeState} as input and returns {@code true} to
         * stop stepping, {@code false} to continue.
         */
        private final Function<JUnit3Wrapper.DebuggeeState, Boolean> stepUntil;

        public StepCommand(byte stepDepth,
            byte stepSize, StepFilter stepFilter,
            Function<DebuggeeState, Boolean> stepUntil) {
          this.stepDepth = stepDepth;
          this.stepSize = stepSize;
          this.stepFilter = stepFilter;
          this.stepUntil = stepUntil;
        }

        @Override
        public void perform(JUnit3Wrapper testBase) {
          long threadId = testBase.getDebuggeeState().getThreadId();
          int stepRequestID;
          {
            EventBuilder eventBuilder = Event.builder(EventKind.SINGLE_STEP, SuspendPolicy.ALL);
            eventBuilder.setStep(threadId, stepSize, stepDepth);
            stepFilter.getExcludedClasses().stream().forEach(s -> eventBuilder.setClassExclude(s));
            ReplyPacket replyPacket = testBase.getMirror().setEvent(eventBuilder.build());
            stepRequestID = replyPacket.getNextValueAsInt();
            testBase.assertAllDataRead(replyPacket);
          }
          testBase.events
              .put(stepRequestID, new StepEventHandler(this, stepRequestID, stepFilter, stepUntil));

          // Resume all threads.
          testBase.resume();
        }

        @Override
        public String toString() {
          return String.format("step %s/%s", JDWPConstants.StepDepth.getName(stepDepth),
              JDWPConstants.StepSize.getName(stepSize));
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
      private final Function<DebuggeeState, Boolean> stepUntil;

      private StepEventHandler(
          JUnit3Wrapper.Command.StepCommand stepCommand,
          int stepRequestID,
          StepFilter stepFilter,
          Function<DebuggeeState, Boolean> stepUntil) {
        this.stepCommand = stepCommand;
        this.stepRequestID = stepRequestID;
        this.stepFilter = stepFilter;
        this.stepUntil = stepUntil;
      }

      @Override
      public void handle(JUnit3Wrapper testBase) {
        // Clear step event.
        testBase.getMirror().clearEvent(EventKind.SINGLE_STEP, stepRequestID);
        testBase.events.remove(Integer.valueOf(stepRequestID));

        // Do we need to step again ?
        boolean repeatStep = false;
        if (stepFilter
            .skipLocation(testBase.getMirror(), testBase.getDebuggeeState().getLocation())) {
          repeatStep = true;
        } else if (stepUntil.apply(testBase.getDebuggeeState()) == Boolean.FALSE) {
          repeatStep = true;
        }
        if (repeatStep) {
          // In order to repeat the step now, we need to add it at the beginning of the queue.
          testBase.enqueueCommandFirst(stepCommand);
        }
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
    boolean skipLocation(VmMirror mirror, Location location);

    /**
     * A {@link StepFilter} that does not filter anything.
     */
    class NoStepFilter implements StepFilter {

      @Override
      public List<String> getExcludedClasses() {
        return Collections.emptyList();
      }

      @Override
      public boolean skipLocation(VmMirror mirror, Location location) {
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
        return Arrays.asList(
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
      public boolean skipLocation(VmMirror mirror, Location location) {
        // TODO(shertz) we also need to skip class loaders to act like IntelliJ.
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
          return true;
        }
        if (isSyntheticMethod(mirror, location)) {
          if (DEBUG_TESTS) {
            System.out.println("Skipping synthetic method");
          }
          return true;
        }
        return false;
      }

      private static boolean isSyntheticMethod(VmMirror mirror, Location location) {
        // We must gather the modifiers of the method. This is only possible using
        // ReferenceType.Methods command which gather information about all methods in a class.
        ReplyPacket reply = mirror.getMethods(location.classID);
        int methodsCount = reply.getNextValueAsInt();
        for (int i = 0; i < methodsCount; ++i) {
          long methodId = reply.getNextValueAsMethodID();
          reply.getNextValueAsString();  // skip method name
          reply.getNextValueAsString();  // skip method signature
          int modifiers = reply.getNextValueAsInt();
          if (methodId == location.methodID &&
              ((modifiers & SYNTHETIC_FLAG) != 0)) {
            return true;
          }
        }
        return false;
      }

      private static boolean isInLambdaClass(VmMirror mirror, Location location) {
        String classSig = mirror.getClassSignature(location.classID);
        return classSig.contains("$$Lambda$");
      }

      private boolean isLambdaMethod(VmMirror mirror, Location location) {
        String methodName = mirror.getMethodName(location.classID, location.methodID);
        return methodName.startsWith("lambda$");
      }
    }
  }

}
