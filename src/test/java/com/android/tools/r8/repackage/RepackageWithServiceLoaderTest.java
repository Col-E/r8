// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.DataResourceConsumerForTesting;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageWithServiceLoaderTest extends RepackageTestBase {

  public RepackageWithServiceLoaderTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void test() throws Exception {
    Box<DataResourceConsumerForTesting> dataResourceConsumer = new Box<>();
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addDataEntryResources(
                DataEntryResource.fromBytes(
                    StringUtils.lines(ServiceImpl.class.getTypeName()).getBytes(),
                    "META-INF/services/" + Service.class.getTypeName(),
                    Origin.unknown()))
            .addKeepMainRule(TestClass.class)
            .addOptionsModification(
                options -> {
                  dataResourceConsumer.set(
                      new DataResourceConsumerForTesting(options.dataResourceConsumer));
                  options.dataResourceConsumer = dataResourceConsumer.get();
                })
            .apply(this::configureRepackaging)
            .setMinApi(parameters)
            .compile()
            .inspect(this::inspect)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutputLines("Hello world!");

    CodeInspector inspector = runResult.inspector();
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());
    assertTrue(
        testClassSubject
            .mainMethod()
            .streamInstructions()
            .filter(InstructionSubject::isInvokeStatic)
            .map(InstructionSubject::getMethod)
            .anyMatch(
                method ->
                    method
                        .toSourceString()
                        .equals(
                            "java.util.ServiceLoader"
                                + " java.util.ServiceLoader.load(java.lang.Class)")));

    ClassSubject serviceClassSubject = inspector.clazz(Service.class);
    assertThat(serviceClassSubject, isPresent());

    ClassSubject serviceImplClassSubject = inspector.clazz(ServiceImpl.class);
    assertThat(serviceImplClassSubject, isPresent());

    inspectResource(
        dataResourceConsumer.get().get("META-INF/services/" + serviceClassSubject.getFinalName()),
        serviceImplClassSubject);
  }

  private void inspectResource(List<String> contents, ClassSubject serviceImplClassSubject) {
    assertNotNull(contents);
    assertEquals(1, contents.size());
    assertEquals(serviceImplClassSubject.getFinalName(), contents.get(0));
  }

  private void inspect(CodeInspector inspector) {
    assertThat(Service.class, isRepackaged(inspector));
    assertThat(ServiceImpl.class, isRepackaged(inspector));
  }

  public static class TestClass {

    public static void main(String[] args) {
      for (Service service : ServiceLoader.load(getServiceClass())) {
        service.greet();
      }
    }

    static Class<? extends Service> getServiceClass() {
      return System.currentTimeMillis() > 0 ? Service.class : ServiceImpl.class;
    }
  }

  public interface Service {

    void greet();
  }

  public static class ServiceImpl implements Service {

    @Override
    public void greet() {
      System.out.println("Hello world!");
    }
  }
}
