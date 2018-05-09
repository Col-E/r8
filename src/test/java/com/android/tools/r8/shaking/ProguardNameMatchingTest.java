// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.ProguardConfigurationParser.IdentifierPatternWithWildcards;
import com.android.tools.r8.shaking.ProguardTypeMatcher.ClassOrType;
import com.android.tools.r8.shaking.ProguardWildcard.BackReference;
import com.android.tools.r8.shaking.ProguardWildcard.Pattern;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ProguardNameMatchingTest {
  private static final String[] BASIC_TYPES = {
      "char", "byte", "short", "int", "long", "float", "double", "boolean"
  };

  private static final DexItemFactory dexItemFactory = new DexItemFactory();

  private static boolean matchTypeName(String typeName, String pattern) {
    return ProguardTypeMatcher.create(
        toIdentifierPatternWithWildCards(pattern, false), ClassOrType.TYPE, dexItemFactory)
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
            ProguardTypeMatcher.create(
                toIdentifierPatternWithWildCards(actualPattern, false),
                ClassOrType.CLASS, dexItemFactory));
      }
      builder.addPattern(listBuilder.build());
    }
    return builder.build()
        .matches(dexItemFactory.createType(DescriptorUtils.javaTypeToDescriptor(className)));
  }

  private static boolean matchMemberName(String pattern, String memberName) {
    ProguardNameMatcher nameMatcher =
        ProguardNameMatcher.create(toIdentifierPatternWithWildCards(pattern, true));
    return nameMatcher.matches(memberName);
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
    assertTrue(matchClassName("java.lang.Object", "?ava.*g.Ob<1>e*"));
    assertTrue(matchClassName("java.lang.Object", "j?v<1>.*<1>*g.Obj*"));
    assertTrue(matchClassName("java.lang.Object", "j*v?.*<2>*g.Obj*"));
    assertFalse(matchClassName("java.lang.Object", "java.*g.O?je?t?"));
    assertFalse(matchClassName("java.lang.Object", "java?lang.Object"));
    assertTrue(matchClassName("java.lang.Object", "*a*.*a**"));
    assertTrue(matchClassName("java.lang.Object", "*a**a**"));
    assertTrue(matchClassName("java.lang.Object", "*?**<2>**"));

    assertTrue(matchClassName("java.lang.Object", "!java.util.**", "java**"));
    assertFalse(matchClassName("java.lang.Object", "!java.**", "java.lang.*"));
    assertTrue(matchClassName("java.lang.Object", "java.lang.*", "!java.**"));

    assertTrue(matchClassName("boobar", "!foobar", "*bar"));
    assertTrue(matchClassName("boobar", "!foobar", "?*<1>ar"));
    assertFalse(matchClassName("foobar", "!foobar", "*bar"));

    assertFalse(matchClassName("foo", "!boo"));
    assertFalse(matchClassName("foo", "baz,!boo"));

    assertFalse(matchClassName("boo", "!boo", "**"));
    assertFalse(matchClassName("boo", "!b*<1>", "**"));
    assertFalse(matchClassName("boo", "!b?<1>", "**"));
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
    assertTrue(matchMemberName("*", ""));
    assertTrue(matchMemberName("*", "get"));
    assertTrue(matchMemberName("get*", "get"));
    assertTrue(matchMemberName("get*", "getObject"));
    assertTrue(matchMemberName("*t", "get"));
    assertTrue(matchMemberName("g*t*", "getObject"));
    assertTrue(matchMemberName("g*t***************", "getObject"));
    assertFalse(matchMemberName("get*y", "getObject"));
    assertFalse(matchMemberName("getObject?", "getObject"));
    assertTrue(matchMemberName("getObject?", "getObject1"));
    assertTrue(matchMemberName("getObject?", "getObject5"));

    assertTrue(matchMemberName("ge?Objec<1>", "getObject"));
    assertTrue(matchMemberName("ge*Objec<1>", "getObject"));
    assertTrue(matchMemberName("ge*Objec<1>", "geObjec"));
    assertTrue(matchMemberName("g?*Obj<1>c<2>", "getObject"));
    assertTrue(matchMemberName("g??Obj<1>c<2>", "getObject"));
    assertTrue(matchMemberName("g*?Obj<1>c<2>", "getObject"));
    assertFalse(matchMemberName("g?*Obj<1>c<2>", "getObject1"));
    assertTrue(matchMemberName("g**Obj<1>c<2>", "getObject"));
    assertFalse(matchMemberName("g**Obj<1>c<2>", "getObject1"));
    assertTrue(matchMemberName("g?*Obj<1>c<2>?", "getObject1"));
    assertTrue(matchMemberName("g**Obj<1>c<2>?", "getObject1"));
    assertTrue(matchMemberName("*foo<1>", "foofoofoo"));
    assertTrue(matchMemberName("*foo<1>", "barfoobar"));
    assertFalse(matchMemberName("*foo<1>", "barfoobaz"));
  }

  private static IdentifierPatternWithWildcards toIdentifierPatternWithWildCards(
      String pattern, boolean isForNameMatcher) {
    ImmutableList.Builder<ProguardWildcard> builder = ImmutableList.builder();
    String allPattern = "";
    String backReference = "";
    for (int i = 0; i < pattern.length(); i++) {
      char patternChar = pattern.charAt(i);
      if (backReference.length() > 0) {
        if (patternChar == '>') {
          backReference += patternChar;
          builder.add(new BackReference(
              Integer.parseInt(backReference.substring(1, backReference.length() - 1))));
          backReference = "";
        } else {
          backReference += patternChar;
        }
        continue;
      } else if (allPattern.length() > 0) {
        if (patternChar == '*') {
          allPattern += patternChar;
          continue;
        } else {
          builder.add(new Pattern(allPattern));
          allPattern = "";
        }
      }

      if (patternChar == '*') {
        if (isForNameMatcher) {
          builder.add(new Pattern(String.valueOf(patternChar)));
        } else {
          allPattern += patternChar;
        }
      } else if (patternChar == '?' || patternChar == '%') {
        builder.add(new Pattern(String.valueOf(patternChar)));
      } else if (patternChar == '<') {
        backReference += patternChar;
      }
    }
    if (allPattern.length() > 0) {
      builder.add(new Pattern(allPattern));
    }
    List<ProguardWildcard> wildcards = builder.build();
    linkBackReferences(wildcards);
    return new IdentifierPatternWithWildcards(pattern, wildcards);
  }

  private static void linkBackReferences(Iterable<ProguardWildcard> wildcards) {
    List<Pattern> patterns = new ArrayList<>();
    for (ProguardWildcard wildcard : wildcards) {
      if (wildcard.isBackReference()) {
        BackReference backReference = wildcard.asBackReference();
        backReference.setReference(patterns.get(backReference.referenceIndex - 1));
      } else {
        assert wildcard.isPattern();
        patterns.add(wildcard.asPattern());
      }
    }
  }
}
