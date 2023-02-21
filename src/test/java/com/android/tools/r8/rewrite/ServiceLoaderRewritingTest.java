// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ServiceLoaderRewritingTest extends TestBase {

  private final TestParameters parameters;
  private final String EXPECTED_OUTPUT =
      StringUtils.lines("Hello World!", "Hello World!", "Hello World!");

  public interface Service {

    void print();
  }

  public static class ServiceImpl implements Service {

    @Override
    public void print() {
      System.out.println("Hello World!");
    }
  }

  public static class ServiceImpl2 implements Service {

    @Override
    public void print() {
      System.out.println("Hello World 2!");
    }
  }

  public static class MainRunner {

    public static void main(String[] args) {
      ServiceLoader.load(Service.class, Service.class.getClassLoader()).iterator().next().print();
      ServiceLoader.load(Service.class, null).iterator().next().print();
      for (Service x : ServiceLoader.load(Service.class, Service.class.getClassLoader())) {
        x.print();
      }
      // TODO(b/120436373) The stream API for ServiceLoader was added in Java 9. When we can model
      //   streams correctly, uncomment the lines below and adjust EXPECTED_OUTPUT.
      // ServiceLoader.load(Service.class, Service.class.getClassLoader())
      //   .stream().forEach(x -> x.get().print());
    }
  }

  public static class MainWithTryCatchRunner {

    public static void main(String[] args) {
      try {
        ServiceLoader.load(Service.class, Service.class.getClassLoader()).iterator().next().print();
      } catch (Throwable e) {
        System.out.println(e);
        throw e;
      }
    }
  }

  public static class OtherRunner {

    public static void main(String[] args) {
      ServiceLoader.load(Service.class).iterator().next().print();
      ServiceLoader.load(Service.class, Thread.currentThread().getContextClassLoader())
          .iterator()
          .next()
          .print();
      ServiceLoader.load(Service.class, OtherRunner.class.getClassLoader())
          .iterator()
          .next()
          .print();
    }
  }

  public static class EscapingRunner {

    public ServiceLoader<Service> serviceImplementations;

    @NeverInline
    public ServiceLoader<Service> getServices() {
      return ServiceLoader.load(Service.class, Thread.currentThread().getContextClassLoader());
    }

    @NeverInline
    public void printServices() {
      print(ServiceLoader.load(Service.class, Thread.currentThread().getContextClassLoader()));
    }

    @NeverInline
    public void print(ServiceLoader<Service> loader) {
      loader.iterator().next().print();
    }

    @NeverInline
    public void assignServicesField() {
      serviceImplementations =
          ServiceLoader.load(Service.class, Thread.currentThread().getContextClassLoader());
    }

    public static void main(String[] args) {
      EscapingRunner escapingRunner = new EscapingRunner();
      escapingRunner.getServices().iterator().next().print();
      escapingRunner.printServices();
      escapingRunner.assignServicesField();
      escapingRunner.print(escapingRunner.serviceImplementations);
    }
  }

  public static class LoadWhereClassLoaderIsPhi {

    public static void main(String[] args) {
      ServiceLoader.load(
              Service.class,
              System.currentTimeMillis() > 0
                  ? Thread.currentThread().getContextClassLoader()
                  : null)
          .iterator()
          .next()
          .print();
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ServiceLoaderRewritingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRewritings() throws IOException, CompilationFailedException, ExecutionException {
    Path path = temp.newFile("out.zip").toPath();
    testForR8(parameters.getBackend())
        .addInnerClasses(ServiceLoaderRewritingTest.class)
        .addKeepMainRule(MainRunner.class)
        .setMinApi(parameters)
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.lines(ServiceImpl.class.getTypeName()).getBytes(),
                "META-INF/services/" + Service.class.getTypeName(),
                Origin.unknown()))
        .compile()
        .writeToZip(path)
        .run(parameters.getRuntime(), MainRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              // Check that we have actually rewritten the calls to ServiceLoader.load.
              assertEquals(0, getServiceLoaderLoads(inspector, MainRunner.class));
            });

    // Check that we have removed the service configuration from META-INF/services.
    ZipFile zip = new ZipFile(path.toFile());
    assertNull(zip.getEntry("META-INF/services/" + Service.class.getTypeName()));
  }

  @Test
  public void testRewritingWithMultiple()
      throws IOException, CompilationFailedException, ExecutionException {
    Path path = temp.newFile("out.zip").toPath();
    testForR8(parameters.getBackend())
        .addInnerClasses(ServiceLoaderRewritingTest.class)
        .addKeepMainRule(MainRunner.class)
        .setMinApi(parameters)
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.lines(ServiceImpl.class.getTypeName(), ServiceImpl2.class.getTypeName())
                    .getBytes(),
                "META-INF/services/" + Service.class.getTypeName(),
                Origin.unknown()))
        .compile()
        .writeToZip(path)
        .run(parameters.getRuntime(), MainRunner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT + StringUtils.lines("Hello World 2!"))
        .inspect(
            inspector -> {
              // Check that we have actually rewritten the calls to ServiceLoader.load.
              assertEquals(0, getServiceLoaderLoads(inspector, MainRunner.class));
            });

    // Check that we have removed the service configuration from META-INF/services.
    ZipFile zip = new ZipFile(path.toFile());
    assertNull(zip.getEntry("META-INF/services/" + Service.class.getTypeName()));
  }

  @Test
  public void testRewritingsWithCatchHandlers()
      throws IOException, CompilationFailedException, ExecutionException {
    Path path = temp.newFile("out.zip").toPath();
    testForR8(parameters.getBackend())
        .addInnerClasses(ServiceLoaderRewritingTest.class)
        .addKeepMainRule(MainWithTryCatchRunner.class)
        .setMinApi(parameters)
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.lines(ServiceImpl.class.getTypeName(), ServiceImpl2.class.getTypeName())
                    .getBytes(),
                "META-INF/services/" + Service.class.getTypeName(),
                Origin.unknown()))
        .compile()
        .writeToZip(path)
        .run(parameters.getRuntime(), MainWithTryCatchRunner.class)
        .assertSuccessWithOutput(StringUtils.lines("Hello World!"))
        .inspect(
            inspector -> {
              // Check that we have actually rewritten the calls to ServiceLoader.load.
              assertEquals(0, getServiceLoaderLoads(inspector, MainWithTryCatchRunner.class));
            });

    // Check that we have removed the service configuration from META-INF/services.
    ZipFile zip = new ZipFile(path.toFile());
    assertNull(zip.getEntry("META-INF/services/" + Service.class.getTypeName()));
  }

  @Test
  public void testDoNoRewrite() throws IOException, CompilationFailedException, ExecutionException {
    Path path = temp.newFile("out.zip").toPath();
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(ServiceLoaderRewritingTest.class)
            .addKeepMainRule(OtherRunner.class)
            .setMinApi(parameters)
            .addDataEntryResources(
                DataEntryResource.fromBytes(
                    StringUtils.lines(ServiceImpl.class.getTypeName()).getBytes(),
                    "META-INF/services/" + Service.class.getTypeName(),
                    Origin.unknown()))
            .compile()
            .writeToZip(path)
            .run(parameters.getRuntime(), OtherRunner.class)
            .assertSuccessWithOutput(EXPECTED_OUTPUT)
            .inspector();

    // Check that we have not rewritten the calls to ServiceLoader.load.
    assertEquals(3, getServiceLoaderLoads(inspector, OtherRunner.class));

    // Check that we have not removed the service configuration from META-INF/services.
    ZipFile zip = new ZipFile(path.toFile());
    ClassSubject serviceImpl = inspector.clazz(ServiceImpl.class);
    assertTrue(serviceImpl.isPresent());
    assertNotNull(zip.getEntry("META-INF/services/" + serviceImpl.getFinalName()));
  }

  @Test
  public void testDoNoRewriteWhenEscaping()
      throws IOException, CompilationFailedException, ExecutionException {
    Path path = temp.newFile("out.zip").toPath();
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(ServiceLoaderRewritingTest.class)
            .addKeepMainRule(EscapingRunner.class)
            .enableInliningAnnotations()
            .setMinApi(parameters)
            .addDontObfuscate()
            .addDataEntryResources(
                DataEntryResource.fromBytes(
                    StringUtils.lines(ServiceImpl.class.getTypeName()).getBytes(),
                    "META-INF/services/" + Service.class.getTypeName(),
                    Origin.unknown()))
            .compile()
            .writeToZip(path)
            .run(parameters.getRuntime(), EscapingRunner.class)
            .assertSuccessWithOutput(EXPECTED_OUTPUT)
            .inspector();

    // Check that we have not rewritten the calls to ServiceLoader.load.
    assertEquals(3, getServiceLoaderLoads(inspector, EscapingRunner.class));

    // Check that we have not removed the service configuration from META-INF/services.
    ZipFile zip = new ZipFile(path.toFile());
    ClassSubject serviceImpl = inspector.clazz(ServiceImpl.class);
    assertTrue(serviceImpl.isPresent());
    assertNotNull(zip.getEntry("META-INF/services/" + serviceImpl.getFinalName()));
  }

  @Test
  public void testDoNoRewriteWhenClassLoaderIsPhi()
      throws IOException, CompilationFailedException, ExecutionException {
    Path path = temp.newFile("out.zip").toPath();
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(ServiceLoaderRewritingTest.class)
            .addKeepMainRule(LoadWhereClassLoaderIsPhi.class)
            .enableInliningAnnotations()
            .setMinApi(parameters)
            .addDataEntryResources(
                DataEntryResource.fromBytes(
                    StringUtils.lines(ServiceImpl.class.getTypeName()).getBytes(),
                    "META-INF/services/" + Service.class.getTypeName(),
                    Origin.unknown()))
            .compile()
            .writeToZip(path)
            .run(parameters.getRuntime(), LoadWhereClassLoaderIsPhi.class)
            .assertSuccessWithOutputLines("Hello World!")
            .inspector();

    // Check that we have not rewritten the calls to ServiceLoader.load.
    assertEquals(1, getServiceLoaderLoads(inspector, LoadWhereClassLoaderIsPhi.class));

    // Check that we have not removed the service configuration from META-INF/services.
    ZipFile zip = new ZipFile(path.toFile());
    ClassSubject serviceImpl = inspector.clazz(ServiceImpl.class);
    assertTrue(serviceImpl.isPresent());
    assertNotNull(zip.getEntry("META-INF/services/" + serviceImpl.getFinalName()));
  }

  @Test
  public void testKeepAsOriginal()
      throws IOException, CompilationFailedException, ExecutionException {
    // The CL that changed behaviour after Nougat is:
    // https://android-review.googlesource.com/c/platform/libcore/+/273135
    assumeTrue(
        parameters.getRuntime().isCf()
            || !parameters.getRuntime().asDex().getVm().getVersion().equals(Version.V7_0_0));
    Path path = temp.newFile("out.zip").toPath();
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(ServiceLoaderRewritingTest.class)
            .addKeepMainRule(MainRunner.class)
            .addKeepClassRules(Service.class)
            .setMinApi(parameters)
            .addDataEntryResources(
                DataEntryResource.fromBytes(
                    StringUtils.lines(ServiceImpl.class.getTypeName()).getBytes(),
                    "META-INF/services/" + Service.class.getTypeName(),
                    Origin.unknown()))
            .compile()
            .writeToZip(path)
            .run(parameters.getRuntime(), MainRunner.class)
            .assertSuccessWithOutput(EXPECTED_OUTPUT)
            .inspector();

    // Check that we have not rewritten the calls to ServiceLoader.load.
    assertEquals(3, getServiceLoaderLoads(inspector, MainRunner.class));

    // Check that we have not removed the service configuration from META-INF/services.
    ZipFile zip = new ZipFile(path.toFile());
    ClassSubject service = inspector.clazz(Service.class);
    assertTrue(service.isPresent());
    assertNotNull(zip.getEntry("META-INF/services/" + service.getFinalName()));
  }

  public static long getServiceLoaderLoads(CodeInspector inspector, Class<?> clazz) {
    ClassSubject classSubject = inspector.clazz(clazz);
    assertTrue(classSubject.isPresent());
    return classSubject.allMethods().stream()
        .mapToLong(
            method ->
                method
                    .streamInstructions()
                    .filter(ServiceLoaderRewritingTest::isServiceLoaderLoad)
                    .count())
        .sum();
  }

  private static boolean isServiceLoaderLoad(InstructionSubject instruction) {
    return instruction.isInvokeStatic()
        && instruction.getMethod().qualifiedName().contains("ServiceLoader.load");
  }
}
