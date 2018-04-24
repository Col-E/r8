// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.shaking.ProguardConfigurationSourceStrings.createConfigurationForTesting;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.DefaultDiagnosticsHandler;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

class EmptyMainClassForProguardTests {

  public static void main(String[] args) {
  }
}

public class ProguardConfigurationParserTest extends TestBase {

  private static final String VALID_PROGUARD_DIR = "src/test/proguard/valid/";
  private static final String INVALID_PROGUARD_DIR = "src/test/proguard/invalid/";
  private static final String PROGUARD_SPEC_FILE = VALID_PROGUARD_DIR + "proguard.flags";
  private static final String MULTIPLE_NAME_PATTERNS_FILE =
      VALID_PROGUARD_DIR + "multiple-name-patterns.flags";
  private static final String ACCESS_FLAGS_FILE = VALID_PROGUARD_DIR + "access-flags.flags";
  private static final String WHY_ARE_YOU_KEEPING_FILE =
      VALID_PROGUARD_DIR + "why-are-you-keeping.flags";
  private static final String ASSUME_NO_SIDE_EFFECTS =
      VALID_PROGUARD_DIR + "assume-no-side-effects.flags";
  private static final String ASSUME_NO_SIDE_EFFECTS_WITH_RETURN_VALUE =
      VALID_PROGUARD_DIR + "assume-no-side-effects-with-return-value.flags";
  private static final String ASSUME_VALUES_WITH_RETURN_VALUE =
      VALID_PROGUARD_DIR + "assume-values-with-return-value.flags";
  private static final String INCLUDING =
      VALID_PROGUARD_DIR + "including.flags";
  private static final String INVALID_INCLUDING_1 =
      INVALID_PROGUARD_DIR + "including-1.flags";
  private static final String INVALID_INCLUDING_2 =
      INVALID_PROGUARD_DIR + "including-2.flags";
  private static final String LIBRARY_JARS =
      VALID_PROGUARD_DIR + "library-jars.flags";
  private static final String LIBRARY_JARS_WIN =
      VALID_PROGUARD_DIR + "library-jars-win.flags";
  private static final String SEEDS =
      VALID_PROGUARD_DIR + "seeds.flags";
  private static final String SEEDS_2 =
      VALID_PROGUARD_DIR + "seeds-2.flags";
  private static final String VERBOSE =
      VALID_PROGUARD_DIR + "verbose.flags";
  private static final String KEEPDIRECTORIES =
      VALID_PROGUARD_DIR + "keepdirectories.flags";
  private static final String DONT_OBFUSCATE =
      VALID_PROGUARD_DIR + "dontobfuscate.flags";
  private static final String PACKAGE_OBFUSCATION_1 =
      VALID_PROGUARD_DIR + "package-obfuscation-1.flags";
  private static final String PACKAGE_OBFUSCATION_2 =
      VALID_PROGUARD_DIR + "package-obfuscation-2.flags";
  private static final String PACKAGE_OBFUSCATION_3 =
      VALID_PROGUARD_DIR + "package-obfuscation-3.flags";
  private static final String PACKAGE_OBFUSCATION_4 =
      VALID_PROGUARD_DIR + "package-obfuscation-4.flags";
  private static final String PACKAGE_OBFUSCATION_5 =
      VALID_PROGUARD_DIR + "package-obfuscation-5.flags";
  private static final String PACKAGE_OBFUSCATION_6 =
      VALID_PROGUARD_DIR + "package-obfuscation-6.flags";
  private static final String APPLY_MAPPING =
      VALID_PROGUARD_DIR + "applymapping.flags";
  private static final String APPLY_MAPPING_WITHOUT_FILE =
      INVALID_PROGUARD_DIR + "applymapping-without-file.flags";
  private static final String DONT_SHRINK =
      VALID_PROGUARD_DIR + "dontshrink.flags";
  private static final String DONT_SKIP_NON_PUBLIC_LIBRARY_CLASSES =
      VALID_PROGUARD_DIR + "dontskipnonpubliclibraryclasses.flags";
  private static final String DONT_SKIP_NON_PUBLIC_LIBRARY_CLASS_MEMBERS =
      VALID_PROGUARD_DIR + "dontskipnonpubliclibraryclassmembers.flags";
  private static final String IDENTIFIER_NAME_STRING =
      VALID_PROGUARD_DIR + "identifiernamestring.flags";
  private static final String OVERLOAD_AGGRESIVELY =
      VALID_PROGUARD_DIR + "overloadaggressively.flags";
  private static final String DONT_OPTIMIZE =
      VALID_PROGUARD_DIR + "dontoptimize.flags";
  private static final String DONT_OPTIMIZE_OVERRIDES_PASSES =
      VALID_PROGUARD_DIR + "dontoptimize-overrides-optimizationpasses.flags";
  private static final String OPTIMIZATION_PASSES =
      VALID_PROGUARD_DIR + "optimizationpasses.flags";
  private static final String OPTIMIZATION_PASSES_WITHOUT_N =
      INVALID_PROGUARD_DIR + "optimizationpasses-without-n.flags";
  private static final String SKIP_NON_PUBLIC_LIBRARY_CLASSES =
      VALID_PROGUARD_DIR + "skipnonpubliclibraryclasses.flags";
  private static final String PARSE_AND_SKIP_SINGLE_ARGUMENT =
      VALID_PROGUARD_DIR + "parse-and-skip-single-argument.flags";
  private static final String PRINT_USAGE =
      VALID_PROGUARD_DIR + "printusage.flags";
  private static final String PRINT_USAGE_TO_FILE =
      VALID_PROGUARD_DIR + "printusage-to-file.flags";
  private static final String TARGET =
      VALID_PROGUARD_DIR + "target.flags";

  private static class KeepingDiagnosticHandler implements DiagnosticsHandler {
    private final List<Diagnostic> infos = new ArrayList<>();
    private final List<Diagnostic> warnings = new ArrayList<>();
    private final List<Diagnostic> errors = new ArrayList<>();

    @Override
    public void info(Diagnostic info) {
      infos.add(info);
    }

    @Override
    public void warning(Diagnostic warning) {
      warnings.add(warning);
    }

    @Override
    public void error(Diagnostic error) {
      errors.add(error);
    }
  }

  private Reporter reporter;
  private KeepingDiagnosticHandler handler;
  private ProguardConfigurationParser parser;

  @Before
  public void reset() {
    handler = new KeepingDiagnosticHandler();
    reporter = new Reporter(handler);
    parser = new ProguardConfigurationParser(new DexItemFactory(), reporter);
  }

  @Test
  public void parse() throws Exception {
    ProguardConfigurationParser parser;

    // Parse from file.
    parser = new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PROGUARD_SPEC_FILE));
    verifyParserEndsCleanly();
    List<ProguardConfigurationRule> rules = parser.getConfig().getRules();
    assertEquals(24, rules.size());
    assertEquals(1, rules.get(0).getMemberRules().size());

    // Parse from strings.
    parser = new ProguardConfigurationParser(new DexItemFactory(), reporter);
    List<String> lines = FileUtils.readAllLines(Paths.get(PROGUARD_SPEC_FILE));
    parser.parse(createConfigurationForTesting(lines));
    rules = parser.getConfig().getRules();
    assertEquals(24, rules.size());
    assertEquals(1, rules.get(0).getMemberRules().size());
  }

  @Test
  public void parseMultipleNamePatterns() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(MULTIPLE_NAME_PATTERNS_FILE));
    verifyParserEndsCleanly();
    List<ProguardConfigurationRule> rules = parser.getConfig().getRules();
    assertEquals(1, rules.size());
    ProguardConfigurationRule rule = rules.get(0);
    assertEquals(1, rule.getMemberRules().size());
    assertEquals("com.company.hello.**,com.company.world.**", rule.getClassNames().toString());
    assertEquals(ProguardKeepRuleType.KEEP, ((ProguardKeepRule) rule).getType());
    assertTrue(rule.getInheritanceIsExtends());
    assertEquals("some.library.Class", rule.getInheritanceClassName().toString());
    ProguardMemberRule memberRule = rule.getMemberRules().iterator().next();
    assertTrue(memberRule.getAccessFlags().isProtected());
    assertEquals(ProguardNameMatcher.create("getContents"), memberRule.getName());
    assertEquals("java.lang.Object[][]", memberRule.getType().toString());
    assertEquals(ProguardMemberType.METHOD, memberRule.getRuleType());
    assertEquals(0, memberRule.getArguments().size());
  }

  @Test
  public void parseNonJavaIdentifiers() throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory,
            new Reporter(new DefaultDiagnosticsHandler()));
    String nonJavaIdentifiers =
        String.join("\n", ImmutableList.of(
            "-keep class -package-.-ClassNameWithDash-{",
            "  -NameWithDash- -field-;",
            "  p-.-OtherNameWithDash- -method-(-p.-WithDash-, -package-.-ClassNameWithDash-[]); ",
            "}"));
    parser.parse(createConfigurationForTesting(ImmutableList.of(nonJavaIdentifiers)));
    verifyParserEndsCleanly();
    List<ProguardConfigurationRule> rules = parser.getConfig().getRules();
    assertEquals(1, rules.size());
    assertEquals(ProguardClassType.CLASS, rules.get(0).getClassType());
    assertEquals(1, rules.get(0).getClassNames().size());
    List<DexType> classTypes = rules.get(0).getClassNames().asSpecificDexTypes();
    assertEquals(1, classTypes.size());
    assertSame(dexItemFactory.createType("L-package-/-ClassNameWithDash-;"), classTypes.get(0));
    ProguardConfigurationRule rule = rules.get(0);
    assertEquals(2, rule.getMemberRules().size());
    int matches = 0;
    for (ProguardMemberRule memberRule : rule.getMemberRules()) {
      if (memberRule.getRuleType() == ProguardMemberType.FIELD) {
        assertTrue(memberRule.getName().matches("-field-"));
        matches |= 0x01;
      } else {
        assertEquals(ProguardMemberType.METHOD, memberRule.getRuleType());
        assertTrue(memberRule.getName().matches("-method-"));
        assertFalse(memberRule.getArguments().get(0).getSpecificType().isArrayType());
        assertSame(dexItemFactory.createType("L-p/-WithDash-;"),
            memberRule.getArguments().get(0).getSpecificType());
        assertSame(dexItemFactory.createType("[L-package-/-ClassNameWithDash-;"),
            memberRule.getArguments().get(1).getSpecificType());
        matches |= 0x02;
      }
    }
    assertEquals(0x03, matches);
  }

  private void testDontXXX(String xxx, Function<ProguardConfiguration, ProguardClassFilter> pattern)
      throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    String configuration = "-dont" + xxx + " !foobar,*bar";
    parser.parse(createConfigurationForTesting(ImmutableList.of(configuration)));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertFalse(pattern.apply(config).matches(dexItemFactory.createType("Lboobaz;")));
    assertTrue(pattern.apply(config).matches(dexItemFactory.createType("Lboobar;")));
    assertFalse(pattern.apply(config).matches(dexItemFactory.createType("Lfoobar;")));
  }

  @Test
  public void testDontXXX() throws Exception {
    testDontXXX("warn", ProguardConfiguration::getDontWarnPatterns);
    testDontXXX("note", ProguardConfiguration::getDontNotePatterns);
  }

  private void testDontXXXMultiple(
      String xxx, Function<ProguardConfiguration, ProguardClassFilter> pattern) throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    List<String> configuration1 = ImmutableList.of("-dont" + xxx + " foo.**, bar.**");
    List<String> configuration2 = ImmutableList.of("-dont" + xxx + " foo.**", "-dontwarn bar.**");
    for (List<String> configuration : ImmutableList.of(configuration1, configuration2)) {
      parser.parse(createConfigurationForTesting(configuration));
      verifyParserEndsCleanly();
      ProguardConfiguration config = parser.getConfig();
      assertTrue(pattern.apply(config).matches(dexItemFactory.createType("Lfoo/Bar;")));
      assertTrue(pattern.apply(config).matches(dexItemFactory.createType("Lfoo/bar7Bar;")));
      assertTrue(pattern.apply(config).matches(dexItemFactory.createType("Lbar/Foo;")));
    }
  }

  @Test
  public void testDontWarnMultiple() throws Exception {
    testDontXXXMultiple("warn", ProguardConfiguration::getDontWarnPatterns);
    testDontXXXMultiple("note", ProguardConfiguration::getDontNotePatterns);
  }

  private void testDontXXXAllExplicitly(
      String xxx, Function<ProguardConfiguration, ProguardClassFilter> pattern) throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    String dontwarnAll = "-dont" + xxx + " *";
    parser.parse(createConfigurationForTesting(ImmutableList.of(dontwarnAll)));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(pattern.apply(config).matches(dexItemFactory.createType("Lboobaz;")));
    assertTrue(pattern.apply(config).matches(dexItemFactory.createType("Lboobar;")));
    assertTrue(pattern.apply(config).matches(dexItemFactory.createType("Lfoobar;")));
  }

  @Test
  public void testDontWarnAllExplicitly() throws Exception {
    testDontXXXAllExplicitly("warn", ProguardConfiguration::getDontWarnPatterns);
    testDontXXXAllExplicitly("note", ProguardConfiguration::getDontNotePatterns);
  }

  private void testDontXXXAllImplicitly(
      String xxx, Function<ProguardConfiguration, ProguardClassFilter> pattern) throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    String dontwarnAll = "-dont" + xxx;
    String otherOption = "-keep class *";
    parser.parse(createConfigurationForTesting(ImmutableList.of(dontwarnAll, otherOption)));
    ProguardConfiguration config = parser.getConfig();
    assertTrue(pattern.apply(config).matches(dexItemFactory.createType("Lboobaz;")));
    assertTrue(pattern.apply(config).matches(dexItemFactory.createType("Lboobar;")));
    assertTrue(pattern.apply(config).matches(dexItemFactory.createType("Lfoobar;")));
  }

  @Test
  public void testDontWarnAllImplicitly() throws Exception {
    testDontXXXAllImplicitly("warn", ProguardConfiguration::getDontWarnPatterns);
    testDontXXXAllImplicitly("note", ProguardConfiguration::getDontNotePatterns);
  }

  @Test
  public void parseAccessFlags() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(ACCESS_FLAGS_FILE));
    verifyParserEndsCleanly();
    List<ProguardConfigurationRule> rules = parser.getConfig().getRules();
    assertEquals(1, rules.size());
    ProguardConfigurationRule rule = rules.get(0);
    ClassAccessFlags publicAndFinalFlags = ClassAccessFlags.fromSharedAccessFlags(0);
    publicAndFinalFlags.setPublic();
    publicAndFinalFlags.setFinal();
    assertTrue(rule.getClassAccessFlags().containsNone(publicAndFinalFlags));
    assertTrue(rule.getNegatedClassAccessFlags().containsAll(publicAndFinalFlags));
    ClassAccessFlags abstractFlags = ClassAccessFlags.fromSharedAccessFlags(0);
    abstractFlags.setAbstract();
    assertTrue(rule.getClassAccessFlags().containsAll(abstractFlags));
    assertTrue(rule.getNegatedClassAccessFlags().containsNone(abstractFlags));
    for (ProguardMemberRule member : rule.getMemberRules()) {
      if (member.getRuleType() == ProguardMemberType.ALL_FIELDS) {
        FieldAccessFlags publicFlags = FieldAccessFlags.fromSharedAccessFlags(0);
        publicFlags.setPublic();
        assertTrue(member.getAccessFlags().containsAll(publicFlags));
        assertTrue(member.getNegatedAccessFlags().containsNone(publicFlags));
        FieldAccessFlags staticFlags = FieldAccessFlags.fromSharedAccessFlags(0);
        staticFlags.setStatic();
        assertTrue(member.getAccessFlags().containsNone(staticFlags));
        assertTrue(member.getNegatedAccessFlags().containsAll(staticFlags));
      } else {
        assertTrue(member.getRuleType() == ProguardMemberType.ALL_METHODS);

        MethodAccessFlags publicNativeFlags = MethodAccessFlags.fromSharedAccessFlags(0, false);
        publicNativeFlags.setPublic();
        publicNativeFlags.setNative();
        assertTrue(member.getAccessFlags().containsAll(publicNativeFlags));
        assertFalse(member.getNegatedAccessFlags().containsNone(publicNativeFlags));

        MethodAccessFlags protectedNativeFlags = MethodAccessFlags.fromSharedAccessFlags(0, false);
        protectedNativeFlags.setProtected();
        protectedNativeFlags.setNative();
        assertTrue(member.getAccessFlags().containsAll(protectedNativeFlags));
        assertFalse(member.getNegatedAccessFlags().containsNone(protectedNativeFlags));
      }
    }
  }

  @Test
  public void parseWhyAreYouKeeping() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(WHY_ARE_YOU_KEEPING_FILE));
    verifyParserEndsCleanly();
    List<ProguardConfigurationRule> rules = parser.getConfig().getRules();
    assertEquals(1, rules.size());
    ProguardConfigurationRule rule = rules.get(0);
    assertEquals(1, rule.getClassNames().size());
    assertEquals("*", rule.getClassNames().toString());
    assertTrue(rule.getInheritanceIsExtends());
    assertEquals("foo.bar", rule.getInheritanceClassName().toString());
  }

  @Test
  public void parseAssumeNoSideEffects() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(ASSUME_NO_SIDE_EFFECTS));
    verifyParserEndsCleanly();
    List<ProguardConfigurationRule> assumeNoSideEffects = parser.getConfig().getRules();
    assertEquals(1, assumeNoSideEffects.size());
    assumeNoSideEffects.get(0).getMemberRules().forEach(rule -> {
      assertFalse(rule.hasReturnValue());
    });
  }

  @Test
  public void parseAssumeNoSideEffectsWithReturnValue() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(ASSUME_NO_SIDE_EFFECTS_WITH_RETURN_VALUE));
    verifyParserEndsCleanly();
    List<ProguardConfigurationRule> assumeNoSideEffects = parser.getConfig().getRules();
    assertEquals(1, assumeNoSideEffects.size());
    int matches = 0;
    for (ProguardMemberRule rule : assumeNoSideEffects.get(0).getMemberRules()) {
      assertTrue(rule.hasReturnValue());
      if (rule.getName().matches("returnsTrue") || rule.getName().matches("returnsFalse")) {
        assertTrue(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertEquals(rule.getName().matches("returnsTrue"), rule.getReturnValue().getBoolean());
        matches |= 1 << 0;
      } else if (rule.getName().matches("returns1")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertTrue(rule.getReturnValue().isSingleValue());
        assertEquals(1, rule.getReturnValue().getValueRange().getMin());
        assertEquals(1, rule.getReturnValue().getValueRange().getMax());
        assertEquals(1, rule.getReturnValue().getSingleValue());
        matches |= 1 << 1;
      } else if (rule.getName().matches("returns2To4")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isSingleValue());
        assertEquals(2, rule.getReturnValue().getValueRange().getMin());
        assertEquals(4, rule.getReturnValue().getValueRange().getMax());
        matches |= 1 << 2;
      } else if (rule.getName().matches("returns234To567")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isSingleValue());
        assertEquals(234, rule.getReturnValue().getValueRange().getMin());
        assertEquals(567, rule.getReturnValue().getValueRange().getMax());
        matches |= 1 << 3;
      } else if (rule.getName().matches("returnsField")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertTrue(rule.getReturnValue().isField());
        assertEquals("com.google.C", rule.getReturnValue().getField().clazz.toString());
        assertEquals("int", rule.getReturnValue().getField().type.toString());
        assertEquals("X", rule.getReturnValue().getField().name.toString());
        matches |= 1 << 4;
      } else {
        fail("Unexpected");
      }
    }
    assertEquals((1 << 5) - 1, matches);
  }

  @Test
  public void parseAssumeValuesWithReturnValue() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(ASSUME_VALUES_WITH_RETURN_VALUE));
    verifyParserEndsCleanly();
    List<ProguardConfigurationRule> assumeValues = parser.getConfig().getRules();
    assertEquals(1, assumeValues.size());
    int matches = 0;
    for (ProguardMemberRule rule : assumeValues.get(0).getMemberRules()) {
      assertTrue(rule.hasReturnValue());
      if (rule.getName().matches("isTrue") || rule.getName().matches("isFalse")) {
        assertTrue(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertEquals(rule.getName().matches("isTrue"), rule.getReturnValue().getBoolean());
        matches |= 1 << 0;
      } else if (rule.getName().matches("is1")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertTrue(rule.getReturnValue().isSingleValue());
        assertEquals(1, rule.getReturnValue().getValueRange().getMin());
        assertEquals(1, rule.getReturnValue().getValueRange().getMax());
        assertEquals(1, rule.getReturnValue().getSingleValue());
        matches |= 1 << 1;
      } else if (rule.getName().matches("is2To4")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isSingleValue());
        assertEquals(2, rule.getReturnValue().getValueRange().getMin());
        assertEquals(4, rule.getReturnValue().getValueRange().getMax());
        matches |= 1 << 2;
      } else if (rule.getName().matches("is234To567")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isSingleValue());
        assertEquals(234, rule.getReturnValue().getValueRange().getMin());
        assertEquals(567, rule.getReturnValue().getValueRange().getMax());
        matches |= 1 << 3;
      } else if (rule.getName().matches("isField")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertTrue(rule.getReturnValue().isField());
        assertEquals("com.google.C", rule.getReturnValue().getField().clazz.toString());
        assertEquals("int", rule.getReturnValue().getField().type.toString());
        assertEquals("X", rule.getReturnValue().getField().name.toString());
        matches |= 1 << 4;
      } else {
        fail("Unexpected");
      }
    }
    assertEquals((1 << 5) - 1, matches);
  }

  @Test
  public void testAdaptClassStrings() throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    String adaptClassStrings = "-adaptclassstrings !foobar,*bar";
    parser.parse(createConfigurationForTesting(ImmutableList.of(adaptClassStrings)));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertFalse(
        config.getAdaptClassStrings().matches(dexItemFactory.createType("Lboobaz;")));
    assertTrue(
        config.getAdaptClassStrings().matches(dexItemFactory.createType("Lboobar;")));
    assertFalse(
        config.getAdaptClassStrings().matches(dexItemFactory.createType("Lfoobar;")));
  }

  @Test
  public void testAdaptClassStringsMultiple() throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    List<String> configuration1 = ImmutableList.of("-adaptclassstrings foo.**, bar.**");
    List<String> configuration2 =
        ImmutableList.of("-adaptclassstrings foo.**", "-adaptclassstrings bar.**");
    for (List<String> configuration : ImmutableList.of(configuration1, configuration2)) {
      parser.parse(createConfigurationForTesting(configuration));
      verifyParserEndsCleanly();
      ProguardConfiguration config = parser.getConfig();
      assertTrue(
          config.getAdaptClassStrings().matches(dexItemFactory.createType("Lfoo/Bar;")));
      assertTrue(
          config.getAdaptClassStrings().matches(dexItemFactory.createType("Lfoo/bar7Bar;")));
      assertTrue(
          config.getAdaptClassStrings().matches(dexItemFactory.createType("Lbar/Foo;")));
    }
  }

  @Test
  public void testAdaptClassStringsAllExplicitly() throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    String adaptAll = "-adaptclassstrings *";
    parser.parse(createConfigurationForTesting(ImmutableList.of(adaptAll)));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(
        config.getAdaptClassStrings().matches(dexItemFactory.createType("Lboobaz;")));
    assertTrue(
        config.getAdaptClassStrings().matches(dexItemFactory.createType("Lboobar;")));
    assertTrue(
        config.getAdaptClassStrings().matches(dexItemFactory.createType("Lfoobar;")));
  }

  @Test
  public void testAdaptClassStringsAllImplicitly() throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    String adaptAll = "-adaptclassstrings";
    parser.parse(createConfigurationForTesting(ImmutableList.of(adaptAll)));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(
        config.getAdaptClassStrings().matches(dexItemFactory.createType("Lboobaz;")));
    assertTrue(
        config.getAdaptClassStrings().matches(dexItemFactory.createType("Lboobar;")));
    assertTrue(
        config.getAdaptClassStrings().matches(dexItemFactory.createType("Lfoobar;")));
  }

  @Test
  public void testIdentifierNameString() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    String config1 =
        "-identifiernamestring class a.b.c.*GeneratedClass {\n"
        + "  static java.lang.String CONTAINING_TYPE_*;\n"
        + "}";
    String config2 =
        "-identifiernamestring class x.y.z.ReflectionBasedFactory {\n"
        + "  private static java.lang.reflect.Field field(java.lang.Class,java.lang.String);\n"
        + "}";
    String config3 =
        "-identifiernamestring class * {\n"
        + "  @my.annotations.IdentifierNameString *;\n"
        + "}";
    parser.parse(createConfigurationForTesting(ImmutableList.of(config1, config2, config3)));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    List<ProguardConfigurationRule> identifierNameStrings = config.getRules();
    assertEquals(3, identifierNameStrings.size());
    assertEquals(1, identifierNameStrings.get(0).getClassNames().size());
    assertEquals(
        "a.b.c.*GeneratedClass",
        identifierNameStrings.get(0).getClassNames().toString());
    identifierNameStrings.get(0).getMemberRules().forEach(memberRule -> {
      assertTrue(memberRule.getRuleType().includesFields());
      assertTrue(memberRule.getName().matches("CONTAINING_TYPE_ABC"));
    });
    assertEquals(1, identifierNameStrings.get(1).getClassNames().size());
    assertEquals(
        "x.y.z.ReflectionBasedFactory",
        identifierNameStrings.get(1).getClassNames().toString());
    identifierNameStrings.get(1).getMemberRules().forEach(memberRule -> {
      assertTrue(memberRule.getRuleType().includesMethods());
      assertTrue(memberRule.getName().matches("field"));
    });
    assertEquals(1, identifierNameStrings.get(2).getClassNames().size());
    assertEquals("*", identifierNameStrings.get(2).getClassNames().toString());
    identifierNameStrings.get(2).getMemberRules().forEach(memberRule -> {
      assertEquals(ProguardMemberType.ALL, memberRule.getRuleType());
      assertTrue(memberRule.getAnnotation().toString().endsWith("IdentifierNameString"));
    });
  }

  @Test
  public void parseDontobfuscate() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_OBFUSCATE));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertFalse(config.isObfuscating());
  }

  @Test
  public void parseRepackageClassesEmpty() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PACKAGE_OBFUSCATION_1));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals(PackageObfuscationMode.REPACKAGE, config.getPackageObfuscationMode());
    assertNotNull(config.getPackagePrefix());
    assertEquals("", config.getPackagePrefix());
  }

  @Test
  public void parseRepackageClassesNonEmpty() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PACKAGE_OBFUSCATION_2));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals(PackageObfuscationMode.REPACKAGE, config.getPackageObfuscationMode());
    assertNotNull(config.getPackagePrefix());
    assertEquals("p.q.r", config.getPackagePrefix());
  }

  @Test
  public void parseFlattenPackageHierarchyEmpty() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PACKAGE_OBFUSCATION_3));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals(PackageObfuscationMode.FLATTEN, config.getPackageObfuscationMode());
    assertNotNull(config.getPackagePrefix());
    assertEquals("", config.getPackagePrefix());
  }

  @Test
  public void parseFlattenPackageHierarchyNonEmpty() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PACKAGE_OBFUSCATION_4));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals(PackageObfuscationMode.FLATTEN, config.getPackageObfuscationMode());
    assertNotNull(config.getPackagePrefix());
    assertEquals("p.q.r", config.getPackagePrefix());
  }

  @Test
  public void flattenPackageHierarchyCannotOverrideRepackageClasses() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path path = Paths.get(PACKAGE_OBFUSCATION_5);
    parser.parse(path);
    checkDiagnostic(handler.warnings, path, 6, 1,
        "repackageclasses", "overrides", "flattenpackagehierarchy");
    ProguardConfiguration config = parser.getConfig();
    assertEquals(PackageObfuscationMode.REPACKAGE, config.getPackageObfuscationMode());
    assertNotNull(config.getPackagePrefix());
    assertEquals("top", config.getPackagePrefix());
  }

  @Test
  public void repackageClassesOverridesFlattenPackageHierarchy() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path path = Paths.get(PACKAGE_OBFUSCATION_6);
    parser.parse(path);
    checkDiagnostic(handler.warnings, path, 6, 1,
        "repackageclasses", "overrides", "flattenpackagehierarchy");
    ProguardConfiguration config = parser.getConfig();
    assertEquals(PackageObfuscationMode.REPACKAGE, config.getPackageObfuscationMode());
    assertNotNull(config.getPackagePrefix());
    assertEquals("top", config.getPackagePrefix());
  }

  @Test
  public void parseApplyMapping() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(APPLY_MAPPING));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.hasApplyMappingFile());
  }

  @Test
  public void parseApplyMappingWithoutFile() throws Exception {
    Path path = Paths.get(APPLY_MAPPING_WITHOUT_FILE);
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(path);
      fail("Expect to fail due to the lack of file name.");
    } catch (AbortException e) {
      checkDiagnostic(handler.errors, path, 6, 14, "File name expected");
    }
  }

  @Test
  public void parseIncluding() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(INCLUDING));
    verifyParserEndsCleanly();
  }

  @Test
  public void parseInvalidIncluding1() throws IOException {
    Path path = Paths.get(INVALID_INCLUDING_1);
    try {
      new ProguardConfigurationParser(new DexItemFactory(), reporter)
          .parse(path);
      fail();
    } catch (AbortException e) {
      checkDiagnostic(handler.errors, path, 6, 10,"does-not-exist.flags");
    }
  }

  @Test
  public void parseInvalidIncluding2() throws IOException {
    Path path = Paths.get(INVALID_INCLUDING_2);
    try {
      new ProguardConfigurationParser(new DexItemFactory(), reporter)
          .parse(path);
      fail();
    } catch (AbortException e) {
      checkDiagnostic(handler.errors, path, 6,2, "does-not-exist.flags");
    }
  }

  @Test
  public void parseLibraryJars() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    if (!ToolHelper.isLinux() && !ToolHelper.isMac()) {
      parser.parse(Paths.get(LIBRARY_JARS_WIN));
    } else {
      parser.parse(Paths.get(LIBRARY_JARS));
    }
    assertEquals(4, parser.getConfig().getLibraryjars().size());
  }

  @Test
  public void parseInvalidFilePattern() throws IOException {
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(createConfigurationForTesting(
          Collections.singletonList("-injars abc.jar(*.zip;*.class)")));
      fail();
    } catch (AbortException e) {
      assertEquals(1, handler.errors.size());
    }
  }

  @Test
  public void parseKeepModifiers() {
    List<String> ws = ImmutableList.of("", " ", "   ", "\t", " \t", " \t", " \t ", " \t\t \t ");

    for (String before : ws) {
      for (String after : ws) {
        reset();
        ProguardConfiguration config = parseAndVerifyParserEndsCleanly(ImmutableList.of(
            "-keep"
                + before + "," + after + "includedescriptorclasses"
                + before + "," + after + "allowshrinking"
                + before + "," + after + "allowobfuscation"
                + before + "," + after + "allowoptimization "
                + "class A { *; }"
        ));
      }
    }
  }

  @Test
  public void regress78442725() {
    parseAndVerifyParserEndsCleanly(ImmutableList.of(
        "-keep, includedescriptorclasses class in.uncod.android.bypass.Document { *; }",
        "-keep, includedescriptorclasses class in.uncod.android.bypass.Element { *; }"
    ));
  }

  @Test
  public void parseSeeds() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(SEEDS));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintSeeds());
    assertNull(config.getSeedFile());
  }

  @Test
  public void parseSeeds2() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(SEEDS_2));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintSeeds());
    assertNotNull(config.getSeedFile());
  }

  @Test
  public void parseVerbose() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(VERBOSE));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isVerbose());
  }

  @Test
  public void parseKeepdirectories() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(KEEPDIRECTORIES));
    verifyParserEndsCleanly();
  }

  @Test
  public void parseDontshrink() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_SHRINK));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertFalse(config.isShrinking());
  }

  @Test
  public void parseDontSkipNonPublicLibraryClasses() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_SKIP_NON_PUBLIC_LIBRARY_CLASSES));
    verifyParserEndsCleanly();
  }

  @Test
  public void parseDontskipnonpubliclibraryclassmembers() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_SKIP_NON_PUBLIC_LIBRARY_CLASS_MEMBERS));
    verifyParserEndsCleanly();
  }

  @Test
  public void parseIdentifiernamestring() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path source = Paths.get(IDENTIFIER_NAME_STRING);
    parser.parse(source);
    verifyParserEndsCleanly();
  }

  @Test
  public void parseOverloadAggressively() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(OVERLOAD_AGGRESIVELY));
    verifyParserEndsCleanly();
  }

  @Test
  public void parseDontOptimize() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_OPTIMIZE));
    ProguardConfiguration config = parser.getConfig();
    verifyParserEndsCleanly();
    assertFalse(config.isOptimizing());
  }

  @Test
  public void parseDontOptimizeOverridesPasses() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path path = Paths.get(DONT_OPTIMIZE_OVERRIDES_PASSES);
    parser.parse(path);
    checkDiagnostic(handler.warnings, path, 7, 1,
        "Ignoring", "-optimizationpasses");
    ProguardConfiguration config = parser.getConfig();
    assertFalse(config.isOptimizing());
  }

  @Test
  public void parseOptimizationPasses() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path path = Paths.get(OPTIMIZATION_PASSES);
    parser.parse(path);
    checkDiagnostic(handler.warnings, path, 5, 1,
        "Ignoring", "-optimizationpasses");
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isOptimizing());
  }

  @Test
  public void parseOptimizationPassesError() throws Exception {
    Path path = Paths.get(OPTIMIZATION_PASSES_WITHOUT_N);
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(path);
      fail();
    } catch (AbortException e) {
      checkDiagnostic(handler.errors, path, 6, 1, "Missing n");
    }
  }

  @Test
  public void parseSkipNonPublicLibraryClasses() throws IOException {
    Path path = Paths.get(SKIP_NON_PUBLIC_LIBRARY_CLASSES);
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(path);
      fail();
    } catch (AbortException e) {
      checkDiagnostic(handler.errors, path, 5, 1, "Unsupported option",
          "-skipnonpubliclibraryclasses");
    }
  }

  @Test
  public void parseAndskipSingleArgument() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PARSE_AND_SKIP_SINGLE_ARGUMENT));
    verifyParserEndsCleanly();
  }

  @Test
  public void parse_printconfiguration_noArguments() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(createConfigurationForTesting(ImmutableList.of(
        "-printconfiguration"
    )));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintConfiguration());
    assertNull(config.getPrintConfigurationFile());
  }

  @Test
  public void parse_printconfiguration_argument() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(createConfigurationForTesting(ImmutableList.of(
        "-printconfiguration file_name"
    )));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintConfiguration());
    assertEquals("." + File.separator + "file_name", config.getPrintConfigurationFile().toString());
  }

  @Test
  public void parsePrintUsage() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PRINT_USAGE));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintUsage());
    assertNull(config.getPrintUsageFile());
  }

  @Test
  public void parsePrintUsageToFile() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PRINT_USAGE_TO_FILE));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintUsage());
    assertNotNull(config.getPrintUsageFile());
  }

  @Test
  public void parseTarget() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(TARGET));
    verifyParserEndsCleanly();
  }

  @Test
  public void parseInvalidKeepClassOption() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-keepclassx public class * {  ",
        "  native <methods>;           ",
        "}                             "
    );
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      fail();
    } catch (AbortException e) {
      checkDiagnostic(handler.errors, proguardConfig, 1, 1, "Unknown option", "-keepclassx");
    }
  }

  @Test
  public void parseCustomFlags() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    // Custom Proguard flags -runtype and -laststageoutput are ignored.
    Path proguardConfig = writeTextToTempFile(
        "-runtype FINAL                    ",
        "-laststageoutput /some/file/name  "
    );
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();
  }

  @Test
  public void testRenameSourceFileAttribute() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    String config1 = "-renamesourcefileattribute PG\n";
    String config2 = "-keepattributes SourceFile,SourceDir\n";
    parser.parse(createConfigurationForTesting(ImmutableList.of(config1, config2)));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals("PG", config.getRenameSourceFileAttribute());
    assertTrue(config.getKeepAttributes().sourceFile);
    assertTrue(config.getKeepAttributes().sourceDir);
  }

  @Test
  public void testRenameSourceFileAttributeEmpty() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    String config1 = "-renamesourcefileattribute\n";
    String config2 = "-keepattributes SourceFile\n";
    parser.parse(createConfigurationForTesting(ImmutableList.of(config1, config2)));
    ProguardConfiguration config = parser.getConfig();
    assertEquals("", config.getRenameSourceFileAttribute());
    assertTrue(config.getKeepAttributes().sourceFile);
  }

  private void testKeepattributes(List<String> expected, String config) throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(createConfigurationForTesting(ImmutableList.of(config)));
    verifyParserEndsCleanly();
    assertEquals(
        ProguardKeepAttributes.fromPatterns(expected),
        parser.getConfigRawForTesting().getKeepAttributes());
  }

  @Test
  public void parseKeepattributes() throws Exception {
    List<String> xxxYYY = ImmutableList.of("xxx", "yyy");
    testKeepattributes(xxxYYY, "-keepattributes xxx,yyy");
    testKeepattributes(xxxYYY, "-keepattributes xxx, yyy");
    testKeepattributes(xxxYYY, "-keepattributes xxx ,yyy");
    testKeepattributes(xxxYYY, "-keepattributes xxx   ,   yyy");
    testKeepattributes(xxxYYY, "-keepattributes       xxx   ,   yyy     ");
    testKeepattributes(xxxYYY, "-keepattributes       xxx   ,   yyy     \n");
    String config =
        "-keepattributes Exceptions,InnerClasses,Signature,Deprecated,\n"
            + "          SourceFile,LineNumberTable,*Annotation*,EnclosingMethod\n";
    List<String> expected = ImmutableList.of(
        "Exceptions", "InnerClasses", "Signature", "Deprecated",
        "SourceFile", "LineNumberTable", "*Annotation*", "EnclosingMethod");
    testKeepattributes(expected, config);
  }

  @Test
  public void parseInvalidKeepattributes() throws Exception {
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(createConfigurationForTesting(ImmutableList.of("-keepattributes xxx,")));
      fail();
    } catch (AbortException e) {
      assertTrue(handler.errors.get(0).getDiagnosticMessage().contains("Expected list element at "));
    }
  }

  @Test
  public void parseUseUniqueClassMemberNames() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(createConfigurationForTesting(ImmutableList.of(
        "-useuniqueclassmembernames"
    )));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isUseUniqueClassMemberNames());
  }

  @Test
  public void parseKeepParameterNames() throws Exception {
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(createConfigurationForTesting(ImmutableList.of(
          "-keepparameternames"
      )));
      parser.getConfig();
      fail();
    } catch (AbortException e) {
      assertTrue(handler.errors.get(0).getDiagnosticMessage().contains(
          "-keepparameternames is not supported"));
    }
  }

  @Test
  public void parseKeepParameterNamesWithoutMinification() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(createConfigurationForTesting(ImmutableList.of(
        "-keepparameternames",
        "-dontobfuscate"
    )));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isKeepParameterNames());

    parser = new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(createConfigurationForTesting(ImmutableList.of(
        "-keepparameternames"
    )));
    verifyParserEndsCleanly();
    parser.parse(createConfigurationForTesting(ImmutableList.of(
        "-dontobfuscate"
    )));
    verifyParserEndsCleanly();
    config = parser.getConfig();
    assertTrue(config.isKeepParameterNames());
  }

  @Test
  public void parseShortLine() throws IOException {
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(createConfigurationForTesting(Collections.singletonList("-")));
      fail();
    } catch (AbortException e) {
      assertEquals(1, handler.errors.size());
      assertTrue(handler.errors.get(0).getDiagnosticMessage().contains("-"));
    }
  }

  @Test
  public void parseNoLocals() throws IOException {
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(createConfigurationForTesting(Collections.singletonList("--no-locals")));
      fail();
    } catch (AbortException e) {

      assertEquals(1, handler.errors.size());
      assertTrue(handler.errors.get(0).getDiagnosticMessage().contains("--no-locals"));
    }
  }

  private void checkFileFilterMatchAnything(ProguardPathFilter filter) {
    assertTrue(filter.matches("x"));
    assertTrue(filter.matches("x/y"));
    assertTrue(filter.matches("x/y/x"));
  }

  @Test
  public void parse_adaptresourcexxx_keepdirectories_noArguments1() {
    ProguardConfiguration config = parseAndVerifyParserEndsCleanly(ImmutableList.of(
        "-adaptresourcefilenames",
        "-adaptresourcefilecontents",
        "-keepdirectories"
    ));
    checkFileFilterMatchAnything(config.getAdaptResourceFilenames());
    checkFileFilterMatchAnything(config.getAdaptResourceFilecontents());
    checkFileFilterMatchAnything(config.getKeepDirectories());
  }

  @Test
  public void parse_adaptresourcexxx_keepdirectories_noArguments2() {
    ProguardConfiguration config = parseAndVerifyParserEndsCleanly(ImmutableList.of(
        "-keepdirectories",
        "-adaptresourcefilenames",
        "-adaptresourcefilecontents"
    ));
    checkFileFilterMatchAnything(config.getAdaptResourceFilenames());
    checkFileFilterMatchAnything(config.getAdaptResourceFilecontents());
    checkFileFilterMatchAnything(config.getKeepDirectories());
  }

  @Test
  public void parse_adaptresourcexxx_keepdirectories_noArguments3() {
    ProguardConfiguration config = parseAndVerifyParserEndsCleanly(ImmutableList.of(
        "-adaptresourcefilecontents",
        "-keepdirectories",
        "-adaptresourcefilenames"
    ));
    checkFileFilterMatchAnything(config.getAdaptResourceFilenames());
    checkFileFilterMatchAnything(config.getAdaptResourceFilecontents());
    checkFileFilterMatchAnything(config.getKeepDirectories());
  }

  private String FILE_FILTER_SINGLE = "xxx/*";

  private void checkFileFilterSingle(ProguardPathFilter filter) {
    assertTrue(filter.matches("xxx/x"));
    assertTrue(filter.matches("xxx/"));
    assertFalse(filter.matches("xxx/yyy/z"));
    assertFalse(filter.matches("xxx"));
  }

  @Test
  public void parse_adaptresourcexxx_keepdirectories_singleArgument() {
    ProguardConfiguration config = parseAndVerifyParserEndsCleanly(ImmutableList.of(
        "-adaptresourcefilenames " + FILE_FILTER_SINGLE,
        "-adaptresourcefilecontents " + FILE_FILTER_SINGLE,
        "-keepdirectories " + FILE_FILTER_SINGLE
    ));
    checkFileFilterSingle(config.getAdaptResourceFilenames());
    checkFileFilterSingle(config.getAdaptResourceFilecontents());
    checkFileFilterSingle(config.getKeepDirectories());
  }

  private String FILE_FILTER_MULTIPLE =
      "xxx/*, !**.gif  ,images/**  ,  com/myapp/**/*.xml,com/mylib/*/*.xml";

  private void checkFileFilterMultiple(ProguardPathFilter filter) {
    assertTrue(filter.matches("xxx/x"));
    assertTrue(filter.matches("xxx/x.gif"));
    assertTrue(filter.matches("images/x.jpg"));
    assertTrue(filter.matches("images/xxx/x.jpg"));
    assertTrue(filter.matches("com/myapp/package1/x.xml"));
    assertTrue(filter.matches("com/myapp/package1/package2/x.xml"));
    assertTrue(filter.matches("com/mylib/package1/x.xml"));
    assertFalse(filter.matches("x.gif"));
    assertFalse(filter.matches("images/x.gif"));
    assertFalse(filter.matches("images/xxx/y.gif"));
    assertFalse(filter.matches("images/xxx/yyy/z.gif"));
    assertFalse(filter.matches("com/myapp/package1/x.jpg"));
    assertFalse(filter.matches("com/myapp/package1/package2/x.jpg"));
    assertFalse(filter.matches("com/mylib/package1/package2/x.xml"));
  }

  @Test
  public void parse_adaptresourcexxx_keepdirectories_multipleArgument() {
    ProguardConfiguration config = parseAndVerifyParserEndsCleanly(ImmutableList.of(
        "-adaptresourcefilenames " + FILE_FILTER_MULTIPLE,
        "-adaptresourcefilecontents " + FILE_FILTER_MULTIPLE,
        "-keepdirectories " + FILE_FILTER_MULTIPLE
    ));
    checkFileFilterMultiple(config.getAdaptResourceFilenames());
    checkFileFilterMultiple(config.getAdaptResourceFilecontents());
    checkFileFilterMultiple(config.getKeepDirectories());
  }

  @Test
  public void parse_adaptresourcexxx_keepdirectories_leadingComma() {
    List<String> options = ImmutableList.of(
        "-adaptresourcefilenames", "-adaptresourcefilecontents", "-keepdirectories");
    for (String option : options) {
      try {
        reset();
        parser.parse(createConfigurationForTesting(ImmutableList.of(option + " ,")));
        fail("Expect to fail due to the lack of path filter.");
      } catch (AbortException e) {
        checkDiagnostic(handler.errors, null, 1, option.length() + 2, "Path filter expected");
      }
    }
  }

  @Test
  public void parse_adaptresourcexxx_keepdirectories_emptyListElement() {
    List<String> options = ImmutableList.of(
        "-adaptresourcefilenames", "-adaptresourcefilecontents", "-keepdirectories");
    for (String option : options) {
      try {
        reset();
        parser.parse(createConfigurationForTesting(ImmutableList.of(option + " xxx,,yyy")));
        fail("Expect to fail due to the lack of path filter.");
      } catch (AbortException e) {
        checkDiagnostic(handler.errors, null, 1, option.length() + 6, "Path filter expected");
      }
    }
  }

  @Test
  public void parse_adaptresourcexxx_keepdirectories_trailingComma() {
    List<String> options = ImmutableList.of(
        "-adaptresourcefilenames", "-adaptresourcefilecontents", "-keepdirectories");
    for (String option : options) {
      try {
        reset();
        parser.parse(createConfigurationForTesting(ImmutableList.of(option + " xxx,")));
        fail("Expect to fail due to the lack of path filter.");
      } catch (AbortException e) {
        checkDiagnostic(handler.errors, null, 1, option.length() + 6, "Path filter expected");
      }
    }
  }

  @Test
  public void parse_if() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if   class **$$ModuleAdapter",
        "-keep class A",
        "-if   class **$$InjectAdapter",
        "-keep class B",
        "-if   class **$$StaticInjection",
        "-keep class C",
        "-keepnames class dagger.Lazy"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    // Three -if rules and one independent -keepnames
    assertEquals(4, config.getRules().size());
    long ifCount =
        config.getRules().stream().filter(rule -> rule instanceof ProguardIfRule).count();
    assertEquals(3, ifCount);
    ProguardIfRule if0 = (ProguardIfRule) config.getRules().get(0);
    assertEquals("**$$ModuleAdapter", if0.getClassNames().toString());
    assertEquals(ProguardKeepRuleType.KEEP, if0.subsequentRule.getType());
    assertEquals("A", if0.subsequentRule.getClassNames().toString());
    ProguardIfRule if1 = (ProguardIfRule) config.getRules().get(1);
    assertEquals("**$$InjectAdapter", if1.getClassNames().toString());
    assertEquals(ProguardKeepRuleType.KEEP, if1.subsequentRule.getType());
    assertEquals("B", if1.subsequentRule.getClassNames().toString());
    ProguardIfRule if2 = (ProguardIfRule) config.getRules().get(2);
    assertEquals("**$$StaticInjection", if2.getClassNames().toString());
    assertEquals(ProguardKeepRuleType.KEEP, if2.subsequentRule.getType());
    assertEquals("C", if2.subsequentRule.getClassNames().toString());
  }

  @Test
  public void parse_if_nthWildcard() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **$R**",
        "-keep class **$D<2>"  // <2> corresponds to the 2nd ** in -if rule.
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals(1, config.getRules().size());
    ProguardIfRule if0 = (ProguardIfRule) config.getRules().get(0);
    assertEquals("**$R**", if0.getClassNames().toString());
    assertEquals(ProguardKeepRuleType.KEEP, if0.subsequentRule.getType());
    assertEquals("**$D<2>", if0.subsequentRule.getClassNames().toString());
    // TODO(b/73800755): Test <2> matches with expected wildcard: ** after '$R'.
  }

  @Test
  public void parse_if_nthWildcard_notNumber() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **$R**",
        "-keep class **D<n>"
    );
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      fail();
    } catch (AbortException e) {
      System.out.println(handler.errors.get(0));
      checkDiagnostic(handler.errors, proguardConfig, 2, 13,
          "Use of generics not allowed for java type");
    }
    verifyFailWithProguard6(proguardConfig, "Use of generics not allowed for java type");
  }

  @Test
  public void parse_if_nthWildcard_outOfRange_tooSmall() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **$R**",
        "-keep class **D<0>"  // nth wildcard starts from 1, not 0.
    );
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      fail();
    } catch (AbortException e) {
      checkDiagnostic(handler.errors, proguardConfig, 2, 13,
          "Wildcard", "<0>", "invalid");
    }
  }

  @Ignore("b/73800755: verify the range of <n>")
  @Test
  public void parse_if_nthWildcard_outOfRange_tooBig() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **$R**",
        "-keep class **D<4>"  // There are 3 previous wildcards in this rule.
    );
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      fail();
    } catch (AbortException e) {
      checkDiagnostic(handler.errors, proguardConfig, 1, 1,
          "Wildcard", "<4>", "invalid");
    }
  }

  @Ignore("b/73800755: verify the range of <n>")
  @Test
  public void parse_if_nthWildcard_outOfRange_inIf() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **$R<2>", // There is only one wildcard prior to <2>.
        "-keep class **D<2>"
    );
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      fail();
    } catch (AbortException e) {
      checkDiagnostic(handler.errors, proguardConfig, 1, 1,
          "Wildcard", "<2>", "invalid");
    }
  }

  @Ignore("b/73800755: verify the range of <n>")
  @Test
  public void parse_if_nthWildcard_not_referable() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **.R {",
        "  int id?;",
        "}",
        "-keep class <1>.D<2> {",
        "  int id<3>;",  // Only ** and ? are referable wildcards.
        "}"
    );
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      fail();
    } catch (AbortException e) {
      checkDiagnostic(handler.errors, proguardConfig, 1, 1,
          "Wildcard", "<3>", "invalid");
    }
  }

  @Test
  public void parse_if_if() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if   class **$$ModuleAdapter",
        "-if   class **$$InjectAdapter"
    );
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      fail();
    } catch (AbortException e) {
      checkDiagnostic(handler.errors, proguardConfig, 1, 1,
          "without", "subsequent", "keep");
    }
  }

  @Test
  public void parse_if_end() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if   class **$$ModuleAdapter"
    );
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      fail();
    } catch (AbortException e) {
      checkDiagnostic(handler.errors, proguardConfig, 1, 1,
          "without", "subsequent", "keep");
    }
  }

  @Test
  public void parse_assumenoexternalsideeffects() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-assumenoexternalsideeffects class java.lang.StringBuilder {",
        "  public java.lang.StringBuilder();",
        "  public java.lang.StringBuilder(int);",
        "  public java.lang.StringBuilder(java.lang.String);",
        "  public java.lang.StringBuilder append(java.lang.Object);",
        "  public java.lang.StringBuilder append(java.lang.String);",
        "  public java.lang.StringBuilder append(java.lang.StringBuffer);",
        "  public java.lang.StringBuilder append(char[]);",
        "  public java.lang.StringBuilder append(char[], int, int);",
        "  public java.lang.StringBuilder append(boolean);",
        "  public java.lang.StringBuilder append(char);",
        "  public java.lang.StringBuilder append(int);",
        "  public java.lang.StringBuilder append(long);",
        "  public java.lang.StringBuilder append(float);",
        "  public java.lang.StringBuilder append(double);",
        "  public java.lang.String toString();",
        "}"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    checkDiagnostic(handler.warnings, proguardConfig, 1, 1,
        "Ignoring", "-assumenoexternalsideeffects");
  }

  @Test
  public void parse_assumenoescapingparameters() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-assumenoescapingparameters class java.lang.System {",
        "  public static void arraycopy(java.lang.Object, int, java.lang.Object, int, int);",
        "}"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    checkDiagnostic(handler.warnings, proguardConfig, 1, 1,
        "Ignoring", "-assumenoescapingparameters");
  }

  @Test
  public void parse_assumenoexternalreturnvalues() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-assumenoexternalreturnvalues class java.lang.StringBuilder {",
        "  public java.lang.StringBuilder append(java.lang.Object);",
        "  public java.lang.StringBuilder append(java.lang.String);",
        "  public java.lang.StringBuilder append(java.lang.StringBuffer);",
        "  public java.lang.StringBuilder append(char[]);",
        "  public java.lang.StringBuilder append(char[], int, int);",
        "  public java.lang.StringBuilder append(boolean);",
        "  public java.lang.StringBuilder append(char);",
        "  public java.lang.StringBuilder append(int);",
        "  public java.lang.StringBuilder append(long);",
        "  public java.lang.StringBuilder append(float);",
        "  public java.lang.StringBuilder append(double);",
        "}"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    checkDiagnostic(handler.warnings, proguardConfig, 1, 1,
        "Ignoring", "-assumenoexternalreturnvalues");
  }

  @Test
  public void parse_android() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-android"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();
  }

  @Test
  public void parse_addconfigurationdebugging() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-addconfigurationdebugging"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    checkDiagnostic(handler.warnings, proguardConfig, 1, 1,
        "Ignoring", "-addconfigurationdebugging");
  }

  @Test
  public void parse_regress74508478() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-keep class A {",
        "  A <fields>;",
        "}"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();
    verifyWithProguard(proguardConfig);
  }

  private ProguardConfiguration parseAndVerifyParserEndsCleanly(List<String> config) {
    parser.parse(createConfigurationForTesting(config));
    verifyParserEndsCleanly();
    return parser.getConfig();
  }

  private void verifyParserEndsCleanly() {
    assertEquals(0, handler.infos.size());
    assertEquals(0, handler.warnings.size());
    assertEquals(0, handler.errors.size());
  }

  private Diagnostic checkDiagnostic(List<Diagnostic> diagnostics, Path path, int lineStart,
      int columnStart, String... messageParts) {
    assertEquals(1, diagnostics.size());
    Diagnostic diagnostic = diagnostics.get(0);
    if (path != null) {
      assertEquals(path, ((PathOrigin) diagnostic.getOrigin()).getPath());
    } else {
      assertSame(Origin.unknown(), diagnostic.getOrigin());
    }
    TextPosition position;
    if (diagnostic.getPosition() instanceof TextRange) {
      position = ((TextRange) diagnostic.getPosition()).getStart();
    } else {
      position = ((TextPosition) diagnostic.getPosition());
    }
    assertEquals(lineStart, position.getLine());
    assertEquals(columnStart, position.getColumn());
    for (String part : messageParts) {
      assertTrue(diagnostic.getDiagnosticMessage() + " doesn't contain \"" + part + "\"",
          diagnostic.getDiagnosticMessage().contains(part));
    }
    return diagnostic;
  }

  private void verifyWithProguard(Path proguardConfig) throws Exception {
    if (isRunProguard()) {
      // Add a keep rule for the test class as Proguard will fail if the resulting output jar is
      // empty
      Class classToKeepForTest = EmptyMainClassForProguardTests.class;
      Path additionalProguardConfig = writeTextToTempFile(
          "-keep class " + classToKeepForTest.getCanonicalName() + " {",
          "  public static void main(java.lang.String[]);",
          "}"
      );
      Path proguardedJar =
          File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
      ToolHelper
          .runProguard(jarTestClasses(ImmutableList.of(classToKeepForTest)),
              proguardedJar, ImmutableList.of(proguardConfig, additionalProguardConfig), null);
      DexInspector proguardInspector = new DexInspector(readJar(proguardedJar));
      assertEquals(1, proguardInspector.allClasses().size());
    }
  }

  private void verifyFailWithProguard6(Path proguardConfig, String expectedMessage)
      throws Exception{
    if (isRunProguard()) {
      // No need for a keep rule for this class, as we are expecting Proguard to fail with the
      // specified message.
      Class classForTest = EmptyMainClassForProguardTests.class;
      Path proguardedJar =
          File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
      ProcessResult result = ToolHelper.runProguard6Raw(
          jarTestClasses(ImmutableList.of(classForTest)), proguardedJar, proguardConfig, null);
      assertNotEquals(0, result.exitCode);
      assertThat(result.stderr, containsString(expectedMessage));
    }
  }
}
