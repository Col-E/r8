// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.workaround;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeVirtualToInterfaceVerifyErrorWorkaroundTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepClassAndMembersRules(Main.class)
        // CameraDevice is not present in rt.jar or android.jar when API<L.
        .applyIf(
            parameters.isCfRuntime() || parameters.getApiLevel().isLessThan(AndroidApiLevel.L),
            testBuilder -> testBuilder.addDontWarn("android.hardware.camera2.CameraDevice"))
        // CameraDeviceUser can only be merged with A when min API>=L.
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .applyIf(
                        parameters.isDexRuntime()
                            && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L),
                        i -> i.assertIsCompleteMergeGroup(A.class, CameraDeviceUser.class))
                    .assertNoOtherClassesMerged())
        .setMinApi(parameters)
        .compile()
        // CameraDeviceUser.m() can only be inlined when min API>=L.
        .inspect(
            inspector -> {
              if (parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L)) {
                assertThat(inspector.clazz(CameraDeviceUser.class), isAbsent());
              } else {
                ClassSubject cameraDeviceUserClassSubject = inspector.clazz(CameraDeviceUser.class);
                assertThat(cameraDeviceUserClassSubject, isPresent());

                MethodSubject mMethodSubject =
                    cameraDeviceUserClassSubject.uniqueMethodWithOriginalName("m");
                assertThat(mMethodSubject, isPresent());
                assertThat(mMethodSubject, invokesMethodWithName("close"));
              }
            });
  }

  private static List<byte[]> getProgramClassFileData() throws IOException {
    String oldDescriptor = DescriptorUtils.javaTypeToDescriptor(CameraDevice.class.getTypeName());
    String newDescriptor = "Landroid/hardware/camera2/CameraDevice;";
    return ImmutableList.of(
        transformer(Main.class)
            .replaceClassDescriptorInMembers(oldDescriptor, newDescriptor)
            .replaceClassDescriptorInMethodInstructions(oldDescriptor, newDescriptor)
            .transform(),
        transformer(CameraDeviceUser.class)
            .replaceClassDescriptorInMembers(oldDescriptor, newDescriptor)
            .replaceClassDescriptorInMethodInstructions(oldDescriptor, newDescriptor)
            .transform());
  }

  // The following classes are transformed to use android.hardware.camera2.CameraDevice.
  static class Main {

    public static void main(String[] args) {
      int minApi = args.length;
      if (minApi >= 21) {
        new CameraDeviceUser().m(getCameraDevice());
      } else {
        System.out.println(new A());
      }
    }

    // @Keep
    static CameraDevice getCameraDevice() {
      return null;
    }
  }

  static class A {}

  static class CameraDeviceUser {

    public void m(CameraDevice cd) {
      if (cd != null) {
        cd.close();
      }
    }
  }

  abstract static class CameraDevice {

    abstract void close();
  }
}
