// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import org.junit.Test;

public class ArrayTargetLookupTest extends TestBase {

  public static class Foo {}

  @Test
  public void testArrays() throws Exception {
    Timing timing = Timing.empty();
    InternalOptions options = new InternalOptions();
    AndroidApp app =
        AndroidApp.builder()
            .addLibraryFile(ToolHelper.getDefaultAndroidJar())
            .addProgramFiles(ToolHelper.getClassFileForTestClass(Foo.class))
            .build();
    DirectMappedDexApplication application =
        new ApplicationReader(app, options, timing).read().toDirect();
    AppInfoWithClassHierarchy appInfo = AppView.createForR8(application).appInfo();
    DexItemFactory factory = options.itemFactory;
    DexType fooType =
        factory.createType(DescriptorUtils.javaTypeToDescriptor(Foo.class.getTypeName()));
    DexType[] arrayTypes =
        new DexType[] {
          factory.intArrayType,
          factory.stringArrayType,
          factory.objectArrayType,
          factory.createArrayType(2, fooType)
        };
    DexEncodedMethod langObjectNotifyMethod =
        appInfo
            .resolveMethodOnClass(
                factory.createMethod(fooType, factory.createProto(factory.voidType), "notify"))
            .getSingleTarget();
    for (DexType arrType : arrayTypes) {
      assertNull(
          appInfo
              .resolveMethodOnClass(
                  factory.createMethod(arrType, factory.createProto(arrType), "clone"))
              .getSingleTarget());
      DexEncodedMethod target =
          appInfo
              .resolveMethodOnClass(
                  factory.createMethod(arrType, factory.createProto(factory.voidType), "notify"))
              .getSingleTarget();
      assertEquals(langObjectNotifyMethod, target);
    }
  }
}
