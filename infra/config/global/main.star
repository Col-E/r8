#!/usr/bin/env lucicfg

luci.project(
    name = "r8",
    buildbucket = "cr-buildbucket.appspot.com",
    logdog = "luci-logdog.appspot.com",
    milo = "luci-milo.appspot.com",
    notify = "luci-notify.appspot.com",
    scheduler = "luci-scheduler.appspot.com",
    swarming = "chrome-swarming.appspot.com",
    acls = [
        acl.entry(
            [
                acl.BUILDBUCKET_READER,
                acl.LOGDOG_READER,
                acl.PROJECT_CONFIGS_READER,
                acl.SCHEDULER_READER,
            ],
            groups = ["all"],
        ),
        acl.entry(
            [
                acl.BUILDBUCKET_TRIGGERER,
            ],
            groups = [
                "luci-scheduler@appspot.gserviceaccount.com",
                "project-r8-committers"
    ],
        ),

    ]
)

luci.bucket(name = "ci")

luci.milo()

luci.notifier(
  name = "r8-failures",
  on_failure = True,
  on_new_failure = True,
  notify_blamelist = True
)

luci.gitiles_poller(
  name = "main-gitiles-trigger",
  bucket = "ci",
  repo = "https://r8.googlesource.com/r8"
)

luci.gitiles_poller(
  name = "branch-gitiles-trigger",
  bucket = "ci",
  repo = "https://r8.googlesource.com/r8",
  # Version branches are named d8-x.y (up until d8-1.5) or just x.y (from 1.6)
  refs = ["regexp:refs/heads/(?:d8-)?[0-9]+\\.[0-9]+(\\.[0-9]+)?"],
  path_regexps = ["src/main/java/com/android/tools/r8/Version.java"]
)

luci.console_view(
    name = "main",
    title = "R8 Main Console",
    repo = "https://r8.googlesource.com/r8",
    refs = ["regexp:refs/heads/.*"]
)

def builder_view(name, category, short_name):
    return luci.console_view_entry(
        console_view = "main",
        builder = name,
        category = category,
        short_name = short_name,
    )

luci.recipe(
      name="rex",
      cipd_package = "infra_internal/recipe_bundles/" +
          "chrome-internal.googlesource.com/chrome/" +
	  "tools/build_limited/scripts/slave",
      cipd_version = "refs/heads/master"
)

common_test_options = [
    "--tool=r8",
    "--no_internal",
    "--one_line_per_test",
    "--archive_failures"
]

def get_dimensions(windows=False, jctf=False, internal=False):
  dimensions = {
    "cores" : "2" if internal else "8",
    "cpu" : "x86-64",
    "pool" : "luci.r8.ci"
  }
  if windows:
    dimensions["os"] = "windows-10"
  else:
    dimensions["os"] = "Ubuntu-16.04"
  if jctf:
    dimensions["jctf"] = "true"
  if internal:
    dimensions["internal"] = "true"
  return dimensions

def r8_tester(name,
    test_options,
    dimensions=None,
    execution_timeout=time.hour * 6,
    expiration_timeout=time.hour * 35):
  dimensions = dimensions if dimensions else get_dimensions()
  luci.builder(
      name = name,
      bucket = "ci",
      service_account = "r8-ci-builder@chops-service-accounts.iam.gserviceaccount.com",
      swarming_tags = ["vpython:native-python-wrapper"],
      execution_timeout=execution_timeout,
      expiration_timeout=expiration_timeout,
      executable = "rex",
      dimensions = dimensions,
      triggered_by = ["main-gitiles-trigger"],
      properties = {
          "test_options" : test_options
      },
      notifies = ["r8-failures"]
  )
  builder_view(name, "R8", name.split("-")[-1])

  # Branch version
  release_name = name + "_release"
  luci.builder(
      name = release_name,
      bucket = "ci",
      service_account = "r8-ci-builder@chops-service-accounts.iam.gserviceaccount.com",
      swarming_tags = ["vpython:native-python-wrapper"],
      execution_timeout=execution_timeout,
      expiration_timeout=expiration_timeout,
      executable = "rex",
      dimensions = dimensions,
      triggered_by = ["branch-gitiles-trigger"],
      properties = {
          "test_options" : test_options
      },
      notifies = ["r8-failures"]
  )
  builder_view(release_name, "R8 release", name.split("-")[-1])

def r8_tester_with_default(name, test_options, dimensions=None):
  r8_tester(name, test_options + common_test_options, dimensions)

luci.builder(
    name = "archive",
    bucket = "ci",
    dimensions = get_dimensions(),
    triggered_by = ["main-gitiles-trigger"],
    executable = "rex",
    execution_timeout = time.minute * 30,
      triggering_policy = scheduler.policy(
        kind = scheduler.GREEDY_BATCHING_KIND,
        max_batch_size = 1,
        max_concurrent_invocations = 3
    ),
    properties = {"archive": True}
)
builder_view("archive", "R8", "archive")

luci.builder(
    name = "archive_release",
    bucket = "ci",
    dimensions = get_dimensions(),
    triggered_by = ["branch-gitiles-trigger"],
    execution_timeout = time.minute * 30,
    executable = "rex",
    triggering_policy = scheduler.policy(
        kind = scheduler.GREEDY_BATCHING_KIND,
        max_batch_size = 1,
        max_concurrent_invocations = 3
    ),
    properties = {"archive": True}
)
builder_view("archive_release", "R8", "archive_release")

r8_tester_with_default("linux-dex_default", ["--runtimes=dex-default"])
r8_tester_with_default("linux-none", ["--runtimes=none"])
r8_tester_with_default("linux-jdk8", ["--runtimes=jdk8"])
r8_tester_with_default("linux-jdk9", ["--runtimes=jdk9"])
r8_tester_with_default("linux-jdk11", ["--runtimes=jdk11"])

r8_tester_with_default("linux-internal", ["--runtimes=jdk11"])

r8_tester_with_default("linux-android-4.0.4",
    ["--dex_vm=4.0.4", "--all_tests"])
r8_tester_with_default("linux-android-4.4.4",
    ["--dex_vm=4.4.4", "--all_tests"])
r8_tester_with_default("linux-android-5.1.1",
    ["--dex_vm=5.1.1", "--all_tests"])
r8_tester_with_default("linux-android-6.0.1",
    ["--dex_vm=6.0.1", "--all_tests"])
r8_tester_with_default("linux-android-7.0.0",
    ["--dex_vm=7.0.0", "--all_tests"])
r8_tester_with_default("linux-android=8.1.0",
    ["--dex_vm=8.1.0", "--all_tests"])
r8_tester_with_default("linux-android=9.0.0",
    ["--dex_vm=9.0.0", "--all_tests"])
r8_tester_with_default("linux-android=10.0.0",
    ["--dex_vm=10.0.0", "--all_tests"])

r8_tester_with_default("windows", ["--all_tests"],
    dimensions=get_dimensions(windows=True))
