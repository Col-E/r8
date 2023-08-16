// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AssertionUtils;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GMSCoreLatestTest extends GMSCoreCompilationTestBase {

  private static final Path base = Paths.get(ToolHelper.THIRD_PARTY_DIR + "gmscore/latest/");

  private static Path sanitizedLibrary;
  private static Path sanitizedProguardConfiguration;

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.L).build();
  }

  public GMSCoreLatestTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @BeforeClass
  public static void setup() throws Exception {
    LibrarySanitizer librarySanitizer =
        new LibrarySanitizer(getStaticTemp())
            .addProguardConfigurationFiles(base.resolve(PG_CONF))
            .sanitize();
    sanitizedLibrary = librarySanitizer.getSanitizedLibrary();
    sanitizedProguardConfiguration = librarySanitizer.getSanitizedProguardConfiguration();
  }

  @Test
  public void testR8Determinism() throws Exception {
    Map<String, String> idsRoundOne = new ConcurrentHashMap<>();
    R8TestCompileResult compileResult =
        compileWithR8(
            builder ->
                builder.addOptionsModification(
                    options -> {
                      options.testing.processingContextsConsumer =
                          id -> assertNull(idsRoundOne.put(id, id));
                    }));

    compileResult.runDex2Oat(parameters.getRuntime()).assertNoVerificationErrors();

    Map<String, String> idsRoundTwo = new ConcurrentHashMap<>();
    R8TestCompileResult otherCompileResult =
        compileWithR8(
            builder ->
                builder.addOptionsModification(
                    options -> {
                      options.testing.processingContextsConsumer =
                          id -> {
                            AssertionUtils.assertNotNull(idsRoundOne.get(id));
                            assertNull(idsRoundTwo.put(id, id));
                          };
                    }));

    // Verify that the result of the two compilations was the same.
    assertEquals(
        Collections.emptySet(),
        Sets.symmetricDifference(idsRoundOne.keySet(), idsRoundTwo.keySet()));
    assertIdenticalApplications(compileResult.getApp(), otherCompileResult.getApp());
    assertEquals(compileResult.getProguardMap(), otherCompileResult.getProguardMap());
  }

  private R8TestCompileResult compileWithR8(ThrowableConsumer<R8FullTestBuilder> configuration)
      throws Exception {
    // Program files are included in Proguard configuration.
    return testForR8(Backend.DEX)
        .addLibraryFiles(sanitizedLibrary)
        .addKeepRuleFiles(sanitizedProguardConfiguration)
        .addDontWarn(
            "android.hardware.location.IActivityRecognitionHardware",
            "android.hardware.location.IFusedLocationHardware",
            "android.location.FusedBatchOptions",
            "android.location.GeocoderParams$1",
            "android.location.ILocationManager",
            "android.media.IRemoteDisplayCallback",
            "android.media.RemoteDisplayState$RemoteDisplayInfo",
            "com.android.internal.location.ProviderProperties",
            "com.android.internal.location.ProviderRequest",
            "com.google.protobuf.java_com_google_android_libraries_performance_primes_release"
                + "_gmscore__primes_bcdd2915GeneratedExtensionRegistryLite$Loader")
        .allowDiagnosticMessages()
        .allowUnusedProguardConfigurationRules()
        .setMinApi(parameters)
        .apply(configuration)
        .compile()
        .assertAllInfoMessagesMatch(
            anyOf(
                containsString("Ignoring option: -optimizations"),
                containsString(
                    "Invalid parameter counts in MethodParameter attributes. "
                        + "This is likely due to Proguard having removed a parameter."),
                containsString("Methods with invalid MethodParameter attributes:"),
                containsString("Proguard configuration rule does not match anything")))
        .assertAllWarningMessagesMatch(
            anyOf(
                containsString("Expected stack map table for method with non-linear control flow."),
                containsString("Ignoring option: -outjars"),
                containsString(
                    "Unverifiable code in `"
                        + "java.net.Socket com.google.android.gms.org.conscrypt."
                        + "KitKatPlatformOpenSSLSocketAdapterFactory.wrap("
                        + "com.google.android.gms.org.conscrypt.OpenSSLSocketImpl)`"),
                containsString(
                    "Unverifiable code in `"
                        + "java.net.Socket com.google.android.gms.org.conscrypt."
                        + "PreKitKatPlatformOpenSSLSocketAdapterFactory.wrap("
                        + "com.google.android.gms.org.conscrypt.OpenSSLSocketImpl)`"),
                containsString(
                    "Unverifiable code in `"
                        + "android.content.pm.PackageStats com.google.android.libraries.performance"
                        + ".primes.metriccapture.PackageStatsCapture"
                        + ".getPackageStatsUsingInternalAPI(android.content.Context, long, "
                        + "com.google.android.libraries.performance.primes.metriccapture."
                        + "PackageStatsCapture$PackageStatsInvocation[])`"),
                allOf(
                    startsWith(
                        "Rule matches the static final field "
                            + "`java.lang.String com.google.protobuf."),
                    containsString("which may have been inlined: -identifiernamestring"))));
  }
}
