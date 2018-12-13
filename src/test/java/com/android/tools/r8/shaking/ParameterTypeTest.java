// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

interface B112452064SuperInterface1 {
  void foo();
}

interface B112452064SuperInterface2 {
  void bar();
}

interface B112452064SubInterface extends B112452064SuperInterface1, B112452064SuperInterface2 {
}

class B112452064TestMain {

  static void bazSuper1(B112452064SuperInterface1 instance) {
    instance.foo();
  }

  static void bazSub(B112452064SubInterface instance) {
    instance.foo();
  }

  public static void main(String[] args) {
    bazSuper1(new B112452064SuperInterface1() {
      @Override
      public void foo() {
        System.out.println("Anonymous1::foo");
      }
    });
    bazSub(new B112452064SubInterface() {
      @Override
      public void foo() {
        System.out.println("Anonymous2::foo");
      }
      @Override
      public void bar() {
        System.out.println("Anonymous2::bar");
      }
    });
  }
}

@RunWith(Parameterized.class)
public class ParameterTypeTest extends TestBase {

  private final boolean enableArgumentRemoval;

  @Parameters(name = "Argument removal: {0}")
  public static Boolean[] data() {
    return BooleanUtils.values();
  }

  public ParameterTypeTest(boolean enableArgumentRemoval) {
    this.enableArgumentRemoval = enableArgumentRemoval;
  }

  @Test
  public void test_fromJavacWithVerticalClassMerging() throws Exception {
    test_fromJavac(true);
  }

  @Test
  public void test_fromJavacWithoutVerticalClassMerging() throws Exception {
    test_fromJavac(false);
  }

  private void test_fromJavac(boolean enableVerticalClassMerging) throws Exception {
    String mainName = B112452064TestMain.class.getCanonicalName();
    ProcessResult javaResult = ToolHelper.runJava(ToolHelper.getClassPathForTests(), mainName);
    assertEquals(0, javaResult.exitCode);
    assertThat(javaResult.stdout, containsString("Anonymous"));
    assertThat(javaResult.stdout, containsString("::foo"));
    assertEquals(-1, javaResult.stderr.indexOf("ClassNotFoundException"));

    List<String> config = ImmutableList.of(
        "-printmapping",
        "-keep class " + mainName + " {",
        "  public static void main(...);",
        "}"
    );
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFilesForTestDirectory(
        ToolHelper.getPackageDirectoryForTestPackage(B112452064TestMain.class.getPackage()),
        path -> path.getFileName().toString().startsWith("B112452064")));
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm().getLevel());
    builder.addProguardConfiguration(config, Origin.unknown());
    AndroidApp processedApp = ToolHelper.runR8(builder.build(), options -> {
      options.enableInlining = false;
      options.enableVerticalClassMerging = enableVerticalClassMerging;
    });

    Path outDex = temp.getRoot().toPath().resolve("dex.zip");
    processedApp.writeToZip(outDex, OutputMode.DexIndexed);
    ProcessResult artResult = ToolHelper.runArtNoVerificationErrorsRaw(outDex.toString(), mainName);
    assertEquals(0, artResult.exitCode);
    assertEquals(javaResult.stdout, artResult.stdout);
    assertEquals(-1, artResult.stderr.indexOf("ClassNotFoundException"));

    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject superInterface1 = inspector.clazz(B112452064SuperInterface1.class);
    assertThat(superInterface1, isRenamed());
    MethodSubject foo = superInterface1.method("void", "foo", ImmutableList.of());
    assertThat(foo, isRenamed());
    ClassSubject superInterface2 = inspector.clazz(B112452064SuperInterface2.class);
    if (enableVerticalClassMerging) {
      assertThat(superInterface2, not(isPresent()));
    } else {
      assertThat(superInterface2, isRenamed());
    }
    MethodSubject bar = superInterface1.method("void", "bar", ImmutableList.of());
    assertThat(bar, not(isPresent()));
    ClassSubject subInterface = inspector.clazz(B112452064SubInterface.class);
    assertThat(subInterface, isRenamed());
  }

  @Test
  public void test_brokenTypeHierarchy_singleInterface() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    // interface SuperInterface {
    //   void foo();
    // }
    ClassBuilder sup = jasminBuilder.addInterface("SuperInterface");
    MethodSignature foo = sup.addAbstractMethod("foo", ImmutableList.of(), "V");
    // interface SubInterface extends SuperInterface
    ClassBuilder sub = jasminBuilder.addInterface("SubInterface", sup.name);

    // class Foo implements SuperInterface /* supposed to implement SubInterface */
    ClassBuilder impl = jasminBuilder.addClass("Foo", "java/lang/Object", sup.name);
    impl.addDefaultConstructor();
    impl.addVirtualMethod(foo.name, ImmutableList.of(), "V",
        ".limit locals 2",
        ".limit stack 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"" + foo.name + "\"",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "return");

    // class TestMain {
    //   static bar(SubInterface instance) {
    //     // instance.foo();
    //   }
    //   public static void main(String[] args) {
    //     // ART verifies the argument (Foo) is an instance of the parameter type (SubInterface).
    //     bar(new Foo());
    //   }
    // }
    ClassBuilder mainClass = jasminBuilder.addClass("Main");
    MethodSignature bar =
        mainClass.addStaticMethod("bar", ImmutableList.of(sub.getDescriptor()), "V",
            ".limit locals 2",
            ".limit stack 2",
            "getstatic java/lang/System/out Ljava/io/PrintStream;",
            "ldc \"" + "bar" + "\"",
            "invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
            "return");
    mainClass.addMainMethod(
        ".limit locals 1",
        ".limit stack 2",
        "new " + impl.name,
        "dup",
        "invokespecial " + impl.name + "/<init>()V",
        "invokestatic " + mainClass.name + "/bar(" + sub.getDescriptor() + ")V",
        "return");

    final String mainClassName = mainClass.name;
    String proguardConfig = keepMainProguardConfiguration(mainClassName, false, false);

    // Run input program on java.
    Path outputDirectory = temp.newFolder().toPath();
    jasminBuilder.writeClassFiles(outputDirectory);
    ProcessResult javaResult = ToolHelper.runJava(outputDirectory, mainClassName);
    assertEquals(0, javaResult.exitCode);
    assertThat(javaResult.stdout, containsString(bar.name));
    assertEquals(-1, javaResult.stderr.indexOf("ClassNotFoundException"));

    AndroidApp processedApp =
        compileWithR8(
            jasminBuilder.build(),
            proguardConfig,
            options -> {
              // Disable inlining to avoid the (short) tested method from being inlined and removed.
              options.enableInlining = false;
              options.enableArgumentRemoval = enableArgumentRemoval;
            });

    // Run processed (output) program on ART
    ProcessResult artResult = runOnArtRaw(processedApp, mainClassName);
    assertEquals(0, artResult.exitCode);
    assertThat(artResult.stdout, containsString(bar.name));
    assertEquals(-1, artResult.stderr.indexOf("ClassNotFoundException"));

    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject subSubject = inspector.clazz(sub.name);
    assertNotEquals(enableArgumentRemoval, subSubject.isPresent());
  }

  @Test
  public void test_brokenTypeHierarchy_arrayType() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    // interface SuperInterface {
    //   void foo();
    // }
    ClassBuilder sup = jasminBuilder.addInterface("SuperInterface");
    MethodSignature foo = sup.addAbstractMethod("foo", ImmutableList.of(), "V");
    // interface SubInterface extends SuperInterface
    ClassBuilder sub = jasminBuilder.addInterface("SubInterface", sup.name);

    // class Foo implements SuperInterface /* supposed to implement SubInterface */
    ClassBuilder impl = jasminBuilder.addClass("Foo", "java/lang/Object", sup.name);
    impl.addDefaultConstructor();
    impl.addVirtualMethod(foo.name, ImmutableList.of(), "V",
        ".limit locals 2",
        ".limit stack 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"" + foo.name + "\"",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "return");

    // class TestMain {
    //   static bar(SubInterface[] array) {
    //     // instance.foo();
    //   }
    //   public static void main(String[] args) {
    //     // ART verifies the argument (Foo) is an instance of the parameter type (SubInterface).
    //     bar(new Foo());
    //   }
    // }
    ClassBuilder mainClass = jasminBuilder.addClass("Main");
    MethodSignature bar =
        mainClass.addStaticMethod("bar", ImmutableList.of("[" + sub.getDescriptor()), "V",
            ".limit locals 2",
            ".limit stack 2",
            "getstatic java/lang/System/out Ljava/io/PrintStream;",
            "ldc \"" + "bar" + "\"",
            "invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
            "return");
    mainClass.addMainMethod(
        ".limit locals 1",
        ".limit stack 2",
        "iconst_1",
        "anewarray " + impl.name,
        "invokestatic " + mainClass.name + "/bar(" + "[" + sub.getDescriptor() + ")V",
        "return");

    final String mainClassName = mainClass.name;
    String proguardConfig = keepMainProguardConfiguration(mainClassName, false, false);

    // Run input program on java.
    Path outputDirectory = temp.newFolder().toPath();
    jasminBuilder.writeClassFiles(outputDirectory);
    ProcessResult javaResult = ToolHelper.runJava(outputDirectory, mainClassName);
    assertEquals(0, javaResult.exitCode);
    assertThat(javaResult.stdout, containsString(bar.name));
    assertEquals(-1, javaResult.stderr.indexOf("ClassNotFoundException"));

    AndroidApp processedApp =
        compileWithR8(
            jasminBuilder.build(),
            proguardConfig,
            options -> {
              // Disable inlining to avoid the (short) tested method from being inlined and removed.
              options.enableInlining = false;
              options.enableArgumentRemoval = enableArgumentRemoval;
            });

    // Run processed (output) program on ART
    ProcessResult artResult = runOnArtRaw(processedApp, mainClassName);
    if (enableArgumentRemoval) {
      assertEquals(0, artResult.exitCode);
    } else {
      assertNotEquals(0, artResult.exitCode);

      DexVm.Version currentVersion = ToolHelper.getDexVm().getVersion();
      String errorMessage =
          currentVersion.isNewerThan(Version.V4_4_4)
              ? "type Precise Reference: Foo[] but expected Reference: SubInterface[]"
              : "[LFoo; is not instance of [LSubInterface;";
      assertThat(artResult.stderr, containsString(errorMessage));
    }

    assertEquals(-1, artResult.stderr.indexOf("ClassNotFoundException"));

    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject subSubject = inspector.clazz(sub.name);
    assertNotEquals(enableArgumentRemoval, subSubject.isPresent());
  }

  @Test
  public void test_brokenTypeHierarchy_doubleInterfaces() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    // interface SuperInterface1 {
    //   void foo();
    // }
    ClassBuilder sup1 = jasminBuilder.addInterface("SuperInterface1");
    MethodSignature foo = sup1.addAbstractMethod("foo", ImmutableList.of(), "V");
    // interface SuperInterface2 {
    //   void bar();
    // }
    ClassBuilder sup2 = jasminBuilder.addInterface("SuperInterface2");
    MethodSignature bar = sup1.addAbstractMethod("bar", ImmutableList.of(), "V");
    // interface SubInterface extends SuperInterface1, SuperInterface2
    ClassBuilder sub = jasminBuilder.addInterface("SubInterface", sup1.name, sup2.name);

    // class Foo implements SuperInterface1, SuperInterface2
    //   /* supposed to implement SubInterface */
    ClassBuilder impl = jasminBuilder.addClass("Foo", "java/lang/Object", sup1.name, sup2.name);
    impl.addDefaultConstructor();
    impl.addVirtualMethod(foo.name, ImmutableList.of(), "V",
        ".limit locals 2",
        ".limit stack 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"" + foo.name + "\"",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "return");
    impl.addVirtualMethod(bar.name, ImmutableList.of(), "V",
        ".limit locals 2",
        ".limit stack 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"" + bar.name + "\"",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "return");

    // class TestMain {
    //   static bar(SubInterface instance) {
    //     // instance.foo();
    //   }
    //   public static void main(String[] args) {
    //     // ART verifies the argument (Foo) is an instance of the parameter type (SubInterface).
    //     bar(new Foo());
    //   }
    // }
    ClassBuilder mainClass = jasminBuilder.addClass("Main");
    MethodSignature baz =
        mainClass.addStaticMethod("baz", ImmutableList.of(sub.getDescriptor()), "V",
            ".limit locals 2",
            ".limit stack 2",
            "getstatic java/lang/System/out Ljava/io/PrintStream;",
            "ldc \"" + "baz" + "\"",
            "invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
            "return");
    mainClass.addMainMethod(
        ".limit locals 1",
        ".limit stack 2",
        "new " + impl.name,
        "dup",
        "invokespecial " + impl.name + "/<init>()V",
        "invokestatic " + mainClass.name + "/baz(" + sub.getDescriptor() + ")V",
        "return");

    final String mainClassName = mainClass.name;
    String proguardConfig = keepMainProguardConfiguration(mainClassName, false, false);

    // Run input program on java.
    Path outputDirectory = temp.newFolder().toPath();
    jasminBuilder.writeClassFiles(outputDirectory);
    ProcessResult javaResult = ToolHelper.runJava(outputDirectory, mainClassName);
    assertEquals(0, javaResult.exitCode);
    assertThat(javaResult.stdout, containsString(baz.name));
    assertEquals(-1, javaResult.stderr.indexOf("ClassNotFoundException"));

    AndroidApp processedApp =
        compileWithR8(
            jasminBuilder.build(),
            proguardConfig,
            options -> {
              // Disable inlining to avoid the (short) tested method from being inlined and removed.
              options.enableInlining = false;
              options.enableArgumentRemoval = enableArgumentRemoval;
            });

    // Run processed (output) program on ART
    ProcessResult artResult = runOnArtRaw(processedApp, mainClassName);
    assertEquals(0, artResult.exitCode);
    assertThat(artResult.stdout, containsString(baz.name));
    assertEquals(javaResult.stdout, artResult.stdout);
    assertEquals(-1, artResult.stderr.indexOf("ClassNotFoundException"));

    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject subSubject = inspector.clazz(sub.name);
    assertNotEquals(enableArgumentRemoval, subSubject.isPresent());
  }
}
