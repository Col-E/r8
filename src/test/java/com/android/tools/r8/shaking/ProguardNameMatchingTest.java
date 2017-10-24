// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.ProguardTypeMatcher.ClassOrType;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ProguardNameMatchingTest {
  private static final String[] BASIC_TYPES = {
      "char", "byte", "short", "int", "long", "float", "double", "boolean"
  };

  private static final DexItemFactory dexItemFactory = new DexItemFactory();

  private static boolean matchTypeName(String typeName, String pattern) {
    return ProguardTypeMatcher.create(pattern, ClassOrType.TYPE, dexItemFactory)
        .matches(dexItemFactory.createType(DescriptorUtils.javaTypeToDescriptor(typeName)));
  }

  private static boolean matchClassName(String className, String... patterns) {
    return matchClassName(className, ImmutableList.of(Arrays.asList(patterns)));
  }

  private static boolean matchClassName(String className, List<List<String>> patternsList) {
    ProguardClassFilter.Builder builder = ProguardClassFilter.builder();
    for (List<String> patterns : patternsList) {
      ProguardClassNameList.Builder listBuilder = ProguardClassNameList.builder();
      for (String pattern : patterns) {
        boolean isNegated = pattern.startsWith("!");
        String actualPattern = isNegated ? pattern.substring(1) : pattern;
        listBuilder.addClassName(isNegated,
            ProguardTypeMatcher.create(actualPattern, ClassOrType.CLASS, dexItemFactory));
      }
      builder.addPattern(listBuilder.build());
    }
    return builder.build()
        .matches(dexItemFactory.createType(DescriptorUtils.javaTypeToDescriptor(className)));
  }

  @Test
  public void matchClassNames() {
    assertTrue(matchClassName("", "**"));
    assertTrue(matchClassName("a", "**"));
    assertTrue(matchClassName("", "*"));
    assertTrue(matchClassName("a", "*"));
    assertFalse(matchClassName("", "?"));
    assertTrue(matchClassName("a", "?"));

    assertTrue(matchClassName("java.lang.Object", "**"));
    assertFalse(matchClassName("java.lang.Object", "ja*"));
    assertTrue(matchClassName("java.lang.Object", "ja**"));
    assertTrue(matchClassName("java.lang.Object", "ja**ject"));
    // Oddly, the proguard specification for this makes a lonely * synonymous with **.
    assertTrue(matchClassName("java.lang.Object", "*"));
    assertFalse(matchClassName("java.lang.Object", "ja*ject"));

    assertTrue(matchClassName("java.lang.Object", "java.*g.O*"));
    assertTrue(matchClassName("java.lang.Object", "java.*g.O?je?t"));
    assertFalse(matchClassName("java.lang.Object", "java.*g.O?je?t?"));
    assertFalse(matchClassName("java.lang.Object", "java?lang.Object"));
    assertTrue(matchClassName("java.lang.Object", "*a*.*a**"));
    assertTrue(matchClassName("java.lang.Object", "*a**a**"));

    assertTrue(matchClassName("java.lang.Object", "!java.util.**", "java**"));
    assertFalse(matchClassName("java.lang.Object", "!java.**", "java.lang.*"));
    assertTrue(matchClassName("java.lang.Object", "java.lang.*", "!java.**"));

    assertTrue(matchClassName("boobar", "!foobar", "*bar"));
    assertFalse(matchClassName("foobar", "!foobar", "*bar"));

    assertFalse(matchClassName("foo", "!boo"));
    assertFalse(matchClassName("foo", "baz,!boo"));

    assertFalse(matchClassName("boo", "!boo", "**"));
    assertTrue(matchClassName("boo", "**", "!boo"));
    assertTrue(matchClassName("boo",
        ImmutableList.of(ImmutableList.of("!boo"), ImmutableList.of("**"))));

    assertFalse(matchClassName("boofoo", "!boo*,*foo,boofoo"));
    assertTrue(matchClassName("boofoo",
        ImmutableList.of(ImmutableList.of("!boo*,*foo"), ImmutableList.of("boofoo"))));
  }

  private void assertMatchesBasicTypes(String pattern) {
    for (String type : BASIC_TYPES) {
      assertTrue(matchTypeName(type, pattern));
    }
  }

  private void assertDoesNotMatchBasicTypes(String pattern) {
    for (String type : BASIC_TYPES) {
      assertFalse(matchTypeName(type, pattern));
    }
  }

  @Test
  public void matchTypeNames() {
    assertTrue(matchTypeName("java.lang.Object", "**"));
    assertDoesNotMatchBasicTypes("**");
    assertDoesNotMatchBasicTypes("*");
    assertFalse(
        matchTypeName("java.lang.Object[]", "**"));
    assertFalse(
        matchTypeName("java.lang.Object", "**z"));
    assertFalse(matchTypeName("java.lang.Object[]", "java.**"
    ));
    assertTrue(
        matchTypeName("java.lang.Object[]", "***"));
    assertTrue(matchTypeName("java.lang.Object[][]", "***"
    ));
    assertFalse(matchTypeName("java.lang.Object", "%"));
    assertTrue(matchTypeName("a", "**"));
    assertTrue(matchTypeName("java.lang.Object[]", "**[]"
    ));
    assertFalse(matchTypeName("java.lang.Object[][]", "**[]"
    ));
    assertTrue(matchTypeName("abc", "*"));
    assertMatchesBasicTypes("***");
    assertMatchesBasicTypes("%");
  }

  @Test
  public void matchFieldOrMethodNames() {
    assertTrue(ProguardNameMatcher.matchFieldOrMethodName("*", ""));
    assertTrue(ProguardNameMatcher.matchFieldOrMethodName("*", "get"));
    assertTrue(ProguardNameMatcher.matchFieldOrMethodName("get*", "get"));
    assertTrue(ProguardNameMatcher.matchFieldOrMethodName("get*", "getObject"));
    assertTrue(ProguardNameMatcher.matchFieldOrMethodName("*t", "get"));
    assertTrue(ProguardNameMatcher.matchFieldOrMethodName("g*t*", "getObject"));
    assertTrue(
        ProguardNameMatcher.matchFieldOrMethodName("g*t***************", "getObject"));
    assertFalse(ProguardNameMatcher.matchFieldOrMethodName("get*y", "getObject"));
    assertFalse(ProguardNameMatcher.matchFieldOrMethodName("getObject?", "getObject"));
    assertTrue(ProguardNameMatcher.matchFieldOrMethodName("getObject?", "getObject1"));
    assertTrue(ProguardNameMatcher.matchFieldOrMethodName("getObject?", "getObject5"));
 }
}
