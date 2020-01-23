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

package org.nuxeo.ecm.core;

import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.Serializable;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ColdStorageHelper;
import org.nuxeo.ecm.core.schema.FacetNames;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 11.1
 */
@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/test-coldstorage-contrib.xml")
public class TestColdStorage {

    protected static final String FILE_CONTENT = "foo";

    @Inject
    protected CoreSession session;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Test
    public void shouldMoveBlobDocumentToColdStorage() throws IOException {
        DocumentModel documentModel = createDocument(true);

        // move the blob to cold storage
        documentModel = ColdStorageHelper.moveContentToColdStorage(session, documentModel.getRef());
        transactionalFeature.nextTransaction();
        documentModel = session.getDocument(documentModel.getRef());

        assertTrue(documentModel.hasFacet(FacetNames.COLD_STORAGE));

        // check if the `file:content` contains the thumbnail blob
        checkBlobContent(documentModel, ColdStorageHelper.FILE_CONTENT_PROPERTY,
                DummyThumbnailFactory.DUMMY_THUMBNAIL_CONTENT);

        // check if the `coldstorage:coldContent` contains the original file content
        checkBlobContent(documentModel, ColdStorageHelper.COLD_STORAGE_CONTENT_PROPERTY, FILE_CONTENT);
    }

    @Test
    public void shouldFailWhenMovingDocumentBlobAlreadyInColdStorage() {
        DocumentModel documentModel = createDocument(true);

        // move for the first time
        documentModel = ColdStorageHelper.moveContentToColdStorage(session, documentModel.getRef());

        // try to make another move
        try {
            ColdStorageHelper.moveContentToColdStorage(session, documentModel.getRef());
            fail("Should fail because the content is already in cold storage");
        } catch (NuxeoException ne) {
            assertEquals(SC_CONFLICT, ne.getStatusCode());
            assertEquals(String.format("The main content for document: %s is already in cold storage.", documentModel),
                    ne.getMessage());
        }
    }

    @Test
    public void shouldFailWhenMovingToColdStorageDocumentWithoutContent() {
        DocumentModel documentModel = createDocument(false);
        try {
            ColdStorageHelper.moveContentToColdStorage(session, documentModel.getRef());
            fail("Should fail because there is no main content associated with the document");
        } catch (NuxeoException ne) {
            assertEquals(SC_NOT_FOUND, ne.getStatusCode());
            assertEquals(String.format("There is no main content for document: %s.", documentModel), ne.getMessage());
        }
    }

    protected DocumentModel createDocument(boolean addBlobContent) {
        DocumentModel documentModel = session.createDocumentModel("/", "anyFile", "File");
        if (addBlobContent) {
            documentModel.setPropertyValue("file:content", (Serializable) Blobs.createBlob(FILE_CONTENT));
        }
        return session.createDocument(documentModel);
    }

    protected void checkBlobContent(DocumentModel documentModel, String xpath, String expectedContent)
            throws IOException {
        Blob content = (Blob) documentModel.getPropertyValue(xpath);
        assertNotNull(content);
        assertEquals(expectedContent, content.getString());
    }
}
