#!/usr/bin/env python3
# Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import sys
import threading
from threading import Thread
import traceback

# A thread that is given a list of jobs. The thread will repeatedly take a job
# from the list of jobs (which is shared with other threads) and execute it
# until there is no more jobs.
#
# If stop_on_first_failure is True, then the thread will exit upon the first
# failing job. The thread will then clear the jobs to ensure that all other
# workers will also terminate after completing there current job.
#
# Each job is a lambda that takes the worker_id as an argument. To guarantee
# termination each job must itself terminate (i.e., each job is responsible for
# setting an appropriate timeout).
class WorkerThread(Thread):

  # The initialization of a WorkerThread is never run concurrently with the
  # initialization of other WorkerThreads.
  def __init__(self, jobs, jobs_lock, stop_on_first_failure, worker_id):
    Thread.__init__(self)
    self.jobs = jobs
    self.jobs_lock = jobs_lock
    self.number_of_jobs = len(jobs)
    self.stop_on_first_failure = stop_on_first_failure
    self.success = True
    self.worker_id = worker_id

  def run(self):
    print_thread("Starting worker", self.worker_id)
    while True:
      (job, job_id) = self.take_job(self.jobs, self.jobs_lock)
      if job is None:
        break
      try:
        print_thread(
            "Starting job %s/%s" % (job_id, self.number_of_jobs),
            self.worker_id)
        exit_code = job(self.worker_id)
        print_thread(
            "Job %s finished with exit code %s"
                % (job_id, exit_code),
            self.worker_id)
        if exit_code:
          self.success = False
          if self.stop_on_first_failure:
            self.clear_jobs(jobs, jobs_lock)
            break
      except:
        print_thread("Job %s crashed" % job_id, self.worker_id)
        print_thread(traceback.format_exc(), self.worker_id)
        self.success = False
        if self.stop_on_first_failure:
          self.clear_jobs(jobs, jobs_lock)
          break
    print_thread("Exiting", self.worker_id)

  def take_job(self, jobs, jobs_lock):
    jobs_lock.acquire()
    job_id = self.number_of_jobs - len(jobs) + 1
    job = jobs.pop(0) if jobs else None
    jobs_lock.release()
    return (job, job_id)

  def clear_jobs(self, jobs, jobs_lock):
    jobs_lock.acquire()
    jobs.clear()
    jobs_lock.release()

def run_in_parallel(jobs, number_of_workers, stop_on_first_failure):
  assert number_of_workers > 0
  if number_of_workers > len(jobs):
    number_of_workers = len(jobs)
  if number_of_workers == 1:
    return run_in_sequence(jobs, stop_on_first_failure)
  jobs_lock = threading.Lock()
  threads = []
  for worker_id in range(1, number_of_workers + 1):
    threads.append(
        WorkerThread(jobs, jobs_lock, stop_on_first_failure, worker_id))
  for thread in threads:
    thread.start()
  for thread in threads:
    thread.join()
  for thread in threads:
    if not thread.success:
      return 1
  return 0

def run_in_sequence(jobs, stop_on_first_failure):
  combined_exit_code = 0
  worker_id = None
  for job in jobs:
    try:
      exit_code = job(worker_id)
      if exit_code:
        combined_exit_code = exit_code
        if stop_on_first_failure:
          break
    except:
      print(traceback.format_exc())
      combined_exit_code = 1
      if stop_on_first_failure:
        break
  return combined_exit_code

def print_thread(msg, worker_id):
  if worker_id is None:
    print(msg)
  else:
    print('WORKER %s: %s' % (worker_id, msg))