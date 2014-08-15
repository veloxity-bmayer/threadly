package org.threadly.concurrent;

import java.util.concurrent.Executor;

import org.threadly.concurrent.lock.StripedLock;

/**
 * <p>TaskDistributor is designed such that tasks executed on it for a given key will run  
 * in a single threaded manner.  It needs a multi-threaded pool supplied to it, to then 
 * execute those tasks on.  While the thread which runs those tasks may be different  
 * between multiple executions, no two tasks for the same key will ever be run in parallel.</p>
 * 
 * <p>Because of that, it is recommended that the executor provided has as many possible 
 * threads as possible keys that could be provided to be run in parallel.  If this class is 
 * starved for threads some keys may continue to process new tasks, while other keys could 
 * be starved.</p>
 * 
 * <p>Assuming that the shared memory (any objects, primitives, etc) are only accessed 
 * through the same instance of {@link TaskExecutorDistributor}, and assuming that those 
 * variables are only accessed via the same key.  Then the programmer does not need to worry 
 * about synchronization, or volatile.  The {@link TaskExecutorDistributor} will handle the 
 * happens-before relationship.</p>
 * 
 * @deprecated use replacement at org.threadly.concurrent.KeyDistributedExecutor
 * 
 * @author jent - Mike Jensen
 * @since 1.0.0
 */
@Deprecated
public class TaskExecutorDistributor extends KeyDistributedExecutor {
  /**
   * Constructor to use a provided executor implementation for running tasks.  
   * 
   * This constructs with a default expected level of concurrency of 16.  This also does not 
   * attempt to have an accurate queue size for the "getTaskQueueSize" call (thus preferring 
   * high performance).
   * 
   * @param executor A multi-threaded executor to distribute tasks to.  
   *                 Ideally has as many possible threads as keys that 
   *                 will be used in parallel. 
   */
  public TaskExecutorDistributor(Executor executor) {
    super(executor);
  }
  
  /**
   * Constructor to use a provided executor implementation for running tasks.  
   * 
   * This constructor allows you to specify if you want accurate queue sizes to be 
   * tracked for given thread keys.  There is a performance hit associated with this, 
   * so this should only be enabled if "getTaskQueueSize" calls will be used.  
   * 
   * This constructs with a default expected level of concurrency of 16.
   * 
   * @param executor A multi-threaded executor to distribute tasks to.  
   *                 Ideally has as many possible threads as keys that 
   *                 will be used in parallel. 
   * @param accurateQueueSize true to make "getTaskQueueSize" more accurate
   */
  public TaskExecutorDistributor(Executor executor, boolean accurateQueueSize) {
    super(executor, accurateQueueSize);
  }
  
  /**
   * Constructor to use a provided executor implementation for running tasks.
   * 
   * This constructor allows you to provide a maximum number of tasks for a key before it 
   * yields to another key.  This can make it more fair, and make it so no single key can 
   * starve other keys from running.  The lower this is set however, the less efficient it 
   * becomes in part because it has to give up the thread and get it again, but also because 
   * it must copy the subset of the task queue which it can run.  
   * 
   * This also allows you to specify if you want accurate queue sizes to be tracked for 
   * given thread keys.  There is a performance hit associated with this, so this should 
   * only be enabled if "getTaskQueueSize" calls will be used.  
   * 
   * This constructs with a default expected level of concurrency of 16.  This also does not 
   * attempt to have an accurate queue size for the "getTaskQueueSize" call (thus preferring 
   * high performance).
   * 
   * @param executor A multi-threaded executor to distribute tasks to.  
   *                 Ideally has as many possible threads as keys that 
   *                 will be used in parallel.
   * @param maxTasksPerCycle maximum tasks run per key before yielding for other keys
   */
  public TaskExecutorDistributor(Executor executor, int maxTasksPerCycle) {
    super(executor, maxTasksPerCycle);
  }
  
  /**
   * Constructor to use a provided executor implementation for running tasks.
   * 
   * This constructor allows you to provide a maximum number of tasks for a key before it 
   * yields to another key.  This can make it more fair, and make it so no single key can 
   * starve other keys from running.  The lower this is set however, the less efficient it 
   * becomes in part because it has to give up the thread and get it again, but also because 
   * it must copy the subset of the task queue which it can run.  
   * 
   * This also allows you to specify if you want accurate queue sizes to be tracked for given 
   * thread keys.  There is a performance hit associated with this, so this should only be 
   * enabled if "getTaskQueueSize" calls will be used.
   * 
   * This constructs with a default expected level of concurrency of 16. 
   * 
   * @param executor A multi-threaded executor to distribute tasks to.  
   *                 Ideally has as many possible threads as keys that 
   *                 will be used in parallel.
   * @param maxTasksPerCycle maximum tasks run per key before yielding for other keys
   * @param accurateQueueSize true to make "getTaskQueueSize" more accurate
   */
  public TaskExecutorDistributor(Executor executor, int maxTasksPerCycle, 
                                 boolean accurateQueueSize) {
    super(executor, maxTasksPerCycle, accurateQueueSize);
  }
    
  /**
   * Constructor to use a provided executor implementation for running tasks.
   * 
   * This constructor does not attempt to have an accurate queue size for the 
   * "getTaskQueueSize" call (thus preferring high performance).
   * 
   * @param expectedParallism level of expected quantity of threads adding tasks in parallel
   * @param executor A multi-threaded executor to distribute tasks to.  
   *                 Ideally has as many possible threads as keys that 
   *                 will be used in parallel. 
   */
  public TaskExecutorDistributor(int expectedParallism, Executor executor) {
    super(expectedParallism, executor);
  }
  
  /**
   * Constructor to use a provided executor implementation for running tasks.
   * 
   * This constructor allows you to specify if you want accurate queue sizes to be 
   * tracked for given thread keys.  There is a performance hit associated with this, 
   * so this should only be enabled if "getTaskQueueSize" calls will be used.
   * 
   * @param expectedParallism level of expected quantity of threads adding tasks in parallel
   * @param executor A multi-threaded executor to distribute tasks to.  
   *                 Ideally has as many possible threads as keys that 
   *                 will be used in parallel.
   * @param accurateQueueSize true to make "getTaskQueueSize" more accurate
   */
  public TaskExecutorDistributor(int expectedParallism, Executor executor, 
                                 boolean accurateQueueSize) {
    super(expectedParallism, executor, accurateQueueSize);
  }
    
  /**
   * Constructor to use a provided executor implementation for running tasks.
   * 
   * This constructor allows you to provide a maximum number of tasks for a key before it 
   * yields to another key.  This can make it more fair, and make it so no single key can 
   * starve other keys from running.  The lower this is set however, the less efficient it 
   * becomes in part because it has to give up the thread and get it again, but also because 
   * it must copy the subset of the task queue which it can run.
   * 
   * This constructor does not attempt to have an accurate queue size for the 
   * "getTaskQueueSize" call (thus preferring high performance).
   * 
   * @param expectedParallism level of expected quantity of threads adding tasks in parallel
   * @param executor A multi-threaded executor to distribute tasks to.  
   *                 Ideally has as many possible threads as keys that 
   *                 will be used in parallel.
   * @param maxTasksPerCycle maximum tasks run per key before yielding for other keys
   */
  public TaskExecutorDistributor(int expectedParallism, Executor executor, 
                                 int maxTasksPerCycle) {
    super(expectedParallism, executor, maxTasksPerCycle);
  }
  
  /**
   * Constructor to use a provided executor implementation for running tasks.
   * 
   * This constructor allows you to provide a maximum number of tasks for a key before it 
   * yields to another key.  This can make it more fair, and make it so no single key can 
   * starve other keys from running.  The lower this is set however, the less efficient it 
   * becomes in part because it has to give up the thread and get it again, but also because 
   * it must copy the subset of the task queue which it can run.
   * 
   * This also allows you to specify if you want accurate queue sizes to be 
   * tracked for given thread keys.  There is a performance hit associated with this, 
   * so this should only be enabled if "getTaskQueueSize" calls will be used.
   * 
   * @param expectedParallism level of expected quantity of threads adding tasks in parallel
   * @param executor A multi-threaded executor to distribute tasks to.  
   *                 Ideally has as many possible threads as keys that 
   *                 will be used in parallel.
   * @param maxTasksPerCycle maximum tasks run per key before yielding for other keys
   * @param accurateQueueSize true to make "getTaskQueueSize" more accurate
   */
  public TaskExecutorDistributor(int expectedParallism, Executor executor, 
                                 int maxTasksPerCycle, boolean accurateQueueSize) {
    super(expectedParallism, executor, maxTasksPerCycle, accurateQueueSize);
  }
  
  /**
   * Constructor to be used in unit tests.
   * 
   * This constructor allows you to provide a maximum number of tasks for a key before it 
   * yields to another key.  This can make it more fair, and make it so no single key can 
   * starve other keys from running.  The lower this is set however, the less efficient it 
   * becomes in part because it has to give up the thread and get it again, but also because 
   * it must copy the subset of the task queue which it can run.
   * 
   * @param executor executor to be used for task worker execution 
   * @param sLock lock to be used for controlling access to workers
   * @param maxTasksPerCycle maximum tasks run per key before yielding for other keys
   * @param accurateQueueSize true to make "getTaskQueueSize" more accurate
   */
  protected TaskExecutorDistributor(Executor executor, StripedLock sLock, 
                                    int maxTasksPerCycle, boolean accurateQueueSize) {
    super(executor, sLock, maxTasksPerCycle, accurateQueueSize);
  }
}
