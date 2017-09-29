# D8 dexer and R8 shrinker

The R8 repo contains two tools:

- D8 is a dexer that converts java byte code to dex code.
- R8 is a java program shrinking and minification tool that converts java byte
  code to optimized dex code.

D8 is a replacement for the DX dexer and R8 is a replacement for
the [Proguard](https://www.guardsquare.com/en/proguard) shrinking and
minification tool.

## Downloading and building

The R8 project uses [`depot_tools`](https://www.chromium.org/developers/how-tos/install-depot-tools)
from the chromium project to manage dependencies. Install `depot_tools` and add it to
your path before proceeding.

The R8 project uses Java 8 language features and requires a Java 8 compiler
and runtime system.

Typical steps to download and build:


    $ git clone https://r8.googlesource.com/r8
    $ cd r8
    $ tools/gradle.py d8 r8

The `tools/gradle.py` script will bootstrap using depot_tools to download
a version of gradle to use for building on the first run. This will produce
two jar files: `build/libs/d8.jar` and `build/libs/r8.jar`.

## Running D8

The D8 dexer has a simple command-line interface with only a few options.

The most important option is whether to build in debug or release mode.  Debug
is the default mode and includes debugging information in the resulting dex
files. Debugging information contains information about local variables used
when debugging dex code. This information is not useful when shipping final
Android apps to users and therefore, final builds should use the `--release`
flag to remove this debugging information to produce smaller dex files.

Typical invocations of D8 to produce dex file(s) in the out directoy:

Debug mode build:

    $ java -jar build/libs/d8.jar --output out input.jar

Release mode build:

    $ java -jar build/libs/d8.jar --release --output out input.jar

The full set of D8 options can be obtained by running the command line tool with
the `--help` option.

## Running R8

R8 is a [Proguard](https://www.guardsquare.com/en/proguard) replacement for
whole-program optimization, shrinking and minification. R8 uses the Proguard
keep rule format for specifying the entry points for an application.

Typical invocations of R8 to produce optimized dex file(s) in the out directory:

    $ java -jar build/libs/r8.jar --release --output out --pg-conf proguard.cfg input.jar

The full set of R8 options can be obtained by running the command line tool with
the `--help` option.

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

Once the license agreement is in place, you can upload your patches
using 'git cl' which is available in depot_tools. Once you have a
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

For questions, bug reports and other issues reach out to us at
r8-dev@googlegroups.com.
