# Compiler-input dumps

The D8 and R8 compilers support generating a compiler-input dump for use in
reproducing compiler issues.


## The content of a dump

The dump contains almost all of the inputs that are given to the compiler as
part of compilation. In particular, it contains *all* class definitions in the
form of Java classfiles (i.e., bytecode, not the Java source files).
In addition to the classfiles, the dump also includes Java resources, the
compiler type, version, and flags, such as `--debug` or `--release`,
main-dex lists or rules, and more. For R8 the dump also contains the full
concatenation of all keep rules.

The dump is a zip file containing the above. You should unzip it and review
the content locally. The program, classpath and library content will be in
nested zip files. The remaining content is in plain text files.


## Generating a dump

To generate a dump file, run the compiler with the
`com.android.tools.r8.dumpinputtofile` system property set:

```
java -cp r8.jar -Dcom.android.tools.r8.dumpinputtofile=mydump.zip com.android.tools.r8.D8 <other-compiler-args>
```

This will generate a dump file `mydump.zip` and exit the compiler with a
non-zero exit value printing an error message about the location of the dump
file that was written.

For some builds, there may be many compilations taking place which cannot be
easily isolated as individual compilation steps. If so, the system property
`com.android.tools.r8.dumpinputtodirectory` can be set to a directory instead.
Doing so will dump the inputs of each compilation to the directory in
individual zip files and they will be named using a timestamp in an attempt
to maintain an order. The compiler will compile as usual thus not disrupting
the build.

### Generating a dump with Gradle and the Android Studio Plugin

To generate a dump from studio, the system property should be set for the
build command. This can typically be done by amending the command-line gradle
build-target command. Say your build target is `assembleRelease`, you would run:

```
./gradlew assembleRelease -Dorg.gradle.caching=false -Dcom.android.tools.r8.dumpinputtofile=mydump.zip --no-daemon
```

If the build is a debug build, such as `assembleDebug`, then it will likely be an
incremental D8 build in which case the best option is to provide a dump
directory and then locate the particular dump file associated with the
problematic compilation (if the compilation fails, the interesting dump will
hopefully be the last dump):

```
./gradlew assembleDebug -Dorg.gradle.caching=false -Dcom.android.tools.r8.dumpinputtodirectory=mydumps/ --no-daemon
```


## Reproducing using a dump

To reproduce a compiler issue with a dump use the script `tools/compiledump.py`
from the R8 repository. Note that the script is *not* a standalone script so you
will need a full checkout of the R8 project.

Reproducing should then be just:
```
./tools/compiledump.py -d mydump.zip
```

Depending on the compiler version that generated the dump additional flags may
be needed to specify the compiler and its version. Run `--help` for a list of
options.

The flags can also be used to override the setting specified in the dump.
Doing so allows compiling with other compiler versions, or other settings.
