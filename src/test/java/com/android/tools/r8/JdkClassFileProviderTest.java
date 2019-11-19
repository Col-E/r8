package com.android.tools.r8;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class JdkClassFileProviderTest extends TestBase implements Opcodes {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  final TestParameters parameters;

  public JdkClassFileProviderTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private CfRuntime getRuntime() {
    return parameters.getRuntime().asCf();
  }

  @Test
  public void testInvalidRuntimeClassPath() throws Exception {
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
  public void testJdkJavaHome() throws Exception {
    ClassFileResourceProvider provider =
        JdkClassFileProvider.fromJdkHome(getRuntime().getJavaHome());
    assertJavaLangObject(provider);
    assert provider instanceof AutoCloseable;
    if (getRuntime().isNewerThanOrEqual(CfVm.JDK9)) {
      assertJavaUtilConcurrentFlowSubscriber(provider);
    }
    ((AutoCloseable) provider).close();
  }

  @Test
  public void testJdk8RuntimeClassPath() throws Exception {
    assumeTrue(getRuntime().getVm() == CfVm.JDK8);
    ClassFileResourceProvider provider =
        JdkClassFileProvider.fromJavaRuntimeJar(
            getRuntime().getJavaHome().resolve("jre").resolve("lib").resolve("rt.jar"));
    assertJavaLangObject(provider);
    assert provider instanceof AutoCloseable;
    ((AutoCloseable) provider).close();
  }

  @Test
  public void testJdk8SystemModules() throws Exception {
    assumeTrue(getRuntime().getVm() == CfVm.JDK8);
    try {
      JdkClassFileProvider.fromSystemModulesJdk(getRuntime().getJavaHome());
      fail("Not supposed to succeed");
    } catch (NoSuchFileException e) {
      assertThat(e.toString(), containsString("lib/jrt-fs.jar"));
    }
  }

  @Test
  public void testJdk9PlusSystemModules() throws Exception {
    assumeTrue(getRuntime().isNewerThanOrEqual(CfVm.JDK9));
    ClassFileResourceProvider provider =
        JdkClassFileProvider.fromSystemModulesJdk(getRuntime().getJavaHome());
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
