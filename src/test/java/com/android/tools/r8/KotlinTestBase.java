// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.SemanticVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.rules.TemporaryFolder;

public abstract class KotlinTestBase extends TestBase {

  protected static final String checkParameterIsNotNullSignature =
      "void kotlin.jvm.internal.Intrinsics.checkParameterIsNotNull("
          + "java.lang.Object, java.lang.String)";
  protected static final String throwParameterIsNotNullExceptionSignature =
      "void kotlin.jvm.internal.Intrinsics.throwParameterIsNullException(java.lang.String)";
  public static final String METADATA_DESCRIPTOR = "Lkotlin/Metadata;";
  public static final String METADATA_TYPE =
      DescriptorUtils.descriptorToJavaType(METADATA_DESCRIPTOR);

  private static final String RSRC = "kotlinR8TestResources";

  private static final Map<String, KotlinCompileMemoizer> compileMemoizers = new HashMap<>();

  protected final KotlinCompiler kotlinc;
  protected final KotlinTargetVersion targetVersion;
  protected final KotlinTestParameters kotlinParameters;

  protected KotlinTestBase(KotlinTestParameters kotlinParameters) {
    this.targetVersion = kotlinParameters.getTargetVersion();
    this.kotlinc = kotlinParameters.getCompiler();
    this.kotlinParameters = kotlinParameters;
  }

  public static CfRuntime getKotlincHostRuntime(TestRuntime runtime) {
    return runtime.isCf() ? runtime.asCf() : TestRuntime.getCheckedInJdk9();
  }

  protected static List<Path> getKotlinFilesInTestPackage(Package pkg) throws IOException {
    String folder = DescriptorUtils.getBinaryNameFromJavaType(pkg.getName());
    return Files.walk(Paths.get(ToolHelper.TESTS_DIR, "java", folder))
        .filter(path -> path.toString().endsWith(".kt"))
        .collect(Collectors.toList());
  }

  protected static Path getKotlinFileInTestPackage(Package pkg, String fileName) {
    String folder = DescriptorUtils.getBinaryNameFromJavaType(pkg.getName());
    return getKotlinFileInTest(folder, fileName);
  }

  protected static Path getKotlinFileInTest(String folder, String fileName) {
    return Paths.get(ToolHelper.TESTS_DIR, "java", folder, fileName + FileUtils.KT_EXTENSION);
  }

  public static Path getKotlinFileInResource(String folder, String fileName) {
    return Paths.get(ToolHelper.TESTS_DIR, RSRC, folder, fileName + FileUtils.KT_EXTENSION);
  }

  public static List<Path> getKotlinFilesInResource(String folder) {
    try {
      return Files.walk(Paths.get(ToolHelper.TESTS_DIR, RSRC, folder))
          .filter(path -> path.toString().endsWith(".kt") || path.toString().endsWith(".java"))
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Path getKotlinResourcesFolder() {
    return Paths.get(ToolHelper.TESTS_DIR, RSRC);
  }

  protected Path getJavaJarFile(String folder) {
    return Paths.get(ToolHelper.THIRD_PARTY_DIR, RSRC, folder + FileUtils.JAR_EXTENSION);
  }

  protected KotlinCompilerTool kotlinCompilerTool() {
    return KotlinCompilerTool.create(CfRuntime.getCheckedInJdk9(), temp, kotlinc, targetVersion);
  }

  public static KotlinCompileMemoizer getCompileMemoizer(Path... source) {
    return new KotlinCompileMemoizer(Arrays.asList(source));
  }

  public static KotlinCompileMemoizer getCompileMemoizer(Collection<Path> sources) {
    assert sources.size() > 0;
    return new KotlinCompileMemoizer(sources);
  }

  public static KotlinCompileMemoizer getCompileMemoizer(
      Collection<Path> sources, CfRuntime runtime, TemporaryFolder temporaryFolder) {
    return new KotlinCompileMemoizer(sources, runtime, temporaryFolder);
  }

  public static KotlinCompileMemoizer getCompileMemoizer(
      Collection<Path> sources, String sharedFolder) {
    return compileMemoizers.computeIfAbsent(
        sharedFolder, ignore -> new KotlinCompileMemoizer(sources));
  }

  public ThrowableConsumer<R8TestCompileResult> assertUnusedKeepRuleForKotlinMetadata(
      boolean condition) {
    return compileResult -> {
      if (!condition) {
        return;
      }
      compileResult
          .getDiagnosticMessages()
          .assertInfoThatMatches(
              diagnosticMessage(
                  containsString(
                      "Proguard configuration rule does not match anything: `-keep class"
                          + " kotlin.Metadata")));
    };
  }

  public static ThrowableConsumer<R8FullTestBuilder>
      configureForLibraryWithEmbeddedProguardRules() {
    // When running on main explicitly configure max compiler version for checking against
    // embeded proguard rules.
    return builder ->
        builder.applyIf(
            Version.isMainVersion(), b -> b.setFakeCompilerVersion(SemanticVersion.max()));
  }

  public static class KotlinCompileMemoizer {

    private final Collection<Path> sources;
    private final CfRuntime runtime;
    private final TemporaryFolder temporaryFolder;

    private Consumer<KotlinCompilerTool> kotlinCompilerToolConsumer = x -> {};
    private final Map<KotlinCompiler, Map<KotlinTargetVersion, Path>> compiledPaths =
        new IdentityHashMap<>();

    public KotlinCompileMemoizer(Collection<Path> sources) {
      this(sources, CfRuntime.getCheckedInJdk9(), null);
    }

    public KotlinCompileMemoizer(
        Collection<Path> sources, CfRuntime runtime, TemporaryFolder temporaryFolder) {
      this.sources = sources;
      this.runtime = runtime;
      this.temporaryFolder = temporaryFolder;
    }

    public KotlinCompileMemoizer configure(Consumer<KotlinCompilerTool> consumer) {
      this.kotlinCompilerToolConsumer = consumer;
      return this;
    }

    public Path getForConfiguration(KotlinTestParameters kotlinParameters) {
      return getForConfiguration(
          kotlinParameters.getCompiler(), kotlinParameters.getTargetVersion());
    }

    public Path getForConfiguration(KotlinCompiler compiler, KotlinTargetVersion targetVersion) {
      Map<KotlinTargetVersion, Path> kotlinTargetVersionPathMap = compiledPaths.get(compiler);
      if (kotlinTargetVersionPathMap == null) {
        kotlinTargetVersionPathMap = new IdentityHashMap<>();
        compiledPaths.put(compiler, kotlinTargetVersionPathMap);
      }
      return kotlinTargetVersionPathMap.computeIfAbsent(
          targetVersion,
          ignored -> {
            try {
              KotlinCompilerTool kotlinc =
                  temporaryFolder == null
                      ? kotlinc(compiler, targetVersion)
                      : kotlinc(runtime, temporaryFolder, compiler, targetVersion);
              return kotlinc.addSourceFiles(sources).apply(kotlinCompilerToolConsumer).compile();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
    }
  }
}
