/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *  
 * Contributors:
 *     Salem Aouana
 */

package org.nuxeo.ecm.automation.core.operations.document;

import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.util.ColdStorageOperationTestUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 11.1
 */
@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.automation.core")
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-coldstorage-contrib.xml")
public class RetrieveFromColdStorageTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Test
    public void shouldRetrieveFromColdStorage() throws OperationException, IOException {
        DocumentModel documentModel = ColdStorageOperationTestUtils.createDocument(session, true);
        // first make the move to cold storage
        ColdStorageOperationTestUtils.moveContentToColdStorage(session, documentModel);
        // now lets retrieve the cold storage content
        ColdStorageOperationTestUtils.retrieveContentFromColdStorage(session, documentModel);
    }

    @Test
    public void shouldFailWhenRetrievingDocumentBlobFromColdStorageBeingRetrieved()
            throws IOException, OperationException {
        DocumentModel documentModel = ColdStorageOperationTestUtils.createDocument(session, true);

        // move the blob to cold storage
        ColdStorageOperationTestUtils.moveContentToColdStorage(session, documentModel);

        // retrieve the cold storage content
        ColdStorageOperationTestUtils.retrieveContentFromColdStorage(session, documentModel);

        // try to retrieve a second time
        try {
            ColdStorageOperationTestUtils.retrieveContentFromColdStorage(session, documentModel);
            fail("Should fail because the cold storage content is being retrieved.");
        } catch (NuxeoException ne) {
            assertEquals(SC_CONFLICT, ne.getStatusCode());
        }
    }

    @Test
    public void shouldFailWhenRetrievingDocumentBlobWithoutColdStorageContent() throws OperationException {
        DocumentModel documentModel = ColdStorageOperationTestUtils.createDocument(session, true);
        try {
            // retrieve the cold storage content
            ColdStorageOperationTestUtils.retrieveContentFromColdStorage(session, documentModel);
            fail("Should fail because there no cold storage content associated to this document.");
        } catch (NuxeoException ne) {
            assertEquals(SC_NOT_FOUND, ne.getStatusCode());
        }
    }
}
