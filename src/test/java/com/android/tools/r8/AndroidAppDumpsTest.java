// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DataResourceProvider.Visitor;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Reporter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AndroidAppDumpsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public AndroidAppDumpsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Reporter reporter = new Reporter();

    String dataResourceName = "my-resource.bin";
    byte[] dataResourceData = new byte[] {1, 2, 3};

    Path dexForB =
        testForD8().addProgramClasses(B.class).setMinApi(AndroidApiLevel.B).compile().writeToZip();

    AndroidApp appIn =
        AndroidApp.builder(reporter)
            .addClassProgramData(ToolHelper.getClassAsBytes(A.class), origin("A"))
            .addClassProgramData(ToolHelper.getClassAsBytes(A.class), origin("A"))
            .addClassProgramData(ToolHelper.getClassAsBytes(A.class), origin("A"))
            .addProgramFile(dexForB)
            .addClasspathResourceProvider(provider(B.class))
            .addClasspathResourceProvider(provider(B.class))
            .addClasspathResourceProvider(provider(B.class))
            .addLibraryResourceProvider(provider(C.class))
            .addLibraryResourceProvider(provider(C.class))
            .addLibraryResourceProvider(provider(C.class))
            .addProgramResourceProvider(
                createDataResourceProvider(
                    dataResourceName, dataResourceData, origin(dataResourceName)))
            .build();

    Path dumpFile = temp.newFolder().toPath().resolve("dump.zip");
    appIn.dump(dumpFile, null, reporter);

    AndroidApp appOut = AndroidApp.builder(reporter).addDump(dumpFile).build();
    assertEquals(1, appOut.getClassProgramResourcesForTesting().size());
    assertEquals(
        DescriptorUtils.javaTypeToDescriptor(A.class.getTypeName()),
        appOut.getClassProgramResourcesForTesting().get(0).getClassDescriptors().iterator().next());

    assertEquals(1, appOut.getDexProgramResourcesForTesting().size());

    assertEquals(1, appOut.getClasspathResourceProviders().size());
    assertEquals(
        DescriptorUtils.javaTypeToDescriptor(B.class.getTypeName()),
        appOut.getClasspathResourceProviders().get(0).getClassDescriptors().iterator().next());

    assertEquals(1, appOut.getLibraryResourceProviders().size());
    assertEquals(
        DescriptorUtils.javaTypeToDescriptor(C.class.getTypeName()),
        appOut.getLibraryResourceProviders().get(0).getClassDescriptors().iterator().next());

    Box<Boolean> foundData = new Box<>(false);
    for (ProgramResourceProvider provider : appOut.getProgramResourceProviders()) {
      DataResourceProvider dataProvider = provider.getDataResourceProvider();
      if (dataProvider != null) {
        dataProvider.accept(
            new Visitor() {
              @Override
              public void visit(DataDirectoryResource directory) {}

              @Override
              public void visit(DataEntryResource file) {
                if (file.getName().equals(dataResourceName)) {
                  foundData.set(true);
                }
              }
            });
      }
    }
    assertTrue(foundData.get());
  }

  private ProgramResourceProvider createDataResourceProvider(
      String name, byte[] content, Origin origin) {
    return new ProgramResourceProvider() {
      @Override
      public Collection<ProgramResource> getProgramResources() throws ResourceException {
        return Collections.emptyList();
      }

      @Override
      public DataResourceProvider getDataResourceProvider() {
        return new DataResourceProvider() {
          @Override
          public void accept(Visitor visitor) throws ResourceException {
            visitor.visit(
                new DataEntryResource() {
                  @Override
                  public InputStream getByteStream() throws ResourceException {
                    return new ByteArrayInputStream(content);
                  }

                  @Override
                  public String getName() {
                    return name;
                  }

                  @Override
                  public Origin getOrigin() {
                    return origin;
                  }
                });
          }
        };
      }
    };
  }

  private Origin origin(String name) {
    return new Origin(Origin.root()) {
      @Override
      public String part() {
        return name;
      }
    };
  }

  private ClassFileResourceProvider provider(Class<?> clazz) {
    return new ClassFileResourceProvider() {
      @Override
      public Set<String> getClassDescriptors() {
        return Collections.singleton(DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName()));
      }

      @Override
      public ProgramResource getProgramResource(String descriptor) {
        try {
          return ProgramResource.fromBytes(
              origin(clazz.getTypeName()),
              Kind.CF,
              ToolHelper.getClassAsBytes(clazz),
              getClassDescriptors());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  public static class A {}

  public static class B {}

  public static class C {}
}
