/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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

package org.nuxeo.ecm.platform.comment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_REMOVED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_UPDATED;
import static org.nuxeo.ecm.platform.comment.CommentUtils.checkDocumentEventContext;
import static org.nuxeo.ecm.platform.comment.CommentUtils.createUser;
import static org.nuxeo.ecm.platform.comment.api.CommentEvents.COMMENT_ADDED;
import static org.nuxeo.ecm.platform.comment.api.CommentEvents.COMMENT_REMOVED;
import static org.nuxeo.ecm.platform.comment.api.CommentEvents.COMMENT_UPDATED;
import static org.nuxeo.ecm.platform.comment.workflow.utils.CommentsConstants.COMMENT_PARENT_ID;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.test.CapturingEventListener;
import org.nuxeo.ecm.platform.comment.api.Annotation;
import org.nuxeo.ecm.platform.comment.api.AnnotationImpl;
import org.nuxeo.ecm.platform.comment.api.AnnotationService;
import org.nuxeo.ecm.platform.comment.api.Comment;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.ecm.platform.comment.api.ExternalEntity;
import org.nuxeo.ecm.platform.ec.notification.NotificationConstants;
import org.nuxeo.ecm.platform.notification.api.NotificationManager;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 11.1
 */
@RunWith(FeaturesRunner.class)
@Features(NotificationCommentFeature.class)
public abstract class AbstractTestAnnotationNotification {

    protected static final String COMMENT_ADDED_NOTIFICATION = "CommentAdded";

    protected static final String COMMENT_UPDATED_NOTIFICATION = "CommentUpdated";

    protected static final String ADMINISTRATOR = "Administrator";

    protected static final String ANY_ANNOTATION_MESSAGE = "any Annotation message";

    @Inject
    protected AnnotationService annotationService;

    @Inject
    protected CommentManager commentManager;

    @Inject
    protected CoreSession session;

    @Inject
    protected NotificationManager notificationManager;

    @Inject
    protected TransactionalFeature transactionalFeature;

    protected DocumentModel annotatedDocumentModel;

    @Before
    public void before() {
        // Create the file under a domain is needed in this test context, due to the control of
        // NotificationEventListener#gatherConcernedUsersForDocument (doc.getPath().segmentCount() > 1)
        DocumentModel domain = session.createDocumentModel("/", "domain", "Domain");
        domain = session.createDocument(domain);
        annotatedDocumentModel = session.createDocumentModel(domain.getPathAsString(), "test", "File");
        annotatedDocumentModel = session.createDocument(annotatedDocumentModel);
        transactionalFeature.nextTransaction();
    }

    @Test
    public void shouldNotifyEventWhenCreateAnnotation() {
        // We subscribe to the creation document to check that we will not be notified about the annotation creation as
        // document (see CommentCreationVeto), only the annotation added, and the 'File' document creation
        captureAndVerifyAnnotationEventNotification(() -> {
            Annotation createdAnnotation = createAnnotationAndAddSubscription(COMMENT_ADDED_NOTIFICATION, "Creation");
            return session.getDocument(new IdRef(createdAnnotation.getId()));
        }, COMMENT_ADDED, DOCUMENT_CREATED);
    }

    @Test
    public void shouldNotifyEventWhenUpdateAnnotation() {
        // We subscribe to the update document to check that we will not be notified about the annotation updated as
        // document (see CommentModificationVeto), only the annotation updated.
        Annotation annotation = createAnnotationAndAddSubscription("CommentUpdated", "Modification");

        captureAndVerifyAnnotationEventNotification(() -> {
            annotation.setText("I update the annotation");
            annotationService.updateAnnotation(session, annotation.getId(), annotation);
            return session.getDocument(new IdRef(annotation.getId()));
        }, COMMENT_UPDATED, DOCUMENT_UPDATED);
    }

    @Test
    public void shouldNotifyEventWhenRemoveAnnotation() {
        Annotation createdAnnotation = createAnnotationAndAddSubscription("CommentRemoved");
        DocumentModel annotationDocModel = session.getDocument(new IdRef(createdAnnotation.getId()));
        annotationDocModel.detach(true);

        captureAndVerifyAnnotationEventNotification(() -> {
            annotationService.deleteAnnotation(session, createdAnnotation.getId());
            return annotationDocModel;
        }, COMMENT_REMOVED, DOCUMENT_REMOVED);
    }

    @Test
    public void testCommentManagerType() {
        assertEquals(getType(), commentManager.getClass());
    }

    protected void captureAndVerifyAnnotationEventNotification(Supplier<DocumentModel> supplier,
            String annotationEventType, String documentEventType) {
        try (CapturingEventListener listener = new CapturingEventListener(annotationEventType, documentEventType)) {
            DocumentModel annotationDocumentModel = supplier.get();
            DocumentModel annotationParentDocumentModel = session.getDocument(new IdRef(
                    (String) annotationDocumentModel.getPropertyValue(COMMENT_PARENT_ID)));
            transactionalFeature.nextTransaction();

            assertTrue(listener.hasBeenFired(annotationEventType));
            assertTrue(listener.hasBeenFired(documentEventType));

            List<Event> handledEvents = listener.getCapturedEvents()
                                                .stream()
                                                .filter(e -> annotationEventType.equals(e.getName()))
                                                .collect(Collectors.toList());

            assertEquals(1, handledEvents.size());
            Event expectedEvent = handledEvents.get(0);
            assertEquals(annotationEventType, expectedEvent.getName());

            checkDocumentEventContext(expectedEvent, annotationDocumentModel, annotationParentDocumentModel,
                    annotatedDocumentModel);
        }
    }

    @Test
    public void shouldNotifyWithTheRightAnnotatedDocument() {
        // First comment
        Annotation createdAnnotation = createAnnotation(annotatedDocumentModel, ADMINISTRATOR, ANY_ANNOTATION_MESSAGE);
        DocumentModel createdAnnotationDocModel = session.getDocument(new IdRef(createdAnnotation.getId()));
        // before subscribing, or previous event will be notified as well
        transactionalFeature.nextTransaction();
        // Reply
        captureAndVerifyAnnotationEventNotification(() -> {
            // subscribe to notifications
            addSubscriptions(COMMENT_ADDED_NOTIFICATION);

            Comment reply = createAnnotation(createdAnnotationDocModel, ADMINISTRATOR, ANY_ANNOTATION_MESSAGE);
            DocumentModel replyDocumentModel = session.getDocument(new IdRef(reply.getId()));
            return session.getDocument(new IdRef(replyDocumentModel.getId()));
        }, COMMENT_ADDED, DOCUMENT_CREATED);
    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.comment:OSGI-INF/notification-subscription-contrib.xml")
    public void testAutoSubscribingOnlyOnceToNewAnnotations() {
        String john = "john";
        String johnSubscription = NotificationConstants.USER_PREFIX + john;
        createUser(john);
        List<String> subscriptions = notificationManager.getSubscriptionsForUserOnDocument(johnSubscription,
                annotatedDocumentModel);
        assertEquals(0, subscriptions.size());
        createAnnotation(annotatedDocumentModel, john, "Test message");
        transactionalFeature.nextTransaction();
        annotatedDocumentModel = session.getDocument(annotatedDocumentModel.getRef());
        subscriptions = notificationManager.getSubscriptionsForUserOnDocument(johnSubscription, annotatedDocumentModel);
        List<String> expectedSubscriptions = Arrays.asList(COMMENT_ADDED_NOTIFICATION, COMMENT_UPDATED_NOTIFICATION);
        assertEquals(expectedSubscriptions.size(), subscriptions.size());
        assertTrue(subscriptions.containsAll(expectedSubscriptions));
        for (String subscription : subscriptions) {
            notificationManager.removeSubscription(johnSubscription, subscription, annotatedDocumentModel);
        }
        createAnnotation(annotatedDocumentModel, john, "Test message again");
        transactionalFeature.nextTransaction();
            subscriptions = notificationManager.getSubscriptionsForUserOnDocument(johnSubscription, annotatedDocumentModel);
        assertTrue(subscriptions.isEmpty());
    }

    protected Annotation createAnnotationAndAddSubscription(String... notifications) {
        addSubscriptions(notifications);
        return createAnnotation(annotatedDocumentModel, ADMINISTRATOR, ANY_ANNOTATION_MESSAGE);
    }

    protected void addSubscriptions(String... notifications) {
        NuxeoPrincipal principal = session.getPrincipal();
        String subscriber = NotificationConstants.USER_PREFIX + principal.getName();
        for (String notif : notifications) {
            notificationManager.addSubscription(subscriber, notif, annotatedDocumentModel, false, principal, notif);
        }
    }

    protected Annotation createAnnotation(DocumentModel annotatedDocModel, String author, String text) {
        Annotation annotation = new AnnotationImpl();
        annotation.setAuthor(author);
        annotation.setText(text);
        annotation.setParentId(annotatedDocModel.getId());
        annotation.setXpath("files:files/0/file");
        annotation.setCreationDate(Instant.now());
        annotation.setModificationDate(Instant.now());
        ExternalEntity externalEntity = (ExternalEntity) annotation;
        externalEntity.setEntityId("foo");
        externalEntity.setOrigin("any origin");
        externalEntity.setEntity("<entity><annotation>bar</annotation></entity>");

        return annotationService.createAnnotation(session, annotation);
    }

    protected abstract Class<? extends CommentManager> getType();
}
