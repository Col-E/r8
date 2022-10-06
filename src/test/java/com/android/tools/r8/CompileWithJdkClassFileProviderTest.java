package com.android.tools.r8;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class CompileWithJdkClassFileProviderTest extends TestBase implements Opcodes {

  @Parameters(name = "{0}, library: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().build(), TestRuntime.getCheckedInCfRuntimes());
  }

  private final TestParameters parameters;
  private final CfRuntime library;

  public CompileWithJdkClassFileProviderTest(TestParameters parameters, CfRuntime library) {
    this.parameters = parameters;
    this.library = library;
  }

  @Test
  public void compileSimpleCodeWithJdkLibrary() throws Exception {
    ClassFileResourceProvider provider = JdkClassFileProvider.fromJdkHome(library.getJavaHome());

    testForR8(parameters.getBackend())
        .addLibraryProvider(provider)
        .addProgramClasses(TestRunner.class)
        .addKeepMainRule(TestRunner.class)
        .setMinApi(AndroidApiLevel.B)
        .run(parameters.getRuntime(), TestRunner.class)
        .assertSuccessWithOutputLines("Hello, world!");

    assert provider instanceof AutoCloseable;
    ((AutoCloseable) provider).close();
  }

  @Test
  public void compileSimpleCodeWithSystemJdk() throws Exception {
    // Don't run duplicate tests (library is not used by the test).
    assumeTrue(library.getVm() == CfVm.JDK8);

    ClassFileResourceProvider provider = JdkClassFileProvider.fromSystemJdk();

    testForR8(parameters.getBackend())
        .addLibraryProvider(provider)
        .addProgramClasses(TestRunner.class)
        .addKeepMainRule(TestRunner.class)
        .setMinApi(AndroidApiLevel.B)
        .run(parameters.getRuntime(), TestRunner.class)
        .assertSuccessWithOutputLines("Hello, world!");

    assert provider instanceof AutoCloseable;
    ((AutoCloseable) provider).close();
  }

  @Test
  public void compileCodeWithJava9APIUsage() throws Exception {
    ClassFileResourceProvider provider = JdkClassFileProvider.fromJdkHome(library.getJavaHome());

    TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder =
        testForR8(parameters.getBackend())
            .setMinApi(AndroidApiLevel.B)
            .addLibraryProvider(provider)
            .addProgramClassFileData(dumpClassWhichUseJava9Flow())
            .addKeepMainRule("MySubscriber");

    if (library.getVm() == CfVm.JDK8) {
      try {
        // java.util.concurrent.Flow$Subscriber is not present in JDK8 rt.jar.
        testBuilder.compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertErrorsMatch(
                  diagnosticMessage(containsString("java.util.concurrent.Flow$Subscriber")));
              // TODO(b/251482856): Unexpected unverifiable code.
              diagnostics.assertWarningsMatch(diagnosticType(UnverifiableCfCodeDiagnostic.class));
              diagnostics.assertNoInfos();
              if (parameters.isDexRuntime()) {
                // TODO(b/175659048): This should likely be a desugar diagnostic.
                diagnostics.assertErrorsMatch(diagnosticType(MissingDefinitionsDiagnostic.class));
              }
            });
      } catch (CompilationFailedException e) {
        return;
      }
      fail("Expected compilation error");
    } else {
      if (parameters.getRuntime().isDex()) {
        // java.util.concurrent.Flow$Subscriber is not present on Android.
        testBuilder
            .run(parameters.getRuntime(), "MySubscriber")
            .applyIf(
                parameters.asDexRuntime().getVersion().isOlderThan(DexVm.Version.V13_0_0),
                b ->
                    b.assertFailureWithErrorThatMatches(
                        anyOf(
                            // Dalvik 4.0.4
                            containsString("java.lang.NoClassDefFoundError: MySubscriber"),
                            // Other Dalviks.
                            containsString(
                                "java.lang.ClassNotFoundException: Didn't find class"
                                    + " \"MySubscriber\""),
                            // Art.
                            containsString(
                                "java.lang.NoClassDefFoundError: Failed resolution of:"
                                    + " Ljava/util/concurrent/Flow$Subscriber;"),
                            // Art 10.
                            containsString("java.lang.ClassNotFoundException: MySubscriber"),
                            // Art 11+.
                            containsString(
                                "java.lang.ClassNotFoundException: "
                                    + "java.util.concurrent.SubmissionPublisher"))),
                b -> b.assertSuccessWithOutputLines("Got : 1", "Got : 2", "Got : 3", "Done"));
      } else {
        if (parameters.getRuntime().asCf().getVm() == CfVm.JDK8) {
          // java.util.concurrent.Flow$Subscriber not present in JDK8.
          testBuilder
              .run(parameters.getRuntime(), "MySubscriber")
              .assertFailureWithErrorThatMatches(
                  containsString("Could not find or load main class MySubscriber"));

        } else {
          // java.util.concurrent.Flow$Subscriber present in JDK9+.
          testBuilder
              .run(parameters.getRuntime(), "MySubscriber")
              .assertSuccessWithOutputLines("Got : 1", "Got : 2", "Got : 3", "Done");
        }
      }
    }

    assert provider instanceof AutoCloseable;
    ((AutoCloseable) provider).close();
  }

  /*
   * The code below is from compiling the following source with javac from OpenJDK 9.0.4 with
   * options:
   *
   *    -source 1.8 -target 1.8
   *
   * Note that -source 1.8 on on Java 9 will use the builtin boot class path (including
   * java.util.concurrent.Flow) if no explicit boot classpath is specified.
   *
   * import java.util.List;
   * import java.lang.Thread;
   * import java.util.concurrent.Flow.Subscriber;
   * import java.util.concurrent.Flow.Subscription;
   * import java.util.concurrent.SubmissionPublisher;
   * import java.util.concurrent.CountDownLatch;
   *
   * public class MySubscriber<T> implements Subscriber<T> {
   *   final static CountDownLatch done = new CountDownLatch(1);
   *
   *   private Subscription subscription;
   *
   *   @Override
   *   public void onSubscribe(Subscription subscription) {
   *     this.subscription = subscription;
   *     subscription.request(1);
   *   }
   *
   *   @Override
   *   public void onNext(T item) {
   *     System.out.println("Got : " + item);
   *     subscription.request(1);
   *   }
   *   @Override
   *   public void onError(Throwable t) {
   *     t.printStackTrace();
   *   }
   *
   *   @Override
   *   public void onComplete() {
   *     System.out.println("Done");
   *     done.countDown();
   *   }
   *
   *   public static void main(String[] args) throws Exception {
   *     SubmissionPublisher<String> publisher = new SubmissionPublisher<>();
   *     MySubscriber<String> subscriber = new MySubscriber<>();
   *     publisher.subscribe(subscriber);
   *     List<String> items = List.of("1", "2", "3");
   *
   *     items.forEach(publisher::submit);
   *     publisher.close();
   *
   *     done.await();
   *   }
   * }
   *
   */
  public static byte[] dumpClassWhichUseJava9Flow() {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_SUPER,
        "MySubscriber",
        "<T:Ljava/lang/Object;>Ljava/lang/Object;Ljava/util/concurrent/Flow$Subscriber<TT;>;",
        "java/lang/Object",
        new String[] {"java/util/concurrent/Flow$Subscriber"});

    classWriter.visitSource("MySubscriber.java", null);

    classWriter.visitInnerClass(
        "java/util/concurrent/Flow$Subscription",
        "java/util/concurrent/Flow",
        "Subscription",
        ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);

    classWriter.visitInnerClass(
        "java/util/concurrent/Flow$Subscriber",
        "java/util/concurrent/Flow",
        "Subscriber",
        ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);

    classWriter.visitInnerClass(
        "java/lang/invoke/MethodHandles$Lookup",
        "java/lang/invoke/MethodHandles",
        "Lookup",
        ACC_PUBLIC | ACC_FINAL | ACC_STATIC);

    {
      fieldVisitor =
          classWriter.visitField(
              ACC_FINAL | ACC_STATIC, "done", "Ljava/util/concurrent/CountDownLatch;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PRIVATE, "subscription", "Ljava/util/concurrent/Flow$Subscription;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(8, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC, "onSubscribe", "(Ljava/util/concurrent/Flow$Subscription;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(15, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitFieldInsn(
          PUTFIELD, "MySubscriber", "subscription", "Ljava/util/concurrent/Flow$Subscription;");
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(16, label1);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitInsn(LCONST_1);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/concurrent/Flow$Subscription", "request", "(J)V", true);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(17, label2);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC, "onNext", "(Ljava/lang/Object;)V", "(TT;)V", null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(21, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      methodVisitor.visitLdcInsn("Got : ");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/Object;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(22, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "MySubscriber", "subscription", "Ljava/util/concurrent/Flow$Subscription;");
      methodVisitor.visitInsn(LCONST_1);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/concurrent/Flow$Subscription", "request", "(J)V", true);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(23, label2);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC, "onError", "(Ljava/lang/Throwable;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(26, label0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Throwable", "printStackTrace", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(27, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "onComplete", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(31, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("Done");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(32, label1);
      methodVisitor.visitFieldInsn(
          GETSTATIC, "MySubscriber", "done", "Ljava/util/concurrent/CountDownLatch;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/util/concurrent/CountDownLatch", "countDown", "()V", false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(33, label2);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC,
              "main",
              "([Ljava/lang/String;)V",
              null,
              new String[] {"java/lang/Exception"});
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(36, label0);
      methodVisitor.visitTypeInsn(NEW, "java/util/concurrent/SubmissionPublisher");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/concurrent/SubmissionPublisher", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(37, label1);
      methodVisitor.visitTypeInsn(NEW, "MySubscriber");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "MySubscriber", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(38, label2);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/util/concurrent/SubmissionPublisher",
          "subscribe",
          "(Ljava/util/concurrent/Flow$Subscriber;)V",
          false);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(39, label3);
      methodVisitor.visitLdcInsn("1");
      methodVisitor.visitLdcInsn("2");
      methodVisitor.visitLdcInsn("3");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "java/util/List",
          "of",
          "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
          true);
      methodVisitor.visitVarInsn(ASTORE, 3);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(41, label4);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "java/util/Objects",
          "requireNonNull",
          "(Ljava/lang/Object;)Ljava/lang/Object;",
          false);
      methodVisitor.visitInsn(POP);
      methodVisitor.visitInvokeDynamicInsn(
          "accept",
          "(Ljava/util/concurrent/SubmissionPublisher;)Ljava/util/function/Consumer;",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/invoke/LambdaMetafactory",
              "metafactory",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {
            Type.getType("(Ljava/lang/Object;)V"),
            new Handle(
                Opcodes.H_INVOKEVIRTUAL,
                "java/util/concurrent/SubmissionPublisher",
                "submit",
                "(Ljava/lang/Object;)I",
                false),
            Type.getType("(Ljava/lang/String;)V")
          });
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/List", "forEach", "(Ljava/util/function/Consumer;)V", true);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(42, label5);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/util/concurrent/SubmissionPublisher", "close", "()V", false);
      Label label6 = new Label();
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(44, label6);
      methodVisitor.visitFieldInsn(
          GETSTATIC, "MySubscriber", "done", "Ljava/util/concurrent/CountDownLatch;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/util/concurrent/CountDownLatch", "await", "()V", false);
      Label label7 = new Label();
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(45, label7);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 4);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(9, label0);
      methodVisitor.visitTypeInsn(NEW, "java/util/concurrent/CountDownLatch");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/concurrent/CountDownLatch", "<init>", "(I)V", false);
      methodVisitor.visitFieldInsn(
          PUTSTATIC, "MySubscriber", "done", "Ljava/util/concurrent/CountDownLatch;");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static class TestRunner {
    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
