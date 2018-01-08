// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.harmony.jpda.tests.framework.jdwp.Frame.Variable;
import org.apache.harmony.jpda.tests.framework.jdwp.Location;
import org.junit.BeforeClass;
import org.junit.rules.TemporaryFolder;

/**
 * A specialization for Kotlin-based tests which provides extra commands.
 */
public abstract class KotlinDebugTestBase extends DebugTestBase {

  private static final Path DEBUGGEE_KOTLIN_JAR =
      Paths.get(ToolHelper.BUILD_DIR, "test", "debug_test_resources_kotlin.jar");

  protected static class KotlinD8Config extends D8DebugTestConfig {

    private static AndroidApp compiledResources = null;

    private static synchronized AndroidApp getCompiledResources() throws Throwable {
      if (compiledResources == null) {
        compiledResources =
            D8DebugTestConfig.d8Compile(Collections.singletonList(DEBUGGEE_KOTLIN_JAR), null);
      }
      return compiledResources;
    }

    public KotlinD8Config(TemporaryFolder temp) {
      super();
      try {
        Path out = temp.newFolder().toPath().resolve("d8_debug_test_resources_kotlin.jar");
        getCompiledResources().write(out, OutputMode.DexIndexed);
        addPaths(out);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static KotlinD8Config d8Config;

  @BeforeClass
  public static void setup() {
    d8Config = new KotlinD8Config(temp);
  }

  protected KotlinD8Config getD8Config() {
    return d8Config;
  }

  protected final JUnit3Wrapper.Command kotlinStepOver() {
    return testBaseBeforeStep -> {
      final JUnit3Wrapper.DebuggeeState debuggeeStateBeforeStep = testBaseBeforeStep
          .getDebuggeeState();
      final int frameDepthBeforeStep = debuggeeStateBeforeStep.getFrameDepth();
      final Location locationBeforeStep = debuggeeStateBeforeStep.getLocation();
      final List<Variable> kotlinLvsBeforeStep = getVisibleKotlinInlineVariables(
          debuggeeStateBeforeStep);

      // This is the command that will be executed after the initial (normal) step over. If we
      // reach an inlined location, this command will step until reaching a non-inlined location.
      JUnit3Wrapper.Command commandAfterStep = testBaseAfterStep -> {
        // Get the new debuggee state (previous one is stale).
        JUnit3Wrapper.DebuggeeState debuggeeStateAfterStep = testBaseBeforeStep.getDebuggeeState();

        // Are we in the same frame ?
        final int frameDepthAfterStep = debuggeeStateAfterStep.getFrameDepth();
        final Location locationAfterStep = debuggeeStateAfterStep.getLocation();
        if (frameDepthBeforeStep == frameDepthAfterStep
            && locationBeforeStep.classID == locationAfterStep.classID
            && locationBeforeStep.methodID == locationAfterStep.methodID) {
          // We remain in the same method. Do we step into an inlined section ?
          List<Variable> kotlinLvsAfterStep = getVisibleKotlinInlineVariables(
              debuggeeStateAfterStep);
          if (kotlinLvsBeforeStep.isEmpty() && !kotlinLvsAfterStep.isEmpty()) {
            assert kotlinLvsAfterStep.size() == 1;

            // We're located in an inlined section. Instead of doing a classic step out, we must
            // jump out of the inlined section.
            Variable inlinedSectionLv = kotlinLvsAfterStep.get(0);
            testBaseAfterStep.enqueueCommandFirst(stepUntilOutOfInlineScope(inlinedSectionLv));
          }
        }
      };

      // Step over then check whether we need to continue stepping.
      testBaseBeforeStep.enqueueCommandsFirst(Arrays.asList(stepOver(), commandAfterStep));
    };
  }

  protected final JUnit3Wrapper.Command kotlinStepOut() {
    return wrapper -> {
      final List<Variable> kotlinLvsBeforeStep = getVisibleKotlinInlineVariables(
          wrapper.getDebuggeeState());

      JUnit3Wrapper.Command nextCommand;
      if (!kotlinLvsBeforeStep.isEmpty()) {
        // We are in an inline section. We need to step until being out of inline scope.
        assert kotlinLvsBeforeStep.size() == 1;
        final Variable inlinedSectionLv = kotlinLvsBeforeStep.get(0);
        nextCommand = stepUntilOutOfInlineScope(inlinedSectionLv);
      } else {
        nextCommand = stepOut();
      }
      wrapper.enqueueCommandFirst(nextCommand);
    };
  }

  private JUnit3Wrapper.Command stepUntilOutOfInlineScope(Variable inlineScopeLv) {
    return stepUntil(StepKind.OVER, StepLevel.LINE, debuggeeState -> {
      boolean inInlineScope = JUnit3Wrapper
          .inScope(debuggeeState.getLocation().index, inlineScopeLv);
      return !inInlineScope;
    });
  }

}
