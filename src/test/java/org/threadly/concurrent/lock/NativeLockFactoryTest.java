package org.threadly.concurrent.lock;

import static org.junit.Assert.*;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class NativeLockFactoryTest {
  @Test
  public void makeLockTest() {
    NativeLockFactory nlf = new NativeLockFactory();
    VirtualLock lock = nlf.makeLock();
    
    assertTrue(lock != null);
    assertTrue(lock instanceof NativeLock);
  }
}