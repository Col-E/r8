// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.shaking.ProguardConfigurationSourceStrings.createConfigurationForTesting;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TextRangeLocation;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.DefaultDiagnosticsHandler;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

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

  @Before
  public void setup() {
    handler = new KeepingDiagnosticHandler();
    reporter = new Reporter(handler);
  }

  @Test
  public void parse() throws Exception {
    ProguardConfigurationParser parser;

    // Parse from file.
    parser = new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PROGUARD_SPEC_FILE));
    List<ProguardConfigurationRule> rules = parser.getConfig().getRules();
    assertEquals(24, rules.size());
    assertEquals(1, rules.get(0).getMemberRules().size());

    // Parse from strings.
    parser = new ProguardConfigurationParser(new DexItemFactory(), reporter);
    List<String> lines = FileUtils.readTextFile(Paths.get(PROGUARD_SPEC_FILE));
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
  public void testDontWarn() throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    String dontwarn = "-dontwarn !foobar,*bar";
    parser.parse(createConfigurationForTesting(ImmutableList.of(dontwarn)));
    ProguardConfiguration config = parser.getConfig();
    assertFalse(
        config.getDontWarnPatterns().matches(dexItemFactory.createType("Lboobaz;")));
    assertTrue(
        config.getDontWarnPatterns().matches(dexItemFactory.createType("Lboobar;")));
    assertFalse(
        config.getDontWarnPatterns().matches(dexItemFactory.createType("Lfoobar;")));
  }

  @Test
  public void testDontWarnMultiple() throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    List<String> configuration1 = ImmutableList.of("-dontwarn foo.**, bar.**");
    List<String> configuration2 = ImmutableList.of("-dontwarn foo.**", "-dontwarn bar.**");
    for (List<String> configuration : ImmutableList.of(configuration1, configuration2)) {
      parser.parse(createConfigurationForTesting(configuration));
      ProguardConfiguration config = parser.getConfig();
      assertTrue(
          config.getDontWarnPatterns().matches(dexItemFactory.createType("Lfoo/Bar;")));
      assertTrue(
          config.getDontWarnPatterns().matches(dexItemFactory.createType("Lfoo/bar7Bar;")));
      assertTrue(
          config.getDontWarnPatterns().matches(dexItemFactory.createType("Lbar/Foo;")));
    }
  }

  @Test
  public void testDontWarnAllExplicitly() throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    String dontwarnAll = "-dontwarn *";
    parser.parse(createConfigurationForTesting(ImmutableList.of(dontwarnAll)));
    ProguardConfiguration config = parser.getConfig();
    assertTrue(
        config.getDontWarnPatterns().matches(dexItemFactory.createType("Lboobaz;")));
    assertTrue(
        config.getDontWarnPatterns().matches(dexItemFactory.createType("Lboobar;")));
    assertTrue(
        config.getDontWarnPatterns().matches(dexItemFactory.createType("Lfoobar;")));
  }

  @Test
  public void testDontWarnAllImplicitly() throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    String dontwarnAll = "-dontwarn";
    String otherOption = "-keep class *";
    parser.parse(createConfigurationForTesting(ImmutableList.of(dontwarnAll, otherOption)));
    ProguardConfiguration config = parser.getConfig();
    assertTrue(
        config.getDontWarnPatterns().matches(dexItemFactory.createType("Lboobaz;")));
    assertTrue(
        config.getDontWarnPatterns().matches(dexItemFactory.createType("Lboobar;")));
    assertTrue(
        config.getDontWarnPatterns().matches(dexItemFactory.createType("Lfoobar;")));
  }

  @Test
  public void parseAccessFlags() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(ACCESS_FLAGS_FILE));
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
    List<ProguardConfigurationRule> assumeNoSideEffects = parser.getConfig().getRules();
    assertEquals(1, assumeNoSideEffects.size());
    assumeNoSideEffects.get(0).getMemberRules().forEach(rule -> {
      assertTrue(rule.hasReturnValue());
      if (rule.getName().matches("returnsTrue") || rule.getName().matches("returnsFalse")) {
        assertTrue(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertEquals(rule.getName().matches("returnsTrue"), rule.getReturnValue().getBoolean());
      } else if (rule.getName().matches("returns1")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertTrue(rule.getReturnValue().isSingleValue());
        assertEquals(1, rule.getReturnValue().getValueRange().getMin());
        assertEquals(1, rule.getReturnValue().getValueRange().getMax());
        assertEquals(1, rule.getReturnValue().getSingleValue());
      } else if (rule.getName().matches("returns2To4")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isSingleValue());
        assertEquals(2, rule.getReturnValue().getValueRange().getMin());
        assertEquals(4, rule.getReturnValue().getValueRange().getMax());
      } else if (rule.getName().matches("returnsField")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertTrue(rule.getReturnValue().isField());
        assertEquals("com.google.C", rule.getReturnValue().getField().clazz.toString());
        assertEquals("int", rule.getReturnValue().getField().type.toString());
        assertEquals("X", rule.getReturnValue().getField().name.toString());
      }
    });
  }

  @Test
  public void parseAssumeValuesWithReturnValue() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(ASSUME_VALUES_WITH_RETURN_VALUE));
    List<ProguardConfigurationRule> assumeValues = parser.getConfig().getRules();
    assertEquals(1, assumeValues.size());
    assumeValues.get(0).getMemberRules().forEach(rule -> {
      assertTrue(rule.hasReturnValue());
      if (rule.getName().matches("isTrue") || rule.getName().matches("isFalse")) {
        assertTrue(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertEquals(rule.getName().matches("isTrue"), rule.getReturnValue().getBoolean());
      } else if (rule.getName().matches("is1")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertTrue(rule.getReturnValue().isSingleValue());
        assertEquals(1, rule.getReturnValue().getValueRange().getMin());
        assertEquals(1, rule.getReturnValue().getValueRange().getMax());
        assertEquals(1, rule.getReturnValue().getSingleValue());
      } else if (rule.getName().matches("is2To4")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertTrue(rule.getReturnValue().isValueRange());
        assertFalse(rule.getReturnValue().isField());
        assertFalse(rule.getReturnValue().isSingleValue());
        assertEquals(2, rule.getReturnValue().getValueRange().getMin());
        assertEquals(4, rule.getReturnValue().getValueRange().getMax());
      } else if (rule.getName().matches("isField")) {
        assertFalse(rule.getReturnValue().isBoolean());
        assertFalse(rule.getReturnValue().isValueRange());
        assertTrue(rule.getReturnValue().isField());
        assertEquals("com.google.C", rule.getReturnValue().getField().clazz.toString());
        assertEquals("int", rule.getReturnValue().getField().type.toString());
        assertEquals("X", rule.getReturnValue().getField().name.toString());
      }
    });
  }

  @Test
  public void testAdaptClassStrings() throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(dexItemFactory, reporter);
    String adaptClassStrings = "-adaptclassstrings !foobar,*bar";
    parser.parse(createConfigurationForTesting(ImmutableList.of(adaptClassStrings)));
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
    ProguardConfiguration config = parser.getConfig();
    assertTrue(
        config.getAdaptClassStrings().matches(dexItemFactory.createType("Lboobaz;")));
    assertTrue(
        config.getAdaptClassStrings().matches(dexItemFactory.createType("Lboobaz;")));
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
    ProguardConfiguration config = parser.getConfig();
    assertTrue(
        config.getAdaptClassStrings().matches(dexItemFactory.createType("Lboobaz;")));
    assertTrue(
        config.getAdaptClassStrings().matches(dexItemFactory.createType("Lboobaz;")));
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
    ProguardConfiguration config = parser.getConfig();
    assertFalse(config.isObfuscating());
  }

  @Test
  public void parseRepackageClassesEmpty() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PACKAGE_OBFUSCATION_1));
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
    ProguardConfiguration config = parser.getConfig();
    assertEquals(PackageObfuscationMode.FLATTEN, config.getPackageObfuscationMode());
    assertNotNull(config.getPackagePrefix());
    assertEquals("p.q.r", config.getPackagePrefix());
  }

  @Test
  public void flattenPackageHierarchyCannotOverrideRepackageClasses()
      throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PACKAGE_OBFUSCATION_5));
    ProguardConfiguration config = parser.getConfig();
    assertEquals(PackageObfuscationMode.REPACKAGE, config.getPackageObfuscationMode());
    assertNotNull(config.getPackagePrefix());
    assertEquals("top", config.getPackagePrefix());
  }

  @Test
  public void repackageClassesOverridesFlattenPackageHierarchy()
      throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PACKAGE_OBFUSCATION_6));
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
    new ProguardConfigurationParser(new DexItemFactory(), reporter)
        .parse(Paths.get(INCLUDING));
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
    } catch (AbortException e) {
      assertEquals(1, handler.errors.size());
      return;
    }
    fail();
  }

  @Test
  public void parseSeeds() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(SEEDS));
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintSeeds());
    assertNull(config.getSeedFile());
  }

  @Test
  public void parseSeeds2() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(SEEDS_2));
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintSeeds());
    assertNotNull(config.getSeedFile());
  }

  @Test
  public void parseVerbose() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(VERBOSE));
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isVerbose());
  }

  @Test
  public void parseKeepdirectories() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(KEEPDIRECTORIES));
  }

  @Test
  public void parseDontshrink() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_SHRINK));
    ProguardConfiguration config = parser.getConfig();
    assertFalse(config.isShrinking());
  }

  @Test
  public void parseDontSkipNonPublicLibraryClasses() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_SKIP_NON_PUBLIC_LIBRARY_CLASSES));
  }

  @Test
  public void parseDontskipnonpubliclibraryclassmembers() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_SKIP_NON_PUBLIC_LIBRARY_CLASS_MEMBERS));
  }

  @Test
  public void parseIdentifiernamestring() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    Path source = Paths.get(IDENTIFIER_NAME_STRING);
    parser.parse(source);
    assertEquals(0, handler.infos.size());
    assertEquals(0, handler.warnings.size());
  }

  @Test
  public void parseOverloadAggressively() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(OVERLOAD_AGGRESIVELY));
  }

  @Test
  public void parseDontOptimize() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_OPTIMIZE));
    ProguardConfiguration config = parser.getConfig();
  }

  @Test
  public void parseDontOptimizeOverridesPasses() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(DONT_OPTIMIZE_OVERRIDES_PASSES));
    ProguardConfiguration config = parser.getConfig();
  }

  @Test
  public void parseOptimizationPasses() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(OPTIMIZATION_PASSES));
    ProguardConfiguration config = parser.getConfig();
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
  }

  @Test
  public void parsePrintUsage() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PRINT_USAGE));
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintUsage());
    assertNull(config.getPrintUsageFile());
  }

  @Test
  public void parsePrintUsageToFile() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(PRINT_USAGE_TO_FILE));
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isPrintUsage());
    assertNotNull(config.getPrintUsageFile());
  }

  @Test
  public void parseTarget() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(Paths.get(TARGET));
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
  }

  @Test
  public void testRenameSourceFileAttribute() throws Exception {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(new DexItemFactory(), reporter);
    String config1 = "-renamesourcefileattribute PG\n";
    String config2 = "-keepattributes SourceFile,SourceDir\n";
    parser.parse(createConfigurationForTesting(ImmutableList.of(config1, config2)));
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
    assertEquals(
        ProguardKeepAttributes.fromPatterns(expected),
        parser.getConfig().getKeepAttributes());
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
    ProguardConfiguration config = parser.getConfig();
    assertTrue(config.isKeepParameterNames());

    parser = new ProguardConfigurationParser(new DexItemFactory(), reporter);
    parser.parse(createConfigurationForTesting(ImmutableList.of(
        "-keepparameternames"
    )));
    parser.parse(createConfigurationForTesting(ImmutableList.of(
        "-dontobfuscate"
    )));
    config = parser.getConfig();
    assertTrue(config.isKeepParameterNames());
  }

  private Diagnostic checkDiagnostic(List<Diagnostic> diagnostics, Path path, int lineStart,
      int columnStart, String... messageParts) {
    assertEquals(1, diagnostics.size());
    Diagnostic diagnostic = diagnostics.get(0);
    assertEquals(path, ((PathOrigin) diagnostic.getLocation().getOrigin()).getPath());
    assertEquals(new TextRangeLocation.TextPosition(lineStart, columnStart),
        ((TextRangeLocation) diagnostic.getLocation()).getStart());
    for (String part:messageParts) {
      assertTrue(diagnostic.getDiagnosticMessage()+ "doesn't contain \"" + part + "\"",
          diagnostic.getDiagnosticMessage().contains(part));
    }
    return diagnostic;
  }
}
