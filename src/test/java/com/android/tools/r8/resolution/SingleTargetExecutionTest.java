// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.VmTestRunner.IgnoreIfVmOlderOrEqualThan;
import com.android.tools.r8.resolution.singletarget.Main;
import com.android.tools.r8.resolution.singletarget.one.AbstractSubClass;
import com.android.tools.r8.resolution.singletarget.one.AbstractTopClass;
import com.android.tools.r8.resolution.singletarget.one.InterfaceWithDefault;
import com.android.tools.r8.resolution.singletarget.one.IrrelevantInterfaceWithDefaultDump;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassOne;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassThree;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class SingleTargetExecutionTest extends AsmTestBase {

  public static List<Class> CLASSES = ImmutableList.of(
      InterfaceWithDefault.class,
      AbstractTopClass.class,
      AbstractSubClass.class,
      SubSubClassOne.class,
      SubSubClassTwo.class,
      SubSubClassThree.class,
      Main.class
  );

  public static List<byte[]> ASM_CLASSES = ImmutableList.of(
      getBytesFromAsmClass(IrrelevantInterfaceWithDefaultDump::dump)
  );

  @Test
  // TODO(b/72208584) The desugared version of this test masks ICCE.
  @IgnoreIfVmOlderOrEqualThan(Version.V7_0_0)
  public void runSingleTargetTest() throws Exception {
    List<byte[]> allBytes = new ArrayList<>();
    allBytes.addAll(ASM_CLASSES);
    for (Class clazz : CLASSES) {
      allBytes.add(ToolHelper.getClassAsBytes(clazz));
    }
    ensureSameOutput(Main.class.getCanonicalName(),
        ToolHelper.getMinApiLevelForDexVm(),
        allBytes.toArray(new byte[allBytes.size()][]));
  }
}
