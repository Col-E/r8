// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.DiagnosticsChecker.checkDiagnostics;
import static com.android.tools.r8.shaking.ProguardConfigurationSourceStrings.createConfigurationForTesting;
import static com.android.tools.r8.utils.BooleanUtils.intValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.dontwarn.DontWarnConfiguration;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.shaking.ProguardClassNameList.SingleClassNameList;
import com.android.tools.r8.shaking.ProguardConfigurationParser.IdentifierPatternWithWildcards;
import com.android.tools.r8.shaking.ProguardTypeMatcher.MatchSpecificType;
import com.android.tools.r8.shaking.constructor.InitMatchingTest;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import com.android.tools.r8.utils.KeepingDiagnosticHandler;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class EmptyMainClassForProguardTests {

  public static void main(String[] args) {
  }
}

@RunWith(Parameterized.class)
public class ProguardConfigurationParserTest extends TestBase {

  private static final String VALID_PROGUARD_DIR = ToolHelper.TESTS_DIR + "proguard/valid/";
  private static final String INVALID_PROGUARD_DIR = ToolHelper.TESTS_DIR + "proguard/invalid/";
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
  private static final String ADAPT_KOTLIN_METADATA =
      VALID_PROGUARD_DIR + "adapt-kotlin-metadata.flags";
  private static final String KEEP_KOTLIN_METADATA =
      VALID_PROGUARD_DIR + "keep-kotlin-metadata.flags";
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
  private static final String PARSE_AND_SKIP_SINGLE_ARGUMENT =
      VALID_PROGUARD_DIR + "parse-and-skip-single-argument.flags";
  private static final String PRINT_USAGE =
      VALID_PROGUARD_DIR + "printusage.flags";
  private static final String PRINT_USAGE_TO_FILE =
      VALID_PROGUARD_DIR + "printusage-to-file.flags";
  private static final String TARGET =
      VALID_PROGUARD_DIR + "target.flags";

  private Reporter reporter;
  private KeepingDiagnosticHandler handler;
  private ProguardConfigurationParser parser;
  private List<String> whiteSpace =
      ImmutableList.of("", " ", "   ", "\t", " \t", " \t", " \t ", " \t\t \t ");
  private List<String> lineSeparators = ImmutableList.of("\n", "\r\n");
  private List<Character> quotes = ImmutableList.of('"', '\'');

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ProguardConfigurationParserTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Before
  public void reset() {
    handler = new KeepingDiagnosticHandler();
    reporter = new Reporter(handler);
    parser = new ProguardConfigurationParser(new DexItemFactory(), reporter);
  }

  @Before
  public void resetAllowTestOptions() {
    handler = new KeepingDiagnosticHandler();
    reporter = new Reporter(handler);
    parser =
        new ProguardConfigurationParser(
            new DexItemFactory(),
            reporter,
            ProguardConfigurationParserOptions.builder()
                .setEnableExperimentalCheckEnumUnboxed(false)
                .setEnableExperimentalConvertCheckNotNull(false)
                .setEnableExperimentalWhyAreYouNotInlining(false)
                .setEnableTestingOptions(true)
                .build());
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
  public void parseMultipleNamePatterns() {
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
    assertEquals(ProguardNameMatcher.create(
        IdentifierPatternWithWildcards.withoutWildcards("getContents")), memberRule.getName());
    assertEquals("java.lang.Object[][]", memberRule.getType().toString());
    assertEquals(ProguardMemberType.METHOD, memberRule.getRuleType());
    assertEquals(0, memberRule.getArguments().size());
  }

  @Test
  public void parseNonJavaIdentifiers() {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, new Reporter());
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

  private void testDontXXX(
      String xxx, Function<ProguardConfiguration, Predicate<DexType>> matcherFactory) {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    String configuration = "-dont" + xxx + " !foobar,*bar";
    parser.parse(createConfigurationForTesting(ImmutableList.of(configuration)));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    Predicate<DexType> matcher = matcherFactory.apply(config);
    assertFalse(matcher.test(dexItemFactory.createType("Lboobaz;")));
    assertTrue(matcher.test(dexItemFactory.createType("Lboobar;")));
    assertFalse(matcher.test(dexItemFactory.createType("Lfoobar;")));
  }

  @Test
  public void testDontXXX() {
    testDontXXX("warn", config -> DontWarnConfiguration.create(config)::matches);
    testDontXXX("note", config -> config.getDontNotePatterns()::matches);
  }

  private void testDontXXXMultiple(
      String xxx, Function<ProguardConfiguration, Predicate<DexType>> matcherFactory) {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    List<String> configuration1 = ImmutableList.of("-dont" + xxx + " foo.**, bar.**");
    List<String> configuration2 = ImmutableList.of("-dont" + xxx + " foo.**", "-dontwarn bar.**");
    for (List<String> configuration : ImmutableList.of(configuration1, configuration2)) {
      parser.parse(createConfigurationForTesting(configuration));
      verifyParserEndsCleanly();
      ProguardConfiguration config = parser.getConfig();
      Predicate<DexType> matcher = matcherFactory.apply(config);
      assertTrue(matcher.test(dexItemFactory.createType("Lfoo/Bar;")));
      assertTrue(matcher.test(dexItemFactory.createType("Lfoo/bar7Bar;")));
      assertTrue(matcher.test(dexItemFactory.createType("Lbar/Foo;")));
    }
  }

  @Test
  public void testDontWarnMultiple() {
    testDontXXXMultiple("warn", config -> DontWarnConfiguration.create(config)::matches);
    testDontXXXMultiple("note", config -> config.getDontNotePatterns()::matches);
  }

  private void testDontXXXAllExplicitly(
      String xxx, Function<ProguardConfiguration, Predicate<DexType>> matcherFactory) {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    String dontwarnAll = "-dont" + xxx + " *";
    parser.parse(createConfigurationForTesting(ImmutableList.of(dontwarnAll)));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    Predicate<DexType> matcher = matcherFactory.apply(config);
    assertTrue(matcher.test(dexItemFactory.createType("Lboobaz;")));
    assertTrue(matcher.test(dexItemFactory.createType("Lboobar;")));
    assertTrue(matcher.test(dexItemFactory.createType("Lfoobar;")));
  }

  @Test
  public void testDontWarnAllExplicitly() {
    testDontXXXAllExplicitly("warn", config -> DontWarnConfiguration.create(config)::matches);
    testDontXXXAllExplicitly("note", config -> config.getDontNotePatterns()::matches);
  }

  private void testDontXXXAllImplicitly(
      String xxx, Function<ProguardConfiguration, Predicate<DexType>> matcherFactory) {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    String dontwarnAll = "-dont" + xxx;
    String otherOption = "-keep class *";
    parser.parse(createConfigurationForTesting(ImmutableList.of(dontwarnAll, otherOption)));
    ProguardConfiguration config = parser.getConfig();
    Predicate<DexType> matcher = matcherFactory.apply(config);
    assertTrue(matcher.test(dexItemFactory.createType("Lboobaz;")));
    assertTrue(matcher.test(dexItemFactory.createType("Lboobar;")));
    assertTrue(matcher.test(dexItemFactory.createType("Lfoobar;")));
  }

  @Test
  public void testDontWarnAllImplicitly() {
    testDontXXXAllImplicitly("warn", config -> DontWarnConfiguration.create(config)::matches);
    testDontXXXAllImplicitly("note", config -> config.getDontNotePatterns()::matches);
  }

  @Test
  public void parseAccessFlags() {
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
  public void parseWhyAreYouKeeping() {
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
  public void parseAssumeNoSideEffects() {
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
  public void parseAssumeNoSideEffectsWithReturnValue() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(ASSUME_NO_SIDE_EFFECTS_WITH_RETURN_VALUE));
    verifyParserEndsCleanly();
    List<ProguardConfigurationRule> assumeNoSideEffects = parser.getConfig().getRules();
    assertEquals(1, assumeNoSideEffects.size());
    int matches = 0;
    for (ProguardMemberRule rule : assumeNoSideEffects.get(0).getMemberRules()) {
      if (rule.getName().matches("returnsTrue") || rule.getName().matches("returnsFalse")) {
        assertTrue(rule.hasReturnValue());
        assertTrue(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isNullability());
        assertEquals(rule.getName().matches("returnsTrue"), rule.getReturnValue().getBoolean());
        matches |= 1 << 0;
      } else if (rule.getName().matches("returns1")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isNullability());
        assertEquals(1, rule.getReturnValue().getValueRange().getMin());
        assertEquals(1, rule.getReturnValue().getValueRange().getMax());
        matches |= 1 << 1;
      } else if (rule.getName().matches("returns2To4")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isNullability());
        assertEquals(2, rule.getReturnValue().getValueRange().getMin());
        assertEquals(4, rule.getReturnValue().getValueRange().getMax());
        matches |= 1 << 2;
      } else if (rule.getName().matches("returns234To567")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isNullability());
        assertEquals(234, rule.getReturnValue().getValueRange().getMin());
        assertEquals(567, rule.getReturnValue().getValueRange().getMax());
        matches |= 1 << 3;
      } else if (rule.getName().matches("returnsField")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertTrue(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isNullability());
        assertEquals("com.google.C", rule.getReturnValue().getFieldHolder().getTypeName());
        assertEquals("X", rule.getReturnValue().getFieldName().toString());
        matches |= 1 << 4;
      } else if (rule.getName().matches("returnsNull")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertTrue(rule.getReturnValue().isNullability());
        assertTrue(rule.getReturnValue().getNullability().isDefinitelyNull());
        matches |= 1 << 5;
      } else if (rule.getName().matches("returnsNonNull")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertTrue(rule.getReturnValue().isNullability());
        assertTrue(rule.getReturnValue().getNullability().isDefinitelyNotNull());
        matches |= 1 << 6;
      } else if (rule.getName().matches("returnsNonNullField")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertTrue(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isNullability());
        assertEquals("com.google.C", rule.getReturnValue().getFieldHolder().getTypeName());
        assertEquals("X", rule.getReturnValue().getFieldName().toString());
        matches |= 1 << 7;
      } else {
        fail("Unexpected");
      }
    }
    assertEquals((1 << 8) - 1, matches);
  }

  @Test
  public void parseAssumeValuesWithReturnValue() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(ASSUME_VALUES_WITH_RETURN_VALUE));
    verifyParserEndsCleanly();
    List<ProguardConfigurationRule> assumeValues = parser.getConfig().getRules();
    assertEquals(1, assumeValues.size());
    int matches = 0;
    for (ProguardMemberRule rule : assumeValues.get(0).getMemberRules()) {
      if (rule.getName().matches("isTrue") || rule.getName().matches("isFalse")) {
        assertTrue(rule.hasReturnValue());
        assertTrue(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isNullability());
        assertEquals(rule.getName().matches("isTrue"), rule.getReturnValue().getBoolean());
        matches |= 1 << 0;
      } else if (rule.getName().matches("is1")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isNullability());
        assertEquals(1, rule.getReturnValue().getValueRange().getMin());
        assertEquals(1, rule.getReturnValue().getValueRange().getMax());
        matches |= 1 << 1;
      } else if (rule.getName().matches("is2To4")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isNullability());
        assertEquals(2, rule.getReturnValue().getValueRange().getMin());
        assertEquals(4, rule.getReturnValue().getValueRange().getMax());
        matches |= 1 << 2;
      } else if (rule.getName().matches("is234To567")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isNullability());
        assertEquals(234, rule.getReturnValue().getValueRange().getMin());
        assertEquals(567, rule.getReturnValue().getValueRange().getMax());
        matches |= 1 << 3;
      } else if (rule.getName().matches("isField")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertTrue(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isNullability());
        assertTrue(rule.getReturnValue().getNullability().isMaybeNull());
        assertEquals("com.google.C", rule.getReturnValue().getFieldHolder().getTypeName());
        assertEquals("X", rule.getReturnValue().getFieldName().toString());
        matches |= 1 << 4;
      } else if (rule.getName().matches("isNull")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertTrue(rule.getReturnValue().isNullability());
        assertTrue(rule.getReturnValue().getNullability().isDefinitelyNull());
        matches |= 1 << 5;
      } else if (rule.getName().matches("returnsNonNull")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertTrue(rule.getReturnValue().isNullability());
        assertTrue(rule.getReturnValue().getNullability().isDefinitelyNotNull());
        matches |= 1 << 6;
      } else if (rule.getName().matches("returnsNonNullField")) {
        assertTrue(rule.hasReturnValue());
        assertFalse(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertTrue(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isNullability());
        assertTrue(rule.getReturnValue().getNullability().isDefinitelyNotNull());
        assertEquals("com.google.C", rule.getReturnValue().getFieldHolder().getTypeName());
        assertEquals("X", rule.getReturnValue().getFieldName().toString());
        matches |= 1 << 7;
      } else {
        fail("Unexpected");
      }
    }
    assertEquals((1 << 8) - 1, matches);
  }

  @Test
  public void testAdaptClassStrings() {
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
  public void testAdaptClassStringsMultiple() {
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
  public void testAdaptClassStringsAllExplicitly() {
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
  public void testAdaptClassStringsAllImplicitly() {
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
  public void testIdentifierNameString() {
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
    identifierNameStrings
        .get(2)
        .getMemberRules()
        .forEach(
            memberRule -> {
              assertEquals(ProguardMemberType.ALL, memberRule.getRuleType());
              assertEquals(1, memberRule.getAnnotations().size());
              assertTrue(
                  ListUtils.first(memberRule.getAnnotations())
                      .toString()
                      .endsWith("IdentifierNameString"));
            });
  }

  @Test
  public void testConvertCheckNotNullWithReturn() {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(
            dexItemFactory,
            reporter,
            ProguardConfigurationParserOptions.builder()
                .setEnableExperimentalCheckEnumUnboxed(false)
                .setEnableExperimentalConvertCheckNotNull(true)
                .setEnableExperimentalWhyAreYouNotInlining(false)
                .setEnableTestingOptions(false)
                .build());
    String rule = "-convertchecknotnull class C { ** m(**, ...); }";
    parser.parse(createConfigurationForTesting(ImmutableList.of(rule)));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals(1, config.getRules().size());
    assertTrue(config.getRules().get(0) instanceof ConvertCheckNotNullRule);
  }

  @Test
  public void testConvertCheckNotNullWithoutReturn() {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(
            dexItemFactory,
            reporter,
            ProguardConfigurationParserOptions.builder()
                .setEnableExperimentalCheckEnumUnboxed(false)
                .setEnableExperimentalConvertCheckNotNull(true)
                .setEnableExperimentalWhyAreYouNotInlining(false)
                .setEnableTestingOptions(false)
                .build());
    String rule = "-convertchecknotnull class C { void m(**, ...); }";
    parser.parse(createConfigurationForTesting(ImmutableList.of(rule)));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals(1, config.getRules().size());
    assertTrue(config.getRules().get(0) instanceof ConvertCheckNotNullRule);
  }

  @Test
  public void parseDontobfuscate() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_OBFUSCATE));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertFalse(config.isObfuscating());
  }

  @Test
  public void parseRepackageClassesEmpty() {
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
  public void parseRepackageClassesNonEmpty() {
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
  public void parseFlattenPackageHierarchyEmpty() {
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
  public void parseFlattenPackageHierarchyNonEmpty() {
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
  public void flattenPackageHierarchyCannotOverrideRepackageClasses() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path path = Paths.get(PACKAGE_OBFUSCATION_5);
    parser.parse(path);
    checkDiagnostics(handler.warnings, path, 6, 1,
        "repackageclasses", "overrides", "flattenpackagehierarchy");
    ProguardConfiguration config = parser.getConfig();
    assertEquals(PackageObfuscationMode.REPACKAGE, config.getPackageObfuscationMode());
    assertNotNull(config.getPackagePrefix());
    assertEquals("top", config.getPackagePrefix());
  }

  @Test
  public void repackageClassesOverridesFlattenPackageHierarchy() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path path = Paths.get(PACKAGE_OBFUSCATION_6);
    parser.parse(path);
    checkDiagnostics(handler.warnings, path, 6, 1,
        "repackageclasses", "overrides", "flattenpackagehierarchy");
    ProguardConfiguration config = parser.getConfig();
    assertEquals(PackageObfuscationMode.REPACKAGE, config.getPackageObfuscationMode());
    assertNotNull(config.getPackagePrefix());
    assertEquals("top", config.getPackagePrefix());
  }

  @Test
  public void parseApplyMapping() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(APPLY_MAPPING));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.hasApplyMappingFile());
  }

  @Test
  public void parseApplyMappingWithoutFile() {
    Path path = Paths.get(APPLY_MAPPING_WITHOUT_FILE);
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(path);
      fail("Expect to fail due to the lack of file name.");
    } catch (RuntimeException e) {
      checkDiagnostics(handler.errors, path, 6, 14, "File name expected");
    }
  }

  @Test
  public void parseAdaptKotlinMetadata() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path path = Paths.get(ADAPT_KOTLIN_METADATA);
    parser.parse(path);
    verifyParserEndsCleanly();
  }

  @Test
  public void parseKeepKotlinMetadata() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path path = Paths.get(KEEP_KOTLIN_METADATA);
    parser.parse(path);
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals(
        "-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations",
        config.getKeepAttributes().toString());
    assertEquals(
        StringUtils.joinLines("-keep class kotlin.Metadata {", "  *;", "}"),
        config.getRules().get(0).toString());
  }

  @Test
  public void parseIncluding() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(INCLUDING));
    verifyParserEndsCleanly();
  }

  @Test
  public void parseInvalidIncluding1() {
    Path path = Paths.get(INVALID_INCLUDING_1);
    try {
      new ProguardConfigurationParser(new DexItemFactory(), reporter)
          .parse(path);
      fail();
    } catch (RuntimeException e) {
      checkDiagnostics(handler.errors, path, 6, 10,"does-not-exist.flags");
    }
  }

  @Test
  public void parseInvalidIncluding2() {
    Path path = Paths.get(INVALID_INCLUDING_2);
    try {
      new ProguardConfigurationParser(new DexItemFactory(), reporter)
          .parse(path);
      fail();
    } catch (RuntimeException e) {
      checkDiagnostics(handler.errors, path, 6,2, "does-not-exist.flags");
    }
  }

  @Test
  public void parseLibraryJars() {
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
  public void parseInvalidFilePattern() {
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(createConfigurationForTesting(
          Collections.singletonList("-injars abc.jar(*.zip;*.class)")));
      fail();
    } catch (RuntimeException e) {
      assertEquals(1, handler.errors.size());
    }
  }

  @Test
  public void parseKeepModifiers() {
    for (String before : whiteSpace) {
      for (String after : whiteSpace) {
        reset();
        parseAndVerifyParserEndsCleanly(
            ImmutableList.of(
                "-keep"
                    + before
                    + ","
                    + after
                    + "includedescriptorclasses"
                    + before
                    + ","
                    + after
                    + "allowaccessmodification"
                    + before
                    + ","
                    + after
                    + "allowshrinking"
                    + before
                    + ","
                    + after
                    + "allowobfuscation"
                    + before
                    + ","
                    + after
                    + "allowoptimization "
                    + "class A { *; }"));
      }
    }
  }

  @Test
  public void parseKeepAnnotation() {
    for (String space : whiteSpace) {
      reset();
      parseAndVerifyParserEndsCleanly(ImmutableList.of(
          "-keep @" + space + "interface A"
      ));
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
  public void parseSeeds() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(SEEDS));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintSeeds());
    assertNull(config.getSeedFile());
  }

  @Test
  public void parseSeeds2() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(SEEDS_2));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintSeeds());
    assertNotNull(config.getSeedFile());
  }

  @Test
  public void parseVerbose() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(VERBOSE));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isVerbose());
  }

  @Test
  public void parseKeepdirectories() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(KEEPDIRECTORIES));
    verifyParserEndsCleanly();
  }

  @Test
  public void parseDontshrink() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_SHRINK));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertFalse(config.isShrinking());
  }

  @Test
  public void parseDontSkipNonPublicLibraryClasses() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_SKIP_NON_PUBLIC_LIBRARY_CLASSES));
    verifyParserEndsCleanly();
  }

  @Test
  public void parseDontskipnonpubliclibraryclassmembers() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_SKIP_NON_PUBLIC_LIBRARY_CLASS_MEMBERS));
    verifyParserEndsCleanly();
  }

  @Test
  public void parseIdentifiernamestring() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path source = Paths.get(IDENTIFIER_NAME_STRING);
    parser.parse(source);
    verifyParserEndsCleanly();
  }

  @Test
  public void parseOverloadAggressively() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(OVERLOAD_AGGRESIVELY));
    verifyParserEndsCleanly();
  }

  @Test
  public void parseDontOptimize() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_OPTIMIZE));
    ProguardConfiguration config = parser.getConfig();
    verifyParserEndsCleanly();
    assertFalse(config.isOptimizing());
  }

  @Test
  public void parseDontOptimizeOverridesPasses() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path path = Paths.get(DONT_OPTIMIZE_OVERRIDES_PASSES);
    parser.parse(path);
    checkDiagnostics(handler.infos, path, 7, 1, "Ignoring", "-optimizationpasses");
    ProguardConfiguration config = parser.getConfig();
    assertFalse(config.isOptimizing());
  }

  @Test
  public void parseOptimizationPasses() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path path = Paths.get(OPTIMIZATION_PASSES);
    parser.parse(path);
    checkDiagnostics(handler.infos, path, 5, 1, "Ignoring", "-optimizationpasses");
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isOptimizing());
  }

  @Test
  public void parseOptimizationPassesError() {
    Path path = Paths.get(OPTIMIZATION_PASSES_WITHOUT_N);
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(path);
      fail();
    } catch (AbortException e) {
      checkDiagnostics(handler.errors, path, 6, 1, "Missing n");
    }
  }

  @Test
  public void parseSkipNonPublicLibraryClasses() {
    testUnsupportedOption("-skipnonpubliclibraryclasses");
  }

  private void testUnsupportedOption(String option) {
    try {
      reset();
      parser.parse(createConfigurationForTesting(ImmutableList.of(option)));
      fail("Expect to fail due to unsupported option.");
    } catch (RuntimeException e) {
      checkDiagnostics(handler.errors, null, 1, 1, "Unsupported option", option);
    }
  }

  @Test
  public void parseAndskipSingleArgument() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PARSE_AND_SKIP_SINGLE_ARGUMENT));
    verifyParserEndsCleanly();
  }

  @Test
  public void parse_printconfiguration_noArguments() {
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
  public void parse_printconfiguration_argument() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(createConfigurationForTesting(ImmutableList.of(
        "-printconfiguration file_name"
    )));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintConfiguration());
    assertEquals("file_name", config.getPrintConfigurationFile().toString());
  }

  @Test
  public void parse_optimizations() throws Exception {
    for (String option :
        ImmutableList.of(
            "-optimizations",
            "-optimizations xxx",
            "-optimizations \"xxx\"",
            "-optimizations 'xxx'",
            "-optimizations     xxx",
            "-optimizations \"    xxx\"",
            "-optimizations '    xxx'",
            "-optimizations xxx/yyy",
            "-optimizations \"xxx/yyy\"",
            "-optimizations 'xxx/yyy'",
            "-optimizations     xxx/yyy",
            "-optimizations \"    xxx/yyy\"",
            "-optimizations '    xxx/yyy'",
            "-optimizations xxx/yyy,zzz*",
            "-optimizations \"xxx/yyy\",\"zzz*\"",
            "-optimizations 'xxx/yyy','zzz*'",
            "-optimizations xxx/yyy  ,  zzz*",
            "-optimizations \"xxx/yyy  \",\"  zzz*\"",
            "-optimizations 'xxx/yyy  ','  zzz*'",
            "-optimizations !xxx",
            "-optimizations \"!xxx\"",
            "-optimizations '!xxx'",
            "-optimizations   !  xxx",
            "-optimizations \"  !  xxx\"",
            "-optimizations '  !  xxx'",
            "-optimizations !xxx,!yyy",
            "-optimizations \"!xxx\",\"!yyy\"",
            "-optimizations '!xxx','!yyy'",
            "-optimizations   !  xxx,  !  yyy",
            "-optimizations \"  !  xxx\", \" !  yyy\"",
            "-optimizations '  !  xxx', ' !  yyy'",
            "-optimizations !code/simplification/advanced,code/simplification/*",
            "-optimizations \"!code/simplification/advanced\",\"code/simplification/*\"",
            "-optimizations '!code/simplification/advanced','code/simplification/*'")) {
      reset();
      Path proguardConfig = writeTextToTempFile(option);
      parser.parse(proguardConfig);
      assertEquals(1, handler.infos.size());
      checkDiagnostics(handler.infos, proguardConfig, 1, 1, "Ignoring", "-optimizations");
    }
  }

  @Test
  public void parse_optimizationpasses() throws Exception {
    for (String lineSeparator : lineSeparators) {
      reset();
      Path proguardConfig = writeTextToTempFile(lineSeparator,
          ImmutableList.of(
              "-optimizations xxx",
              "-optimizationpasses 5",
              "-optimizations yyy"));
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      assertEquals(3, handler.infos.size());

      checkDiagnostics(handler.infos, 0, proguardConfig, 1, 1, "Ignoring", "-optimizations");
      Position p1 = handler.infos.get(0).getPosition();
      assertTrue(p1 instanceof TextRange);
      TextRange r1 = (TextRange) p1;
      assertEquals(1, r1.getStart().getLine());
      assertEquals(1, r1.getStart().getColumn());
      assertEquals(1, r1.getEnd().getLine());
      assertEquals(15, r1.getEnd().getColumn());

      checkDiagnostics(
          handler.infos, 1, proguardConfig, 2, 1, "Ignoring", "-optimizationpasses");
      Position p2 = handler.infos.get(1).getPosition();
      assertTrue(p2 instanceof TextRange);
      TextRange r2 = (TextRange) p2;
      assertEquals(2, r2.getStart().getLine());
      assertEquals(1, r2.getStart().getColumn());
      assertEquals(2, r2.getEnd().getLine());
      assertEquals(22, r2.getEnd().getColumn());

      checkDiagnostics(handler.infos, 2, proguardConfig, 3, 1, "Ignoring", "-optimizations");
      Position p3 = handler.infos.get(2).getPosition();
      assertTrue(p3 instanceof TextRange);
      TextRange r3 = (TextRange) p3;
      assertEquals(3, r3.getStart().getLine());
      assertEquals(1, r3.getStart().getColumn());
      assertEquals(3, r3.getEnd().getLine());
      assertEquals(15, r3.getEnd().getColumn());
    }
  }

  @Test
  public void parsePrintUsage() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PRINT_USAGE));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintUsage());
    assertNull(config.getPrintUsageFile());
  }

  @Test
  public void parsePrintUsageToFile() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PRINT_USAGE_TO_FILE));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintUsage());
    assertNotNull(config.getPrintUsageFile());
  }

  @Test
  public void parseTarget() {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(TARGET));
    verifyParserEndsCleanly();
  }

  @Test
  public void parseInvalidKeepOption() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-keepx public class * {       ",
        "  native <methods>;           ",
        "}                             "
    );
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      fail();
    } catch (AbortException e) {
      checkDiagnostics(handler.errors, proguardConfig, 1, 1,
          "Unknown option", "-keepx");
    }
  }

  @Test
  public void parseKeepOptionEOF() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        System.lineSeparator(), ImmutableList.of("-keep"), false);
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      fail();
    } catch (AbortException e) {
      checkDiagnostics(handler.errors, proguardConfig, 1, 6,
          "Expected [!]interface|@interface|class|enum");
    }
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
      checkDiagnostics(handler.errors, proguardConfig, 1, 1,
          "Unknown option", "-keepclassx");
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
    for (String lineSeparator : lineSeparators) {
      reset();
      Path proguardConfig = writeTextToTempFile(lineSeparator,
          ImmutableList.of(
              "-renamesourcefileattribute PG",
              "-keepattributes SourceFile,SourceDir"));
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      verifyParserEndsCleanly();
      ProguardConfiguration config = parser.getConfigRawForTesting();
      assertEquals("PG", config.getRenameSourceFileAttribute());
      assertTrue(config.getKeepAttributes().sourceFile);
      assertTrue(config.getKeepAttributes().sourceDir);
    }
  }

  @Test
  public void testRenameSourceFileAttribute_empty() throws Exception {
    for (String lineSeparator : lineSeparators) {
      reset();
      Path proguardConfig = writeTextToTempFile(lineSeparator,
          ImmutableList.of(
              "-renamesourcefileattribute",
              "-keepattributes SourceFile"));
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      verifyParserEndsCleanly();
      ProguardConfiguration config = parser.getConfigRawForTesting();
      assertEquals("", config.getRenameSourceFileAttribute());
      assertTrue(config.getKeepAttributes().sourceFile);
      assertFalse(config.getKeepAttributes().sourceDir);
    }
  }

  @Test
  public void testRenameSourceFileAttribute_qouted() throws Exception {
    for (String lineSeparator : lineSeparators) {
      for (Character quote : quotes) {
        reset();
        Path proguardConfig = writeTextToTempFile(lineSeparator,
            ImmutableList.of(
                "-renamesourcefileattribute " + quote + "PG" + quote,
                "-keepattributes SourceFile"));
        ProguardConfigurationParser parser =
            new ProguardConfigurationParser(new DexItemFactory(), reporter);
        parser.parse(proguardConfig);
        verifyParserEndsCleanly();
        ProguardConfiguration config = parser.getConfigRawForTesting();
        assertEquals("PG", config.getRenameSourceFileAttribute());
        assertTrue(config.getKeepAttributes().sourceFile);
        assertFalse(config.getKeepAttributes().sourceDir);
      }
    }
  }

  @Test
  public void testRenameSourceFileAttribute_qouted_empty() throws Exception {
    for (String lineSeparator : lineSeparators) {
      for (Character quote : quotes) {
        reset();
        Path proguardConfig = writeTextToTempFile(lineSeparator,
            ImmutableList.of(
                "-renamesourcefileattribute " + quote + quote,
                "-keepattributes SourceFile"));
        ProguardConfigurationParser parser =
            new ProguardConfigurationParser(new DexItemFactory(), reporter);
        parser.parse(proguardConfig);
        verifyParserEndsCleanly();
        ProguardConfiguration config = parser.getConfigRawForTesting();
        assertEquals("", config.getRenameSourceFileAttribute());
        assertTrue(config.getKeepAttributes().sourceFile);
        assertFalse(config.getKeepAttributes().sourceDir);
      }
    }
  }

  private void testKeepattributes(List<String> expected, String config) {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(createConfigurationForTesting(ImmutableList.of(config)));
    verifyParserEndsCleanly();
    assertEquals(
        ProguardKeepAttributes.fromPatterns(expected),
        parser.getConfigRawForTesting().getKeepAttributes());
  }

  @Test
  public void parseKeepattributes() {
    List<String> xxxYYY = ImmutableList.of("xxx", "yyy");
    testKeepattributes(xxxYYY, "-keepattributes xxx,yyy");
    testKeepattributes(xxxYYY, "-keepattributes \"xxx\",\"yyy\"");
    testKeepattributes(xxxYYY, "-keepattributes 'xxx','yyy'");
    testKeepattributes(xxxYYY, "-keepattributes xxx, yyy");
    testKeepattributes(xxxYYY, "-keepattributes \"xxx\", \"yyy\"");
    testKeepattributes(xxxYYY, "-keepattributes 'xxx', 'yyy'");
    testKeepattributes(xxxYYY, "-keepattributes xxx ,yyy");
    testKeepattributes(xxxYYY, "-keepattributes xxx   ,   yyy");
    testKeepattributes(xxxYYY, "-keepattributes \"xxx\"   ,   \"yyy\"");
    testKeepattributes(xxxYYY, "-keepattributes 'xxx'   ,   'yyy'");
    testKeepattributes(xxxYYY, "-keepattributes       xxx   ,   yyy     ");
    testKeepattributes(xxxYYY, "-keepattributes       \"xxx\"   ,   \"yyy\"     ");
    testKeepattributes(xxxYYY, "-keepattributes       'xxx'   ,   'yyy'     ");
    testKeepattributes(xxxYYY, "-keepattributes       xxx   ,   yyy     \n");
    testKeepattributes(xxxYYY, "-keepattributes       \"xxx\"   ,   \"yyy\"     \n");
    testKeepattributes(xxxYYY, "-keepattributes       'xxx'   ,   'yyy'     \n");
    String config =
        "-keepattributes Exceptions,InnerClasses,Signature,Deprecated,\n"
            + "          SourceFile,LineNumberTable,*Annotation*,EnclosingMethod\n";
    List<String> expected = ImmutableList.of(
        "Exceptions", "InnerClasses", "Signature", "Deprecated",
        "SourceFile", "LineNumberTable", "*Annotation*", "EnclosingMethod");
    testKeepattributes(expected, config);
    config =
        "-keepattributes \"Exceptions\",\"InnerClasses\",\"Signature\",\"Deprecated\",\n"
            + "          \"SourceFile\",\"LineNumberTable\",\"*Annotation*\",\"EnclosingMethod\"\n";
    testKeepattributes(expected, config);
    config =
        "-keepattributes 'Exceptions','InnerClasses','Signature','Deprecated',\n"
            + "          'SourceFile','LineNumberTable','*Annotation*','EnclosingMethod'\n";
    testKeepattributes(expected, config);
  }

  private void testKeeppackagenames(String config) {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(createConfigurationForTesting(ImmutableList.of(config)));
    verifyParserEndsCleanly();
  }

  @Test
  public void parseKeeppackagenames() {
    testKeeppackagenames("-keeppackagenames xxx,yyy");
    testKeeppackagenames("-keeppackagenames xxx, yyy");
    testKeeppackagenames("-keeppackagenames xxx ,yyy");
    testKeeppackagenames("-keeppackagenames xxx   ,   yyy");
    testKeeppackagenames("-keeppackagenames       xxx   ,   yyy     ");
    testKeeppackagenames("-keeppackagenames       xxx   ,   yyy     \n");
    testKeeppackagenames("-keeppackagenames \"xxx\",\"yyy\"");

    testKeeppackagenames(
        "-keeppackagenames com.**, org.*");

    testKeeppackagenames(
        "-keeppackagenames c?m.**, ?r?.*");

    testKeeppackagenames(
        "-keeppackagenames !c?m.**, !?r?.*");
  }

  @Test
  public void parseInvalidKeepattributes_brokenList() {
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(createConfigurationForTesting(ImmutableList.of("-keepattributes xxx,")));
      fail();
    } catch (RuntimeException e) {
      assertTrue(
          handler.errors.get(0).getDiagnosticMessage().contains("Expected list element at "));
    }
  }

  @Test
  public void parseUseUniqueClassMemberNames() throws IOException {
    Path proguardConfig = writeTextToTempFile("-useuniqueclassmembernames");
    new ProguardConfigurationParser(new DexItemFactory(), reporter).parse(proguardConfig);
    checkDiagnostics(
        handler.warnings, proguardConfig, 1, 1, "Ignoring", "-useuniqueclassmembernames");
  }

  @Test
  public void parseKeepParameterNames() {
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
  public void parseKeepParameterNamesWithoutMinification() {
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
  public void parseShortLine() {
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
  public void parseNoLocals() {
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
    checkFileFilterMatchAnything(config.getAdaptResourceFileContents());
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
    checkFileFilterMatchAnything(config.getAdaptResourceFileContents());
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
    checkFileFilterMatchAnything(config.getAdaptResourceFileContents());
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
    checkFileFilterSingle(config.getAdaptResourceFileContents());
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
    checkFileFilterMultiple(config.getAdaptResourceFileContents());
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
      } catch (RuntimeException e) {
        checkDiagnostics(handler.errors, null, 1, option.length() + 2, "Path filter expected");
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
      } catch (RuntimeException e) {
        checkDiagnostics(handler.errors, null, 1, option.length() + 6, "Path filter expected");
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
      } catch (RuntimeException e) {
        checkDiagnostics(handler.errors, null, 1, option.length() + 6, "Path filter expected");
      }
    }
  }

  @Test
  public void parse_testInlineOptions() {
    List<String> options = ImmutableList.of("-neverinline");
    for (String option : options) {
      try {
        reset();
        parser.parse(createConfigurationForTesting(ImmutableList.of(option + " class A { *; }")));
        fail("Expect to fail due to testing option being turned off.");
      } catch (AbortException e) {
        assertEquals(1, handler.errors.size());
        checkDiagnostics(handler.errors, 0, null, 1, 1, "Unknown option \"" + option + "\"");
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

    // Test <2> literally refers to the second wildcard in the rule.
    Iterator<ProguardWildcard> it1 = if0.getClassNames().getWildcards().iterator();
    it1.next();
    ProguardWildcard secondWildcardInIf = it1.next();
    assertTrue(secondWildcardInIf.isPattern());
    Iterator<ProguardWildcard> it2 = if0.subsequentRule.getWildcards().iterator();
    it2.next();
    ProguardWildcard backReference = it2.next();
    assertTrue(backReference.isBackReference());
    assertSame(secondWildcardInIf.asPattern(), backReference.asBackReference().reference);

    verifyWithProguard6(proguardConfig);
  }

  @Test
  public void parse_if_fieldType() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **.R*",
        "-keep class **.D<2> {",
        "  <1>.F<2> fld;",
        "}"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals(1, config.getRules().size());
    ProguardIfRule if0 = (ProguardIfRule) config.getRules().get(0);
    assertEquals("**.R*", if0.getClassNames().toString());
    assertEquals(ProguardKeepRuleType.KEEP, if0.subsequentRule.getType());
    assertEquals("**.D<2>", if0.subsequentRule.getClassNames().toString());
    assertEquals(1, if0.subsequentRule.getMemberRules().size());
    ProguardMemberRule fieldRule = if0.subsequentRule.getMemberRules().get(0);
    assertEquals("<1>.F<2>", fieldRule.getType().toString());

    verifyWithProguard6(proguardConfig);
  }

  @Test
  public void parse_if_fieldName() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **.R*",
        "-keep class **.D<2> {",
        "  java.lang.String fld<2>;",
        "}"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals(1, config.getRules().size());
    ProguardIfRule if0 = (ProguardIfRule) config.getRules().get(0);
    assertEquals("**.R*", if0.getClassNames().toString());
    assertEquals(ProguardKeepRuleType.KEEP, if0.subsequentRule.getType());
    assertEquals("**.D<2>", if0.subsequentRule.getClassNames().toString());
    assertEquals(1, if0.subsequentRule.getMemberRules().size());
    ProguardMemberRule fieldRule = if0.subsequentRule.getMemberRules().get(0);
    assertEquals("fld<2>", fieldRule.getName().toString());

    verifyWithProguard6(proguardConfig);
  }

  @Test
  public void parse_if_returnType() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **.R*",
        "-keep class **.D<2> {",
        "  <1>.M<2> mtd(...);",
        "}"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals(1, config.getRules().size());
    ProguardIfRule if0 = (ProguardIfRule) config.getRules().get(0);
    assertEquals("**.R*", if0.getClassNames().toString());
    assertEquals(ProguardKeepRuleType.KEEP, if0.subsequentRule.getType());
    assertEquals("**.D<2>", if0.subsequentRule.getClassNames().toString());
    assertEquals(1, if0.subsequentRule.getMemberRules().size());
    ProguardMemberRule methodRule = if0.subsequentRule.getMemberRules().get(0);
    assertEquals("<1>.M<2>", methodRule.getType().toString());

    verifyWithProguard6(proguardConfig);
  }

  @Test
  public void parse_if_init() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **.R* {",
        "  void *(...);",
        "}",
        "-keep class **.D<2> {",
        "  <3>(...);",
        "}"
    );
    try {
      parser.parse(proguardConfig);
      fail("Expect to fail due to unsupported constructor name pattern.");
    } catch (RuntimeException e) {
      checkDiagnostics(
          handler.errors, proguardConfig, 5, 3, "Unexpected character", "method name");
    }

    verifyFailWithProguard6(proguardConfig, "Expecting type and name instead of just '<3>'");
  }

  @Test
  public void parse_if_methodName_void() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **.R* {",
        "  void *(...);",
        "}",
        "-keep class **.D<2> {",
        "  void <3>_delegate(...);",
        "}"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals(1, config.getRules().size());
    ProguardIfRule if0 = (ProguardIfRule) config.getRules().get(0);
    assertEquals("**.R*", if0.getClassNames().toString());
    assertEquals(ProguardKeepRuleType.KEEP, if0.subsequentRule.getType());
    assertEquals("**.D<2>", if0.subsequentRule.getClassNames().toString());
    assertEquals(1, if0.subsequentRule.getMemberRules().size());
    ProguardMemberRule methodRule = if0.subsequentRule.getMemberRules().get(0);
    assertEquals("<3>_delegate", methodRule.getName().toString());

    verifyWithProguard6(proguardConfig);
  }

  @Test
  public void parse_if_methodName_class() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **.R* {",
        "  ** *(...);",
        "}",
        "-keep class **.D<2> {",
        "  <3> <4>(...);",
        "}"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertEquals(1, config.getRules().size());
    ProguardIfRule if0 = (ProguardIfRule) config.getRules().get(0);
    assertEquals("**.R*", if0.getClassNames().toString());
    assertEquals(ProguardKeepRuleType.KEEP, if0.subsequentRule.getType());
    assertEquals("**.D<2>", if0.subsequentRule.getClassNames().toString());
    assertEquals(1, if0.subsequentRule.getMemberRules().size());
    ProguardMemberRule methodRule = if0.subsequentRule.getMemberRules().get(0);
    assertEquals("<3>", methodRule.getType().toString());
    assertEquals("<4>", methodRule.getName().toString());

    verifyWithProguard6(proguardConfig);
  }

  @Test
  public void parse_if_nthWildcard_notNumber_literalN() throws Exception {
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
      checkDiagnostics(handler.errors, proguardConfig, 2, 13,
          "Use of generics not allowed for java type");
    }
    verifyFailWithProguard6(proguardConfig, "Use of generics not allowed for java type");
  }

  @Test
  public void parse_if_nthWildcard_notNumber_asterisk_inClassName() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **$R**",
        "-keep class **D<*>"
    );
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      fail();
    } catch (AbortException e) {
      checkDiagnostics(handler.errors, proguardConfig, 2, 13,
          "Use of generics not allowed for java type");
    }
    verifyFailWithProguard6(proguardConfig, "Use of generics not allowed for java type");
  }

  @Test
  public void parse_if_nthWildcard_notNumber_asterisk_inMemberName() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **$R**",
        "-keep class **D<2> {",
        "  int id<*>;",
        "}"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();

    verifyWithProguard6(proguardConfig);
  }

  @Test
  public void parse_if_nestedAngularBrackets_inMemberName() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **$R**",
        "-keep class **D<2> {",
        "  int id<<*>>;",
        "}"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    checkDiagnostics(handler.warnings, proguardConfig, 3, 7, "The field name \"id<<*>>\" is");

    verifyWithProguard6(proguardConfig);
  }

  @Test
  public void parse_if_nestedAngularBrackets_outOfRange() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **$R**",
        "-keep class **D<2> {",
        "  int id<<4>>;",  // There are 3 previous referable wildcards in this rule.
        "}"
    );
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      fail();
    } catch (AbortException e) {
      checkDiagnostics(handler.errors, proguardConfig, 4, 2,
          "Wildcard", "<4>", "invalid");
    }
    verifyFailWithProguard6(proguardConfig, "Invalid reference to wildcard (4,");
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
      checkDiagnostics(handler.errors, proguardConfig, 2, 13,
          "Wildcard", "<0>", "invalid");
    }
    verifyFailWithProguard6(proguardConfig, "Invalid reference to wildcard (0,");
  }

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
      checkDiagnostics(handler.errors, proguardConfig, 3, 1,
          "Wildcard", "<4>", "invalid");
    }
    verifyFailWithProguard6(proguardConfig, "Invalid reference to wildcard (4,");
  }

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
      checkDiagnostics(handler.errors, proguardConfig, 3, 1,
          "Wildcard", "<2>", "invalid");
    }
    verifyFailWithProguard6(proguardConfig, "Invalid reference to wildcard (2,");
  }

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
      checkDiagnostics(handler.errors, proguardConfig, 6, 2,
          "Wildcard", "<3>", "invalid");
    }
    verifyFailWithProguard6(proguardConfig, "Invalid reference to wildcard (3,");
  }

  @Test
  public void parse_if_nthWildcard_not_referable_after_backreference() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-if class **.*User {",
        "  @<1>.*<2> <methods>;", // As back reference starts, * in the middle is not referable.
        "}",
        "-keep @interface <1>.<3><2>"
    );
    try {
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(proguardConfig);
      fail();
    } catch (AbortException e) {
      checkDiagnostics(handler.errors, proguardConfig, 5, 1,
          "Wildcard", "<3>", "invalid");
    }
    verifyFailWithProguard6(
        proguardConfig, "Use of generics not allowed for java type at '<1>.<3><2>'");
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
      checkDiagnostics(handler.errors, proguardConfig, 1, 1,
          "Expecting", "'-keep'", "after", "'-if'");
    }
    verifyFailWithProguard6(proguardConfig, "Expecting '-keep' option after '-if' option");
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
      checkDiagnostics(handler.errors, proguardConfig, 1, 1,
          "Expecting", "'-keep'", "after", "'-if'");
    }
    verifyFailWithProguard6(proguardConfig, "Expecting '-keep' option after '-if' option");
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
    checkDiagnostics(handler.warnings, proguardConfig, 1, 1,
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
    checkDiagnostics(handler.warnings, proguardConfig, 1, 1,
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
    checkDiagnostics(handler.warnings, proguardConfig, 1, 1,
        "Ignoring", "-assumenoexternalreturnvalues");
  }

  @Test
  public void parse_dump_withoutFile() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-dump"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    checkDiagnostics(handler.warnings, proguardConfig, 1, 1,
        "Ignoring", "-dump");
  }

  @Test
  public void parse_dump_withFile() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-dump class_files.txt"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    checkDiagnostics(handler.warnings, proguardConfig, 1, 1,
        "Ignoring", "-dump");
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
  public void parse_mergeinterfaceaggressively() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-mergeinterfacesaggressively"
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
    verifyParserEndsCleanly();
    assertTrue(parser.getConfig().isConfigurationDebugging());
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
    checkDiagnostics(handler.warnings, proguardConfig, 2, 5, "The field name \"<fields>\" is");
    verifyWithProguard(proguardConfig);
  }

  @Test
  public void parse_regress79925760() throws Exception {
    Path proguardConfig = writeTextToTempFile(
        "-keep public @ interface test.MyAnnotation"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();

    ProguardConfiguration config = parser.getConfig();
    assertEquals(1, config.getRules().size());
    ProguardKeepRule rule = (ProguardKeepRule) config.getRules().get(0);
    assertEquals(ProguardClassType.ANNOTATION_INTERFACE, rule.getClassType());

    verifyWithProguard(proguardConfig);
  }

  @Test
  public void parse_regress110021323() throws Exception {
    Path proguardConfig = writeTextToTempFile(
      "-keepclassmembernames class A {",
      "  <public methods>;",
      "  <public fields>;",
      "}"
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    assertEquals(4, handler.warnings.size());
    checkDiagnostics(handler.warnings, 0, proguardConfig, 2, 3, "The type \"<public\" is");
    checkDiagnostics(handler.warnings, 1, proguardConfig, 2, 11, "The field name \"methods>\" is");
    checkDiagnostics(handler.warnings, 2, proguardConfig, 3, 3, "The type \"<public\" is");
    checkDiagnostics(handler.warnings, 3, proguardConfig, 3, 11, "The field name \"fields>\" is");

    verifyWithProguard(proguardConfig);
  }

  private void checkRulesSourceSnippet(List<String> sourceRules) {
    checkRulesSourceSnippet(sourceRules, sourceRules, false);
  }

  private void checkRulesSourceSnippet(
      List<String> sourceRules, List<String> expected, boolean trim) {
    reset();
    parser.parse(createConfigurationForTesting(sourceRules));
    verifyParserEndsCleanly();
    List<ProguardConfigurationRule> rules = parser.getConfig().getRules();
    assertEquals(expected.size(), rules.size());
    for (int i = 0; i < expected.size(); i++) {
      assertEquals(trim ? expected.get(i).trim() : expected.get(i), rules.get(i).getSource());
    }
  }

  @Test
  public void accurateSourceSnippet() {
    String rule1 = String.join(System.lineSeparator(), ImmutableList.of("-keep class A  {  *;  }"));
    String rule2 =
        String.join(System.lineSeparator(), ImmutableList.of("-keep class A  ", "{  *;  ", "}"));
    String rule3 =
        String.join(
            System.lineSeparator(), ImmutableList.of("-checkdiscard class A  ", "{  *;  ", "}"));

    checkRulesSourceSnippet(ImmutableList.of(rule1));
    checkRulesSourceSnippet(ImmutableList.of(rule2));
    checkRulesSourceSnippet(ImmutableList.of(rule3));
    checkRulesSourceSnippet(
        ImmutableList.of(rule1, rule2, rule3), ImmutableList.of(rule1, rule3), false);
  }

  @Test
  public void accurateSourceSnippet_withWhitespace() {
    Iterable<String> nonEmptyWhiteSpace =
        whiteSpace.stream().filter(space -> !space.equals("")).collect(Collectors.toList());
    for (String space : nonEmptyWhiteSpace) {
      String rule1 =
          String.join(System.lineSeparator(), ImmutableList.of("  -keep class A  {  *;  }  "))
              .replaceAll(" {2}", space);
      String rule2 =
          String.join(
                  System.lineSeparator(), ImmutableList.of("-keep class A  ", "{  *;  ", "}", ""))
              .replaceAll(" {2}", space);

      checkRulesSourceSnippet(ImmutableList.of(rule1), ImmutableList.of(rule1), true);
      checkRulesSourceSnippet(
          ImmutableList.of("#Test comment ", "", rule1), ImmutableList.of(rule1), true);
      checkRulesSourceSnippet(
          ImmutableList.of("#Test comment ", "", rule1, "", "#Test comment ", ""),
          ImmutableList.of(rule1),
          true);
      checkRulesSourceSnippet(ImmutableList.of(rule2), ImmutableList.of(rule2), true);
      checkRulesSourceSnippet(ImmutableList.of(rule1, rule2), ImmutableList.of(rule1), true);
      checkRulesSourceSnippet(
          ImmutableList.of(
              "#Test comment ", "", rule1, " ", "#Test comment ", "", rule2, "#Test comment ", ""),
          ImmutableList.of(rule1),
          true);
    }
  }

  @Test
  public void parseOptionalArgumentsFollowedByArobaseInclude() throws Exception {
    Path includeFile = writeTextToTempFile(
        "-renamesourcefileattribute SRC"
    );
    Path proguardConfig = writeTextToTempFile(
        "-printconfiguration",
        "@" + includeFile.toAbsolutePath()
    );
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintConfiguration());
    assertNull(config.getPrintConfigurationFile());
    assertEquals("SRC", config.getRenameSourceFileAttribute());
  }

  private void testFlagWithFilenames(
      String flag,
      List<String> values,
      Function<ProguardConfiguration, Path> extractPath,
      BiConsumer<String, String> check) {
    for (String value : values) {
      reset();
      parser.parse(createConfigurationForTesting(ImmutableList.of(flag + " " + value)));
      verifyParserEndsCleanly();
      ProguardConfiguration config = parser.getConfig();
      check.accept(value, extractPath.apply(config).toString());
    }
  }

  @Test
  public void parseSingleFileNameOptions() {
    List<String> fileNames = ImmutableList.of(
        "xxx",
        "xxx" + File.pathSeparatorChar + "xxx");
    testFlagWithFilenames("-printusage", fileNames,
        ProguardConfiguration::getPrintUsageFile, Assert::assertEquals);
    testFlagWithFilenames("-printconfiguration", fileNames,
        ProguardConfiguration::getPrintConfigurationFile, Assert::assertEquals);
    testFlagWithFilenames("-printmapping", fileNames,
        ProguardConfiguration::getPrintMappingFile, Assert::assertEquals);
    testFlagWithFilenames("-applymapping", fileNames,
        ProguardConfiguration::getApplyMappingFile, Assert::assertEquals);
    // The parsed value of -basedirectory is not available in the configuration.
    testFlagWithFilenames("-basedirectory", fileNames, (x) -> Paths.get(""), (x, y) -> {});
    testFlagWithFilenames("-printseeds", fileNames,
        ProguardConfiguration::getSeedFile, Assert::assertEquals);
    // TODO(sgjesse): Add tests for -obfuscationdictionary, -classobfuscationdictionary,
    // -packageobfuscationdictionary and -include
  }

  private void checkQuotedFileName(String quoted, String parsed) {
    assertTrue(quoted.charAt(0) == '\'' || quoted.charAt(0) == '\"');
    assertTrue(quoted.charAt(0) == quoted.charAt(quoted.length() - 1));
    assertEquals(quoted.substring(1, quoted.length() - 1), parsed);
  }

  @Test
  public void parseSingleFleNameOptionsQuoted() {
    List<String> fileNames = new ArrayList<>();
    fileNames.add("'xxx'");
    fileNames.add("\"xxx\"");
    fileNames.add("'xxx xxx\'");
    fileNames.add("\"xxx xxx\"");
    fileNames.add("\"'xxx'\"");
    fileNames.add("\" xxx xxx (\"");
    fileNames.add("' xxx xxx ('");
    // Java Path implementation on Windows does not allow " or trailing
    // spaces in file names.
    if (!ToolHelper.isWindows()) {
      fileNames.add("'\"xxx\"'");
      fileNames.add("\" xxx xxx \"");
      fileNames.add("' xxx xxx '");
    }
    testFlagWithFilenames("-printusage", fileNames,
        ProguardConfiguration::getPrintUsageFile, this::checkQuotedFileName);
    testFlagWithFilenames("-printconfiguration", fileNames,
        ProguardConfiguration::getPrintConfigurationFile, this::checkQuotedFileName);
    testFlagWithFilenames("-printmapping", fileNames,
        ProguardConfiguration::getPrintMappingFile, this::checkQuotedFileName);
    testFlagWithFilenames("-applymapping", fileNames,
        ProguardConfiguration::getApplyMappingFile, this::checkQuotedFileName);
    // The parsed value of -basedirectory is not available in the configuration.
    testFlagWithFilenames("-basedirectory", fileNames, (x) -> Paths.get(""), (x, y) -> {});
    testFlagWithFilenames("-printseeds", fileNames,
        ProguardConfiguration::getSeedFile, this::checkQuotedFileName);
    // TODO(sgjesse): Add tests for -obfuscationdictionary, -classobfuscationdictionary,
    // -packageobfuscationdictionary and -include
  }

  private void testFlagWithFilenamesWithSystemProperty(
      String flag,
      List<String> values,
      Function<ProguardConfiguration, Path> extractPath,
      BiConsumer<String, String> check) {
    for (String value : values) {
      reset();
      parser.parse(createConfigurationForTesting(ImmutableList.of(flag + " " + value)));
      verifyParserEndsCleanly();
      ProguardConfiguration config = parser.getConfig();
      Path path = extractPath.apply(config);
      check.accept(
          value
              .replaceAll("<java.home>", Matcher.quoteReplacement(System.getProperty("java.home")))
              .replaceAll("<user.home>", Matcher.quoteReplacement(System.getProperty("user.home"))),
          path.toString());
    }
  }

  @Test
  public void parseFlagWithFilenamesWithSystemProperty() {
    List<String> fileNames = new ArrayList<>();
    fileNames.add("<java.home>");
    fileNames.add("<user.home>");
    if (!ToolHelper.isWindows()) {
      // If the system property has e.g. C:\ prefix, then these file names
      // will not work on Windows.
      fileNames.add("xxx<java.home>");
      fileNames.add("xxx<user.home>");
    }
    fileNames.add("<java.home>" +  File.separatorChar + "xxx");
    fileNames.add("<user.home>" +  File.separatorChar + "xxx");
    if (!ToolHelper.isWindows()) {
      fileNames.add("xxx<java.home>/xxx");
      fileNames.add("xxx<user.home>/xxx");
      fileNames.add("<java.home><java.home>");
      fileNames.add("<user.home><user.home>");
      fileNames.add("<java.home><user.home>");
      fileNames.add("<user.home><java.home>");
      // The characters < and > are not allowed in paths on Windows.
      fileNames.add(">");
      fileNames.add("<");
      fileNames.add("<<<<");
      fileNames.add("><");
      fileNames.add(">><<");
    }
    testFlagWithFilenamesWithSystemProperty("-printusage", fileNames,
        ProguardConfiguration::getPrintUsageFile, Assert::assertEquals);
    testFlagWithFilenamesWithSystemProperty("-printconfiguration", fileNames,
        ProguardConfiguration::getPrintConfigurationFile, Assert::assertEquals);
    testFlagWithFilenamesWithSystemProperty("-printmapping", fileNames,
        ProguardConfiguration::getPrintMappingFile, Assert::assertEquals);
    testFlagWithFilenamesWithSystemProperty("-applymapping", fileNames,
        ProguardConfiguration::getApplyMappingFile, Assert::assertEquals);
    // The parsed value of -basedirectory is not available in the configuration.
    testFlagWithFilenamesWithSystemProperty("-basedirectory", fileNames,
        (x) -> Paths.get(""), (x, y) -> {});
    testFlagWithFilenamesWithSystemProperty("-printseeds", fileNames,
        ProguardConfiguration::getSeedFile, Assert::assertEquals);
    // TODO(sgjesse): Add tests for -obfuscationdictionary, -classobfuscationdictionary,
    // -packageobfuscationdictionary and -include
  }

  @Test
  public void pasteFlagWithFilenamesWithSystemProperty_empty() {
    try {
      parser.parse(createConfigurationForTesting(ImmutableList.of("-printusage <>")));
      fail("Expect to fail due to the lack of file name.");
    } catch (RuntimeException e) {
      checkDiagnostics(handler.errors, null, 1, 15, "Value of system property '' not found");
    }
  }

  @Test
  public void pasteFlagWithFilenamesWithSystemProperty_notFound() {
    // Find a non-existent system property.
    String property = "x";
    while (System.getProperty(property) != null) {
      property = property + "x";
    }

    try {
      parser.parse(
          createConfigurationForTesting(ImmutableList.of("-printusage <" + property + ">")));
      fail("Expect to fail due to the lack of file name.");
    } catch (RuntimeException e) {
      checkDiagnostics(
          handler.errors, null, 1, 16, "Value of system property '" + property + "' not found");
    }
  }

  private void testFlagWithQuotedValue(
      String flag, String value, BiConsumer<PackageObfuscationMode, String> checker) {
    reset();
    parser.parse(createConfigurationForTesting(ImmutableList.of(flag + " " + value)));
    verifyParserEndsCleanly();
    ProguardConfiguration config = parser.getConfig();
    checker.accept(config.getPackageObfuscationMode(), config.getPackagePrefix());
  }

  private void testFlagWithQuotedValueFailure(
      String flag, String value, Supplier<Void> checker) {
    try {
      reset();
      parser.parse(createConfigurationForTesting(ImmutableList.of(flag + " " + value)));
      fail("Expect to fail due to un-closed quote.");
    } catch (RuntimeException e) {
      checker.get();
    }
  }

  @Test
  public void parseRepackageclassesQuotes() {
    testFlagWithQuotedValue("-repackageclasses", "'xxx'", (mode, prefix) -> {
      assertEquals(PackageObfuscationMode.REPACKAGE, mode);
      assertEquals("xxx", prefix);
    });
    testFlagWithQuotedValue("-repackageclasses", "\"xxx\"", (mode, prefix) -> {
      assertEquals(PackageObfuscationMode.REPACKAGE, mode);
      assertEquals("xxx", prefix);
    });
    testFlagWithQuotedValueFailure("-repackageclasses", "'xxx", () -> {
      checkDiagnostics(handler.errors, null, 1, 23, "Missing closing quote");
      return null;
    });
    testFlagWithQuotedValueFailure("-repackageclasses", "'xxx\"", () -> {
      checkDiagnostics(handler.errors, null, 1, 23, "Missing closing quote");
      return null;
    });
    testFlagWithQuotedValueFailure("-repackageclasses", "\"xxx", () -> {
      checkDiagnostics(handler.errors, null, 1, 23, "Missing closing quote");
      return null;
    });
    testFlagWithQuotedValueFailure("-repackageclasses", "\"xxx'", () -> {
      checkDiagnostics(handler.errors, null, 1, 23, "Missing closing quote");
      return null;
    });
  }

  @Test
  public void parseFlattenpackagehierarchyQuotes() {
    testFlagWithQuotedValue("-flattenpackagehierarchy", "'xxx'", (mode, prefix) -> {
      assertEquals(PackageObfuscationMode.FLATTEN, mode);
      assertEquals("xxx", prefix);
    });
    testFlagWithQuotedValue("-flattenpackagehierarchy", "\"xxx\"", (mode, prefix) -> {
      assertEquals(PackageObfuscationMode.FLATTEN, mode);
      assertEquals("xxx", prefix);
    });
    testFlagWithQuotedValueFailure("-flattenpackagehierarchy", "'xxx", () -> {
      checkDiagnostics(handler.errors, null, 1, 30, "Missing closing quote");
      return null;
    });
    testFlagWithQuotedValueFailure("-flattenpackagehierarchy", "'xxx\"", () -> {
      checkDiagnostics(handler.errors, null, 1, 30, "Missing closing quote");
      return null;
    });
    testFlagWithQuotedValueFailure("-flattenpackagehierarchy", "\"xxx", () -> {
      checkDiagnostics(handler.errors, null, 1, 30, "Missing closing quote");
      return null;
    });
    testFlagWithQuotedValueFailure("-flattenpackagehierarchy", "\"xxx'", () -> {
      checkDiagnostics(handler.errors, null, 1, 30, "Missing closing quote");
      return null;
    });
  }

  private ProguardConfiguration parseAndVerifyParserEndsCleanly(List<String> config) {
    parser.parse(createConfigurationForTesting(config));
    verifyParserEndsCleanly();
    return parser.getConfig();
  }

  private ProguardConfiguration parseAndVerifyParserEndsCleanly(Path config) {
    parser.parse(config);
    verifyParserEndsCleanly();
    return parser.getConfig();
  }

  private void verifyParserEndsCleanly() {
    assertEquals(0, handler.infos.size());
    assertEquals(0, handler.warnings.size());
    assertEquals(0, handler.errors.size());
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
      ProcessResult result = ToolHelper.runProguardRaw(
          jarTestClasses(ImmutableList.of(classToKeepForTest)),
          proguardedJar,
          ImmutableList.of(proguardConfig, additionalProguardConfig),
          null);
      assertEquals(0, result.exitCode);
      CodeInspector proguardInspector = new CodeInspector(readJar(proguardedJar));
      assertEquals(1, proguardInspector.allClasses().size());
    }
  }

  private void verifyWithProguard6(Path proguardConfig) throws Exception {
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
      ProcessResult result = ToolHelper.runProguard6Raw(
          jarTestClasses(ImmutableList.of(classToKeepForTest)),
          proguardedJar,
          ImmutableList.of(proguardConfig, additionalProguardConfig),
          null);
      assertEquals(0, result.exitCode);
      CodeInspector proguardInspector = new CodeInspector(readJar(proguardedJar));
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

  @Test
  public void b124181032() {
    // Test spaces and quotes in class name list.
    ProguardConfigurationParser parser;
    parser = new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(
        createConfigurationForTesting(
            ImmutableList.of(
                "-keepclassmembers class \"a.b.c.**\" ,"
                    + " !**d , '!**e' , \"!**f\" , g , 'h' , \"i\" { ",
                "<fields>;",
                "<init>();",
                "}")));
    List<ProguardConfigurationRule> rules = parser.getConfig().getRules();
    assertEquals(1, rules.size());
    ProguardConfigurationRule rule = rules.get(0);
    assertEquals(ProguardKeepRuleType.KEEP_CLASS_MEMBERS.toString(), rule.typeString());
    assertEquals("a.b.c.**,!**d,!**e,!**f,g,h,i", rule.getClassNames().toString());
  }

  @Test
  public void directiveAfterRepackagingRuleTest() {
    List<PackageObfuscationMode> packageObfuscationModes =
        ImmutableList.of(PackageObfuscationMode.FLATTEN, PackageObfuscationMode.REPACKAGE);
    for (PackageObfuscationMode packageObfuscationMode : packageObfuscationModes) {
      String directive =
          packageObfuscationMode == PackageObfuscationMode.FLATTEN
              ? "-flattenpackagehierarchy"
              : "-repackageclasses";
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(createConfigurationForTesting(ImmutableList.of(directive + " -keep class *")));
      ProguardConfiguration configuration = parser.getConfig();
      assertEquals(packageObfuscationMode, configuration.getPackageObfuscationMode());
      assertEquals("", configuration.getPackagePrefix());

      List<ProguardConfigurationRule> rules = configuration.getRules();
      assertEquals(1, rules.size());

      ProguardConfigurationRule rule = rules.get(0);
      assertEquals(ProguardKeepRuleType.KEEP.toString(), rule.typeString());
      assertEquals("*", rule.getClassNames().toString());
    }
  }

  @Test
  public void arobaseAfterRepackagingRuleTest() throws IOException {
    Path includeFile = writeTextToTempFile("-keep class *");
    List<PackageObfuscationMode> packageObfuscationModes =
        ImmutableList.of(PackageObfuscationMode.FLATTEN, PackageObfuscationMode.REPACKAGE);
    for (PackageObfuscationMode packageObfuscationMode : packageObfuscationModes) {
      String directive =
          packageObfuscationMode == PackageObfuscationMode.FLATTEN
              ? "-flattenpackagehierarchy"
              : "-repackageclasses";
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(new DexItemFactory(), reporter);
      parser.parse(
          createConfigurationForTesting(
              ImmutableList.of(directive + " @" + includeFile.toAbsolutePath())));
      ProguardConfiguration configuration = parser.getConfig();
      assertEquals(packageObfuscationMode, configuration.getPackageObfuscationMode());
      assertEquals("", configuration.getPackagePrefix());

      List<ProguardConfigurationRule> rules = configuration.getRules();
      assertEquals(1, rules.size());

      ProguardConfigurationRule rule = rules.get(0);
      assertEquals(ProguardKeepRuleType.KEEP.toString(), rule.typeString());
      assertEquals("*", rule.getClassNames().toString());
    }
  }

  @Test
  public void negatedMemberTypeTest() throws IOException {
    Path proguardConfigurationFile = writeTextToTempFile("-keepclassmembers class ** { !int x; }");
    try {
      new ProguardConfigurationParser(new DexItemFactory(), reporter)
          .parse(proguardConfigurationFile);
      fail("Expected to fail since the type name cannot be negated.");
    } catch (RuntimeException e) {
      checkDiagnostics(
          handler.errors,
          proguardConfigurationFile,
          1,
          30,
          "Unexpected character '!': "
              + "The negation character can only be used to negate access flags");
    }
  }

  @Test
  public void parseInits() throws Exception {
    for (String initName : InitMatchingTest.ALLOWED_INIT_NAMES) {
      reset();
      Path initConfig = writeTextToTempFile(
          "-keep class **.MyClass {",
          "  " + initName + "(...);",
          "}");
      parseAndVerifyParserEndsCleanly(initConfig);
    }

    for (String initName : InitMatchingTest.INIT_NAMES) {
      // Tested above.
      if (InitMatchingTest.ALLOWED_INIT_NAMES.contains(initName)) {
        continue;
      }
      reset();
      Path proguardConfig = writeTextToTempFile(
          "-keep class **.MyClass {",
          "  " + initName + "(...);",
          "}");
      try {
        parser.parse(proguardConfig);
        fail("Expect to fail due to unsupported constructor name pattern.");
      } catch (RuntimeException e) {
        int column = initName.contains("void") ? initName.indexOf("void") + 8
            : (initName.contains("XYZ") ? initName.indexOf(">") + 4 : 3);
        if (initName.contains("XYZ")) {
          checkDiagnostics(
              handler.errors, proguardConfig, 2, column, "Expected [access-flag]* void ");
        } else {
          checkDiagnostics(
              handler.errors, proguardConfig, 2, column, "Unexpected character", "method name");
        }
      }
      // For some exceptional cases, Proguard accepts the rules but fails with an empty jar message.
      if (initName.contains("<init>")
          || initName.contains("<clinit>")
          || initName.contains("void")) {
        continue;
      }
      verifyFailWithProguard6(
          proguardConfig, "Expecting type and name instead of just '" + initName + "'");
    }
  }

  @Test
  public void parseIncludeCode() throws Exception {
    ProguardConfigurationParser parser;
    parser = new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path proguardConfig = writeTextToTempFile("-keep,includecode class A { method(); }");
    parser.parse(proguardConfig);
    assertEquals(1, parser.getConfig().getRules().size());
    assertEquals(1, handler.infos.size());
    checkDiagnostics(handler.infos, proguardConfig, 1, 7, "Ignoring modifier", "includecode");
  }

  @Test
  public void parseFileStartingWithBOM() throws Exception {
    // Copied from test 'parseIncludeCode()' and added a BOM.
    ProguardConfigurationParser parser;
    parser = new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path proguardConfig =
        writeTextToTempFile(StringUtils.BOM + "-keep,includecode class A { method(); }");
    byte[] bytes = Files.readAllBytes(proguardConfig);
    assertEquals(0xef, Byte.toUnsignedLong(bytes[0]));
    assertEquals(0xbb, Byte.toUnsignedLong(bytes[1]));
    assertEquals(0xbf, Byte.toUnsignedLong(bytes[2]));
    parser.parse(proguardConfig);
    assertEquals(1, parser.getConfig().getRules().size());
    assertEquals(1, handler.infos.size());
    checkDiagnostics(handler.infos, proguardConfig, 1, 7, "Ignoring modifier", "includecode");
  }

  @Test
  public void parseFileWithLotsOfWhitespace() throws Exception {
    List<String> ws =
        ImmutableList.of(
            "",
            " ",
            "  ",
            "\t ",
            " \t",
            "" + StringUtils.BOM,
            StringUtils.BOM + " " + StringUtils.BOM);
    // Copied from test 'parseIncludeCode()' and added whitespace.
    for (String whitespace1 : ws) {
      for (String whitespace2 : ws) {
        reset();
        Path proguardConfig =
            writeTextToTempFile(
                whitespace1
                    + "-keep,includecode "
                    + whitespace2
                    + "class A { method(); }"
                    + whitespace1);
        parser.parse(proguardConfig);
        assertEquals(1, parser.getConfig().getRules().size());
        assertEquals(1, handler.infos.size());
        // All BOSs except the leading are counted as input characters.
        checkDiagnostics(handler.infos, proguardConfig, 1, 0, "Ignoring modifier", "includecode");
      }
    }
  }

  @Test
  public void backReferenceElimination() {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser = new ProguardConfigurationParser(dexItemFactory, reporter);
    String configuration = StringUtils.lines("-if class *.*.*", "-keep class <1>.<2>$<3>");
    parser.parse(createConfigurationForTesting(ImmutableList.of(configuration)));
    verifyParserEndsCleanly();

    ProguardConfiguration config = parser.getConfig();
    assertEquals(1, config.getRules().size());

    ProguardIfRule ifRule = (ProguardIfRule) config.getRules().iterator().next();

    // Evaluate the class name matcher against foo.bar.Baz.
    DexType type = dexItemFactory.createType("Lfoo/bar/Baz;");
    ifRule.getClassNames().matches(type);

    // Materialize the subsequent rule.
    ProguardKeepRule materializedSubsequentRule = ifRule.subsequentRule.materialize(dexItemFactory);

    // Verify that the class name matcher of the materialized rule has a specific type.
    ProguardClassNameList classNameList = materializedSubsequentRule.getClassNames();
    assertTrue(classNameList instanceof SingleClassNameList);

    SingleClassNameList singleClassNameList = (SingleClassNameList) classNameList;
    assertTrue(singleClassNameList.className instanceof MatchSpecificType);

    MatchSpecificType specificTypeMatcher = (MatchSpecificType) singleClassNameList.className;
    assertEquals("foo.bar$Baz", specificTypeMatcher.type.toSourceString());
  }

  @Test
  public void parseCheckenumstringsdiscarded() throws Exception {
    Path proguardConfig =
        writeTextToTempFile("-checkenumstringsdiscarded @com.example.SomeAnnotation enum *");
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(proguardConfig);
    verifyParserEndsCleanly();
  }

  @Test
  public void parseClassAnnotationsAndFlags() {
    List<String> configurationContents =
        ImmutableList.of(
            // 1 annotation without flags.
            "-keep @Foo class *",
            // 1 annotation with public flag.
            "-keep public @Foo class *",
            "-keep @Foo public class *",
            // 1 annotation with public final flags.
            "-keep public final @Foo class *",
            "-keep public @Foo final class *",
            "-keep @Foo public final class *",
            // 2 annotations without flags.
            "-keep @Foo @Bar class *",
            // 2 annotations with public flag.
            "-keep public @Foo @Bar class *",
            "-keep @Foo public @Bar class *",
            "-keep @Foo @Bar public class *",
            // 2 annotations with public final flags.
            "-keep public final @Foo @Bar class *",
            "-keep public @Foo final @Bar class *",
            "-keep public @Foo @Bar final class *",
            "-keep @Foo public final @Bar class *",
            "-keep @Foo public @Bar final class *",
            "-keep @Foo @Bar public final class *");
    for (String configurationContent : configurationContents) {
      DexItemFactory dexItemFactory = new DexItemFactory();
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(dexItemFactory, reporter);
      parser.parse(createConfigurationForTesting(ImmutableList.of(configurationContent)));
      verifyParserEndsCleanly();

      ProguardConfiguration configuration = parser.getConfig();
      assertEquals(1, configuration.getRules().size());

      ProguardKeepRule rule = ListUtils.first(configuration.getRules()).asProguardKeepRule();
      assertEquals(configurationContent.contains("final"), rule.getClassAccessFlags().isFinal());
      assertEquals(configurationContent.contains("public"), rule.getClassAccessFlags().isPublic());
      assertEquals(
          1 + intValue(configurationContent.contains("@Bar")), rule.getClassAnnotations().size());

      ProguardTypeMatcher.MatchSpecificType fooAnnotation =
          rule.getClassAnnotations().get(0).asSpecificTypeMatcher();
      assertEquals("Foo", fooAnnotation.getSpecificType().toSourceString());

      if (configurationContent.contains("@Bar")) {
        ProguardTypeMatcher.MatchSpecificType barAnnotation =
            rule.getClassAnnotations().get(1).asSpecificTypeMatcher();
        assertEquals("Bar", barAnnotation.getSpecificType().toSourceString());
      }
    }
  }

  @Test
  public void parseFieldAnnotationsAndFlags() {
    Map<String, Optional<Class<? extends Exception>>> configurationContents =
        ImmutableMap.<String, Optional<Class<? extends Exception>>>builder()
            // 1 annotation without flags.
            .put("-keep class * { @Foo Type FIELD; }", Optional.empty())
            // 1 annotation with public flag.
            .put("-keep class * { public @Foo Type FIELD; }", Optional.of(RuntimeException.class))
            .put("-keep class * { @Foo public Type FIELD; }", Optional.empty())
            // 1 annotation with public final flags.
            .put(
                "-keep class * { public final @Foo Type FIELD; }",
                Optional.of(RuntimeException.class))
            .put(
                "-keep class * { public @Foo final Type FIELD; }",
                Optional.of(RuntimeException.class))
            .put("-keep class * { @Foo public final Type FIELD; }", Optional.empty())
            //// 2 annotations without flags.
            .put("-keep class * { @Foo @Bar Type FIELD; }", Optional.empty())
            //// 2 annotations with public flag.
            .put(
                "-keep class * { public @Foo @Bar Type FIELD; }",
                Optional.of(RuntimeException.class))
            .put(
                "-keep class * { @Foo public @Bar Type FIELD; }",
                Optional.of(RuntimeException.class))
            .put("-keep class * { @Foo @Bar public Type FIELD; }", Optional.empty())
            //// 2 annotations with public final flags.
            .put(
                "-keep class * { public final @Foo @Bar Type FIELD; }",
                Optional.of(RuntimeException.class))
            .put(
                "-keep class * { public @Foo final @Bar Type FIELD; }",
                Optional.of(RuntimeException.class))
            .put(
                "-keep class * { public @Foo @Bar final Type FIELD; }",
                Optional.of(RuntimeException.class))
            .put(
                "-keep class * { @Foo public final @Bar Type FIELD; }",
                Optional.of(RuntimeException.class))
            .put(
                "-keep class * { @Foo public @Bar final Type FIELD; }",
                Optional.of(RuntimeException.class))
            .put("-keep class * { @Foo @Bar public final Type FIELD; }", Optional.empty())
            .build();
    configurationContents.forEach(
        (configurationContent, expectedExceptionClass) -> {
          DexItemFactory dexItemFactory = new DexItemFactory();
          ProguardConfigurationParser parser =
              new ProguardConfigurationParser(dexItemFactory, reporter);
          try {
            parser.parse(createConfigurationForTesting(ImmutableList.of(configurationContent)));
            assertFalse(expectedExceptionClass.isPresent());
          } catch (Throwable e) {
            assertTrue(expectedExceptionClass.isPresent());
            assertEquals(expectedExceptionClass.get(), e.getClass());
            reset();
            return;
          }

          verifyParserEndsCleanly();

          ProguardConfiguration configuration = parser.getConfig();
          assertEquals(1, configuration.getRules().size());

          ProguardKeepRule rule = ListUtils.first(configuration.getRules()).asProguardKeepRule();
          assertEquals(1, rule.getMemberRules().size());

          ProguardMemberRule memberRule = ListUtils.first(rule.getMemberRules());

          assertEquals(
              configurationContent.contains("final"), memberRule.getAccessFlags().isFinal());
          assertEquals(
              configurationContent.contains("public"), memberRule.getAccessFlags().isPublic());
          assertEquals(
              1 + intValue(configurationContent.contains("@Bar")),
              memberRule.getAnnotations().size());

          ProguardTypeMatcher.MatchSpecificType fooAnnotation =
              memberRule.getAnnotations().get(0).asSpecificTypeMatcher();
          assertEquals("Foo", fooAnnotation.getSpecificType().toSourceString());

          if (configurationContent.contains("@Bar")) {
            ProguardTypeMatcher.MatchSpecificType barAnnotation =
                memberRule.getAnnotations().get(1).asSpecificTypeMatcher();
            assertEquals("Bar", barAnnotation.getSpecificType().toSourceString());
          }
        });
  }

  @Test
  public void parseMethodAnnotationsAndFlags() {
    Map<String, Optional<Class<? extends Exception>>> configurationContents =
        ImmutableMap.<String, Optional<Class<? extends Exception>>>builder()
            // 1 annotation without flags.
            .put("-keep class * { @Foo Type method(); }", Optional.empty())
            // 1 annotation with public flag.
            .put(
                "-keep class * { public @Foo Type method(); }", Optional.of(RuntimeException.class))
            .put("-keep class * { @Foo public Type method(); }", Optional.empty())
            // 1 annotation with public final flags.
            .put(
                "-keep class * { public final @Foo Type method(); }",
                Optional.of(RuntimeException.class))
            .put(
                "-keep class * { public @Foo final Type method(); }",
                Optional.of(RuntimeException.class))
            .put("-keep class * { @Foo public final Type method(); }", Optional.empty())
            //// 2 annotations without flags.
            .put("-keep class * { @Foo @Bar Type method(); }", Optional.empty())
            //// 2 annotations with public flag.
            .put(
                "-keep class * { public @Foo @Bar Type method(); }",
                Optional.of(RuntimeException.class))
            .put(
                "-keep class * { @Foo public @Bar Type method(); }",
                Optional.of(RuntimeException.class))
            .put("-keep class * { @Foo @Bar public Type method(); }", Optional.empty())
            //// 2 annotations with public final flags.
            .put(
                "-keep class * { public final @Foo @Bar Type method(); }",
                Optional.of(RuntimeException.class))
            .put(
                "-keep class * { public @Foo final @Bar Type method(); }",
                Optional.of(RuntimeException.class))
            .put(
                "-keep class * { public @Foo @Bar final Type method(); }",
                Optional.of(RuntimeException.class))
            .put(
                "-keep class * { @Foo public final @Bar Type method(); }",
                Optional.of(RuntimeException.class))
            .put(
                "-keep class * { @Foo public @Bar final Type method(); }",
                Optional.of(RuntimeException.class))
            .put("-keep class * { @Foo @Bar public final Type method(); }", Optional.empty())
            .build();
    configurationContents.forEach(
        (configurationContent, expectedExceptionClass) -> {
          DexItemFactory dexItemFactory = new DexItemFactory();
          ProguardConfigurationParser parser =
              new ProguardConfigurationParser(dexItemFactory, reporter);
          try {
            parser.parse(createConfigurationForTesting(ImmutableList.of(configurationContent)));
            assertFalse(expectedExceptionClass.isPresent());
          } catch (Throwable e) {
            assertTrue(expectedExceptionClass.isPresent());
            assertEquals(expectedExceptionClass.get(), e.getClass());
            reset();
            return;
          }

          verifyParserEndsCleanly();

          ProguardConfiguration configuration = parser.getConfig();
          assertEquals(1, configuration.getRules().size());

          ProguardKeepRule rule = ListUtils.first(configuration.getRules()).asProguardKeepRule();
          assertEquals(1, rule.getMemberRules().size());

          ProguardMemberRule memberRule = ListUtils.first(rule.getMemberRules());

          assertEquals(
              configurationContent.contains("final"), memberRule.getAccessFlags().isFinal());
          assertEquals(
              configurationContent.contains("public"), memberRule.getAccessFlags().isPublic());
          assertEquals(
              1 + intValue(configurationContent.contains("@Bar")),
              memberRule.getAnnotations().size());

          ProguardTypeMatcher.MatchSpecificType fooAnnotation =
              memberRule.getAnnotations().get(0).asSpecificTypeMatcher();
          assertEquals("Foo", fooAnnotation.getSpecificType().toSourceString());

          if (configurationContent.contains("@Bar")) {
            ProguardTypeMatcher.MatchSpecificType barAnnotation =
                memberRule.getAnnotations().get(1).asSpecificTypeMatcher();
            assertEquals("Bar", barAnnotation.getSpecificType().toSourceString());
          }
        });
  }

  @Test
  public void parseKeepXNamesIsAllowShrinking() {
    for (String rule : ImmutableList.of("-keep", "-keepclassmembers", "-keepclasseswithmembers")) {
      for (String modifier :
          ImmutableList.of("", "names", ",allowshrinking", "names,allowshrinking")) {
        DexItemFactory dexItemFactory = new DexItemFactory();
        ProguardConfigurationParser parser =
            new ProguardConfigurationParser(dexItemFactory, reporter);
        String ruleWithModifier =
            (rule.endsWith("s") && (!modifier.startsWith(",") && !modifier.isEmpty())
                    ? rule.substring(0, rule.length() - 1)
                    : rule)
                + modifier;
        parser.parse(
            createConfigurationForTesting(ImmutableList.of(ruleWithModifier + " class A")));
        verifyParserEndsCleanly();

        ProguardConfiguration configuration = parser.getConfig();
        assertEquals(1, configuration.getRules().size());
        assertEquals(
            !modifier.isEmpty(),
            ListUtils.first(configuration.getRules())
                .asProguardKeepRule()
                .getModifiers()
                .allowsShrinking);
      }
    }
  }

  @Test
  public void parseMaximumRemovedAndroidLogLevelWithoutClassSpecification() {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser = new ProguardConfigurationParser(dexItemFactory, reporter);
    String configuration = StringUtils.lines("-maximumremovedandroidloglevel 2");
    parser.parse(createConfigurationForTesting(ImmutableList.of(configuration)));
    verifyParserEndsCleanly();

    ProguardConfiguration config = parser.getConfig();
    assertEquals(MaximumRemovedAndroidLogLevelRule.VERBOSE, config.getMaxRemovedAndroidLogLevel());
    assertEquals(0, config.getRules().size());
  }

  @Test
  public void parseMaximumRemovedAndroidLogLevelWithClassSpecification() {
    for (String input :
        new String[] {
          "-maximumremovedandroidloglevel 2 class * { <methods>; }",
          "-maximumremovedandroidloglevel 2 @Foo class * { <methods>; }"
        }) {
      DexItemFactory dexItemFactory = new DexItemFactory();
      ProguardConfigurationParser parser =
          new ProguardConfigurationParser(dexItemFactory, reporter);
      String configuration = StringUtils.lines(input);
      parser.parse(createConfigurationForTesting(ImmutableList.of(configuration)));
      verifyParserEndsCleanly();

      ProguardConfiguration config = parser.getConfig();
      assertEquals(
          MaximumRemovedAndroidLogLevelRule.NOT_SET, config.getMaxRemovedAndroidLogLevel());
      assertEquals(1, config.getRules().size());
      assertTrue(config.getRules().get(0).isMaximumRemovedAndroidLogLevelRule());

      MaximumRemovedAndroidLogLevelRule rule =
          config.getRules().get(0).asMaximumRemovedAndroidLogLevelRule();
      assertEquals(MaximumRemovedAndroidLogLevelRule.VERBOSE, rule.getMaxRemovedAndroidLogLevel());
    }
  }
}