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
 *     Nour AL KOTOB
 */

package org.nuxeo.ecm.core.io.marshallers.json.enrichers;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import org.apache.logging.log4j.core.LogEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriterTest;
import org.nuxeo.ecm.core.io.marshallers.json.document.DocumentModelJsonWriter;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext.CtxBuilder;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;

@RunWith(FeaturesRunner.class)
@Features({ LogCaptureFeature.class, CoreFeature.class })
@Deploy("org.nuxeo.ecm.core.test.tests:enrichers-contrib.xml")
public class FailingEnricherTest extends AbstractJsonWriterTest.External<DocumentModelJsonWriter, DocumentModel> {

    @Inject
    protected LogCaptureFeature.Result logCaptureResult;

    public FailingEnricherTest() {
        super(DocumentModelJsonWriter.class, DocumentModel.class);
    }

    @Inject
    protected CoreSession session;

    @LogCaptureFeature.FilterOn(logLevel = "ERROR")
    @Test
    public void test() throws IOException {
        DocumentModel root = session.getRootDocument();
        // shouldn't throw
        jsonAssert(root, CtxBuilder.enrichDoc(FailingEnricher.NAME).get());
        List<LogEvent> caughtEvents = logCaptureResult.getCaughtEvents();
        // should log an error
        assertEquals(1, caughtEvents.size());
        assertEquals("java.io.IOException: Simulated exception for test purpose",
                caughtEvents.get(0).getMessage().toString());
    }

}
