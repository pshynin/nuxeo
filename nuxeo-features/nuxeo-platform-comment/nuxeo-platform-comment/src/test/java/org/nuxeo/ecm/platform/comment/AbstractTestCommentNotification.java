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
import org.nuxeo.ecm.platform.comment.api.Comment;
import org.nuxeo.ecm.platform.comment.api.CommentImpl;
import org.nuxeo.ecm.platform.comment.api.CommentManager;
import org.nuxeo.ecm.platform.ec.notification.NotificationConstants;
import org.nuxeo.ecm.platform.notification.api.NotificationManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 11.1
 */
@RunWith(FeaturesRunner.class)
@Features(NotificationCommentFeature.class)
public abstract class AbstractTestCommentNotification {

    protected static final String COMMENT_ADDED_NOTIFICATION = "CommentAdded";

    protected static final String COMMENT_UPDATED_NOTIFICATION = "CommentUpdated";

    protected static final String ADMINISTRATOR = "Administrator";

    protected static final String ANY_COMMENT_MESSAGE = "any Comment message";

    @Inject
    protected NotificationManager notificationManager;

    @Inject
    protected CoreSession session;

    @Inject
    protected TransactionalFeature transactionalFeature;

    protected CommentManager commentManager;

    protected DocumentModel commentedDocumentModel;

    @Before
    public void before() {
        commentManager = getCommentManager();

        // Create the file under a domain is needed in this test context, due to the control of
        // NotificationEventListener#gatherConcernedUsersForDocument (doc.getPath().segmentCount() > 1)
        DocumentModel domain = session.createDocumentModel("/", "domain", "Domain");
        domain = session.createDocument(domain);
        commentedDocumentModel = session.createDocumentModel(domain.getPathAsString(), "test", "File");
        commentedDocumentModel = session.createDocument(commentedDocumentModel);
        transactionalFeature.nextTransaction();
    }

    /**
     * Overrides this method if you want to test the {@link org.nuxeo.ecm.platform.comment.impl.BridgeCommentManager}.
     */
    protected CommentManager getCommentManager() {
        return Framework.getService(CommentManager.class);
    }

    @Test
    public void shouldNotifyEventWhenCreateComment() {
        // We subscribe to the creation document to check that we will not be notified about the comment creation as
        // document (see CommentCreationVeto), only the comment added, and the 'File' document creation
        captureAndVerifyCommentEventNotification(() -> {
            Comment createdComment = createCommentAndAddSubscription(COMMENT_ADDED_NOTIFICATION, "Creation");
            return session.getDocument(new IdRef(createdComment.getId()));
        }, COMMENT_ADDED, DOCUMENT_CREATED);
    }

    @Test
    public void shouldNotifyEventWhenUpdateComment() {
        // We subscribe to the update document to check that we will not be notified about the comment updated as
        // document (see CommentModificationVeto), only the comment updated.
        Comment createdComment = createCommentAndAddSubscription(COMMENT_UPDATED_NOTIFICATION, "Modification");

        captureAndVerifyCommentEventNotification(() -> {
            createdComment.setText("I update the message");
            commentManager.updateComment(session, createdComment.getId(), createdComment);
            return session.getDocument(new IdRef(createdComment.getId()));
        }, COMMENT_UPDATED, DOCUMENT_UPDATED);
    }

    @Test
    public void shouldNotifyEventWhenRemoveComment() {
        Comment createdComment = createCommentAndAddSubscription("CommentRemoved");
        DocumentModel commentDocModel = session.getDocument(new IdRef(createdComment.getId()));
        commentDocModel.detach(true);

        captureAndVerifyCommentEventNotification(() -> {
            commentManager.deleteComment(session, createdComment.getId());
            return commentDocModel;
        }, COMMENT_REMOVED, DOCUMENT_REMOVED);
    }

    @Test
    public void shouldNotifyWithTheRightCommentedDocument() {
        // First comment
        Comment createdComment = createComment(commentedDocumentModel, ADMINISTRATOR, ANY_COMMENT_MESSAGE);
        DocumentModel createdCommentDocModel = session.getDocument(new IdRef(createdComment.getId()));
        // before subscribing, or previous event will be notified as well
        transactionalFeature.nextTransaction();
        // Reply
        captureAndVerifyCommentEventNotification(() -> {
            addSubscriptions(COMMENT_ADDED_NOTIFICATION);

            Comment reply = createComment(commentedDocumentModel, ADMINISTRATOR, ANY_COMMENT_MESSAGE);
            DocumentModel replyDocumentModel = session.getDocument(new IdRef(reply.getId()));
            return session.getDocument(new IdRef(replyDocumentModel.getId()));
        }, COMMENT_ADDED, DOCUMENT_CREATED);
    }

    protected void addSubscriptions(String... notifications) {
        NuxeoPrincipal principal = session.getPrincipal();
        String subscriber = NotificationConstants.USER_PREFIX + principal.getName();
        for (String notif : notifications) {
            notificationManager.addSubscription(subscriber, notif, commentedDocumentModel, false, principal, notif);
        }
    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.comment:OSGI-INF/notification-subscription-contrib.xml")
    public void testAutoSubscribingOnlyOnceToNewComments() {
        String john = "john";
        String johnSubscription = NotificationConstants.USER_PREFIX + john;
        createUser(john);
        List<String> subscriptions = notificationManager.getSubscriptionsForUserOnDocument(johnSubscription,
                commentedDocumentModel);
        assertEquals(0, subscriptions.size());
        createComment(commentedDocumentModel, john, "Test message");
        transactionalFeature.nextTransaction();
        commentedDocumentModel = session.getDocument(commentedDocumentModel.getRef());
        subscriptions = notificationManager.getSubscriptionsForUserOnDocument(johnSubscription, commentedDocumentModel);
        List<String> expectedSubscriptions = Arrays.asList(COMMENT_ADDED_NOTIFICATION, COMMENT_UPDATED_NOTIFICATION);
        assertEquals(expectedSubscriptions.size(), subscriptions.size());
        assertTrue(subscriptions.containsAll(expectedSubscriptions));
        for (String subscription : subscriptions) {
            notificationManager.removeSubscription(johnSubscription, subscription, commentedDocumentModel);
        }
        createComment(commentedDocumentModel, john, "Test message again");
        transactionalFeature.nextTransaction();
        subscriptions = notificationManager.getSubscriptionsForUserOnDocument(johnSubscription, commentedDocumentModel);
        assertTrue(subscriptions.isEmpty());
    }

    @Test
    public void testCommentManagerType() {
        assertEquals(getType(), commentManager.getClass());
    }

    protected void captureAndVerifyCommentEventNotification(Supplier<DocumentModel> supplier, String commentEventType,
            String documentEventType) {
        try (CapturingEventListener listener = new CapturingEventListener(commentEventType, documentEventType)) {
            DocumentModel commentDocumentModel = supplier.get();
            DocumentModel commentParentDocumentModel = session.getDocument(new IdRef(
                    (String) commentDocumentModel.getPropertyValue(COMMENT_PARENT_ID)));
            transactionalFeature.nextTransaction();

            assertTrue(listener.hasBeenFired(commentEventType));
            assertTrue(listener.hasBeenFired(documentEventType));

            List<Event> handledEvents = listener.streamCapturedEvents()
                                                .filter(e -> commentEventType.equals(e.getName()))
                                                .collect(Collectors.toList());

            assertEquals(1, handledEvents.size());

            checkDocumentEventContext(handledEvents.get(0), commentDocumentModel, commentParentDocumentModel,
                    commentedDocumentModel);
        }
    }

    protected Comment createComment(DocumentModel commentedDocModel, String author, String text) {
        Comment comment = new CommentImpl();
        comment.setAuthor(author);
        comment.setText(text);
        comment.setParentId(commentedDocModel.getId());

        return commentManager.createComment(session, comment);
    }

    protected Comment createCommentAndAddSubscription(String... notifications) {
        NuxeoPrincipal principal = session.getPrincipal();
        String subscriber = NotificationConstants.USER_PREFIX + principal.getName();
        for (String notif : notifications) {
            notificationManager.addSubscription(subscriber, notif, commentedDocumentModel, false, principal, notif);
        }

        return createComment(commentedDocumentModel, ADMINISTRATOR, ANY_COMMENT_MESSAGE);
    }

    protected abstract Class<? extends CommentManager> getType();
}
