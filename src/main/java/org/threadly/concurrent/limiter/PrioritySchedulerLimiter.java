package org.threadly.concurrent.limiter;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.threadly.concurrent.CallableContainerInterface;
import org.threadly.concurrent.PrioritySchedulerInterface;
import org.threadly.concurrent.RunnableContainerInterface;
import org.threadly.concurrent.TaskPriority;
import org.threadly.concurrent.future.FutureFuture;
import org.threadly.concurrent.future.FutureListenableFuture;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.StaticCancellationException;

/**
 * <p>This class is designed to limit how much parallel execution happens 
 * on a provided {@link PrioritySchedulerInterface}.  This allows the 
 * implementor to have one thread pool for all their code, and if 
 * they want certain sections to have less levels of parallelism 
 * (possibly because those those sections would completely consume the 
 * global pool), they can wrap the executor in this class.</p>
 * 
 * <p>Thus providing you better control on the absolute thread count and 
 * how much parallelism can occur in different sections of the program.</p>
 * 
 * <p>This is an alternative from having to create multiple thread pools.  
 * By using this you also are able to accomplish more efficiently thread use 
 * than multiple thread pools would.</p>
 * 
 * @author jent - Mike Jensen
 */
public class PrioritySchedulerLimiter extends AbstractThreadPoolLimiter 
                                      implements PrioritySchedulerInterface {
  protected final PrioritySchedulerInterface scheduler;
  protected final Queue<PriorityWrapper> waitingTasks;
  
  /**
   * Constructs a new limiter that implements the {@link PrioritySchedulerInterface}.
   * 
   * @param scheduler {@link PrioritySchedulerInterface} implementation to submit task executions to.
   * @param maxConcurrency maximum qty of runnables to run in parallel
   */
  public PrioritySchedulerLimiter(PrioritySchedulerInterface scheduler, 
                                  int maxConcurrency) {
    this(scheduler, maxConcurrency, null);
  }
  
  /**
   * Constructs a new limiter that implements the {@link PrioritySchedulerInterface}.
   * 
   * @param scheduler {@link PrioritySchedulerInterface} implementation to submit task executions to.
   * @param maxConcurrency maximum qty of runnables to run in parallel
   * @param subPoolName name to describe threads while tasks running in pool (null to not change thread names)
   */
  public PrioritySchedulerLimiter(PrioritySchedulerInterface scheduler, 
                                  int maxConcurrency, String subPoolName) {
    super(maxConcurrency, subPoolName);
    
    if (scheduler == null) {
      throw new IllegalArgumentException("Must provide scheduler");
    }
    
    this.scheduler = scheduler;
    waitingTasks = new ConcurrentLinkedQueue<PriorityWrapper>();
  }
  
  @Override
  protected void consumeAvailable() {
    /* must synchronize in queue consumer to avoid 
     * multiple threads from consuming tasks in parallel 
     * and possibly emptying after .isEmpty() check but 
     * before .poll()
     */
    synchronized (this) {
      while (! waitingTasks.isEmpty() && canRunTask()) {
        // by entering loop we can now execute task
        PriorityWrapper next = waitingTasks.poll();
        if (next.hasFuture()) {
          if (next.isCallable()) {
            ListenableFuture<?> f = scheduler.submit(next.getCallable(), next.getPriority());
            next.getFuture().setParentFuture(f);
          } else {
            ListenableFuture<?> f = scheduler.submit(next.getRunnable(), next.getPriority());
            next.getFuture().setParentFuture(f);
          }
        } else {
          // all callables will have futures, so we know this is a runnable
          scheduler.execute(next.getRunnable(), next.getPriority());
        }
      }
    }
  }

  @Override
  public void execute(Runnable task) {
    execute(task, scheduler.getDefaultPriority());
  }

  @Override
  public ListenableFuture<?> submit(Runnable task) {
    return submit(task, scheduler.getDefaultPriority());
  }

  @Override
  public <T> ListenableFuture<T> submit(Runnable task, T result) {
    return submit(task, result, scheduler.getDefaultPriority());
  }

  @Override
  public <T> ListenableFuture<T> submit(Callable<T> task) {
    return submit(task, scheduler.getDefaultPriority());
  }

  @Override
  public void schedule(Runnable task, long delayInMs) {
    schedule(task, delayInMs, 
             scheduler.getDefaultPriority());
  }

  @Override
  public ListenableFuture<?> submitScheduled(Runnable task, long delayInMs) {
    return submitScheduled(task, delayInMs, 
                           scheduler.getDefaultPriority());
  }

  @Override
  public <T> ListenableFuture<T> submitScheduled(Runnable task, T result, long delayInMs) {
    return submitScheduled(task, result, delayInMs, 
                           scheduler.getDefaultPriority());
  }

  @Override
  public <T> ListenableFuture<T> submitScheduled(Callable<T> task, long delayInMs) {
    return submitScheduled(task, delayInMs, 
                           scheduler.getDefaultPriority());
  }

  @Override
  public void scheduleWithFixedDelay(Runnable task, long initialDelay,
                                     long recurringDelay) {
    scheduleWithFixedDelay(task, initialDelay, recurringDelay, 
                           scheduler.getDefaultPriority());
  }

  @Override
  public boolean isShutdown() {
    return scheduler.isShutdown();
  }

  @Override
  public void execute(Runnable task, TaskPriority priority) {
    if (task == null) {
      throw new IllegalArgumentException("Must provide task");
    }
    if (priority == null) {
      priority = scheduler.getDefaultPriority();
    }
    
    RunnableFutureWrapper wrapper = new RunnableFutureWrapper(task, priority, null);
    
    if (canRunTask()) {  // try to avoid adding to queue if we can
      scheduler.execute(wrapper, priority);
    } else {
      waitingTasks.add(wrapper);
      consumeAvailable(); // call to consume in case task finished after first check
    }
  }

  @Override
  public ListenableFuture<?> submit(Runnable task, TaskPriority priority) {
    return submit(task, null, priority);
  }

  @Override
  public <T> ListenableFuture<T> submit(Runnable task, T result, TaskPriority priority) {
    if (task == null) {
      throw new IllegalArgumentException("Must provide task");
    }
    if (priority == null) {
      priority = scheduler.getDefaultPriority();
    }
    
    FutureListenableFuture<T> ff = new FutureListenableFuture<T>();
    
    doSubmit(task, result, priority, ff);
    
    return ff;
  }
  
  private <T> void doSubmit(Runnable task, T result, 
                            TaskPriority priority, 
                            FutureListenableFuture<T> ff) {
    RunnableFutureWrapper wrapper = new RunnableFutureWrapper(task, priority, ff);
    ff.setTaskCanceler(wrapper);
    
    if (canRunTask()) {  // try to avoid adding to queue if we can
      ff.setParentFuture(scheduler.submit(wrapper, result, priority));
    } else {
      waitingTasks.add(wrapper);
      consumeAvailable(); // call to consume in case task finished after first check
    }
  }

  @Override
  public <T> ListenableFuture<T> submit(Callable<T> task, TaskPriority priority) {
    if (task == null) {
      throw new IllegalArgumentException("Must provide task");
    }
    if (priority == null) {
      priority = scheduler.getDefaultPriority();
    }
    
    FutureListenableFuture<T> ff = new FutureListenableFuture<T>();
    
    doSubmit(task, priority, ff);
    
    return ff;
  }
  
  private <T> void doSubmit(Callable<T> task, 
                            TaskPriority priority, 
                            FutureListenableFuture<T> ff) {
    CallableFutureWrapper<T> wrapper = new CallableFutureWrapper<T>(task, priority, ff);
    ff.setTaskCanceler(wrapper);
    
    if (canRunTask()) {  // try to avoid adding to queue if we can
      ff.setParentFuture(scheduler.submit(wrapper, priority));
    } else {
      waitingTasks.add(wrapper);
      consumeAvailable(); // call to consume in case task finished after first check
    }
  }

  @Override
  public void schedule(Runnable task, long delayInMs, 
                       TaskPriority priority) {
    if (task == null) {
      throw new IllegalArgumentException("Must provide a task");
    } else if (delayInMs < 0) {
      throw new IllegalArgumentException("delayInMs must be >= 0");
    }
    if (priority == null) {
      priority = scheduler.getDefaultPriority();
    }
    
    if (delayInMs == 0) {
      execute(task, priority);
    } else {
      scheduler.schedule(new DelayedExecutionRunnable<Object>(task, null, priority, null), 
                         delayInMs, TaskPriority.High);
    }
  }

  @Override
  public ListenableFuture<?> submitScheduled(Runnable task, long delayInMs,
                                             TaskPriority priority) {
    return submitScheduled(task, null, delayInMs, priority);
  }

  @Override
  public <T> ListenableFuture<T> submitScheduled(Runnable task, T result, long delayInMs,
                                                 TaskPriority priority) {
    if (task == null) {
      throw new IllegalArgumentException("Must provide a task");
    } else if (delayInMs < 0) {
      throw new IllegalArgumentException("delayInMs must be >= 0");
    }
    if (priority == null) {
      priority = scheduler.getDefaultPriority();
    }

    FutureListenableFuture<T> ff = new FutureListenableFuture<T>();
    if (delayInMs == 0) {
      doSubmit(task, result, priority, ff);
    } else {
      scheduler.schedule(new DelayedExecutionRunnable<T>(task, result, priority, ff), 
                         delayInMs, TaskPriority.High);
    }
      
    return ff;
  }

  @Override
  public <T> ListenableFuture<T> submitScheduled(Callable<T> task, long delayInMs,
                                                 TaskPriority priority) {
    if (task == null) {
      throw new IllegalArgumentException("Must provide a task");
    } else if (delayInMs < 0) {
      throw new IllegalArgumentException("delayInMs must be >= 0");
    }
    if (priority == null) {
      priority = scheduler.getDefaultPriority();
    }

    FutureListenableFuture<T> ff = new FutureListenableFuture<T>();
    if (delayInMs == 0) {
      doSubmit(task, priority, ff);
    } else {
      scheduler.schedule(new DelayedExecutionCallable<T>(task, priority, ff), 
                         delayInMs, priority);
    }
    
    return ff;
  }

  @Override
  public void scheduleWithFixedDelay(Runnable task, long initialDelay,
                                     long recurringDelay, TaskPriority priority) {
    if (task == null) {
      throw new IllegalArgumentException("Must provide a task");
    } else if (initialDelay < 0) {
      throw new IllegalArgumentException("initialDelay must be >= 0");
    } else if (recurringDelay < 0) {
      throw new IllegalArgumentException("recurringDelay must be >= 0");
    }
    if (priority == null) {
      priority = scheduler.getDefaultPriority();
    }
    
    RecurringRunnableWrapper rrw = new RecurringRunnableWrapper(task, recurringDelay, priority);
    
    if (initialDelay == 0) {
      execute(rrw, priority);
    } else {
      scheduler.schedule(new DelayedExecutionRunnable<Object>(rrw, null, priority, null), 
                         initialDelay, TaskPriority.High);
    }
  }

  @Override
  public TaskPriority getDefaultPriority() {
    return scheduler.getDefaultPriority();
  }
  
  /**
   * <p>Small runnable that allows scheduled tasks to pass through 
   * the same execution queue that immediate execution has to.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected class DelayedExecutionRunnable<T> implements Runnable, 
                                                         RunnableContainerInterface {
    private final Runnable runnable;
    private final T runnableResult;
    private final TaskPriority priority;
    private final FutureListenableFuture<T> future;

    public DelayedExecutionRunnable(Runnable runnable, T runnableResult, 
                                    TaskPriority priority, 
                                    FutureListenableFuture<T> future) {
      this.runnable = runnable;
      this.runnableResult = runnableResult;
      this.priority = priority;
      this.future = future;
    }
    
    @Override
    public void run() {
      try {
        if (future == null) {
          execute(runnable, priority);
        } else {
          doSubmit(runnable, runnableResult, priority, future);
        }
      } catch (IllegalStateException e) {
        // catch exception in case scheduler shutdown
      }
    }

    @Override
    public Runnable getContainedRunnable() {
      return runnable;
    }
  }
  
  /**
   * <p>Small runnable that allows scheduled tasks to pass through 
   * the same execution queue that immediate execution has to.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected class DelayedExecutionCallable<T> implements Runnable, 
                                                         CallableContainerInterface<T>, 
                                                         RunnableContainerInterface {
    private final Callable<T> callable;
    private final TaskPriority priority;
    private final FutureListenableFuture<T> future;

    public DelayedExecutionCallable(Callable<T> runnable, 
                                    TaskPriority priority, 
                                    FutureListenableFuture<T> future) {
      this.callable = runnable;
      this.priority = priority;
      this.future = future;
    }
    
    @Override
    public void run() {
      doSubmit(callable, priority, future);
    }

    @Override
    public Runnable getContainedRunnable() {
      if (callable instanceof RunnableContainerInterface) {
        return ((RunnableContainerInterface)callable).getContainedRunnable();
      } else {
        return null;
      }
    }

    @Override
    public Callable<T> getContainedCallable() {
      return callable;
    }
  }

  /**
   * <p>Wrapper for priority tasks which are executed in this sub pool, 
   * this ensures that handleTaskFinished() will be called 
   * after the task completes.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected class RecurringRunnableWrapper extends LimiterRunnableWrapper
                                           implements PriorityWrapper {
    private final long recurringDelay;
    private final TaskPriority priority;
    private final DelayedExecutionRunnable<?> delayRunnable;
    
    public RecurringRunnableWrapper(Runnable runnable, 
                                    long recurringDelay, 
                                    TaskPriority priority) {
      super(runnable);
      
      this.recurringDelay = recurringDelay;
      this.priority = priority;
      delayRunnable = new DelayedExecutionRunnable<Object>(this, null, priority, null);
    }
    
    @Override
    protected void doAfterRunTasks() {
      try {
        scheduler.schedule(delayRunnable, recurringDelay, 
                           TaskPriority.High);
      } catch (IllegalStateException e) {
        // catch exception in case scheduler shutdown
      }
    }

    @Override
    public boolean isCallable() {
      return false;
    }

    @Override
    public FutureListenableFuture<?> getFuture() {
      throw new UnsupportedOperationException();
    }

    @Override
    public TaskPriority getPriority() {
      return priority;
    }

    @Override
    public boolean hasFuture() {
      return false;
    }

    @Override
    public Callable<?> getCallable() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Runnable getRunnable() {
      return this;
    }
  }
  
  /**
   * <p>Wrapper for priority tasks which are executed in this sub pool, 
   * this ensures that handleTaskFinished() will be called 
   * after the task completes.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected class RunnableFutureWrapper extends LimiterRunnableWrapper
                                        implements PriorityWrapper  {
    private final TaskPriority priority;
    private final FutureListenableFuture<?> future;
    
    public RunnableFutureWrapper(Runnable runnable, 
                                 TaskPriority priority, 
                                 FutureListenableFuture<?> future) {
      super(runnable);
      
      this.priority = priority;
      this.future = future;
    }

    @Override
    public TaskPriority getPriority() {
      return priority;
    }
    
    @Override
    protected void doAfterRunTasks() {
      // nothing to do here
    }

    @Override
    public boolean isCallable() {
      return false;
    }

    @Override
    public FutureListenableFuture<?> getFuture() {
      return future;
    }

    @Override
    public boolean hasFuture() {
      return future != null;
    }

    @Override
    public Callable<?> getCallable() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Runnable getRunnable() {
      return this;
    }
  }

  // TODO - can this class be avoided by turning callables into ListenableFutureTasks sooner?
  /**
   * <p>Wrapper for priority tasks which are executed in this sub pool, 
   * this ensures that handleTaskFinished() will be called 
   * after the task completes.</p>
   * 
   * @author jent - Mike Jensen
   * @param <T> type for return of callable contained within wrapper
   */
  protected class CallableFutureWrapper<T> extends LimiterCallableWrapper<T>
                                           implements PriorityWrapper {
    private final TaskPriority priority;
    private final FutureListenableFuture<?> future;
    
    public CallableFutureWrapper(Callable<T> callable, 
                                 TaskPriority priority, 
                                 FutureListenableFuture<?> future) {
      super(callable);
      
      this.priority = priority;
      this.future = future;
    }

    @Override
    public TaskPriority getPriority() {
      return priority;
    }

    @Override
    public boolean isCallable() {
      return true;
    }

    @Override
    public FutureListenableFuture<?> getFuture() {
      return future;
    }

    @Override
    public boolean hasFuture() {
      return future != null;
    }

    @Override
    public Callable<?> getCallable() {
      return this;
    }

    @Override
    public Runnable getRunnable() {
      throw new UnsupportedOperationException();
    }
  }
  
  // TODO - can this class be avoided by turning callables into ListenableFutureTasks sooner?
  /**
   * <p>Generic wrapper for callables which are used within the limiters.</p>
   * 
   * @author jent - Mike Jensen
   * @param <T> type for return of callable contained within wrapper
   */
  protected class LimiterCallableWrapper<T> implements Callable<T>, 
                                                       FutureFuture.TaskCanceler, 
                                                       CallableContainerInterface<T>, 
                                                       RunnableContainerInterface {
    private final Callable<T> callable;
    private final AtomicInteger runStatus;  // 0 = not started, -1 = canceled, 1 = running
    
    public LimiterCallableWrapper(Callable<T> callable) {
      this.callable = callable;
      runStatus = new AtomicInteger(0);
    }
    
    @Override
    public T call() throws Exception {
      Thread currentThread = null;
      String originalThreadName = null;
      if (subPoolName != null) {
        currentThread = Thread.currentThread();
        originalThreadName = currentThread.getName();
        
        currentThread.setName(makeSubPoolThreadName(originalThreadName));
      }
      
      try {
        if (runStatus.compareAndSet(0, 1)) {
          return callable.call();
        } else {
          throw StaticCancellationException.instance();
        }
      } finally {
        runStatus.compareAndSet(1, 0);
        handleTaskFinished();
          
        if (subPoolName != null) {
          currentThread.setName(originalThreadName);
        }
      }
    }

    @Override
    public boolean cancel() {
      return runStatus.compareAndSet(0, -1);
    }

    @Override
    public Runnable getContainedRunnable() {
      if (callable instanceof RunnableContainerInterface) {
        return ((RunnableContainerInterface)callable).getContainedRunnable();
      } else {
        return null;
      }
    }

    @Override
    public Callable<T> getContainedCallable() {
      return callable;
    }
  }
  
  /**
   * <p>Interface so that we can handle both callables and runnables.  
   * This also provides us the ability to get the original priority the 
   * task was submitted with.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected interface PriorityWrapper {
    public TaskPriority getPriority();
    public boolean isCallable();
    public FutureListenableFuture<?> getFuture();
    public boolean hasFuture();
    public Callable<?> getCallable();
    public Runnable getRunnable();
  }
}
