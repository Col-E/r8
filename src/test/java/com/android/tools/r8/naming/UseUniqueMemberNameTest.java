// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Timing;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UseUniqueMemberNameTest extends NamingTestBase {

  public UseUniqueMemberNameTest(
      String test,
      List<String> keepRulesFiles,
      BiConsumer<DexItemFactory, NamingLens> inspection) {
    super(test, keepRulesFiles, inspection, new Timing("UseUniqueMemberNameTest"));
  }

  @Test
  public void minifierTest() throws Exception {
    NamingLens naming = runMinifier(ListUtils.map(keepRulesFiles, Paths::get));
    inspection.accept(dexItemFactory, naming);
  }

  @Parameters(name = "test: {0} keep: {1}")
  public static Collection<Object[]> data() {
    List<String> tests = Arrays.asList("uniquemembernames");

    Map<String, BiConsumer<DexItemFactory, NamingLens>> inspections = new HashMap<>();
    inspections.put("uniquemembernames:keep-rules-1.txt", UseUniqueMemberNameTest::test00_rule1);
    inspections.put("uniquemembernames:keep-rules-2.txt", UseUniqueMemberNameTest::test00_rule2);

    return createTests(tests, inspections);
  }

  private static void test00_rule1(DexItemFactory dexItemFactory, NamingLens naming) {
    DexType a = dexItemFactory.createType("Luniquemembernames/ClsA;");
    assertNotEquals("Luniquemembernames/ClsA;", naming.lookupDescriptor(a).toSourceString());

    DexMethod foo = dexItemFactory.createMethod(
        a, dexItemFactory.createProto(dexItemFactory.intType), "foo");
    String foo_renamed = naming.lookupName(foo).toSourceString();
    assertNotEquals("foo", foo_renamed);

    DexType aa = dexItemFactory.createType("Luniquemembernames/AnotherCls;");
    assertNotEquals("Luniquemembernames/AnotherCls;", naming.lookupDescriptor(aa).toSourceString());

    DexMethod another_foo = dexItemFactory.createMethod(
        aa, dexItemFactory.createProto(dexItemFactory.intType), "foo");
    String another_foo_renamed = naming.lookupName(another_foo).toSourceString();
    assertNotEquals("foo", another_foo_renamed);

    // BaseCls#a and AnotherCls#b are kept.
    // Due to BaseCls#a, BaseCls#foo should not be renamed to a.
    // On the other hand, AnotherCls#foo should be renamed to a.
    assertNotEquals(foo_renamed, another_foo_renamed);

    DexType base = dexItemFactory.createType("Luniquemembernames/BaseCls;");
    DexField f2 = dexItemFactory.createField(base, dexItemFactory.doubleType, "f2");
    DexField another_f2 = dexItemFactory.createField(aa, dexItemFactory.intType, "f2");
    // Fields f2's are only fields that are allowed to be renamed. Thus, they would be renamed to
    // the same name as long as R8 is deterministic.
    assertEquals(
        naming.lookupName(f2).toSourceString(),
        naming.lookupName(another_f2).toSourceString());
  }

  // -useuniqueclassmembernames
  private static void test00_rule2(DexItemFactory dexItemFactory, NamingLens naming) {
    DexType a = dexItemFactory.createType("Luniquemembernames/ClsA;");
    assertNotEquals("Luniquemembernames/ClsA;", naming.lookupDescriptor(a).toSourceString());

    DexMethod foo = dexItemFactory.createMethod(
        a, dexItemFactory.createProto(dexItemFactory.intType), "foo");
    String foo_renamed = naming.lookupName(foo).toSourceString();
    assertNotEquals("foo", foo_renamed);

    DexType aa = dexItemFactory.createType("Luniquemembernames/AnotherCls;");
    assertNotEquals("Luniquemembernames/AnotherCls;", naming.lookupDescriptor(aa).toSourceString());

    DexMethod another_foo = dexItemFactory.createMethod(
        aa, dexItemFactory.createProto(dexItemFactory.intType), "foo");
    String another_foo_renamed = naming.lookupName(another_foo).toSourceString();
    assertNotEquals("foo", another_foo_renamed);

    // With -useuniquemembernames, foo() with the same signature should be renamed to the same name.
    assertEquals(foo_renamed, another_foo_renamed);
    // But, those cannot be renamed to a and b, as those are _globally_ reserved.
    assertNotEquals("a", foo_renamed);
    assertNotEquals("a", another_foo_renamed);
    assertNotEquals("b", foo_renamed);
    assertNotEquals("b", another_foo_renamed);

    DexType base = dexItemFactory.createType("Luniquemembernames/BaseCls;");
    DexField f2 = dexItemFactory.createField(base, dexItemFactory.doubleType, "f2");
    DexField another_f2 = dexItemFactory.createField(aa, dexItemFactory.intType, "f2");
    // They should be renamed differently even w/ -useuniqueclassmembernames.
    assertNotEquals(
        naming.lookupName(f2).toSourceString(),
        naming.lookupName(another_f2).toSourceString());
  }

}
