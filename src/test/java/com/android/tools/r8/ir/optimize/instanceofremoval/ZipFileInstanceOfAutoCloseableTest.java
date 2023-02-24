// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.instanceofremoval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.InterfaceCollection;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ZipFileInstanceOfAutoCloseableTest extends TestBase {

  static final String EXPECTED_PRE_API_19 = StringUtils.lines("Not an AutoCloseable");
  static final String EXPECTED_POST_API_19 = StringUtils.lines("Is an AutoCloseable");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ZipFileInstanceOfAutoCloseableTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean runtimeZipFileIsCloseable() {
    return parameters.isCfRuntime()
        || parameters
            .getRuntime()
            .asDex()
            .maxSupportedApiLevel()
            .isGreaterThanOrEqualTo(AndroidApiLevel.K);
  }

  private String expectedOutput() {
    return runtimeZipFileIsCloseable() ? EXPECTED_POST_API_19 : EXPECTED_PRE_API_19;
  }

  private Path getAndroidJar() {
    // Always use an android jar later than API 19. Thus at compile-time ZipFile < Closeable.
    return ToolHelper.getAndroidJar(AndroidApiLevel.LATEST);
  }

  private String getZipFile() throws IOException {
    return ZipBuilder.builder(temp.newFile("file.zip").toPath())
        .addBytes("entry", new byte[1])
        .build()
        .toString();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClasses(ZipFileInstanceOfAutoCloseableTest.class)
        .setMinApi(parameters)
        .addLibraryFiles(getAndroidJar())
        .run(parameters.getRuntime(), TestClass.class, getZipFile())
        .assertSuccessWithOutput(expectedOutput());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ZipFileInstanceOfAutoCloseableTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .addLibraryFiles(getAndroidJar())
        .run(parameters.getRuntime(), TestClass.class, getZipFile())
        .assertSuccessWithOutput(expectedOutput());
  }

  @Test
  public void testTypeStructure() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    // Set the min API and create the raw app.
    InternalOptions options = new InternalOptions();
    options.setMinApiLevel(parameters.getApiLevel());
    DirectMappedDexApplication application =
        new ApplicationReader(
                AndroidApp.builder()
                    .addProgramFiles(ToolHelper.getClassFileForTestClass(MyJarFileScanner.class))
                    .addLibraryFiles(getAndroidJar())
                    .build(),
                options,
                Timing.empty())
            .read()
            .toDirect();
    AppView<AppInfoWithClassHierarchy> appView = AppView.createForR8(application);

    // Type references.
    DexType zipFileType = appView.dexItemFactory().createType("Ljava/util/zip/ZipFile;");
    DexType closeableType = appView.dexItemFactory().createType("Ljava/io/Closeable;");
    DexType autoCloseableType = appView.dexItemFactory().createType("Ljava/lang/AutoCloseable;");
    DexType scannerType = appView.dexItemFactory().createType("Ljava/util/Scanner;");
    DexType myJarFileChannel = buildType(MyJarFileScanner.class, appView.dexItemFactory());

    // Read the computed interface types. Both the "type element" which is used in IR and the full
    // computed dependencies.
    ClassTypeElement zipFileTypeElement = makeTypeElement(appView, zipFileType);
    {
      InterfaceCollection directInterfaces = zipFileTypeElement.getInterfaces();
      InterfaceCollection allInterfaces = appView.appInfo().implementedInterfaces(zipFileType);
      if (zipFileHasCloseable()) {
        // After API 19 / K the types are known and present.
        assertEquals(closeableType, directInterfaces.getSingleKnownInterface());
        assertTrue(allInterfaces.containsKnownInterface(closeableType));
        assertTrue(allInterfaces.containsKnownInterface(autoCloseableType));
        assertEquals(2, allInterfaces.size());
      } else {
        // The interfaces are still present due to the android jar for K, but they are marked
        // unknown.
        assertTrue(directInterfaces.contains(closeableType).isUnknown());
        assertFalse(directInterfaces.containsKnownInterface(closeableType));
        assertEquals(1, directInterfaces.size());
        // Same for the collection of all interfaces. Since the
        assertTrue(allInterfaces.contains(closeableType).isUnknown());
        assertTrue(allInterfaces.contains(autoCloseableType).isUnknown());
        assertFalse(allInterfaces.containsKnownInterface(closeableType));
        assertFalse(allInterfaces.containsKnownInterface(autoCloseableType));
        assertEquals(2, allInterfaces.size());
      }
    }

    ClassTypeElement scannerTypeElement = makeTypeElement(appView, scannerType);
    {
      // Scanner implements Closable and Iterator on all APIs.
      InterfaceCollection directInterfaces = scannerTypeElement.getInterfaces();
      assertTrue(directInterfaces.containsKnownInterface(closeableType));
      assertEquals(2, directInterfaces.size());

      // Joining a type of known with a type of unknown should still be unknown.
      ClassTypeElement joinLeft1 =
          scannerTypeElement.join(zipFileTypeElement, appView).asClassType();
      ClassTypeElement joinRight1 =
          zipFileTypeElement.join(scannerTypeElement, appView).asClassType();
      if (zipFileHasCloseable()) {
        assertTrue(joinLeft1.getInterfaces().contains(closeableType).isTrue());
        assertTrue(joinRight1.getInterfaces().contains(closeableType).isTrue());
      } else {
        assertTrue(joinLeft1.getInterfaces().contains(closeableType).isUnknown());
        assertTrue(joinRight1.getInterfaces().contains(closeableType).isUnknown());
      }
    }

    // Custom class derived from JarFile and Channel, thus it must implement closable.
    ClassTypeElement myJarFileScannerTypeElement = makeTypeElement(appView, myJarFileChannel);

    // Joining with Scanner will retain it as always Closeable.
    ClassTypeElement joinWithScanner =
        myJarFileScannerTypeElement.join(scannerTypeElement, appView).asClassType();
    assertTrue(joinWithScanner.getInterfaces().contains(closeableType).isTrue());

    // Joining with ZipFile will loose the assurance that it is Closeable.
    ClassTypeElement joinWithZipFile =
        myJarFileScannerTypeElement.join(zipFileTypeElement, appView).asClassType();
    assertTrue(joinWithZipFile.getInterfaces().contains(closeableType).isPossiblyTrue());
    assertEquals(
        zipFileHasCloseable(), joinWithZipFile.getInterfaces().contains(closeableType).isTrue());
  }

  private boolean zipFileHasCloseable() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K);
  }

  private static ClassTypeElement makeTypeElement(
      AppView<AppInfoWithClassHierarchy> appView, DexType type) {
    return ClassTypeElement.create(type, Nullability.maybeNull(), appView);
  }

  static class MyJarFileScanner extends JarFile implements Channel {

    public MyJarFileScanner(String name) throws IOException {
      super(name);
    }

    @Override
    public boolean isOpen() {
      return false;
    }
  }

  static class TestClass {

    public static void foo(Object o) throws Exception {
      if (o instanceof AutoCloseable) {
        System.out.println("Is an AutoCloseable");
        ((AutoCloseable) o).close();
      } else {
        System.out.println("Not an AutoCloseable");
      }
    }

    public static void main(String[] args) throws Exception {
      foo(new JarFile(args[0]));
    }
  }
}
