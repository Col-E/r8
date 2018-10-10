// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// AbstractChecker -> X:
abstract class AbstractChecker {
  // String tag -> p
  private String tag = "PrivateInitialTag_AbstractChecker";

  // check() -> x
  private void check() {
    System.out.println("AbstractChecker#check:" + tag);
  }

  // foo() -> a
  protected void foo() {
    check();
  }
}

// ConcreteChecker -> Y:
class ConcreteChecker extends AbstractChecker {
  // This should not be conflict with AbstractChecker#tag due to the access control.
  // String tag -> q
  private String tag = "PrivateInitialTag_ConcreteChecker";

  ConcreteChecker(String tag){
    this.tag = tag;
  }

  // This should not be conflict with AbstractChecker#check due to the access control.
  // check() -> y
  private void check() {
    System.out.println("ConcreteChecker#check:" + tag);
  }

  // foo() -> a
  @Override
  protected void foo() {
    super.foo();
    check();
  }
}

class MemberResolutionTestMain {
  public static void main(String[] args) {
    ConcreteChecker c = new ConcreteChecker("NewTag");
    c.foo();
  }
}

@RunWith(Parameterized.class)
public class MemberResolutionTest extends TestBase {
  private final static List<Class> CLASSES = ImmutableList.of(
      AbstractChecker.class, ConcreteChecker.class, MemberResolutionTestMain.class);

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public MemberResolutionTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testPrivateMethodsWithSameName() throws Exception {
    String pkg = this.getClass().getPackage().getName();
    Path mapPath = temp.newFile("test-mapping.txt").toPath();
    List<String> pgMap = ImmutableList.of(
        pkg + ".AbstractChecker -> " + pkg + ".X:",
        "  java.lang.String tag -> p",
        "  void check() -> x",
        "  void foo() -> a",
        pkg + ".ConcreteChecker -> " + pkg + ".Y:",
        "  java.lang.String tag -> q",
        "  void check() -> y",
        "  void foo() -> a"
    );
    FileUtils.writeTextFile(mapPath, pgMap);

    AndroidApp app = readClasses(CLASSES);
    R8Command.Builder builder = ToolHelper.prepareR8CommandBuilder(app, emptyConsumer(backend));
    builder
        .addProguardConfiguration(
            ImmutableList.of(
                keepMainProguardConfiguration(MemberResolutionTestMain.class),
                // Do not turn on -allowaccessmodification
                "-applymapping " + mapPath,
                "-dontobfuscate"), // to use the renamed names in test-mapping.txt
            Origin.unknown())
        .addLibraryFiles(runtimeJar(backend));
    AndroidApp processedApp =
        ToolHelper.runR8(
            builder.build(),
            options -> {
              options.enableInlining = false;
              options.enableVerticalClassMerging = false;
            });

    String outputBefore = runOnJava(MemberResolutionTestMain.class);
    String outputAfter = runOnVM(processedApp, MemberResolutionTestMain.class, backend);
    assertEquals(outputBefore, outputAfter);

    CodeInspector codeInspector = new CodeInspector(processedApp, mapPath);
    ClassSubject base = codeInspector.clazz(AbstractChecker.class);
    assertThat(base, isPresent());
    FieldSubject p = base.field("java.lang.String", "tag");
    assertThat(p, isPresent());
    assertThat(p, isRenamed());
    assertEquals("p", p.getFinalName());
    MethodSubject x = base.method("void", "check", ImmutableList.of());
    assertThat(x, isPresent());
    assertThat(x, isRenamed());
    assertEquals("x", x.getFinalName());

    ClassSubject sub = codeInspector.clazz(ConcreteChecker.class);
    assertThat(sub, isPresent());
    FieldSubject q = sub.field("java.lang.String", "tag");
    assertThat(q, isPresent());
    assertThat(q, isRenamed());
    assertEquals("q", q.getFinalName());
    MethodSubject y = sub.method("void", "check", ImmutableList.of());
    assertThat(y, isPresent());
    assertThat(y, isRenamed());
    assertEquals("y", y.getFinalName());
  }
}
