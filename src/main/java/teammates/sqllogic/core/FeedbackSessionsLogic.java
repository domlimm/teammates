package teammates.sqllogic.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import teammates.common.exception.EntityAlreadyExistsException;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.InvalidParametersException;
import teammates.common.util.Logger;
import teammates.storage.sqlapi.FeedbackSessionsDb;
import teammates.storage.sqlentity.DeadlineExtension;
import teammates.storage.sqlentity.FeedbackQuestion;
import teammates.storage.sqlentity.FeedbackSession;

/**
 * Handles operations related to feedback sessions.
 *
 * @see FeedbackSession
 * @see FeedbackSessionsDb
 */
public final class FeedbackSessionsLogic {

    private static final String ERROR_NON_EXISTENT_FS_STRING_FORMAT = "Trying to %s a non-existent feedback session: ";
    private static final String ERROR_NON_EXISTENT_FS_UPDATE = String.format(ERROR_NON_EXISTENT_FS_STRING_FORMAT, "update");
    private static final String ERROR_FS_ALREADY_PUBLISH = "Error publishing feedback session: "
            + "Session has already been published.";

    private static final FeedbackSessionsLogic instance = new FeedbackSessionsLogic();

    private static final Logger log = Logger.getLogger();

    private FeedbackSessionsDb fsDb;
    private FeedbackQuestionsLogic fqLogic;
    private FeedbackResponsesLogic frLogic;
    private DeadlineExtensionsLogic deLogic;

    private FeedbackSessionsLogic() {
        // prevent initialization
    }

    public static FeedbackSessionsLogic inst() {
        return instance;
    }

    void initLogicDependencies(FeedbackSessionsDb fsDb, CoursesLogic coursesLogic,
            FeedbackResponsesLogic frLogic, FeedbackQuestionsLogic fqLogic,
            DeadlineExtensionsLogic deLogic) {
        this.fsDb = fsDb;
        this.frLogic = frLogic;
        this.fqLogic = fqLogic;
        this.deLogic = deLogic;
    }

    /**
     * Gets a feedback session.
     *
     * @return null if not found.
     */
    public FeedbackSession getFeedbackSession(UUID id) {
        assert id != null;
        return fsDb.getFeedbackSession(id);
    }

    /**
     * Gets a feedback session for {@code feedbackSessionName} and {@code courseId}.
     *
     * @return null if not found.
     */
    public FeedbackSession getFeedbackSession(String feedbackSessionName, String courseId) {
        assert feedbackSessionName != null;
        assert courseId != null;

        return fsDb.getFeedbackSession(feedbackSessionName, courseId);
    }

    /**
     * Gets all feedback sessions of a course, except those that are soft-deleted.
     */
    public List<FeedbackSession> getFeedbackSessionsForCourse(String courseId) {
        return fsDb.getFeedbackSessionEntitiesForCourse(courseId).stream()
                .filter(fs -> fs.getDeletedAt() == null)
                .collect(Collectors.toList());
    }

    /**
     * Gets all feedback sessions of a course started after time, except those that are soft-deleted.
     */
    public List<FeedbackSession> getFeedbackSessionsForCourseStartingAfter(String courseId, Instant after) {
        return fsDb.getFeedbackSessionEntitiesForCourseStartingAfter(courseId, after).stream()
                .filter(session -> session.getDeletedAt() == null)
                .collect(Collectors.toList());
    }

    /**
     * Creates a feedback session.
     *
     * @return created feedback session
     * @throws InvalidParametersException if the session is not valid
     * @throws EntityAlreadyExistsException if the session already exist
     */
    public FeedbackSession createFeedbackSession(FeedbackSession session)
            throws InvalidParametersException, EntityAlreadyExistsException {
        assert session != null;
        return fsDb.createFeedbackSession(session);
    }

    /**
     * Publishes a feedback session.
     *
     * @return the published feedback session
     * @throws InvalidParametersException if session is already published
     * @throws EntityDoesNotExistException if the feedback session cannot be found
     */
    public FeedbackSession publishFeedbackSession(String feedbackSessionName, String courseId)
            throws EntityDoesNotExistException, InvalidParametersException {

        FeedbackSession sessionToPublish = getFeedbackSession(feedbackSessionName, courseId);

        if (sessionToPublish == null) {
            throw new EntityDoesNotExistException(ERROR_NON_EXISTENT_FS_UPDATE + courseId + "/" + feedbackSessionName);
        }
        if (sessionToPublish.isPublished()) {
            throw new InvalidParametersException(ERROR_FS_ALREADY_PUBLISH);
        }

        sessionToPublish.setResultsVisibleFromTime(Instant.now());

        return sessionToPublish;
    }

    /**
     * Returns true if there are any questions for the specified user type (students/instructors) to answer.
     */
    public boolean isFeedbackSessionForUserTypeToAnswer(FeedbackSession session, boolean isInstructor) {
        if (!session.isVisible()) {
            return false;
        }

        return isInstructor
                ? fqLogic.hasFeedbackQuestionsForInstructors(session.getFeedbackQuestions(), false)
                : fqLogic.hasFeedbackQuestionsForStudents(session.getFeedbackQuestions());
    }

    /**
     * Returns true if the feedback session is viewable by the given user type (students/instructors).
     */
    public boolean isFeedbackSessionViewableToUserType(FeedbackSession session, boolean isInstructor) {
        // Allow user to view the feedback session if there are questions for them
        if (isFeedbackSessionForUserTypeToAnswer(session, isInstructor)) {
            return true;
        }

        // Allow user to view the feedback session if there are any question whose responses are visible to the user
        List<FeedbackQuestion> questionsWithVisibleResponses = new ArrayList<>();
        List<FeedbackQuestion> questionsForUser = session.getFeedbackQuestions();
        for (FeedbackQuestion question : questionsForUser) {
            if (!isInstructor && frLogic.isResponseOfFeedbackQuestionVisibleToStudent(question)
                    || isInstructor && frLogic.isResponseOfFeedbackQuestionVisibleToInstructor(question)) {
                // We only need one question with visible responses for the entire session to be visible
                questionsWithVisibleResponses.add(question);
                break;
            }
        }

        return session.isVisible() && !questionsWithVisibleResponses.isEmpty();
    }

    /**
     * Deletes the student email address for all their deadlines in the feedback sessions of the given course.
     */
    public void deleteFeedbackSessionsDeadlinesForStudent(String courseId, String emailAddress) {
        deleteFeedbackSessionsDeadlinesForUser(courseId, emailAddress, false);
    }

    private void deleteFeedbackSessionsDeadlinesForUser(String courseId, String emailAddress, boolean isInstructor) {
        updateFeedbackSessionsDeadlinesForUser(courseId, emailAddress, isInstructor,
                deadlines -> deadlines.remove(emailAddress));
    }

    private void updateFeedbackSessionsDeadlinesForUser(String courseId, String emailAddress, boolean isInstructor,
            Consumer<Map<String, Instant>> deadlinesUpdater) {
        List<FeedbackSession> feedbackSessions = fsDb.getFeedbackSessionEntitiesForCourse(courseId)
                .stream()
                .filter(session -> !session.isSessionDeleted())
                .collect(Collectors.toList());

        feedbackSessions.forEach(feedbackSession -> {
            if (isInstructor) {
                if (!instructorDeadlines.containsKey(emailAddress)) {
                    return;
                }

                deadlinesUpdater.accept(instructorDeadlines);
                updateOptionsBuilder.withInstructorDeadlines(instructorDeadlines);
            }

            FeedbackSessionAttributes.UpdateOptions.Builder updateOptionsBuilder = FeedbackSessionAttributes
                    .updateOptionsBuilder(feedbackSession.getFeedbackSessionName(), courseId);
            if (isInstructor) {
                Map<String, Instant> instructorDeadlines = feedbackSession.getInstructorDeadlines();
                if (!instructorDeadlines.containsKey(emailAddress)) {
                    return;
                }
                deadlinesUpdater.accept(instructorDeadlines);
                updateOptionsBuilder.withInstructorDeadlines(instructorDeadlines);
            } else {
                Map<String, Instant> studentDeadlines = feedbackSession.getStudentDeadlines();
                if (!studentDeadlines.containsKey(emailAddress)) {
                    return;
                }
                deadlinesUpdater.accept(studentDeadlines);
                updateOptionsBuilder.withStudentDeadlines(studentDeadlines);
            }
            try {
                fsDb.updateFeedbackSession(updateOptionsBuilder.build());
            } catch (InvalidParametersException | EntityDoesNotExistException e) {
                // Both Exceptions should not be thrown.
                log.severe("Unexpected error", e);
            }
        });
    }
    
}
