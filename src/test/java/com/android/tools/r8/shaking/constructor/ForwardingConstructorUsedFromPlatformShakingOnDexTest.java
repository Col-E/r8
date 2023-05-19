// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.constructor;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.apimodel.ApiModelingTestHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ForwardingConstructorUsedFromPlatformShakingOnDexTest extends TestBase {

  private static final String APPLICATION_INFO_DESCRIPTOR = "Landroid/content/pm/ApplicationInfo;";
  private static final String FRAGMENT_DESCRIPTOR = "Landroid/app/Fragment;";
  private static final String ZYGOTE_PRELOAD_DESCRIPTOR = "Landroid/app/ZygotePreload;";

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "Fragment.onCreate()", "MyFragment.onCreate()", "MyZygotePreload.doPreload()");

  private static List<byte[]> transformedProgramClassFileData;
  private static List<byte[]> transformedLibraryClassFileData;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    transformedProgramClassFileData = getTransformedProgramClasses();
    transformedLibraryClassFileData = getTransformedLibraryClasses();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(transformedProgramClassFileData)
        .addProgramClassFileData(transformedLibraryClassFileData)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(transformedProgramClassFileData)
        .addLibraryClassFileData(transformedLibraryClassFileData)
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addKeepMainRule(Main.class)
        // Since Fragment is first defined in API 11.
        .apply(ApiModelingTestHelper::disableStubbingOfClasses)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .addRunClasspathClassFileData(transformedLibraryClassFileData)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private static List<byte[]> getTransformedProgramClasses() throws Exception {
    return ImmutableList.of(
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(Fragment.class), FRAGMENT_DESCRIPTOR)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(ZygotePreload.class), ZYGOTE_PRELOAD_DESCRIPTOR)
            .transform(),
        transformer(MyFragment.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(Fragment.class), FRAGMENT_DESCRIPTOR)
            .setSuper(FRAGMENT_DESCRIPTOR)
            .transform(),
        transformer(MyZygotePreload.class)
            .replaceClassDescriptorInMembers(
                descriptor(ApplicationInfo.class), APPLICATION_INFO_DESCRIPTOR)
            .setImplementsClassDescriptors(ZYGOTE_PRELOAD_DESCRIPTOR)
            .transform());
  }

  private static List<byte[]> getTransformedLibraryClasses() throws Exception {
    return ImmutableList.of(
        transformer(ApplicationInfo.class)
            .setClassDescriptor(APPLICATION_INFO_DESCRIPTOR)
            .transform(),
        transformer(Fragment.class).setClassDescriptor(FRAGMENT_DESCRIPTOR).transform(),
        transformer(Platform.class)
            .replaceClassDescriptorInMembers(descriptor(Fragment.class), FRAGMENT_DESCRIPTOR)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(Fragment.class), FRAGMENT_DESCRIPTOR)
            .replaceClassDescriptorInMembers(
                descriptor(ZygotePreload.class), ZYGOTE_PRELOAD_DESCRIPTOR)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(ApplicationInfo.class), APPLICATION_INFO_DESCRIPTOR)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(ZygotePreload.class), ZYGOTE_PRELOAD_DESCRIPTOR)
            .transform(),
        transformer(ZygotePreload.class)
            .replaceClassDescriptorInMembers(
                descriptor(ApplicationInfo.class), APPLICATION_INFO_DESCRIPTOR)
            .setClassDescriptor(ZYGOTE_PRELOAD_DESCRIPTOR)
            .transform());
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject myFragmentClassSubject = inspector.clazz(MyFragment.class);
    assertThat(myFragmentClassSubject, isPresent());
    assertThat(myFragmentClassSubject.init(), isPresent());

    ClassSubject myZygotePreloadClassSubject = inspector.clazz(MyZygotePreload.class);
    assertThat(myZygotePreloadClassSubject, isPresent());
    assertThat(myZygotePreloadClassSubject.init(), isPresent());
  }

  // Library classes.

  public abstract static class ApplicationInfo {}

  public abstract static class Fragment {

    public void onCreate() {
      System.out.println("Fragment.onCreate()");
    }
  }

  public interface ZygotePreload {

    void doPreload(ApplicationInfo applicationInfo);
  }

  public static class Platform {

    public static void accept(Fragment fragment) throws Exception {
      Fragment newFragment = fragment.getClass().getDeclaredConstructor().newInstance();
      newFragment.onCreate();
    }

    public static void accept(ZygotePreload runnable) throws Exception {
      ZygotePreload newZygotePreload = runnable.getClass().getDeclaredConstructor().newInstance();
      newZygotePreload.doPreload(null);
    }
  }

  // Program classes.

  public static class Main {

    public static void main(String[] args) throws Exception {
      Platform.accept(new MyFragment());
      Platform.accept(new MyZygotePreload());
    }
  }

  @NeverClassInline
  public static class MyFragment extends Fragment {

    public MyFragment() {}

    @Override
    public void onCreate() {
      super.onCreate();
      System.out.println("MyFragment.onCreate()");
    }
  }

  public static class MyZygotePreload implements ZygotePreload {

    public MyZygotePreload() {}

    @Override
    public void doPreload(ApplicationInfo applicationInfo) {
      System.out.println("MyZygotePreload.doPreload()");
    }
  }
}
