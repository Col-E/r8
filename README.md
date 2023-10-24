# D8 dexer and R8 shrinker

The R8 repo contains two tools:

- D8 is a dexer that converts java byte code to dex code.
- R8 is a java program shrinking and minification tool that converts java byte
  code to optimized dex code.

D8 is a replacement for the DX dexer and R8 is an alternative to
the [ProGuard](https://www.guardsquare.com/en/proguard) shrinking and
minification tool.

## Obtaining prebuilts

There are several places to obtain a prebuilt version without building it
yourself.

The stable release versions shipped with Android Studio are available from
the Google Maven repository, see
https://maven.google.com/web/index.html#com.android.tools:r8.

Our [CI](https://ci.chromium.org/p/r8/g/main/console) builds for each commit and
stores all build artifacts in Google Cloud Storage in the bucket r8-releases.

To obtain a prebuild from the CI for a specifc version (including both
stable and `-dev` versions), download from the following URL:

    https://storage.googleapis.com/r8-releases/raw/<version>/r8lib.jar

To obtain a prebuild from the CI for a specifc main branch hash, download from the
following URL:

    https://storage.googleapis.com/r8-releases/raw/main/<hash>/r8lib.jar

The prebuilt JARs have been processed by R8, and for each build the corresponding
mapping file is located together with the prebuild under the name `r8lib.jar.map`.

To get prebuilds which has not been processed by R8 replace `r8lib.jar` with `r8.jar`
in the URLs above.

The Google Cloud Storage bucket r8-releases can also be used as a simple
Maven repository using the following in a Gradle build file:

    maven {
        url = uri("https://storage.googleapis.com/r8-releases/raw")
    }

See [Running D8](#running-d8) and [Running R8](#running-r8) below on how to invoke
D8 and R8 using the obtained `r8lib.jar` in place of `build/libs/r8.jar`.

## Downloading source and building

The R8 project uses [`depot_tools`](https://www.chromium.org/developers/how-tos/install-depot-tools)
from the chromium project to manage dependencies. Install `depot_tools` and add it to
your path before proceeding.

The R8 project uses Java 8 language features and requires a Java 8 compiler
and runtime system.

Typical steps to download and build:


    $ git clone https://r8.googlesource.com/r8
    $ cd r8
    $ tools/gradle.py r8

The `tools/gradle.py` script will bootstrap using depot_tools to download
a version of gradle to use for building on the first run. This will produce
a jar file: `build/libs/r8.jar` which contains both R8 and D8.

## <a name="running-d8"></a>Running D8

The D8 dexer has a simple command-line interface with only a few options.
D8 consumes class files and produce DEX.

The most important option is whether to build in debug or release mode.  Debug
is the default mode and includes debugging information in the resulting dex
files. Debugging information contains information about local variables used
when debugging dex code. This information is not useful when shipping final
Android apps to users and therefore, final builds should use the `--release`
flag to remove this debugging information to produce smaller dex files.

Typical invocations of D8 to produce dex file(s) in the out directoy:

Debug mode build:

    $ java -cp build/libs/r8.jar com.android.tools.r8.D8 \
           --min-api <min-api> \
           --output out \
           --lib <android.jar/rt.jar> \
           input.jar

Release mode build:

    $ java -cp build/libs/r8.jar com.android.tools.r8.D8
           --release \
           --min-api <min-api> \
           --output out \
           --lib <android.jar/rt.jar> \
           input.jar

See [Running R8](#running-r8) for information on options `--min-api` and `--lib`.

The full set of D8 options can be obtained by running the command line tool with
the `--help` option.

## <a name="running-r8"></a>Running R8

R8 is a whole-program optimizing compiler with focus on shrinking the size of
programs. R8 uses the
[ProGuard configuration format](https://www.guardsquare.com/manual/configuration/usage)
for configuring the whole-program optimization including specifying the entry points
for an application.

R8 consumes class files and can output either DEX for Android apps or class files
for Java apps.

Typical invocations of R8 to produce optimized DEX file(s) in the `out` directory:

    $ java -cp build/libs/r8.jar com.android.tools.r8.R8 \
           --release \
           --min-api <min-api> \
           --output out \
           --pg-conf proguard.cfg \
           --lib <android.jar/rt.jar> \
           input.jar

This produce DEX targeting Android devices with a API level of `<min-api>` and above.

The option `--lib` is passing the bootclasspath for the targeted runtime.
For targeting Android use an `android.jar` from the Android Platform SDK, typically
located in `~/Android/Sdk/platforms/android-XX`, where `XX` should be the latest
Android version.

To produce class files pass the option `--classfile` and leave out `--min-api <min-api>`.
This invocation will provide optimized Java classfiles in `output.jar`:

    $ java -cp build/libs/r8.jar com.android.tools.r8.R8 \
           --release \
           --classfile \
           --output output.jar \
           --pg-conf proguard.cfg \
           --lib <android.jar/rt.jar> \
           input.jar


When producing output targeted for the JVM one can pass `$JAVA_HOME` to `-lib`.
R8 will then locate the Java bootclasspath from there.

The full set of R8 options can be obtained by running the command line tool with
the `--help` option.

R8 is not command line compatible with ProGuard, for instance keep rules cannot be passed
on the command line, but have to be passed in a file using the `--pg-conf` option.

## <a name="replacing-r8-in-agp"></a>Replacing R8 in Android Gradle plugin

Android Gradle plugin (AGP) ships with R8 embedded (as part of the `builder.jar` from
`com.android.tools.build:builder:<agp version>` on https://maven.google.com).

To override the embedded version with a prebuilt R8 with version `<version>`, merge
the following into the top level `settings.gradle` or `settings.gradle.kts`:
```
pluginManagement {
    buildscript {
        repositories {
            mavenCentral()
            maven {
                url = uri("https://storage.googleapis.com/r8-releases/raw")
            }
        }
        dependencies {
            classpath("com.android.tools:r8:<version>")
        }
    }
}
```
To override the embedded version with a downloaded or freshly built `<path>/r8.jar` merge
the following into the top level `settings.gradle` or `settings.gradle.kts`:
```
pluginManagement {
    buildscript {
        dependencies {
            classpath(files("<path>/r8.jar"))
        }
    }
}
```

## Testing

Typical steps to run tests:

    $ tools/test.py --no_internal

The `tools/test.py` script will use depot_tools to download a lot of tests
and test dependencies on the first run. This includes prebuilt version of the
art runtime on which to validate the produced dex code.

## Contributing

In order to contribute to D8/R8 you have to sign the
[Contributor License Agreement](https://cla.developers.google.com/about/google-individual).
If your contribution is owned by your employer you need the
[Corporate Contributor License Agreement](https://cla.developers.google.com/about/google-corporate).

Once the license agreement is in place, please send an email to
[r8-dev@googlegroups.com](mailto:r8-dev@googlegroups.com) to be added as a
contributor.

After being added as a contributer you can upload your patches
using `git cl` which is available in `depot_tools`. Once you have a
change that you are happy with you should make sure that it passes
all tests and then upload the change to our code review tool using:

    $ git cl upload

On your first upload you will be asked to acquire credentials. Follow the
instructions given by `git cl upload`.

On successful uploads a link to the code review is printed in the
output of the upload command. In the code review tool you can
assign reviewers and mark the change ready for review. At that
point the code review tool will send emails to reviewers.

## Getting help

For questions, reach out to us at
[r8-dev@googlegroups.com](mailto:r8-dev@googlegroups.com).

For D8, find known issues in the
[D8 issue tracker](https://issuetracker.google.com/issues?q=componentid:317603)
or file a new
[D8 bug report](https://issuetracker.google.com/issues/new?component=317603).

For R8, find known issues in the
[R8 issue tracker](https://issuetracker.google.com/issues?q=componentid:326788)
or file a new
[R8 bug report](https://issuetracker.google.com/issues/new?component=326788).
