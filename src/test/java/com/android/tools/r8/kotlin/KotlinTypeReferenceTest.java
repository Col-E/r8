// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinTypeReferenceTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KotlinTypeReferenceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRef() {
    // This is a unit test for b/299878903.
    DexItemFactory factory = new DexItemFactory();
    KotlinTypeReference bar =
        KotlinTypeReference.fromBinaryNameOrKotlinClassifier(
            "com/bar/Foo.Bar", factory, "com.bar.Foo.Bar");
    Assert.assertEquals(bar.toString(), "Lcom/bar/Foo$Bar;");
    KotlinTypeReference kotlinTypeReference =
        KotlinTypeReference.fromBinaryNameOrKotlinClassifier(
            "com/bar/Foo$Bar", factory, "com.bar.Foo.Bar");
    Assert.assertEquals(kotlinTypeReference.toString(), "Lcom/bar/Foo$Bar;");
  }
}
