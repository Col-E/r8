// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.checkcast;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.debug.CfDebugTestConfig;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.debug.DexDebugTestConfig;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CheckCastDebugTestRunner extends DebugTestBase {
  private static final Class<?> MAIN = CheckCastDebugTest.class;
  private final Backend backend;

  private Path r8Out;
  private CodeInspector inspector;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public CheckCastDebugTestRunner(Backend backend) {
    this.backend = backend;
  }

  private static int testCounter = 0;

  @Before
  public void setUp() throws Exception {
    AndroidApp app = readClasses(NeverInline.class, A.class, B.class, C.class, MAIN);
    r8Out = temp.newFile(String.format("r8Out-%s-%d.zip", backend, testCounter++)).toPath();
    R8Command.Builder builder = ToolHelper.prepareR8CommandBuilder(app);
    if (backend == Backend.DEX) {
      builder.setProgramConsumer(new DexIndexedConsumer.ArchiveConsumer(r8Out));
    } else {
      assert backend == Backend.CF;
      builder.setProgramConsumer(new ClassFileConsumer.ArchiveConsumer(r8Out));
    }
    builder.addProguardConfiguration(
        ImmutableList.of(
            "-dontobfuscate",
            keepMainProguardConfigurationWithInliningAnnotation(MAIN)),
        Origin.unknown());
    builder.setMode(CompilationMode.DEBUG);
    ToolHelper.allowTestProguardOptions(builder);
    ToolHelper.runR8(builder.build(), o -> o.enableVerticalClassMerging = false);
    inspector = new CodeInspector(r8Out);
    ClassSubject classSubject = inspector.clazz(MAIN);
    assertThat(classSubject, isPresent());
  }

  @Test
  public void test_differentLocals() throws Throwable {
    ClassSubject classSubject = inspector.clazz(MAIN);
    MethodSubject method = classSubject.method("void", "differentLocals", ImmutableList.of());
    assertThat(method, isPresent());
    long count =
        Streams.stream(method.iterateInstructions(InstructionSubject::isCheckCast)).count();
    assertEquals(1, count);

    DebugTestConfig config = backend == Backend.CF
        ? new CfDebugTestConfig()
        : new DexDebugTestConfig();
    config.addPaths(r8Out);
    runDebugTest(config, MAIN.getCanonicalName(),
        // Object obj = new C();
        breakpoint(MAIN.getCanonicalName(), "differentLocals", "()V", 35),
        run(),
        checkNoLocal("obj"),
        checkNoLocal("a"),
        checkNoLocal("b"),
        checkNoLocal("c"),
        // A a = (A) obj;
        breakpoint(MAIN.getCanonicalName(), "differentLocals", "()V", 36),
        run(),
        checkLocal("obj"),
        checkNoLocal("a"),
        checkNoLocal("b"),
        checkNoLocal("c"),
        // B b = (B) a;
        breakpoint(MAIN.getCanonicalName(), "differentLocals", "()V", 37),
        run(),
        checkLocal("obj"),
        checkLocal("a"),
        checkNoLocal("b"),
        checkNoLocal("c"),
        // C c = (C) b;
        breakpoint(MAIN.getCanonicalName(), "differentLocals", "()V", 38),
        run(),
        checkLocal("obj"),
        checkLocal("a"),
        checkLocal("b"),
        checkNoLocal("c"),
        // System.out.println(c.toString());
        breakpoint(MAIN.getCanonicalName(), "differentLocals", "()V", 39),
        run(),
        checkLocal("obj"),
        checkLocal("a"),
        checkLocal("b"),
        checkLocal("c"),
        run()
    );
  }

  @Test
  public void test_sameLocal() throws Throwable {
    ClassSubject classSubject = inspector.clazz(MAIN);
    MethodSubject method = classSubject.method("void", "sameLocal", ImmutableList.of());
    assertThat(method, isPresent());
    long count =
        Streams.stream(method.iterateInstructions(InstructionSubject::isCheckCast)).count();
    assertEquals(0, count);

    DebugTestConfig config = backend == Backend.CF
        ? new CfDebugTestConfig()
        : new DexDebugTestConfig();
    config.addPaths(r8Out);
    runDebugTest(config, MAIN.getCanonicalName(),
        // Object obj = new C();
        breakpoint(MAIN.getCanonicalName(), "sameLocal", "()V", 44),
        run(),
        checkNoLocal("obj"),
        // obj = (A) obj;
        breakpoint(MAIN.getCanonicalName(), "sameLocal", "()V", 45),
        run(),
        checkLocal("obj"),
        // obj = (B) obj;
        breakpoint(MAIN.getCanonicalName(), "sameLocal", "()V", 46),
        run(),
        checkLocal("obj"),
        // obj = (C) obj;
        breakpoint(MAIN.getCanonicalName(), "sameLocal", "()V", 47),
        run(),
        checkLocal("obj"),
        // System.out.println(obj.toString());
        breakpoint(MAIN.getCanonicalName(), "sameLocal", "()V", 48),
        run(),
        checkLocal("obj"),
        run()
    );
  }

}
