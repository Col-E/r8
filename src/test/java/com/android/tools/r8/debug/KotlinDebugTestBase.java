// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.AndroidApp;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.harmony.jpda.tests.framework.jdwp.Frame.Variable;
import org.apache.harmony.jpda.tests.framework.jdwp.Location;
import org.junit.BeforeClass;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * A specialization for Kotlin-based tests which provides extra commands.
 */
@RunWith(Parameterized.class)
public abstract class KotlinDebugTestBase extends DebugTestBase {

  protected static class KotlinD8Config extends D8DebugTestConfig {

    private static Map<ToolHelper.KotlinTargetVersion, AndroidApp> compiledResourcesMap =
        new EnumMap<>(ToolHelper.KotlinTargetVersion.class);

    private static synchronized AndroidApp getCompiledResources(KotlinTargetVersion targetVersion)
        throws Throwable {
      AndroidApp compiledResources = compiledResourcesMap.get(targetVersion);
      if (compiledResources == null) {
        Path kotlinJarPath = getKotlinDebugJar(targetVersion);
        compiledResources =
            D8DebugTestConfig.d8Compile(Collections.singletonList(kotlinJarPath), null);
        compiledResourcesMap.put(targetVersion, compiledResources);
      }
      return compiledResources;
    }

    private static Path getKotlinDebugJar(KotlinTargetVersion targetVersion) {
      switch (targetVersion) {
        case JAVA_6:
          return Paths.get(ToolHelper.BUILD_DIR, "test", "debug_test_resources_kotlin_JAVA_6.jar");
        case JAVA_8:
          return Paths.get(ToolHelper.BUILD_DIR, "test", "debug_test_resources_kotlin_JAVA_8.jar");
        default:
          throw new AssertionError("Unknown Kotlin target version");
      }
    }

    public KotlinD8Config(TemporaryFolder temp, KotlinTargetVersion targetVersion) {
      super();
      try {
        Path out = temp.newFolder().toPath().resolve("d8_debug_test_resources_kotlin.jar");
        getCompiledResources(targetVersion).write(out, OutputMode.DexIndexed);
        addPaths(out);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static KotlinD8Config d8ConfigForKotlinJava6;
  private static KotlinD8Config d8ConfigForKotlinJava8;

  @BeforeClass
  public static void setup() {
    d8ConfigForKotlinJava6 = new KotlinD8Config(temp, KotlinTargetVersion.JAVA_6);
    d8ConfigForKotlinJava8 = new KotlinD8Config(temp, KotlinTargetVersion.JAVA_8);
  }

  @Parameters(name = "{0}")
  public static ToolHelper.KotlinTargetVersion[] kotlinTargetVersions() {
    return ToolHelper.KotlinTargetVersion.values();
  }

  @Parameter(0)
  public KotlinTargetVersion targetVersion;

  protected KotlinD8Config getD8Config() {
    switch (targetVersion) {
      case JAVA_6:
        return d8ConfigForKotlinJava6;
      case JAVA_8:
        return d8ConfigForKotlinJava8;
      default:
        throw new AssertionError("Unknown Kotlin target version");
    }
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
