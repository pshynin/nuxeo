/*
 *  (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Contributors:
 *      Thierry Casanova
 */

package org.nuxeo.directory.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.HotDeployer;

/**
 * @since 11.1
 */
@RunWith(FeaturesRunner.class)
@Features({ DirectoryFeature.class })
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
public class TestInitLoadDirectory {

    protected static final String CSV_LOAD_DIRECTORY = "csvLoadedDirectory";

    @Inject
    protected DirectoryService directoryService;

    @Inject
    protected HotDeployer hotDeployer;

    @Test
    @Deploy("org.nuxeo.ecm.directory.tests:csv-always-loaded-directory-contrib.xml")
    public void testInitDirectoryWithAlways() throws Exception {
        assertDirectoryIsInitialized();
        hotDeployer.deploy(
                "org.nuxeo.ecm.directory.tests:csv-always-loaded-directory-contrib.override.withAutoincrementId.xml");
        try (Session session = directoryService.open(CSV_LOAD_DIRECTORY)) {
            // We verify that the second csv file has overwritten the first one
            DocumentModelList entries = queryFullDirectory(session);
            assertEquals(3, entries.size());
        }
    }

    @Test
    @Deploy("org.nuxeo.ecm.directory.tests:csv-always-loaded-directory-contrib.xml")
    public void testInitDirectoryWithNever() throws Exception {
        assertDirectoryIsInitialized();
        // Test case NEVER - NEVER_LOAD
        hotDeployer.deploy("org.nuxeo.ecm.directory.tests:csv-never-neverload-directory-contrib.xml");
        assertDirectoryIsInitialized();
        // Test that autoincrementId is never updated
        hotDeployer.deploy(
                "org.nuxeo.ecm.directory.tests:csv-never-and-update-duplicate-and-AutoIncrementId-directory-override-contrib.xml");
        assertDirectoryIsInitialized();
        // Test case NEVER - UPDATE
        hotDeployer.deploy(
                "org.nuxeo.ecm.directory.tests:csv-never-and-update-duplicate-directory-override-contrib.xml");
        try (Session session = directoryService.open(CSV_LOAD_DIRECTORY)) {
            // We verify that the 2 csv files have been correctly merged
            DocumentModelList entries = queryFullDirectory(session);
            assertEquals(4, entries.size());
            assertNotNull(session.getEntry("8"));
            assertEquals("European Union", queryEurope(session).getPropertyValue("label"));
        }
    }

    @Test
    @Deploy("org.nuxeo.ecm.directory.tests:csv-loaded-directory-contrib.xml")
    public void testInitDirectoryWithOnMissingColumns() throws Exception {
        assertDirectoryIsInitialized();
        // First we test with dataLoadingPolicy = never_load
        hotDeployer.deploy(
                "org.nuxeo.ecm.directory.tests:csv-on-missing-columns-and-never-load-directory-override-contrib.xml");
        // We verify that nothing has changed
        assertDirectoryIsInitialized();

        // Then we test with dataLoadingPolicy = ignore_duplicate
        hotDeployer.deploy(
                "org.nuxeo.ecm.directory.tests:csv-on-missing-columns-and-ignore-duplicate-directory-override-contrib.xml");

        try (Session session = directoryService.open(CSV_LOAD_DIRECTORY)) {
            // We verify that the 2 csv files have been correctly merged with duplicate line ignored
            DocumentModelList entries = queryFullDirectory(session);
            assertEquals(4, entries.size());
            // assert unchanged existing entry:
            assertEquals("Europe", queryEurope(session).getPropertyValue("label"));
        }
        // Then we test with dataLoadingPolicy = update_duplicate
        hotDeployer.deploy(
                "org.nuxeo.ecm.directory.tests:csv-on-missing-columns-and-update-duplicate-directory-override-contrib.xml");
        try (Session session = directoryService.open(CSV_LOAD_DIRECTORY)) {
            // We verify that the 2 csv files have been correctly merged
            DocumentModelList entries = queryFullDirectory(session);
            assertEquals(4, entries.size());
            assertNotNull(session.getEntry("8"));
            assertEquals("European Union", queryEurope(session).getPropertyValue("label"));
        }
        // Then we test with dataLoadingPolicy = error_on_duplicate
        hotDeployer.deploy(
                "org.nuxeo.ecm.directory.tests:csv-on-missing-columns-and-error-on-duplicate-directory-override-contrib.xml");
        try (Session session = directoryService.open(CSV_LOAD_DIRECTORY)) {
            //we check that Directory has not been changed
            assertEquals("European Union", queryEurope(session).getPropertyValue("label"));
        }
    }

    protected void assertDirectoryIsInitialized() {
        try (Session session = directoryService.open(CSV_LOAD_DIRECTORY)) {
            DocumentModelList entries = queryFullDirectory(session);
            assertEquals(2, entries.size());
            Map<String, Serializable> filter = Collections.singletonMap("label", "Europe");
            entries = session.query(filter);
            assertEquals(1, entries.size());
        }
    }

    protected static DocumentModelList queryFullDirectory(Session session) {
        Map<String, Serializable> filter = Collections.singletonMap("obsolete", false);
        return session.query(filter);
    }

    protected static DocumentModel queryEurope(Session session){
        Map<String, Serializable> europeFilterQuery = Collections.singletonMap("continent", "europe");
        return session.query(europeFilterQuery).get(0);
    }
}
