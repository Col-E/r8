package com.android.tools.r8;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.Test;
import org.objectweb.asm.Opcodes;

public class JdkClassFileProviderTest extends TestBase implements Opcodes {

  @Test
  public void testInvalid8RuntimeClassPath() throws Exception {
    Path path = temp.newFolder().toPath();
    try {
      JdkClassFileProvider.fromJdkHome(path);
      fail("Not supposed to succeed");
    } catch (IOException e) {
      assertThat(e.toString(), containsString(path.toString()));
      assertThat(e.toString(), containsString("does not look like a Java home"));
    }
  }

  @Test
  public void testJdk8JavHome() throws Exception {
    ClassFileResourceProvider provider =
        JdkClassFileProvider.fromJdkHome(ToolHelper.getJavaHome(TestRuntime.CfVm.JDK8));
    assertJavaLangObject(provider);
    assert provider instanceof AutoCloseable;
    ((AutoCloseable) provider).close();
  }

  @Test
  public void testJdk8RuntimeClassPath() throws Exception {
    ClassFileResourceProvider provider =
        JdkClassFileProvider.fromJavaRuntimeJar(
            ToolHelper.getJavaHome(TestRuntime.CfVm.JDK8)
                .resolve("jre")
                .resolve("lib")
                .resolve("rt.jar"));
    assertJavaLangObject(provider);
    assert provider instanceof AutoCloseable;
    ((AutoCloseable) provider).close();
  }

  @Test
  public void testJdk8SystemModules() throws Exception {
    try {
      JdkClassFileProvider.fromSystemModulesJdk(ToolHelper.getJavaHome(TestRuntime.CfVm.JDK8));
      fail("Not supposed to succeed");
    } catch (NoSuchFileException e) {
      assertThat(e.toString(), containsString("lib/jrt-fs.jar"));
    }
  }

  @Test
  public void testJdk9JavaHome() throws Exception {
    ClassFileResourceProvider provider =
        JdkClassFileProvider.fromJdkHome(ToolHelper.getJavaHome(TestRuntime.CfVm.JDK9));
    assertJavaLangObject(provider);
    assertJavaUtilConcurrentFlowSubscriber(provider);
    assert provider instanceof AutoCloseable;
    ((AutoCloseable) provider).close();
  }

  @Test
  public void testJdk9SystemModules() throws Exception {
    ClassFileResourceProvider provider =
        JdkClassFileProvider.fromSystemModulesJdk(ToolHelper.getJavaHome(TestRuntime.CfVm.JDK9));
    assertJavaLangObject(provider);
    assertJavaUtilConcurrentFlowSubscriber(provider);
    assert provider instanceof AutoCloseable;
    ((AutoCloseable) provider).close();
  }

  @Test
  public void testJdk11JavaHome() throws Exception {
    ClassFileResourceProvider provider =
        JdkClassFileProvider.fromJdkHome(ToolHelper.getJavaHome(TestRuntime.CfVm.JDK11));
    assertJavaLangObject(provider);
    assertJavaUtilConcurrentFlowSubscriber(provider);
    assert provider instanceof AutoCloseable;
    ((AutoCloseable) provider).close();
  }

  @Test
  public void testJdk11SystemModules() throws Exception {
    ClassFileResourceProvider provider =
        JdkClassFileProvider.fromSystemModulesJdk(ToolHelper.getJavaHome(TestRuntime.CfVm.JDK11));
    assertJavaLangObject(provider);
    assertJavaUtilConcurrentFlowSubscriber(provider);
    assert provider instanceof AutoCloseable;
    ((AutoCloseable) provider).close();
  }

  private void assertJavaLangObject(ClassFileResourceProvider provider) throws Exception {
    assertTrue(provider.getClassDescriptors().contains("Ljava/lang/Object;"));
    assertTrue(
        ByteStreams.toByteArray(provider.getProgramResource("Ljava/lang/Object;").getByteStream())
                .length
            > 0);
  }

  private void assertJavaUtilConcurrentFlowSubscriber(ClassFileResourceProvider provider)
      throws Exception {
    assertTrue(provider.getClassDescriptors().contains("Ljava/util/concurrent/Flow$Subscriber;"));
    assertTrue(
        ByteStreams.toByteArray(
                    provider
                        .getProgramResource("Ljava/util/concurrent/Flow$Subscriber;")
                        .getByteStream())
                .length
            > 0);
  }
}
