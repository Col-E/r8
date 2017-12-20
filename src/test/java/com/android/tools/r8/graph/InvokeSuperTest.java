// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.graph.invokesuper.InvokerClassDump;
import com.android.tools.r8.graph.invokesuper.InvokerClassFailingDump;
import com.android.tools.r8.graph.invokesuper.MainClass;
import com.android.tools.r8.graph.invokesuper.MainClassFailing;
import com.android.tools.r8.graph.invokesuper.SubLevel1;
import com.android.tools.r8.graph.invokesuper.SubLevel2;
import com.android.tools.r8.graph.invokesuper.SubclassOfInvokerClass;
import com.android.tools.r8.graph.invokesuper.Super;
import org.junit.Ignore;
import org.junit.Test;

public class InvokeSuperTest extends AsmTestBase {

  @Test
  public void testInvokeSuperTargets() throws Exception {
    ensureSameOutput(MainClass.class.getCanonicalName(),
        asBytes(MainClass.class),
        asBytes(Super.class),
        asBytes(SubLevel1.class),
        asBytes(SubLevel2.class),
        InvokerClassDump.dump(),
        asBytes(SubclassOfInvokerClass.class));
  }

  @Test
  @Ignore("b/70707023")
  public void testInvokeSuperTargetsNonVerifying() throws Exception {
    ensureSameOutputD8R8(MainClassFailing.class.getCanonicalName(),
        asBytes(MainClassFailing.class),
        asBytes(Super.class),
        asBytes(SubLevel1.class),
        asBytes(SubLevel2.class),
        InvokerClassFailingDump.dump(),
        asBytes(SubclassOfInvokerClass.class));
  }
}
