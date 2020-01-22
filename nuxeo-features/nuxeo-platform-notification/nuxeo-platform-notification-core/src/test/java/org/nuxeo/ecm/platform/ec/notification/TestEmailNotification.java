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

package org.nuxeo.ecm.platform.ec.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.notification.api.NotificationManager;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.mail.SmtpMailServerFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import net.htmlparser.jericho.Renderer;
import net.htmlparser.jericho.Source;

/**
 * Test the whole process: from subscribing to a given event until the reception of the mail notification on this event.
 *
 * @since 11.1
 */
@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, SmtpMailServerFeature.class })
@Deploy("org.nuxeo.ecm.platform.notification.core")
@Deploy("org.nuxeo.ecm.platform.notification.api")
@Deploy("org.nuxeo.ecm.platform.url.api")
@Deploy("org.nuxeo.ecm.platform.url.core")
@Deploy("org.nuxeo.ecm.platform.notification.core.tests:OSGI-INF/notification-event-listener-contrib.xml")
public class TestEmailNotification {

    protected static final String DUMMY_NOTIFICATION_NAME = "DummyNotificationToSendMail";

    protected static final String DUMMY_EVENT_NAME = "dummyNotificationToSendMail";

    protected static final String DOCUMENT_NAME = "anyFile";

    protected static final String ANOTHER_DOCUMENT_NAME = "anyFile2";

    @Inject
    protected CoreSession session;

    @Inject
    protected NotificationManager notificationManager;

    @Inject
    protected EventService eventService;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Inject
    protected SmtpMailServerFeature.MailsResult emailsResult;

    protected DocumentModel domain;

    @Before
    public void before() {
        domain = session.createDocumentModel("/", "domain", "Domain");
        domain = session.createDocument(domain);
    }

    @Test
    public void shouldReceiveNotificationMailWhenSubscribeToMainDocument() {
        // create a File document and
        DocumentModel documentModel = createDocument(domain, DOCUMENT_NAME);

        // subscribe to the dummy notification
        addSubscriptions(documentModel);

        // fire the dummy event
        DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), documentModel);
        Event event = ctx.newEvent(DUMMY_EVENT_NAME);
        eventService.fireEvent(event);
        transactionalFeature.nextTransaction();

        // check the received mail
        checkMailContent(documentModel, DOCUMENT_NAME);
    }

    @Test
    public void shouldReceiveNotificationMailWhenSubscribeToParentDocument() {
        // create a simple hierarchy (domain <- DOCUMENT_NAME <- ANOTHER_DOCUMENT_NAME)
        DocumentModel parentDocModel = createDocument(domain, DOCUMENT_NAME);

        // subscribe to the dummy notification on the parent document
        addSubscriptions(parentDocModel);

        // create a child document
        DocumentModel mainDocModel = createDocument(parentDocModel, ANOTHER_DOCUMENT_NAME);

        // fire the dummy event on the child document
        DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), mainDocModel);
        Event event = ctx.newEvent(DUMMY_EVENT_NAME);
        eventService.fireEvent(event);
        transactionalFeature.nextTransaction();

        // check the received mail
        checkMailContent(mainDocModel, ANOTHER_DOCUMENT_NAME);
    }

    protected void checkMailContent(final DocumentModel documentModel, String documentName) {
        assertEquals(1, emailsResult.getMails().size());
        SmtpMailServerFeature.MailMessage mailMessage = emailsResult.getMails().get(0);
        assertEquals(String.format("[Dummy]Notification on the document '%s'", documentName), mailMessage.getSubject());

        // check the mail content (see templates/dummyNotificationToSendMail.ftl)
        String mailContent = getMailContent(mailMessage);
        // description
        assertTrue(mailContent.contains(String.format("The document %s is now available.", documentName)));
        // author
        assertTrue(mailContent.contains(session.getPrincipal().getName()));
        // location
        assertTrue(mailContent.contains(documentModel.getPathAsString()));
        // version
        assertTrue(mailContent.contains(documentModel.getVersionLabel()));
        // state
        assertTrue(mailContent.contains(documentModel.getCurrentLifeCycleState()));
        // link to the document
        assertTrue(mailContent.contains(String.format("Consult the document %s", documentName)));
    }

    protected DocumentModel createDocument(DocumentModel parent, String name) {
        DocumentModel documentModel = session.createDocumentModel(parent.getPathAsString(), name, "File");
        return session.createDocument(documentModel);
    }

    protected void addSubscriptions(DocumentModel documentModel) {
        NuxeoPrincipal principal = session.getPrincipal();
        String subscriber = NotificationConstants.USER_PREFIX + principal.getName();
        notificationManager.addSubscription(subscriber, DUMMY_NOTIFICATION_NAME, documentModel, false, principal,
                DUMMY_NOTIFICATION_NAME);
    }

    protected String getMailContent(SmtpMailServerFeature.MailMessage mailMessage) {
        String content = mailMessage.getContent();
        if (!mailMessage.getContentType().contains("text/html")) {
            return content;
        }

        Renderer renderer = new Source(mailMessage.getContent()).getRenderer();
        renderer.setIncludeHyperlinkURLs(false);
        renderer.setDecorateFontStyles(false);
        renderer.setNewLine("\n");
        renderer.setMaxLineLength(150);
        return renderer.toString();
    }
}
