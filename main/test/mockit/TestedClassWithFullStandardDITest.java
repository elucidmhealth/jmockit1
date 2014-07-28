/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.io.*;
import javax.annotation.*;
import javax.inject.*;
import javax.persistence.*;

import org.junit.*;
import static org.junit.Assert.*;

public final class TestedClassWithFullStandardDITest
{
   public static class TestedClass
   {
      @Inject private Runnable dependencyToBeMocked;
      @Inject private FirstLevelDependency dependency2;
      @Resource private FirstLevelDependency dependency3;
      @Inject private CommonDependency commonDependency;
      boolean initialized;
      static boolean destroyed;

      @PostConstruct
      void initialize()
      {
         assertNotNull(dependency3);
         initialized = true;
      }

      @PreDestroy
      void destroy()
      {
         assertTrue(initialized);
         destroyed = true;
      }
   }

   public static class FirstLevelDependency
   {
      @Inject private SecondLevelDependency dependency;
      @Inject private static SecondLevelDependency staticDependency;
      @Inject private CommonDependency commonDependency;
      @Resource private static Runnable dependencyToBeMocked;
      @PersistenceContext private EntityManager em;
   }

   public static class SecondLevelDependency
   {
      @Inject CommonDependency commonDependency;
      @PersistenceUnit private EntityManagerFactory emFactory;
      @PersistenceContext private EntityManager em;
      boolean initialized;

      @PostConstruct
      final void initialize()
      {
         initialized = true;
      }
   }

   public static class CommonDependency
   {
      @PersistenceUnit(unitName = "test") private EntityManagerFactory emFactory;
      @PersistenceContext(unitName = "test") private EntityManager em;
   }

   @Tested(fullyInitialized = true) TestedClass tested;
   @Injectable Runnable mockedDependency;

   @Mocked Persistence persistence;
   @Mocked EntityManagerFactory defaultEMFactory;
   @Mocked EntityManagerFactory namedEMFactory;
   @Mocked EntityManager defaultEM;
   @Mocked EntityManager namedEM;

   @Before
   public void setUpPersistence() throws Exception
   {
      new NonStrictExpectations() {{
         Persistence.createEntityManagerFactory("test"); result = namedEMFactory;
         Persistence.createEntityManagerFactory("default"); result = defaultEMFactory;
         Persistence.createEntityManagerFactory(""); result = new PersistenceException("Empty persistence unit name");

         namedEMFactory.createEntityManager(); result = namedEM;
         defaultEMFactory.createEntityManager(); result = defaultEM;
      }};

      String rootOfClasspath = getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
      File tempFolder = new File(rootOfClasspath + "META-INF");
      if (tempFolder.mkdir()) tempFolder.deleteOnExit();

      File xmlFile = new File(tempFolder, "persistence.xml");
      xmlFile.deleteOnExit();

      Writer xmlWriter = new FileWriter(xmlFile);
      xmlWriter.write("<persistence><persistence-unit name='default'/></persistence>");
      xmlWriter.close();
   }

   @Test
   public void useFullyInitializedTestedObject()
   {
      // First level dependencies:
      assertSame(mockedDependency, tested.dependencyToBeMocked);
      assertNotNull(tested.dependency2);
      assertSame(tested.dependency2, tested.dependency3);
      assertNotNull(tested.commonDependency);

      // Second level dependencies:
      assertNotNull(tested.dependency2.dependency);
      assertSame(FirstLevelDependency.staticDependency, tested.dependency2.dependency);
      assertSame(tested.dependency3.dependency, tested.dependency2.dependency);
      assertSame(tested.commonDependency, tested.dependency2.commonDependency);
      assertSame(tested.commonDependency, tested.dependency3.commonDependency);
      assertSame(mockedDependency, FirstLevelDependency.dependencyToBeMocked);
      assertSame(mockedDependency, FirstLevelDependency.dependencyToBeMocked);
      assertSame(defaultEM, tested.dependency2.em);
      assertSame(tested.dependency2.em, tested.dependency3.em);
      assertSame(namedEMFactory, tested.commonDependency.emFactory);
      assertSame(namedEM, tested.commonDependency.em);
      assertNotSame(tested.dependency2.em, tested.commonDependency.em);

      // Third level dependencies:
      assertSame(tested.commonDependency, tested.dependency2.dependency.commonDependency);
      assertSame(defaultEMFactory, tested.dependency2.dependency.emFactory);
      assertSame(tested.dependency2.em, tested.dependency2.dependency.em);

      // Lifecycle methods:
      assertTrue(tested.initialized);
      assertTrue(tested.dependency2.dependency.initialized);
   }

   @After
   public void verifyThatTestedFieldsWereClearedAndPreDestroyMethodsWereExecuted()
   {
      assertNull(tested);
      assertTrue(TestedClass.destroyed);
   }
}
