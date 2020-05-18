package com.android.tools.r8;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.List;
import org.hamcrest.core.StringContains;
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
            .addLibraryProvider(provider)
            .addProgramClassFileData(dumpClassWhichUseJava9Flow())
            .addKeepMainRule("MySubscriber");

    if (library.getVm() == CfVm.JDK8) {
      try {
        // java.util.concurrent.Flow$Subscriber is not present in JDK8 rt.jar.
        testBuilder.compileWithExpectedDiagnostics(
            diagnotics -> {
              diagnotics.assertErrorsCount(1);
              diagnotics.assertWarningsCount(1);
              diagnotics.assertInfosCount(0);
              assertThat(
                  diagnotics.getErrors().get(0).getDiagnosticMessage(),
                  StringContains.containsString("java.util.concurrent.Flow$Subscriber"));
              assertThat(
                  diagnotics.getWarnings().get(0).getDiagnosticMessage(),
                  StringContains.containsString("java.util.concurrent.Flow$Subscriber"));
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
            .assertFailureWithErrorThatMatches(
                anyOf(
                    // Dalvik 4.0.4
                    containsString("java.lang.NoClassDefFoundError: MySubscriber"),
                    // Other Dalviks.
                    containsString(
                        "java.lang.ClassNotFoundException: Didn't find class \"MySubscriber\""),
                    // Art.
                    containsString(
                        "java.lang.NoClassDefFoundError: "
                            + "Failed resolution of: Ljava/util/concurrent/Flow$Subscriber;"),
                    // Art 10+.
                    containsString("java.lang.ClassNotFoundException: MySubscriber")));
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
   * import java.util.concurrent.locks.Condition;
   * import java.util.concurrent.locks.Lock;
   * import java.util.concurrent.locks.ReentrantLock;
   *
   * public class MySubscriber<T> implements Subscriber<T> {
   *   final static Lock lock = new ReentrantLock();
   *   final static Condition done  = lock.newCondition();
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
   *     signalCondition(done);
   *   }
   *
   *   public static void awaitCondition(Condition condition) throws Exception {
   *     lock.lock();
   *     try {
   *       condition.await();
   *     } finally {
   *       lock.unlock();
   *     }
   *   }
   *
   *   public static void signalCondition(Condition condition) {
   *     lock.lock();
   *     try {
   *       condition.signal();
   *     } finally {
   *       lock.unlock();
   *     }
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
   *     awaitCondition(done);
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
              ACC_FINAL | ACC_STATIC, "lock", "Ljava/util/concurrent/locks/Lock;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_FINAL | ACC_STATIC, "done", "Ljava/util/concurrent/locks/Condition;", null, null);
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
      methodVisitor.visitLineNumber(10, label0);
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
      methodVisitor.visitLineNumber(18, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitFieldInsn(
          PUTFIELD, "MySubscriber", "subscription", "Ljava/util/concurrent/Flow$Subscription;");
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(19, label1);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitInsn(LCONST_1);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/concurrent/Flow$Subscription", "request", "(J)V", true);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(20, label2);
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
      methodVisitor.visitLineNumber(24, label0);
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
      methodVisitor.visitLineNumber(25, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "MySubscriber", "subscription", "Ljava/util/concurrent/Flow$Subscription;");
      methodVisitor.visitInsn(LCONST_1);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/concurrent/Flow$Subscription", "request", "(J)V", true);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(26, label2);
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
      methodVisitor.visitLineNumber(29, label0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Throwable", "printStackTrace", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(30, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "onComplete", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(34, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("Done");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(35, label1);
      methodVisitor.visitFieldInsn(
          GETSTATIC, "MySubscriber", "done", "Ljava/util/concurrent/locks/Condition;");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "MySubscriber",
          "signalCondition",
          "(Ljava/util/concurrent/locks/Condition;)V",
          false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(36, label2);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC,
              "awaitCondition",
              "(Ljava/util/concurrent/locks/Condition;)V",
              null,
              new String[] {"java/lang/Exception"});
      methodVisitor.visitCode();
      Label label0 = new Label();
      Label label1 = new Label();
      Label label2 = new Label();
      methodVisitor.visitTryCatchBlock(label0, label1, label2, null);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(39, label3);
      methodVisitor.visitFieldInsn(
          GETSTATIC, "MySubscriber", "lock", "Ljava/util/concurrent/locks/Lock;");
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/concurrent/locks/Lock", "lock", "()V", true);
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(41, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/concurrent/locks/Condition", "await", "()V", true);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(43, label1);
      methodVisitor.visitFieldInsn(
          GETSTATIC, "MySubscriber", "lock", "Ljava/util/concurrent/locks/Lock;");
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/concurrent/locks/Lock", "unlock", "()V", true);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(44, label4);
      Label label5 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label5);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(43, label2);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 1);
      methodVisitor.visitFieldInsn(
          GETSTATIC, "MySubscriber", "lock", "Ljava/util/concurrent/locks/Lock;");
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/concurrent/locks/Lock", "unlock", "()V", true);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(45, label5);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC,
              "signalCondition",
              "(Ljava/util/concurrent/locks/Condition;)V",
              null,
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      Label label1 = new Label();
      Label label2 = new Label();
      methodVisitor.visitTryCatchBlock(label0, label1, label2, null);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(48, label3);
      methodVisitor.visitFieldInsn(
          GETSTATIC, "MySubscriber", "lock", "Ljava/util/concurrent/locks/Lock;");
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/concurrent/locks/Lock", "lock", "()V", true);
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(50, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/concurrent/locks/Condition", "signal", "()V", true);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(52, label1);
      methodVisitor.visitFieldInsn(
          GETSTATIC, "MySubscriber", "lock", "Ljava/util/concurrent/locks/Lock;");
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/concurrent/locks/Lock", "unlock", "()V", true);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(53, label4);
      Label label5 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label5);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(52, label2);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 1);
      methodVisitor.visitFieldInsn(
          GETSTATIC, "MySubscriber", "lock", "Ljava/util/concurrent/locks/Lock;");
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "java/util/concurrent/locks/Lock", "unlock", "()V", true);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(54, label5);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 2);
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
      methodVisitor.visitLineNumber(57, label0);
      methodVisitor.visitTypeInsn(NEW, "java/util/concurrent/SubmissionPublisher");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/concurrent/SubmissionPublisher", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(58, label1);
      methodVisitor.visitTypeInsn(NEW, "MySubscriber");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "MySubscriber", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(59, label2);
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
      methodVisitor.visitLineNumber(60, label3);
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
      methodVisitor.visitLineNumber(62, label4);
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
      methodVisitor.visitLineNumber(63, label5);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/util/concurrent/SubmissionPublisher", "close", "()V", false);
      Label label6 = new Label();
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(65, label6);
      methodVisitor.visitFieldInsn(
          GETSTATIC, "MySubscriber", "done", "Ljava/util/concurrent/locks/Condition;");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "MySubscriber",
          "awaitCondition",
          "(Ljava/util/concurrent/locks/Condition;)V",
          false);
      Label label7 = new Label();
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(66, label7);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 4);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(11, label0);
      methodVisitor.visitTypeInsn(NEW, "java/util/concurrent/locks/ReentrantLock");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/util/concurrent/locks/ReentrantLock", "<init>", "()V", false);
      methodVisitor.visitFieldInsn(
          PUTSTATIC, "MySubscriber", "lock", "Ljava/util/concurrent/locks/Lock;");
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(12, label1);
      methodVisitor.visitFieldInsn(
          GETSTATIC, "MySubscriber", "lock", "Ljava/util/concurrent/locks/Lock;");
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE,
          "java/util/concurrent/locks/Lock",
          "newCondition",
          "()Ljava/util/concurrent/locks/Condition;",
          true);
      methodVisitor.visitFieldInsn(
          PUTSTATIC, "MySubscriber", "done", "Ljava/util/concurrent/locks/Condition;");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 0);
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
