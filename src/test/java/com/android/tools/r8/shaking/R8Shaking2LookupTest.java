// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.SubtypingInfo;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;

public class R8Shaking2LookupTest {

  private DirectMappedDexApplication program;
  private DexItemFactory dexItemFactory;
  private AppInfoWithClassHierarchy appInfo;
  private SubtypingInfo subtypingInfo;

  @Before
  public void readApp() throws IOException, ExecutionException {
    program =
        ToolHelper.buildApplication(
            ImmutableList.of(ToolHelper.EXAMPLES_BUILD_DIR + "shaking2.jar"));
    dexItemFactory = program.dexItemFactory;
    AppView<AppInfoWithClassHierarchy> appView = AppView.createForR8(program);
    appInfo = appView.appInfo();
    subtypingInfo = SubtypingInfo.create(appView);
  }

  private void validateSubtype(DexType super_type, DexType sub_type) {
    assertFalse(super_type.equals(sub_type));
    assertTrue(subtypingInfo.subtypes(super_type).contains(sub_type));
    assertTrue(appInfo.isSubtype(sub_type, super_type));
    assertFalse(subtypingInfo.subtypes(sub_type).contains(super_type));
    assertFalse(appInfo.isSubtype(super_type, sub_type));
  }

  private void validateSubtypeSize(DexType type, int size) {
    assertEquals(size, subtypingInfo.subtypes(type).size());
  }

  @Test
  public void testLookup() {
    DexType object_type = dexItemFactory.createType("Ljava/lang/Object;");

    DexType interface_type = dexItemFactory.createType("Lshaking2/Interface;");
    DexType superInterface1_type = dexItemFactory.createType("Lshaking2/SuperInterface1;");
    DexType superInterface2_type = dexItemFactory.createType("Lshaking2/SuperInterface2;");

    DexType superclass_type = dexItemFactory.createType("Lshaking2/SuperClass;");
    DexType subClass1_type = dexItemFactory.createType("Lshaking2/SubClass1;");
    DexType subClass2_type = dexItemFactory.createType("Lshaking2/SubClass2;");

    validateSubtype(object_type, interface_type);
    validateSubtypeSize(interface_type, 4);
    validateSubtype(interface_type, superclass_type);
    validateSubtype(superInterface1_type, subClass1_type);
    validateSubtype(superInterface2_type, subClass2_type);
    validateSubtype(superclass_type, subClass2_type);
    validateSubtype(superInterface1_type, interface_type);
    validateSubtype(superInterface2_type, interface_type);
    validateSubtypeSize(subClass2_type, 0);
  }
}
