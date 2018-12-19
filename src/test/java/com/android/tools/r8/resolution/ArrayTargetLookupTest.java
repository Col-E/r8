// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class ArrayTargetLookupTest extends TestBase {

  public static class Foo {}

  @Test
  public void testArrays() throws IOException, ExecutionException {
    Timing timing = new Timing(ArrayTargetLookupTest.class.getCanonicalName());
    InternalOptions options = new InternalOptions();
    AndroidApp app =
        AndroidApp.builder()
            .addLibraryFile(ToolHelper.getDefaultAndroidJar())
            .addProgramFiles(ToolHelper.getClassFileForTestClass(Foo.class))
            .build();
    DexApplication application = new ApplicationReader(app, options, timing).read().toDirect();
    AppInfoWithSubtyping appInfo = new AppInfoWithSubtyping(application);
    DexItemFactory factory = options.itemFactory;
    DexType fooType =
        factory.createType(DescriptorUtils.javaTypeToDescriptor(Foo.class.getTypeName()));
    DexType[] arrayTypes =
        new DexType[] {
          factory.createType("[I"),
          factory.stringArrayType,
          factory.objectArrayType,
          factory.createArrayType(2, fooType)
        };
    DexEncodedMethod langObjectNotifyMethod =
        appInfo.lookupVirtualTarget(
            fooType,
            factory.createMethod(fooType, factory.createProto(factory.voidType), "notify"));
    for (DexType arrType : arrayTypes) {
      assertNull(
          appInfo.lookupVirtualTarget(
              arrType, factory.createMethod(arrType, factory.createProto(arrType), "clone")));
      DexEncodedMethod target =
          appInfo.lookupVirtualTarget(
              arrType,
              factory.createMethod(arrType, factory.createProto(factory.voidType), "notify"));
      assertEquals(langObjectNotifyMethod, target);
    }
  }
}
